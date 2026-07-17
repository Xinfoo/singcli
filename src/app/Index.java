package app;

import commands.ConfigGet;
import commands.NodeSwitcher;
import commands.SetSystemProxy;
import commands.StartSingBox;
import commands.StatusSingBox;
import commands.StopSingBox;
import commands.UnsetSystemProxy;
import support.InputSupport;

import java.util.Scanner;

// 交互式首页菜单：让用户选择获取配置、启动、停止或切换节点。
class Index {
    static int run(String[] args) {
        // 首页和各个命令共用同一个 Scanner，命令返回后仍可继续读取输入。
        Scanner scanner = InputSupport.scanner();
        while (true) {
            printMenu();
            System.out.print("Select an action: ");
            if (!scanner.hasNextLine()) {
                return 0;
            }
            String choice = scanner.nextLine().trim();

            // 首页输入 0 时可以直接退出；空输入按无效选项处理并进入返回提示。
            if ("0".equals(choice)) {
                System.out.println("Exited.");
                return 0;
            }

            // 命令的退出码只表示本次操作结果，交互模式仍然回到统一的后续选择提示。
            switch (choice) {
                case "1" -> ConfigGet.run(args);
                case "2" -> StartSingBox.run(args);
                case "3" -> StopSingBox.run(args);
                case "4" -> NodeSwitcher.run(args);
                case "5" -> SetSystemProxy.run(args);
                case "6" -> UnsetSystemProxy.run(args);
                case "7" -> StatusSingBox.run(args);
                default -> System.err.println("Invalid action: " + choice);
            }

            System.out.println();
            System.out.print("Enter 0 to exit, or enter anything else to return to Index: ");
            if (!scanner.hasNextLine() || "0".equals(scanner.nextLine().trim())) {
                System.out.println("Exited.");
                return 0;
            }
            System.out.println();
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
        System.out.println("7. Show sing-box status");
        System.out.println();
    }
}
