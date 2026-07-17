package singcli.commands;

import singcli.app.BuildInfo;

// version 命令：显示当前程序版本和构建时写入的元数据。
public final class Version {
    private Version() {
    }

    public static int run(String[] args) {
        BuildInfo buildInfo = BuildInfo.load();
        System.out.println("singcli " + buildInfo.version());
        System.out.println("Build time: " + buildInfo.buildTime());
        System.out.println("Build JDK: " + buildInfo.buildJdk());
        return 0;
    }
}
