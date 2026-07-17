package process;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

// Windows 进程信息兼容层：补充 ProcessHandle 经常无法返回的完整命令行。
final class WindowsProcessSupport {
    private WindowsProcessSupport() {
    }

    static Optional<String> commandLine(ProcessHandle process) {
        if (!ProcessSupport.isWindows()) {
            return Optional.empty();
        }

        String script = "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; "
                + "$p = Get-CimInstance Win32_Process -Filter 'ProcessId = " + process.pid() + "'; "
                + "if ($null -ne $p -and $null -ne $p.CommandLine) { [Console]::Out.Write($p.CommandLine) }";
        ProcessBuilder builder = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script
        );
        builder.redirectErrorStream(true);

        try {
            Process powershell = builder.start();
            if (!powershell.waitFor(3, TimeUnit.SECONDS)) {
                powershell.destroyForcibly();
                return Optional.empty();
            }
            String output = new String(powershell.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (powershell.exitValue() != 0 || output.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(output);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    static String displayArguments(String commandLine) {
        List<String> arguments = splitCommandLine(commandLine);
        return arguments.size() <= 1 ? "" : String.join(" ", arguments.subList(1, arguments.size()));
    }

    static List<String> splitCommandLine(String commandLine) {
        List<String> arguments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < commandLine.length(); i++) {
            char ch = commandLine.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(ch) && !quoted) {
                if (!current.isEmpty()) {
                    arguments.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }
        if (!current.isEmpty()) {
            arguments.add(current.toString());
        }
        return arguments;
    }
}
