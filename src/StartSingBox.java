import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class StartSingBox {
    // start 命令固定读取平台配置目录下的 config.json。
    private static final Path CONFIG_PATH = AppPaths.configPath();

    public static void main(String[] args) {
        // 主流程统一放在 try 块中，任何启动失败都会打印错误并以非零状态退出。
        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            // 启动前先查找已有 sing-box 进程，避免多个进程同时占用端口或使用不同配置。
            List<ProcessHandle> running = ProcessSupport.findRunningSingBoxProcesses();
            if (!running.isEmpty()) {
                System.out.println("Detected an existing running sing-box process:");
                printProcessTable(running);
                // 用户不确认停止旧进程时，保持现状并正常退出。
                if (!confirm(scanner, "Stop the currently running sing-box process? (y/N): ")) {
                    System.out.println("Exited.");
                    return;
                }
                terminateProcesses(running);
            }

            if (!Files.exists(CONFIG_PATH)) {
                throw new IllegalArgumentException("config.json was not found: " + CONFIG_PATH.toAbsolutePath().normalize());
            }

            List<Path> candidates = findSingBoxBinaries();
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException("sing-box executable was not found in PATH or the installation directory: " + AppPaths.installationDirectory());
            }

            // 多个候选时让用户选择，之后用选中的二进制启动进程。
            Path singBox = chooseSingBox(scanner, candidates);
            startSingBox(singBox);
            System.out.println("sing-box started.");
        } catch (Exception e) {
            System.err.println("Start failed: " + e.getMessage());
            System.exit(1);
        }
    }

    // 打印检测到的进程列表，便于用户确认当前运行的是哪个 sing-box。
    private static void printProcessTable(List<ProcessHandle> processes) {
        System.out.printf("%-8s  %-20s  %s%n", "PID", "Command", "Arguments");
        for (ProcessHandle process : processes) {
            // Java 的 ProcessHandle.Info 可能拿不到命令或参数，所以都提供默认显示。
            ProcessHandle.Info info = process.info();
            String command = info.command().orElse("-");
            String arguments = info.arguments().map(args -> String.join(" ", args)).orElse("");
            System.out.printf("%-8d  %-20s  %s%n", process.pid(), command, arguments);
        }
    }

    // 读取 y/yes 确认，其它输入都按否处理，避免误停止已有进程。
    private static boolean confirm(Scanner scanner, String prompt) {
        System.out.print(prompt);
        String answer = scanner.nextLine().trim().toLowerCase(Locale.ROOT);
        return "y".equals(answer) || "yes".equals(answer);
    }

    // 先温和终止进程，超时后再强制终止，最后检查是否仍有残留。
    private static void terminateProcesses(List<ProcessHandle> processes) {
        for (ProcessHandle process : processes) {
            process.destroy();
        }
        waitForExit(processes, 3);
        // 仍未退出的进程使用强制终止，防止端口继续被占用。
        for (ProcessHandle process : processes) {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
        waitForExit(processes, 5);
        // 如果强制终止后仍有存活进程，就把 PID 返回给用户排查。
        List<ProcessHandle> stillAlive = processes.stream().filter(ProcessHandle::isAlive).toList();
        if (!stillAlive.isEmpty()) {
            throw new IllegalStateException("Some sing-box processes could not be stopped: " + processIds(stillAlive));
        }
    }

    // 在总超时时间内等待一组进程退出；单个进程等待失败不立即中断整体清理。
    private static void waitForExit(List<ProcessHandle> processes, long seconds) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        for (ProcessHandle process : processes) {
            long remaining = deadline - System.nanoTime();
            // 超时或进程已退出时跳过等待。
            if (remaining <= 0 || !process.isAlive()) {
                continue;
            }
            try {
                process.onExit().get(remaining, TimeUnit.NANOSECONDS);
            } catch (Exception ignored) {
            }
        }
    }

    // 把进程列表格式化成逗号分隔的 PID 字符串，用于错误消息。
    private static String processIds(List<ProcessHandle> processes) {
        List<String> ids = new ArrayList<>();
        for (ProcessHandle process : processes) {
            ids.add(Long.toString(process.pid()));
        }
        return String.join(", ", ids);
    }

    private static List<Path> findSingBoxBinaries() {
        Map<String, Path> candidates = new LinkedHashMap<>();
        // 优先允许用户把 sing-box 放在 singcli.jar 同目录，便于做成一个便携安装目录。
        addCandidate(candidates, AppPaths.installationDirectory().resolve(ProcessSupport.executableName()));

        String pathValue = System.getenv("PATH");
        if (pathValue != null && !pathValue.isBlank()) {
            for (String entry : pathValue.split(File.pathSeparator)) {
                // 跳过 PATH 中的空项，避免拼出无意义路径。
                if (entry.isBlank()) {
                    continue;
                }
                addCandidate(candidates, Path.of(entry, ProcessSupport.executableName()));
                // 非 Windows 下也兼容用户放了 sing-box.exe 的情况。
                if (!ProcessSupport.isWindows()) {
                    addCandidate(candidates, Path.of(entry, ProcessSupport.WINDOWS_BINARY_NAME));
                }
            }
        }

        return new ArrayList<>(candidates.values());
    }

    // 检查候选文件是否存在、可执行且文件名符合 sing-box，然后按绝对路径去重。
    private static void addCandidate(Map<String, Path> candidates, Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute) || !Files.isExecutable(absolute)) {
            return;
        }
        if (!ProcessSupport.isSingBoxFileName(absolute.getFileName().toString())) {
            return;
        }
        candidates.putIfAbsent(absolute.toString(), absolute);
    }

    // 只有一个候选时直接使用；多个候选时让用户按编号选择。
    private static Path chooseSingBox(Scanner scanner, List<Path> candidates) {
        if (candidates.size() == 1) {
            System.out.println("Found sing-box: " + candidates.get(0));
            return candidates.get(0);
        }

        // 打印所有候选路径，避免用户不知道将启动哪个二进制。
        System.out.println("Found multiple sing-box executables. Select one:");
        System.out.printf("%-4s  %s%n", "No.", "Path");
        for (int i = 0; i < candidates.size(); i++) {
            System.out.printf("%-4d  %s%n", i + 1, candidates.get(i));
        }
        System.out.print("Enter the number: ");
        String choice = scanner.nextLine().trim();
        try {
            // 输入必须落在候选列表范围内。
            int index = Integer.parseInt(choice);
            if (index >= 1 && index <= candidates.size()) {
                return candidates.get(index - 1);
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException("Invalid sing-box number: " + choice);
    }

    private static void startSingBox(Path singBox) throws IOException {
        Path config = CONFIG_PATH.toAbsolutePath().normalize();
        ProcessBuilder builder = new ProcessBuilder(
                singBox.toString(),
                "run",
                "-c",
                config.toString()
        );
        // 进程工作目录放在配置目录，方便配置里使用相对资源路径。
        builder.directory(AppPaths.configDirectory().toFile());
        // 当前工具不接管 sing-box 日志，启动输出直接丢弃。
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.start();
    }
}
