package singcli.platform;

import singcli.process.SingBoxProcessManager;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 让 Windows Native Image 的标准流编码与当前控制台代码页保持一致。
public final class ConsoleEncoding {
    private static final Pattern CODE_PAGE_PATTERN = Pattern.compile("(\\d+)");

    private ConsoleEncoding() {
    }

    public static void configure() {
        if (!SingBoxProcessManager.isWindows()
                || !NativeImageRuntime.isActive()) {
            return;
        }

        currentConsoleCharset().ifPresent(charset -> {
            System.setOut(new PrintStream(
                    new FileOutputStream(FileDescriptor.out), true, charset));
            System.setErr(new PrintStream(
                    new FileOutputStream(FileDescriptor.err), true, charset));
        });
    }

    // chcp 的提示文字会随系统语言变化，因此只提取输出中的代码页数字。
    private static Optional<Charset> currentConsoleCharset() {
        try {
            Process process = new ProcessBuilder("cmd.exe", "/d", "/c", "chcp")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
            if (process.waitFor() != 0) {
                return Optional.empty();
            }

            Matcher matcher = CODE_PAGE_PATTERN.matcher(output);
            int codePage = -1;
            while (matcher.find()) {
                codePage = Integer.parseInt(matcher.group(1));
            }
            if (codePage < 0) {
                return Optional.empty();
            }
            return Optional.of(codePage == 65001
                    ? StandardCharsets.UTF_8
                    : Charset.forName("cp" + codePage));
        } catch (IOException | IllegalArgumentException exception) {
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
