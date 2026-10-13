package singcli.app;

import singcli.commands.ConfigGet;
import singcli.commands.NodeSwitcher;
import singcli.commands.SetSystemProxy;
import singcli.commands.StartSingBox;
import singcli.commands.StatusSingBox;
import singcli.commands.StopSingBox;
import singcli.commands.UnsetSystemProxy;
import singcli.commands.Version;

import java.util.Scanner;

// 交互式首页菜单：集中提供配置、sing-box、系统代理和版本信息相关操作入口。
public class Index {
    public static int run(String[] args) {
        // 首页和各个命令共用同一个 Scanner，命令返回后仍可继续读取输入。
        Scanner scanner = ConsoleInput.scanner();
        AppLog.info("menu.opened");
        while (true) {
            printMenu();
            System.out.print("Select an action: ");
            if (!scanner.hasNextLine()) {
                AppLog.info("menu.exited", "reason", "end_of_input");
                return 0;
            }
            String choice = scanner.nextLine().trim();
            AppLog.info("menu.selected", "choice", choice);

            // 首页输入 0 时可以直接退出；空输入按无效选项处理并进入返回提示。
            if ("0".equals(choice)) {
                AppLog.info("menu.exited", "reason", "user_exit");
                System.out.println("Exited.");
                return 0;
            }

            // 命令的退出码只表示本次操作结果，交互模式仍然回到统一的后续选择提示。
            switch (choice) {
                case "1" -> AppLog.command("get", () -> ConfigGet.run(args));
                case "2" -> AppLog.command("start", () -> StartSingBox.run(args));
                case "3" -> AppLog.command("status", () -> StatusSingBox.run(args));
                case "4" -> AppLog.command("switch", () -> NodeSwitcher.run(args));
                case "5" -> AppLog.command("stop", () -> StopSingBox.run(args));
                case "6" -> AppLog.command("set", () -> SetSystemProxy.run(args));
                case "7" -> AppLog.command("unset", () -> UnsetSystemProxy.run(args));
                case "8" -> AppLog.command("version", () -> Version.run(args));
                default -> {
                    AppLog.failure("menu.invalid_selection", "choice", choice);
                    System.err.println("Invalid action: " + choice);
                }
            }

            System.out.println();
            System.out.print("Enter 0 to exit, or enter anything else to return to Index: ");
            if (!scanner.hasNextLine()) {
                AppLog.info("menu.exited", "reason", "end_of_input");
                System.out.println("Exited.");
                return 0;
            }
            if ("0".equals(scanner.nextLine().trim())) {
                AppLog.info("menu.exited", "reason", "user_exit");
                System.out.println("Exited.");
                return 0;
            }
            AppLog.info("menu.returned");
            System.out.println();
        }
    }

    // 打印首页菜单内容。
    private static void printMenu() {
        System.out.println("=============== singcli ================");
        System.out.println("0. Exit");
        System.out.println("1. Fetch config and generate config.json");
        System.out.println("2. Start sing-box");
        System.out.println("3. Show sing-box status");
        System.out.println("4. Switch node");
        System.out.println("5. Stop sing-box");
        System.out.println("6. Set system proxy");
        System.out.println("7. Unset system proxy");
        System.out.println("8. Show version and build information");
        System.out.println();
    }
}
