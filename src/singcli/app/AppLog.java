package singcli.app;

import singcli.json.JsonSyntax;
import singcli.platform.AppPaths;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.regex.Pattern;

// 同步追加操作日志；每次写入关闭文件，不创建后台线程或常驻进程。
public final class AppLog {
    private static final long PID = ProcessHandle.current().pid();
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)[a-z][a-z0-9+.-]*://[^\\s\"'<>]+");
    private static int sequence;
    private static CommandContext current;
    private static boolean writeFailureReported;

    private AppLog() {
    }

    // 命令行和交互菜单共用入口，记录命令开始、结果、退出码和耗时。
    public static int command(String command, IntSupplier action) {
        CommandContext previous = current;
        CommandContext context = new CommandContext(command, ++sequence);
        current = context;
        long started = System.nanoTime();
        int exitCode = 1;
        info("command.started");
        try {
            exitCode = action.getAsInt();
            return exitCode;
        } catch (RuntimeException | Error exception) {
            error("command.unhandled_error", exception);
            throw exception;
        } finally {
            String result = exitCode != 0 || "FAILED".equals(context.result)
                    ? "FAILED" : context.result;
            write("FAILED".equals(result) ? "ERROR" : "INFO", "command.finished",
                    "result", result, "exit_code", exitCode,
                    "duration_ms", (System.nanoTime() - started) / 1_000_000);
            current = previous;
        }
    }

    public static void info(String event, Object... fields) {
        write("INFO", event, fields);
    }

    public static void warning(String event, Object... fields) {
        write("WARN", event, fields);
    }

    public static void cancelled(String event, Object... fields) {
        result("CANCELLED");
        info(event, fields);
    }

    public static void skipped(String event, Object... fields) {
        result("SKIPPED");
        info(event, fields);
    }

    public static void failure(String event, Object... fields) {
        result("FAILED");
        write("ERROR", event, fields);
    }

    public static void error(String event, Throwable exception, Object... fields) {
        errorRedacted(event, exception, null, fields);
    }

    // get 的异常消息可能包含订阅 URL，先移除完整输入，再统一隐藏其它 URL。
    public static void errorRedacted(String event, Throwable exception, String sensitive, Object... fields) {
        result("FAILED");
        List<Object> details = new ArrayList<>(Arrays.asList(fields));
        details.add("error_type");
        details.add(exception.getClass().getName());
        details.add("error_message");
        details.add(redact(exception.getMessage(), sensitive));
        if (exception.getCause() != null) {
            details.add("cause_type");
            details.add(exception.getCause().getClass().getName());
            details.add("cause_message");
            details.add(redact(exception.getCause().getMessage(), sensitive));
        }
        StringWriter trace = new StringWriter();
        exception.printStackTrace(new PrintWriter(trace));
        details.add("stacktrace");
        details.add(redact(trace.toString(), sensitive));
        write("ERROR", event, details.toArray());
    }

    private static String redact(String value, String sensitive) {
        if (value == null) {
            return "";
        }
        return sensitive == null || sensitive.isEmpty() ? value : value.replace(sensitive, "[redacted]");
    }

    private static void result(String result) {
        if (current != null && !"FAILED".equals(current.result)) {
            current.result = result;
        }
    }

    private static synchronized void write(String level, String event, Object... fields) {
        // 日志写入失败只提示一次，不改变用户命令的原有执行结果。
        try {
            StringBuilder line = new StringBuilder(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .append(' ').append(level);
            field(line, "pid", PID);
            if (current != null) {
                field(line, "command", current.command);
                field(line, "operation", current.id);
            }
            field(line, "event", event);
            for (int i = 0; i + 1 < fields.length; i += 2) {
                field(line, String.valueOf(fields[i]), fields[i + 1]);
            }
            line.append(System.lineSeparator());
            Path path = AppPaths.singcliLogPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception exception) {
            if (!writeFailureReported) {
                writeFailureReported = true;
                System.err.println("Could not write singcli.log: " + exception.getClass().getSimpleName());
            }
        }
    }

    private static void field(StringBuilder line, String key, Object value) {
        String text = URL_PATTERN.matcher(String.valueOf(value)).replaceAll("[redacted-url]");
        line.append(' ').append(key).append("=\"").append(JsonSyntax.escapeString(text)).append('"');
    }

    private static final class CommandContext {
        private final String command;
        private final int id;
        private String result = "SUCCESS";

        private CommandContext(String command, int id) {
            this.command = command;
            this.id = id;
        }
    }
}
