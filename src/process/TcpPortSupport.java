package process;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// 检查指定进程是否拥有某个 TCP 监听端口。
final class TcpPortSupport {
    private TcpPortSupport() {
    }

    static boolean isListening(ProcessHandle process, int port) {
        return ProcessSupport.isWindows()
                ? isListeningOnWindows(process.pid(), port)
                : isListeningOnProcfs(process.pid(), port);
    }

    private static boolean isListeningOnProcfs(long pid, int port) {
        Path fdDirectory = Path.of("/proc", Long.toString(pid), "fd");
        try (var entries = Files.list(fdDirectory)) {
            Set<String> socketInodes = entries.map(path -> {
                        try {
                            return Files.readSymbolicLink(path).toString();
                        } catch (Exception ignored) {
                            // 进程运行期间 fd 可能随时关闭，单个链接读取失败不影响其它 fd。
                            return "";
                        }
                    })
                    .filter(value -> value.startsWith("socket:[") && value.endsWith("]"))
                    .map(value -> value.substring(8, value.length() - 1))
                    .collect(Collectors.toSet());
            return procfsContainsListener(Path.of("/proc/net/tcp"), port, socketInodes)
                    || procfsContainsListener(Path.of("/proc/net/tcp6"), port, socketInodes);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean procfsContainsListener(Path tcpFile, int port, Set<String> socketInodes) {
        try {
            for (String line : Files.readAllLines(tcpFile, StandardCharsets.US_ASCII)) {
                String[] fields = line.trim().split("\\s+");
                // 第 4 列的 0A 表示 TCP_LISTEN，第 10 列是 socket inode。
                if (fields.length < 10 || !"0A".equals(fields[3])) {
                    continue;
                }
                int colon = fields[1].lastIndexOf(':');
                if (colon >= 0 && Integer.parseInt(fields[1].substring(colon + 1), 16) == port
                        && socketInodes.contains(fields[9])) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean isListeningOnWindows(long pid, int port) {
        String script = "$c = Get-NetTCPConnection -State Listen -LocalPort " + port
                + " -ErrorAction SilentlyContinue | Where-Object OwningProcess -eq " + pid
                + "; if ($null -ne $c) { [Console]::Out.Write('true') }";
        ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-Command", script);
        builder.redirectErrorStream(true);
        try {
            Process powershell = builder.start();
            if (!powershell.waitFor(3, TimeUnit.SECONDS)) {
                powershell.destroyForcibly();
                return false;
            }
            String output = new String(powershell.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return powershell.exitValue() == 0 && "true".equalsIgnoreCase(output);
        } catch (Exception ignored) {
            return false;
        }
    }
}
