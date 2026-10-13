package singcli.commands;

import singcli.platform.KdeSystemProxy;
import singcli.platform.WindowsSystemProxy;
import singcli.process.SingBoxProcessManager;

// 系统代理取消命令：关闭 Windows 或 Linux KDE 当前用户的系统代理。
public final class UnsetSystemProxy {
    private UnsetSystemProxy() {
    }

    public static int run(String[] args) {
        if (!SingBoxProcessManager.isWindows() && !SingBoxProcessManager.isLinux()) {
            System.err.println("Unset system proxy is only supported on Windows and Linux KDE (kwriteconfig6).");
            return 1;
        }

        try {
            if (SingBoxProcessManager.isWindows()) {
                WindowsSystemProxy.unset();
                System.out.println("Windows system proxy disabled.");
            } else {
                KdeSystemProxy.unset();
                System.out.println("Linux KDE system proxy disabled.");
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Unset system proxy failed: " + SingBoxProcessManager.errorMessage(e));
            return 1;
        }
    }
}
