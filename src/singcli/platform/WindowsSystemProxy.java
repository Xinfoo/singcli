package singcli.platform;

import singcli.process.SingBoxProcessManager;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import java.util.ArrayList;
import java.util.List;

// 通过 GraalVM 原生绑定设置当前用户的 Windows 代理，不启动 PowerShell。
// 代理选项和默认排除规则与 Clash Verge Rev 保持一致。
public final class WindowsSystemProxy {
    private static final String DEFAULT_BYPASS =
            "localhost;127.*;192.168.*;10.*;172.16.*;172.17.*;172.18.*;172.19.*;172.20.*;172.21.*;"
                    + "172.22.*;172.23.*;172.24.*;172.25.*;172.26.*;172.27.*;172.28.*;172.29.*;172.30.*;172.31.*;<local>";

    private WindowsSystemProxy() {
    }

    public static void set(String proxyAddress) {
        requireWindowsNativeImage();
        if (proxyAddress == null || proxyAddress.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Proxy address must be non-null and cannot contain NUL");
        }
        // 编译其它平台的 Native Image 时排除 Windows 绑定。
        if (Platform.includedIn(Platform.WINDOWS.class)) {
            WindowsNative.apply(true, proxyAddress);
        }
    }

    public static void unset() {
        requireWindowsNativeImage();
        if (Platform.includedIn(Platform.WINDOWS.class)) {
            WindowsNative.apply(false, null);
        }
    }

    private static void requireWindowsNativeImage() {
        if (!NativeImageRuntime.isActive() || !SingBoxProcessManager.isWindows()) {
            throw new IllegalStateException("Windows system proxy requires a Windows Native Image executable");
        }
    }

    public static final class WindowsHeaders implements CContext.Directives {
        @Override
        public boolean isInConfiguration() {
            return Platform.includedIn(Platform.WINDOWS.class);
        }

        @Override
        public List<String> getHeaderFiles() {
            return List.of("<windows.h>", "<wininet.h>", "<ras.h>");
        }

        @Override
        public List<String> getLibraries() {
            return List.of("kernel32", "wininet", "rasapi32");
        }
    }

    @Platforms(Platform.WINDOWS.class)
    @CContext(WindowsHeaders.class)
    private static final class WindowsNative {
        private static final int INTERNET_OPTION_PER_CONNECTION_OPTION = 75;
        private static final int INTERNET_OPTION_PROXY_SETTINGS_CHANGED = 95;
        private static final int INTERNET_OPTION_REFRESH = 37;
        private static final int INTERNET_PER_CONN_FLAGS = 1;
        private static final int INTERNET_PER_CONN_PROXY_SERVER = 2;
        private static final int INTERNET_PER_CONN_PROXY_BYPASS = 3;
        private static final int PROXY_TYPE_DIRECT = 1;
        private static final int PROXY_TYPE_PROXY = 2;
        private static final int ERROR_BUFFER_TOO_SMALL = 603;

        private static void apply(boolean enabled, String server) {
            // NULL 表示 LAN；其余项为当前用户和 AllUsers 电话簿中的拨号/VPN 连接。
            List<String> connections = rasConnections();
            connections.add(0, null);
            try (WideText serverText = new WideText(server);
                    WideText bypassText = new WideText(enabled ? DEFAULT_BYPASS : null)) {
                int optionCount = enabled ? 3 : 1;
                InternetOption options = UnmanagedMemory.calloc(
                        Math.multiplyExact(SizeOf.get(InternetOption.class), optionCount));
                if (options.isNull()) {
                    throw new OutOfMemoryError("Could not allocate Windows proxy options");
                }
                try {
                    options.option(INTERNET_PER_CONN_FLAGS);
                    // 手动代理仅启用 DIRECT | PROXY，同时禁用 PAC 和自动检测。
                    options.number(enabled ? PROXY_TYPE_DIRECT | PROXY_TYPE_PROXY : PROXY_TYPE_DIRECT);
                    if (enabled) {
                        options.addressOf(1).option(INTERNET_PER_CONN_PROXY_SERVER);
                        options.addressOf(1).text(serverText.pointer());
                        options.addressOf(2).option(INTERNET_PER_CONN_PROXY_BYPASS);
                        options.addressOf(2).text(bypassText.pointer());
                    }
                    InternetOptionList list = StackValue.get(InternetOptionList.class);
                    int listSize = SizeOf.get(InternetOptionList.class);
                    list.size(listSize);
                    list.count(optionCount);
                    list.options(options);
                    for (String connection : connections) {
                        try (WideText connectionText = new WideText(connection)) {
                            list.connection(connectionText.pointer());
                            list.error(0);
                            int result = internetSetOption(WordFactory.nullPointer(),
                                    INTERNET_OPTION_PER_CONNECTION_OPTION, list, listSize);
                            // 在构造错误消息、分配对象或释放内存之前读取线程错误码。
                            int error = getLastError();
                            if (result == 0) {
                                throw new IllegalStateException("InternetSetOptionW failed for "
                                        + (connection == null ? "LAN" : connection)
                                        + "; option index " + Integer.toUnsignedString(list.error())
                                        + "; Win32 error " + Integer.toUnsignedString(error));
                            }
                        }
                    }
                    notifyChange(INTERNET_OPTION_PROXY_SETTINGS_CHANGED);
                    notifyChange(INTERNET_OPTION_REFRESH);
                } finally {
                    UnmanagedMemory.free(options);
                }
            }
        }

