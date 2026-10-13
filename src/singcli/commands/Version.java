package singcli.commands;

import singcli.app.BuildInfo;
import singcli.app.AppLog;

// version 命令：显示当前程序版本和构建时写入的元数据。
public final class Version {
    private Version() {
    }

    public static int run(String[] args) {
        BuildInfo buildInfo = BuildInfo.load();
        AppLog.info("version.displayed", "version", buildInfo.version(),
                "build_time", buildInfo.buildTime(), "build_jdk", buildInfo.buildJdk());
        System.out.println("singcli " + buildInfo.version());
        System.out.println("Build time: " + buildInfo.buildTime());
        System.out.println("Build JDK: " + buildInfo.buildJdk());
        return 0;
    }
}
