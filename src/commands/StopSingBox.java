import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

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
                ProcessSupport.printProcessTable(running);
                ProcessSupport.terminateProcesses(List.of(process));
                System.out.println("Stopped sing-box process: " + process.pid());
                return 0;
            }

            // 多个进程时打印带编号列表，由用户决定停止哪个或全部停止。
            System.out.println("Detected multiple sing-box processes:");
            ProcessSupport.printIndexedProcessTable(running);
            List<ProcessHandle> selected = chooseProcesses(scanner, running);
            // 用户直接回车表示取消操作，按正常退出处理。
            if (selected.isEmpty()) {
                return 0;
            }
            ProcessSupport.terminateProcesses(selected);
            System.out.println("Stopped sing-box processes: " + ProcessSupport.processIds(selected));
            return 0;
        } catch (Exception e) {
            System.err.println("Stop failed: " + ProcessSupport.errorMessage(e));
            return 1;
        }
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
}
