import java.nio.charset.StandardCharsets;
import java.util.Scanner;

// 交互式首页菜单：让用户选择获取配置、启动、停止或切换节点。
public class Index {
    public static void main(String[] args) {
        // 从标准输入读取用户选择，使用 UTF-8 兼容中文环境。
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);

        // 展示菜单后读取一行；空输入等同于退出。
        printMenu();
        System.out.print("Select an action, or press Enter to exit: ");
        String choice = scanner.nextLine().trim();

        // 用户明确选择 0 或直接回车时正常结束。
        if (choice.isEmpty() || "0".equals(choice)) {
            System.out.println("Exited.");
            return;
        }

        // 根据菜单编号转发到对应模块；无效编号以失败状态退出。
        switch (choice) {
            case "1" -> ConfigGet.main(args);
            case "2" -> StartSingBox.main(args);
            case "3" -> StopSingBox.main(args);
            case "4" -> NodeSwitcher.main(args);
            case "5" -> SetSystemProxy.main(args);
            case "6" -> UnsetSystemProxy.main(args);
            default -> {
                System.err.println("Invalid action: " + choice);
                System.exit(1);
            }
        }
    }

    // 打印首页菜单内容。
    private static void printMenu() {
        System.out.println("=============== singcli ================");
        System.out.println("0. Exit");
        System.out.println("1. Fetch config and generate config.json");
        System.out.println("2. Start sing-box");
        System.out.println("3. Stop sing-box");
        System.out.println("4. Switch node");
        System.out.println("5. Set Windows system proxy");
        System.out.println("6. Unset Windows system proxy");
        System.out.println();
    }
}
