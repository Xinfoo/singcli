// 程序总入口：根据命令行参数分发到不同功能模块。
public class Main {
    public static void main(String[] args) {
        System.exit(run(args));
    }

    private static int run(String[] args) {
        // 没有传命令时进入交互式菜单。
        if (args.length == 0) {
            return Index.run(args);
        }

        // 当前只接受一个命令参数，多余参数直接打印用法并失败退出。
        if (args.length != 1) {
            printUsage();
            return 1;
        }

        // 按命令名称调用对应功能；help 和未知命令只打印用法。
        return switch (args[0].toLowerCase()) {
            case "get" -> ConfigGet.run(new String[0]);
            case "start" -> StartSingBox.run(new String[0]);
            case "stop" -> StopSingBox.run(new String[0]);
            case "switch" -> NodeSwitcher.run(new String[0]);
            case "set" -> SetSystemProxy.run(new String[0]);
            case "unset" -> UnsetSystemProxy.run(new String[0]);
            case "help", "-h", "--help" -> {
                printUsage();
                yield 0;
            }
            default -> {
                printUsage();
                yield 1;
            }
        };
    }

    // 输出命令行用法，退出码由 main 统一处理。
    private static void printUsage() {
        System.out.println("Usage: java -jar singcli.jar [command]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  get      Fetch config and generate config.json");
        System.out.println("  start    Start sing-box");
        System.out.println("  stop     Stop sing-box");
        System.out.println("  switch   Switch node");
        System.out.println("  set      Set Windows system proxy");
        System.out.println("  unset    Unset Windows system proxy");
        System.out.println("  help     Show this help message");
        System.out.println();
        System.out.println("No command: open the interactive index menu.");
    }
}
