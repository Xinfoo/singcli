package process;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

// 通用进程工具：识别、展示和终止 sing-box 进程，并对外提供进程相关查询入口。
public final class ProcessSupport {
    public static final String UNIX_BINARY_NAME = "sing-box";
    public static final String WINDOWS_BINARY_NAME = "sing-box.exe";

    private ProcessSupport() {
    }

    // 枚举系统所有进程，过滤出当前进程以外的 sing-box 进程。
    public static List<ProcessHandle> findRunningSingBoxProcesses() {
        long currentPid = ProcessHandle.current().pid();
        return ProcessHandle.allProcesses()
                .filter(process -> process.pid() != currentPid)
                .filter(ProcessSupport::isSingBoxProcess)
                .toList();
    }

    // 判断文件名是否是 sing-box；Windows 文件名按大小写不敏感处理。
    public static boolean isSingBoxFileName(String fileName) {
        return UNIX_BINARY_NAME.equals(fileName) || WINDOWS_BINARY_NAME.equalsIgnoreCase(fileName);
    }

    public static String executableName() {
        return isWindows() ? WINDOWS_BINARY_NAME : UNIX_BINARY_NAME;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    public static boolean isListeningOnTcpPort(ProcessHandle process, int port) {
        return TcpPortSupport.isListening(process, port);
    }

    public static Optional<Path> configPath(ProcessHandle process) {
        return ProcessConfigPathSupport.find(process);
    }

    public static void printProcessTable(List<ProcessHandle> processes) {
        System.out.printf("%-8s  %-20s  %s%n", "PID", "Command", "Arguments");
        for (ProcessHandle process : processes) {
            printProcessRow(process);
        }
    }

    public static void printIndexedProcessTable(List<ProcessHandle> processes) {
        System.out.printf("%-4s  %-8s  %-20s  %s%n", "No.", "PID", "Command", "Arguments");
        for (int i = 0; i < processes.size(); i++) {
            System.out.printf("%-4d  ", i + 1);
            printProcessRow(processes.get(i));
        }
    }

    // ProcessHandle.Info 取不到 Windows 参数时，通过系统查询完整命令行作为兜底。
    public static void printProcessRow(ProcessHandle process) {
        ProcessHandle.Info info = process.info();
        String command = info.command().orElse("-");
        String arguments = info.arguments()
                .map(args -> String.join(" ", args))
                .or(() -> WindowsProcessSupport.commandLine(process)
                        .map(WindowsProcessSupport::displayArguments))
                .orElse("");
        System.out.printf("%-8d  %-20s  %s%n", process.pid(), command, arguments);
    }

    // 先温和终止进程，超时后强制终止，最后校验是否仍有残留。
    public static void terminateProcesses(List<ProcessHandle> processes) {
        if (processes.isEmpty()) {
            return;
        }
        for (ProcessHandle process : processes) {
            process.destroy();
        }
        waitForExit(processes, 3);
        for (ProcessHandle process : processes) {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
        waitForExit(processes, 5);
        List<ProcessHandle> stillAlive = processes.stream().filter(ProcessHandle::isAlive).toList();
        if (!stillAlive.isEmpty()) {
            throw new IllegalStateException("Some sing-box processes could not be stopped: " + processIds(stillAlive));
        }
    }

    public static void waitForExit(List<ProcessHandle> processes, long seconds) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        for (ProcessHandle process : processes) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0 || !process.isAlive()) {
                continue;
            }
            try {
                process.onExit().get(remaining, TimeUnit.NANOSECONDS);
            } catch (Exception ignored) {
            }
        }
    }

    public static String processIds(List<ProcessHandle> processes) {
        List<String> ids = new ArrayList<>();
        for (ProcessHandle process : processes) {
            ids.add(Long.toString(process.pid()));
        }
        return String.join(", ", ids);
    }

    public static String errorMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private static boolean isSingBoxProcess(ProcessHandle process) {
        ProcessHandle.Info info = process.info();
        Optional<String> command = info.command();
        if (command.isPresent() && isSingBoxFileName(Path.of(command.get()).getFileName().toString())) {
            return true;
        }
        return info.arguments().map(ProcessSupport::argumentsContainSingBoxCommand).orElse(false);
    }

    private static boolean argumentsContainSingBoxCommand(String[] arguments) {
        for (String argument : arguments) {
            if (UNIX_BINARY_NAME.equalsIgnoreCase(argument) || WINDOWS_BINARY_NAME.equalsIgnoreCase(argument)) {
                return true;
            }
        }
        return false;
    }
}
