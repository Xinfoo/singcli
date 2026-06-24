import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

// 停止 sing-box：列出当前运行进程，支持停止单个或全部。
class StopSingBox {
    static int run(String[] args) {
        // 停止流程统一处理异常，失败时以非零状态退出。
        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            // 先搜索所有疑似 sing-box 的进程。
            List<ProcessHandle> running = ProcessSupport.findRunningSingBoxProcesses();
            if (running.isEmpty()) {
                System.out.println("No running sing-box process was detected.");
                return 0;
            }

            // 只有一个进程时无需再询问编号，直接停止。
            if (running.size() == 1) {
                ProcessHandle process = running.get(0);
                System.out.println("Detected a sing-box process:");
                printProcessTable(running);
                terminateProcesses(List.of(process));
                System.out.println("Stopped sing-box process: " + process.pid());
                return 0;
            }

            // 多个进程时打印带编号列表，由用户决定停止哪个或全部停止。
            System.out.println("Detected multiple sing-box processes:");
            printIndexedProcessTable(running);
            List<ProcessHandle> selected = chooseProcesses(scanner, running);
            // 用户直接回车表示取消操作，按正常退出处理。
            if (selected.isEmpty()) {
                return 0;
            }
            terminateProcesses(selected);
            System.out.println("Stopped sing-box processes: " + processIds(selected));
            return 0;
        } catch (Exception e) {
            System.err.println("Stop failed: " + e.getMessage());
            return 1;
        }
    }

    // 打印不带编号的进程表，用于只有一个进程的场景。
    private static void printProcessTable(List<ProcessHandle> processes) {
        System.out.printf("%-8s  %-20s  %s%n", "PID", "Command", "Arguments");
        for (ProcessHandle process : processes) {
            printProcessRow(process);
        }
    }

    // 打印带编号的进程表，让用户按编号选择要停止的进程。
    private static void printIndexedProcessTable(List<ProcessHandle> processes) {
        System.out.printf("%-4s  %-8s  %-20s  %s%n", "No.", "PID", "Command", "Arguments");
        for (int i = 0; i < processes.size(); i++) {
            // 进程命令和参数都可能不可用，使用默认值保持表格可打印。
            ProcessHandle process = processes.get(i);
            ProcessHandle.Info info = process.info();
            String command = info.command().orElse("-");
            String arguments = info.arguments().map(args -> String.join(" ", args)).orElse("");
            System.out.printf("%-4d  %-8d  %-20s  %s%n", i + 1, process.pid(), command, arguments);
        }
    }

    // 打印单个进程行，供不带编号的表格复用。
    private static void printProcessRow(ProcessHandle process) {
        ProcessHandle.Info info = process.info();
        String command = info.command().orElse("-");
        String arguments = info.arguments().map(args -> String.join(" ", args)).orElse("");
        System.out.printf("%-8d  %-20s  %s%n", process.pid(), command, arguments);
    }

    // 解析用户选择：空输入取消，a/all 表示全部，数字表示单个编号。
    private static List<ProcessHandle> chooseProcesses(Scanner scanner, List<ProcessHandle> processes) {
        System.out.print("Enter the number to stop, enter a/all to stop all, or press Enter to exit: ");
        String choice = scanner.nextLine().trim();
        if (choice.isEmpty()) {
            System.out.println("Exited.");
            return List.of();
        }

        String normalized = choice.toLowerCase(Locale.ROOT);
        // 支持简写 a 和完整 all，方便停止所有检测到的进程。
        if ("a".equals(normalized) || "all".equals(normalized)) {
            return processes;
        }

        try {
            // 数字选择必须落在进程列表范围内。
            int index = Integer.parseInt(choice);
            if (index >= 1 && index <= processes.size()) {
                return List.of(processes.get(index - 1));
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException("Invalid process number: " + choice);
    }

    // 执行真正的停止逻辑：先正常终止，再强制终止，最后校验结果。
    private static void terminateProcesses(List<ProcessHandle> processes) {
        if (processes.isEmpty()) {
            return;
        }
        for (ProcessHandle process : processes) {
            process.destroy();
        }
        waitForExit(processes, 3);
        // 对仍然存活的进程升级为强制终止。
        for (ProcessHandle process : processes) {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
        waitForExit(processes, 5);
        // 如果仍有进程无法停止，抛出错误并展示 PID。
        List<ProcessHandle> stillAlive = processes.stream().filter(ProcessHandle::isAlive).toList();
        if (!stillAlive.isEmpty()) {
            throw new IllegalStateException("Some sing-box processes could not be stopped: " + processIds(stillAlive));
        }
    }

    // 在指定秒数内等待进程退出；等待异常被忽略，由后续 isAlive 统一判断。
    private static void waitForExit(List<ProcessHandle> processes, long seconds) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        for (ProcessHandle process : processes) {
            long remaining = deadline - System.nanoTime();
            // 没有剩余时间或进程已经结束时，无需继续等待。
            if (remaining <= 0 || !process.isAlive()) {
                continue;
            }
            try {
                process.onExit().get(remaining, TimeUnit.NANOSECONDS);
            } catch (Exception ignored) {
            }
        }
    }

    // 将进程列表转换成 PID 字符串，用于成功提示和错误提示。
    private static String processIds(List<ProcessHandle> processes) {
        List<String> ids = new ArrayList<>();
        for (ProcessHandle process : processes) {
            ids.add(Long.toString(process.pid()));
        }
        return String.join(", ", ids);
    }
}
