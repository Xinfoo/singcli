package singcli.app;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 仅在启动时归档 singcli.log；不接管 sing-box 日志，也不在运行中检查大小。
final class LogArchive {
    private static final long MAX_LOG_BYTES = 2L * 1024 * 1024;
    private static final int MAX_ARCHIVES = 4;
    // 文件名精确到秒并保留时区，替换 Windows 文件名不允许的冒号。
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter
            .ofPattern("uuuu-MM-dd_HH-mm-ssxxxx", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    // 清理时兼容此前带小数秒的归档，统一计入四份保留上限。
    private static final DateTimeFormatter ARCHIVE_TIMESTAMP = DateTimeFormatter
            .ofPattern("uuuu-MM-dd_HH-mm-ss[.SSSSSSSSS]xxxx", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern ARCHIVE_NAME = Pattern.compile(
            "^singcli-(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}(?:\\.\\d{9})?[+-]\\d{4}(?:\\d{2})?)(?:-(\\d+))?\\.log$");

    private LogArchive() {
    }

    static Result prepare(Path logFile) throws IOException {
        Files.createDirectories(logFile.getParent());
        Path archived = null;
        boolean usedModificationTime = false;
        long size = Files.exists(logFile) ? Files.size(logFile) : 0;
        if (size >= MAX_LOG_BYTES) {
            OffsetDateTime timestamp;
            try {
                timestamp = OffsetDateTime.parse(lastLineTimestamp(logFile), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            } catch (DateTimeParseException exception) {
                // 末行损坏时保留整份日志，使用文件修改时间作为归档时间。
                timestamp = Files.getLastModifiedTime(logFile).toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
                usedModificationTime = true;
            }
            String baseName = "singcli-" + FILE_TIMESTAMP.format(timestamp);
            int suffix = 0;
            while (true) {
                Path target = logFile.resolveSibling(baseName + (suffix == 0 ? "" : "-" + suffix) + ".log");
                try {
                    Files.move(logFile, target);
                    archived = target;
                    break;
                } catch (FileAlreadyExistsException exception) {
                    // 同一时间戳也不覆盖已有归档，附加序号仍保留原始时间戳。
                    suffix++;
                }
            }
        }
        // 无论本次是否归档，都按文件名中的时间清理到最多四份。
        List<Path> deleted = cleanArchives(logFile.getParent());
        return new Result(archived, size, deleted, usedModificationTime);
    }

    // 从末尾按块查找最后一个非空行，只读取其时间戳，避免加载整份日志或巨大堆栈行。
    private static String lastLineTimestamp(Path path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            byte[] block = new byte[8192];
            long position = file.length();
            long lineStart = 0;
            boolean foundContent = false;
            boolean foundStart = false;
            while (position > 0 && !foundStart) {
                int length = (int) Math.min(block.length, position);
                position -= length;
                file.seek(position);
                file.readFully(block, 0, length);
                for (int i = length - 1; i >= 0; i--) {
                    byte value = block[i];
                    if (!foundContent) {
                        if (value == '\n' || value == '\r' || value == ' ' || value == '\t') {
                            continue;
                        }
                        foundContent = true;
                    }
                    if (value == '\n' || value == '\r') {
                        lineStart = position + i + 1;
                        foundStart = true;
                        break;
                    }
                }
            }
            if (!foundContent) {
                return "";
            }
            file.seek(lineStart);
            byte[] prefix = new byte[(int) Math.min(64, file.length() - lineStart)];
            file.readFully(prefix);
            String text = new String(prefix, StandardCharsets.UTF_8);
            int end = 0;
            while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
                end++;
            }
            return text.substring(0, end);
        }
    }

    private static List<Path> cleanArchives(Path directory) throws IOException {
        List<Archive> archives = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.toList()) {
                Matcher match = ARCHIVE_NAME.matcher(path.getFileName().toString());
                if (!Files.isRegularFile(path) || !match.matches()) {
                    continue;
                }
                try {
                    Instant timestamp = OffsetDateTime.parse(match.group(1), ARCHIVE_TIMESTAMP).toInstant();
                    int suffix = match.group(2) == null ? 0 : Integer.parseInt(match.group(2));
                    archives.add(new Archive(path, timestamp, suffix));
                } catch (DateTimeParseException | NumberFormatException exception) {
                    // 不删除名字相似但时间戳或序号无效的其它文件。
                }
            }
        }
        archives.sort(Comparator.comparing(Archive::timestamp).thenComparingInt(Archive::suffix)
                .thenComparing(archive -> archive.path().getFileName().toString()));
        List<Path> deleted = new ArrayList<>();
        for (int i = 0; i < archives.size() - MAX_ARCHIVES; i++) {
            Path path = archives.get(i).path();
            Files.delete(path);
            deleted.add(path);
        }
        return deleted;
    }

    record Result(Path archived, long previousBytes, List<Path> deleted, boolean usedModificationTime) {
    }

    private record Archive(Path path, Instant timestamp, int suffix) {
    }
}
