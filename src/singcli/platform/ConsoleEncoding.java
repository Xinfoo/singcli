package singcli.platform;

import singcli.process.SingBoxProcessManager;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.List;

// 让 Windows Native Image 的标准流编码与当前控制台代码页保持一致。
public final class ConsoleEncoding {
    private ConsoleEncoding() {
    }

    public static void configure() {
        if (!SingBoxProcessManager.isWindows()
                || !NativeImageRuntime.isActive()) {
            return;
        }

        currentConsoleCharset().ifPresent(charset -> {
            System.setOut(new PrintStream(
                    new FileOutputStream(FileDescriptor.out), true, charset));
            System.setErr(new PrintStream(
                    new FileOutputStream(FileDescriptor.err), true, charset));
        });
    }

    // 直接读取当前控制台代码页，查询失败时保留标准流的默认编码。
    private static Optional<Charset> currentConsoleCharset() {
        if (!Platform.includedIn(Platform.WINDOWS.class)) {
            return Optional.empty();
        }
        try {
            int codePage = WindowsConsole.outputCodePage();
            if (codePage == 0) {
                return Optional.empty();
            }
            return Optional.of(codePage == 65001
                    ? StandardCharsets.UTF_8
                    : Charset.forName("cp" + codePage));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static final class WindowsHeaders implements CContext.Directives {
        @Override
        public boolean isInConfiguration() {
            return Platform.includedIn(Platform.WINDOWS.class);
        }

        @Override
        public List<String> getHeaderFiles() {
            return List.of("<windows.h>");
        }

        @Override
        public List<String> getLibraries() {
            return List.of("kernel32");
        }
    }

    @Platforms(Platform.WINDOWS.class)
    @CContext(WindowsHeaders.class)
    private static final class WindowsConsole {
        @CFunction(value = "GetConsoleOutputCP", transition = CFunction.Transition.NO_TRANSITION)
        private static native int outputCodePage();
    }
}
