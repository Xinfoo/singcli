// 程序总入口：根据命令行参数分发到不同功能模块。
public class Main {
    public static void main(String[] args) {
        // 没有传命令时进入交互式菜单。
        if (args.length == 0) {
            Index.main(args);
            return;
        }

        // 当前只接受一个命令参数，多余参数直接打印用法并失败退出。
        if (args.length != 1) {
            printUsageAndExit(1);
            return;
        }

        // 按命令名称调用对应功能；help 和未知命令只打印用法。
        switch (args[0].toLowerCase()) {
            case "get" -> ConfigGet.main(new String[0]);
            case "start" -> StartSingBox.main(new String[0]);
            case "stop" -> StopSingBox.main(new String[0]);
            case "switch" -> NodeSwitcher.main(new String[0]);
            case "help", "-h", "--help" -> printUsageAndExit(0);
            default -> printUsageAndExit(1);
        }
    }

    // 输出命令行用法，并用调用方指定的退出码结束程序。
    private static void printUsageAndExit(int exitCode) {
        System.out.println("Usage: java -jar singcli.jar [command]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  get      Fetch config and generate config.json");
        System.out.println("  start    Start sing-box");
        System.out.println("  stop     Stop sing-box");
        System.out.println("  switch   Switch node");
        System.out.println("  help     Show this help message");
        System.out.println();
        System.out.println("No command: open the interactive index menu.");
        System.exit(exitCode);
    }
}
