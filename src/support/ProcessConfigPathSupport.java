package support;

import java.nio.file.Path;
import java.util.Optional;

// 从进程参数中读取 sing-box 配置路径，并尽量解析为绝对路径。
final class ProcessConfigPathSupport {
    private ProcessConfigPathSupport() {
    }

    static Optional<Path> find(ProcessHandle process) {
        Optional<Path> config = rawConfigPath(process);
        if (config.isEmpty()) {
            return Optional.empty();
        }
        Path path = config.get();
        if (path.isAbsolute()) {
            return Optional.of(path);
        }
        return workingDirectory(process).map(directory -> directory.resolve(path));
    }

    private static Optional<Path> rawConfigPath(ProcessHandle process) {
        Optional<String[]> arguments = process.info().arguments();
        if (arguments.isPresent()) {
            Optional<Path> config = rawConfigPath(arguments.get());
            if (config.isPresent()) {
                return config;
            }
        }
        if (ProcessSupport.isWindows()) {
            return WindowsProcessSupport.commandLine(process)
                    .map(WindowsProcessSupport::splitCommandLine)
                    .flatMap(values -> rawConfigPath(values.toArray(String[]::new)));
        }
        return Optional.empty();
    }

    private static Optional<Path> rawConfigPath(String[] arguments) {
        for (int i = 0; i < arguments.length; i++) {
            String argument = arguments[i];
            if (("-c".equals(argument) || "--config".equals(argument)) && i + 1 < arguments.length) {
                return Optional.of(Path.of(arguments[i + 1]));
            }
            if (argument.startsWith("--config=")) {
                return Optional.of(Path.of(argument.substring("--config=".length())));
            }
        }
        return Optional.empty();
    }

    // Linux/Unix 下通过 /proc/<pid>/cwd 解析相对配置路径。
    private static Optional<Path> workingDirectory(ProcessHandle process) {
        if (ProcessSupport.isWindows()) {
            return Optional.empty();
        }
        Path cwdLink = Path.of("/proc", Long.toString(process.pid()), "cwd");
        try {
            return Optional.of(cwdLink.toRealPath());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
