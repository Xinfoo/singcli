package singcli.platform;

// 集中判断当前代码是否正在 GraalVM Native Image 中运行。
public final class NativeImageRuntime {
    private static final String IMAGE_CODE_PROPERTY = "org.graalvm.nativeimage.imagecode";

    private NativeImageRuntime() {
    }

    public static boolean isActive() {
        return "runtime".equals(System.getProperty(IMAGE_CODE_PROPERTY));
    }
}
