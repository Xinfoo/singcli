import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

// 进程相关工具：识别 sing-box 进程，并从进程参数中推断配置文件路径。
final class ProcessSupport {
    // 不同平台上的 sing-box 可执行文件名。
    static final String UNIX_BINARY_NAME = "sing-box";
    static final String WINDOWS_BINARY_NAME = "sing-box.exe";

    // 工具类不需要实例化。
    private ProcessSupport() {
    }

    // 枚举系统所有进程，过滤出当前进程以外的 sing-box 进程。
    static List<ProcessHandle> findRunningSingBoxProcesses() {
        long currentPid = ProcessHandle.current().pid();
        return ProcessHandle.allProcesses()
                .filter(process -> process.pid() != currentPid)
                .filter(ProcessSupport::isSingBoxProcess)
                .toList();
    }

    // 判断文件名是否是 sing-box；Windows 文件名按大小写不敏感处理。
    static boolean isSingBoxFileName(String fileName) {
        return UNIX_BINARY_NAME.equals(fileName) || WINDOWS_BINARY_NAME.equalsIgnoreCase(fileName);
    }

    // 根据当前系统返回优先查找的可执行文件名。
    static String executableName() {
        return isWindows() ? WINDOWS_BINARY_NAME : UNIX_BINARY_NAME;
    }

    // 通过 os.name 判断当前运行系统是否为 Windows。
    static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    // 从进程启动参数中提取配置路径；相对路径会尝试按进程工作目录解析。
    static Optional<Path> configPath(ProcessHandle process) {
        Optional<Path> config = rawConfigPath(process);
        if (config.isEmpty()) {
            return Optional.empty();
        }
        Path path = config.get();
        // 绝对路径可以直接返回。
        if (path.isAbsolute()) {
            return Optional.of(path);
        }
        // 相对路径需要结合进程工作目录，否则无法和当前 config.json 比较。
        Optional<Path> workingDirectory = workingDirectory(process);
        return workingDirectory.map(directory -> directory.resolve(path));
    }

    // 打印不带编号的进程表。
    static void printProcessTable(List<ProcessHandle> processes) {
        System.out.printf("%-8s  %-20s  %s%n", "PID", "Command", "Arguments");
        for (ProcessHandle process : processes) {
            printProcessRow(process);
        }
    }

    // 打印带编号的进程表，用于用户从多个进程中选择。
    static void printIndexedProcessTable(List<ProcessHandle> processes) {
        System.out.printf("%-4s  %-8s  %-20s  %s%n", "No.", "PID", "Command", "Arguments");
        for (int i = 0; i < processes.size(); i++) {
            System.out.printf("%-4d  ", i + 1);
            printProcessRow(processes.get(i));
        }
    }

    // 打印单个进程信息；ProcessHandle.Info 取不到命令或参数时使用占位值。
    static void printProcessRow(ProcessHandle process) {
        ProcessHandle.Info info = process.info();
        String command = info.command().orElse("-");
        String arguments = info.arguments().map(args -> String.join(" ", args)).orElse("");
        System.out.printf("%-8d  %-20s  %s%n", process.pid(), command, arguments);
    }

    // 先温和终止进程，超时后强制终止，最后校验是否仍有残留。
    static void terminateProcesses(List<ProcessHandle> processes) {
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

    // 在指定秒数内等待进程退出；等待异常由后续 isAlive 统一判断。
    static void waitForExit(List<ProcessHandle> processes, long seconds) {
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

    // 把进程列表格式化成逗号分隔的 PID 字符串。
    static String processIds(List<ProcessHandle> processes) {
        List<String> ids = new ArrayList<>();
        for (ProcessHandle process : processes) {
            ids.add(Long.toString(process.pid()));
        }
        return String.join(", ", ids);
    }

    // 输出异常信息；异常没有 message 时退回异常类名。
    static String errorMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    // 只从命令行参数中提取 -c、--config 或 --config= 形式的原始配置路径。
    private static Optional<Path> rawConfigPath(ProcessHandle process) {
        Optional<String[]> arguments = process.info().arguments();
        if (arguments.isEmpty()) {
            return Optional.empty();
        }
        String[] values = arguments.get();
        for (int i = 0; i < values.length; i++) {
            String argument = values[i];
            // -c config.json 或 --config config.json：路径在下一个参数。
            if (("-c".equals(argument) || "--config".equals(argument)) && i + 1 < values.length) {
                return Optional.of(Path.of(values[i + 1]));
            }
            // --config=config.json：路径在等号之后。
            if (argument.startsWith("--config=")) {
                return Optional.of(Path.of(argument.substring("--config=".length())));
            }
        }
        return Optional.empty();
    }

    // 获取目标进程工作目录；Linux/Unix 下通过 /proc/<pid>/cwd 读取。
    private static Optional<Path> workingDirectory(ProcessHandle process) {
        // 当前实现没有 Windows 进程工作目录读取逻辑。
        if (isWindows()) {
            return Optional.empty();
        }
        Path cwdLink = Path.of("/proc", Long.toString(process.pid()), "cwd");
        try {
            // /proc/<pid>/cwd 是符号链接，toRealPath 会解析到真实目录。
            return Optional.of(cwdLink.toRealPath());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    // 判断一个进程是否看起来是 sing-box。
    private static boolean isSingBoxProcess(ProcessHandle process) {
        ProcessHandle.Info info = process.info();
        Optional<String> command = info.command();
        // 优先看可执行文件路径的文件名。
        if (command.isPresent() && isSingBoxFileName(Path.of(command.get()).getFileName().toString())) {
            return true;
        }
        // 有些启动方式 command 不是 sing-box，本工具再从参数中兜底识别。
        Optional<String[]> arguments = info.arguments();
        return arguments.isPresent() && argumentsContainSingBoxCommand(arguments.get());
    }

    // 检查参数列表里是否出现 sing-box 命令名。
    private static boolean argumentsContainSingBoxCommand(String[] arguments) {
        for (String argument : arguments) {
            if (UNIX_BINARY_NAME.equalsIgnoreCase(argument) || WINDOWS_BINARY_NAME.equalsIgnoreCase(argument)) {
                return true;
            }
        }
        return false;
    }
}