        private static void notifyChange(int option) {
            int result = internetSetOption(WordFactory.nullPointer(), option, WordFactory.nullPointer(), 0);
            int error = getLastError();
            if (result == 0) {
                throw new IllegalStateException("InternetSetOptionW notification failed: " + option
                        + "; Win32 error " + Integer.toUnsignedString(error));
            }
        }

        private static List<String> rasConnections() {
            CIntPointer bytes = StackValue.get(CIntPointer.class);
            CIntPointer count = StackValue.get(CIntPointer.class);
            bytes.write(0);
            count.write(0);
            int result = rasEnumEntries(WordFactory.nullPointer(), WordFactory.nullPointer(),
                    WordFactory.nullPointer(), bytes, count);
            List<String> names = new ArrayList<>();
            if (result == 0 && count.read() == 0) {
                return names;
            }
            if (result != ERROR_BUFFER_TOO_SMALL) {
                throw rasError(result);
            }
            int entrySize = SizeOf.get(RasEntryName.class);
            // 电话簿可能在两次调用间变化，缓冲区不足时按 API 返回的大小重新枚举。
            while (true) {
                int allocated = bytes.read();
                if (allocated < entrySize) {
                    throw new IllegalStateException("RasEnumEntriesW returned an invalid buffer size");
                }
                RasEntryName entries = UnmanagedMemory.calloc(allocated);
                if (entries.isNull()) {
                    throw new OutOfMemoryError("Could not allocate Windows RAS entries");
                }
                try {
                    entries.size(entrySize);
                    result = rasEnumEntries(WordFactory.nullPointer(), WordFactory.nullPointer(),
                            entries, bytes, count);
                    if (result == ERROR_BUFFER_TOO_SMALL) {
                        continue;
                    }
                    if (result != 0) {
                        throw rasError(result);
                    }
                    int entryCount = count.read();
                    if (entryCount < 0 || entryCount > allocated / entrySize) {
                        throw new IllegalStateException("RasEnumEntriesW returned an invalid entry count");
                    }
                    for (int i = 0; i < entryCount; i++) {
                        String name = readEntryName(entries.addressOf(i).name());
                        if (name.isEmpty()) {
                            throw new IllegalStateException("RasEnumEntriesW returned an empty connection name");
                        }
                        names.add(name);
                    }
                    return names;
                } finally {
                    UnmanagedMemory.free(entries);
                }
            }
        }

        private static IllegalStateException rasError(int error) {
            return new IllegalStateException("RasEnumEntriesW failed: " + Integer.toUnsignedString(error));
        }

        private static String readEntryName(WideCharPointer text) {
            StringBuilder name = new StringBuilder();
            for (int i = 0; i <= maxEntryName(); i++) {
                char value = text.read(i);
                if (value == '\0') {
                    return name.toString();
                }
                name.append(value);
            }
            throw new IllegalStateException("RasEnumEntriesW returned an unterminated connection name");
        }

        // W 系列 Win32 API 使用以 NUL 结尾的 UTF-16 字符串，不能使用普通的 UTF-8 C 字符串。
        private static final class WideText implements AutoCloseable {
            private final WideCharPointer pointer;

            private WideText(String value) {
                if (value == null) {
                    pointer = WordFactory.nullPointer();
                    return;
                }
                pointer = UnmanagedMemory.malloc(Math.multiplyExact(Math.addExact(value.length(), 1), Character.BYTES));
                if (pointer.isNull()) {
                    throw new OutOfMemoryError("Could not allocate Windows UTF-16 text");
                }
                for (int i = 0; i < value.length(); i++) {
                    pointer.write(i, value.charAt(i));
                }
                pointer.write(value.length(), '\0');
            }

            private WideCharPointer pointer() {
                return pointer;
            }

            @Override
            public void close() {
                UnmanagedMemory.free(pointer);
            }
        }

        @CPointerTo(nameOfCType = "WCHAR")
        private interface WideCharPointer extends PointerBase {
            char read(int index);

            void write(int index, char value);
        }

        // 字段大小、偏移和联合体布局由 Windows SDK 头文件确定。
        @CStruct("INTERNET_PER_CONN_OPTIONW")
        private interface InternetOption extends PointerBase {
            InternetOption addressOf(int index);

            @CField("dwOption")
            void option(int value);

            @CField("Value.dwValue")
            void number(int value);

            @CField("Value.pszValue")
            void text(WideCharPointer value);
        }

        @CStruct("INTERNET_PER_CONN_OPTION_LISTW")
        private interface InternetOptionList extends PointerBase {
            @CField("dwSize")
            void size(int value);

            @CField("pszConnection")
            void connection(WideCharPointer value);

            @CField("dwOptionCount")
            void count(int value);

            @CField("dwOptionError")
            int error();

            @CField("dwOptionError")
            void error(int value);

            @CField("pOptions")
            void options(InternetOption value);
        }

        @CStruct("RASENTRYNAMEW")
        private interface RasEntryName extends PointerBase {
            RasEntryName addressOf(int index);

            @CField("dwSize")
            void size(int value);

            @CFieldAddress("szEntryName")
            WideCharPointer name();
        }

        @CConstant("RAS_MaxEntryName")
        private static native int maxEntryName();

        // BOOL 是 32 位整数，不能映射成单字节 Java boolean。
        @CFunction("InternetSetOptionW")
        private static native int internetSetOption(PointerBase internet, int option, PointerBase buffer, int bytes);

        @CFunction("RasEnumEntriesW")
        private static native int rasEnumEntries(WideCharPointer reserved, WideCharPointer phonebook,
                RasEntryName entries, CIntPointer bytes, CIntPointer count);

        @CFunction(value = "GetLastError", transition = CFunction.Transition.NO_TRANSITION)
        private static native int getLastError();
    }
}
