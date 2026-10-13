package singcli.platform;

// 与 Clash Verge Rev 的 Windows 手动代理选项和默认排除规则保持一致，只在命令执行时设置一次。
// 参考 https://github.com/clash-verge-rev/sysproxy-rs/blob/main/src/windows.rs
// 和 https://github.com/clash-verge-rev/clash-verge-rev/blob/main/src-tauri/src/core/sysopt.rs
public final class WindowsSystemProxy {
    private static final String DEFAULT_BYPASS =
            "localhost;127.*;192.168.*;10.*;172.16.*;172.17.*;172.18.*;172.19.*;172.20.*;172.21.*;"
                    + "172.22.*;172.23.*;172.24.*;172.25.*;172.26.*;172.27.*;172.28.*;172.29.*;172.30.*;172.31.*;<local>";

    // 通过 Windows 自带 PowerShell 的 C# P/Invoke 调用 WinINet，兼容 JAR 和 Native Image。
    private static final String API_SCRIPT = """
$source = @'
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Runtime.InteropServices;

public static class SingcliWindowsProxy {
    private const uint INTERNET_OPTION_PER_CONNECTION_OPTION = 75;
    private const uint INTERNET_OPTION_PROXY_SETTINGS_CHANGED = 95;
    private const uint INTERNET_OPTION_REFRESH = 37;
    private const uint INTERNET_PER_CONN_FLAGS = 1;
    private const uint INTERNET_PER_CONN_PROXY_SERVER = 2;
    private const uint INTERNET_PER_CONN_PROXY_BYPASS = 3;
    private const uint PROXY_TYPE_DIRECT = 1;
    private const uint PROXY_TYPE_PROXY = 2;
    private const uint ERROR_BUFFER_TOO_SMALL = 603;

    // FILETIME 使联合体在 32 位下也保持 8 字节；IntPtr 决定 64 位下的对齐。
    [StructLayout(LayoutKind.Explicit)]
    private struct OptionValue {
        [FieldOffset(0)] public uint Number;
        [FieldOffset(0)] public IntPtr Text;
        [FieldOffset(0)] public System.Runtime.InteropServices.ComTypes.FILETIME FileTime;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct ConnectionOption {
        public uint Kind;
        public OptionValue Value;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct ConnectionOptionList {
        public uint Size;
        public IntPtr Connection;
        public uint Count;
        public uint Error;
        public IntPtr Options;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct RasEntryName {
        public uint Size;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 257)]
        public string Name;
        public uint Flags;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 261)]
        public string PhonebookPath;
    }

    [DllImport("wininet.dll", EntryPoint = "InternetSetOptionW", ExactSpelling = true, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool SetConnectionOptions(
        IntPtr handle, uint option, ref ConnectionOptionList buffer, uint size);

    [DllImport("wininet.dll", ExactSpelling = true, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool InternetSetOptionW(IntPtr handle, uint option, IntPtr buffer, uint size);

    [DllImport("rasapi32.dll", ExactSpelling = true)]
    private static extern uint RasEnumEntriesW(
        IntPtr reserved, IntPtr phonebook, IntPtr entries, ref uint bytes, out uint count);

    public static void Set(string server, string bypass) {
        Apply(true, server, bypass);
    }

    public static void Unset() {
        Apply(false, null, null);
    }

    private static void Apply(bool enabled, string server, string bypass) {
        // NULL 表示 LAN；其余项为用户和 AllUsers 电话簿中的拨号/VPN 连接。
        List<string> connections = GetRasConnections();
        connections.Insert(0, null);
        IntPtr serverText = IntPtr.Zero;
        IntPtr bypassText = IntPtr.Zero;
        IntPtr optionsBuffer = IntPtr.Zero;
        try {
            ConnectionOption[] options = new ConnectionOption[enabled ? 3 : 1];
            options[0].Kind = INTERNET_PER_CONN_FLAGS;
            // 与 CVR 相同：手动代理仅启用 DIRECT | PROXY，同时禁用 PAC 和自动检测。
            options[0].Value.Number = enabled ? PROXY_TYPE_DIRECT | PROXY_TYPE_PROXY : PROXY_TYPE_DIRECT;
            if (enabled) {
                serverText = Marshal.StringToHGlobalUni(server);
                bypassText = Marshal.StringToHGlobalUni(bypass);
                options[1].Kind = INTERNET_PER_CONN_PROXY_SERVER;
                options[1].Value.Text = serverText;
                options[2].Kind = INTERNET_PER_CONN_PROXY_BYPASS;
                options[2].Value.Text = bypassText;
            }
            int optionSize = Marshal.SizeOf(typeof(ConnectionOption));
            optionsBuffer = Marshal.AllocHGlobal(optionSize * options.Length);
            for (int i = 0; i < options.Length; i++) {
                Marshal.StructureToPtr(options[i], IntPtr.Add(optionsBuffer, i * optionSize), false);
            }
            ConnectionOptionList list = new ConnectionOptionList();
            list.Size = (uint)Marshal.SizeOf(typeof(ConnectionOptionList));
            list.Count = (uint)options.Length;
            list.Options = optionsBuffer;
            foreach (string connection in connections) {
                IntPtr connectionText = IntPtr.Zero;
                try {
                    if (connection != null) {
                        connectionText = Marshal.StringToHGlobalUni(connection);
                    }
                    list.Connection = connectionText;
                    list.Error = 0;
                    if (!SetConnectionOptions(IntPtr.Zero, INTERNET_OPTION_PER_CONNECTION_OPTION, ref list, list.Size)) {
                        int error = Marshal.GetLastWin32Error();
                        throw new Win32Exception(error, "InternetSetOptionW failed for " + (connection ?? "LAN")
                            + "; option index " + list.Error + "; Win32 error " + error);
                    }
                }
                finally {
                    Marshal.FreeHGlobal(connectionText);
                }
            }
            Notify(INTERNET_OPTION_PROXY_SETTINGS_CHANGED);
            Notify(INTERNET_OPTION_REFRESH);
        }
        finally {
            Marshal.FreeHGlobal(optionsBuffer);
            Marshal.FreeHGlobal(bypassText);
            Marshal.FreeHGlobal(serverText);
        }
    }

    private static void Notify(uint option) {
        if (!InternetSetOptionW(IntPtr.Zero, option, IntPtr.Zero, 0)) {
            int error = Marshal.GetLastWin32Error();
            throw new Win32Exception(error, "InternetSetOptionW notification failed: " + option + "; Win32 error " + error);
        }
    }

    private static List<string> GetRasConnections() {
        uint bytes = 0;
        uint count;
        uint result = RasEnumEntriesW(IntPtr.Zero, IntPtr.Zero, IntPtr.Zero, ref bytes, out count);
        List<string> names = new List<string>();
        if (result == 0 && count == 0) {
            return names;
        }
        if (result != ERROR_BUFFER_TOO_SMALL) {
            throw new Win32Exception((int)result, "RasEnumEntriesW failed: " + result);
        }
        int entrySize = Marshal.SizeOf(typeof(RasEntryName));
        // 电话簿可能在两次调用间变化；缓冲区不足时按 API 返回的大小重新枚举。
        while (true) {
            int allocated = checked((int)bytes);
            if (allocated < entrySize) {
                throw new InvalidOperationException("RasEnumEntriesW returned an invalid buffer size");
            }
            IntPtr buffer = Marshal.AllocHGlobal(allocated);
            try {
                Marshal.WriteInt32(buffer, entrySize);
                result = RasEnumEntriesW(IntPtr.Zero, IntPtr.Zero, buffer, ref bytes, out count);
                if (result == ERROR_BUFFER_TOO_SMALL) {
                    continue;
                }
                if (result != 0) {
                    throw new Win32Exception((int)result, "RasEnumEntriesW failed: " + result);
                }
                if (count > (uint)(allocated / entrySize)) {
                    throw new InvalidOperationException("RasEnumEntriesW returned an invalid entry count");
                }
                for (uint i = 0; i < count; i++) {
                    RasEntryName entry = (RasEntryName)Marshal.PtrToStructure(
                        IntPtr.Add(buffer, checked((int)i * entrySize)), typeof(RasEntryName));
                    if (String.IsNullOrEmpty(entry.Name)) {
                        throw new InvalidOperationException("RasEnumEntriesW returned an empty connection name");
                    }
                    names.Add(entry.Name);
                }
                return names;
            }
            finally {
                Marshal.FreeHGlobal(buffer);
            }
        }
    }
}
'@
Add-Type -TypeDefinition $source
""";

    private WindowsSystemProxy() {
    }

    public static void set(String proxyAddress) throws Exception {
        PowerShell.run(API_SCRIPT + "[SingcliWindowsProxy]::Set('"
                + singleQuoted(proxyAddress) + "', '" + DEFAULT_BYPASS + "')\n");
    }

    public static void unset() throws Exception {
        // 与 CVR 的 unset 相同，仅设置 DIRECT，保留代理地址、排除列表及未启用的 PAC URL。
        PowerShell.run(API_SCRIPT + "[SingcliWindowsProxy]::Unset()\n");
    }

    private static String singleQuoted(String value) {
        if (value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Proxy address cannot contain NUL");
        }
        return value.replace("'", "''");
    }
}
