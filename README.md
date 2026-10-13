# singcli

singcli 是一个轻量的 `sing-box` 命令行辅助工具。

它只是一个非常简单的工具，默认只会设置一个本地代理入口。生成后的配置会提供这个本地代理地址：

```text
http://127.0.0.1:7897
```

它可以：

- 获取远程 sing-box 配置并生成规范化后的 `config.json`
- 自动补齐本工具需要的 mixed inbound 和 Clash API 配置
- 启动和停止 `sing-box`
- 通过 Clash API 切换 selector 节点

项目刻意不使用 Maven 或 Gradle。统一使用 Python 脚本调用 GraalVM Native Image 构建原生程序。

仓库包含 VS Code Java 配置。安装工作区推荐的 `Extension Pack for Java` 后，可使用 Java LSP、实时错误检查和源码调试功能；项目使用 GraalVM SDK，请在 `Java: Configure Java Runtime` 中选择 GraalVM 作为项目 JDK。按 `Ctrl+Shift+B`（macOS 为 `Cmd+Shift+B`）会执行默认构建任务并生成原生程序，`GraalVM: Run singcli` 任务会先构建再运行原生程序。运行和调试面板中的 `Debug singcli` 用于 JVM 下的 Java 源码调试。

## 环境要求

- Python 3
- GraalVM Native Image（包含 `javac`、`jar`、`native-image` 和 `jmods/org.graalvm.nativeimage.jmod` SDK 模块）
- Linux 原生构建需要 GCC 和 zlib 开发文件
- Linux 系统代理设置仅支持 KDE Plasma 6，需要 `kwriteconfig6` 位于 `PATH` 中
- `sing-box` 位于 `PATH` 中，或放在生成后的 singcli 程序同目录

## 构建

在仓库根目录运行：

```bash
python3 scripts/build.py
```

脚本依次从 `GRAALVM_HOME`、`JAVA_HOME`、`PATH` 和
`~/.local/lib/jvm/current/` 查找 GraalVM。Linux 下生成的原生程序位于：

```text
dist/singcli
```

运行方式：

```bash
./dist/singcli
```

Windows 下生成 `dist/singcli.exe`。构建过程中生成的 `build/native/singcli.jar` 仅作为 Native Image 的中间输入，`dist` 中只输出原生程序，运行时不需要 Java 或 JDK。

## Windows 安装包构建

Windows 安装器使用 `scripts/windows-installer.iss`，需要先构建
`dist/singcli.exe`，再通过 Inno Setup 命令行编译器生成安装包：
构建环境需要 Windows x64 版 GraalVM Native Image、MSVC 和 Inno Setup。

```powershell
python scripts\build.py
ISCC.exe scripts\windows-installer.iss
```

生成的安装器位于 `dist\windows`。只有项目根目录的 `sing-box` 目录中同时存在
`sing-box.exe`、`LICENSE` 和 `GPL-3.0.txt` 时，安装器才会自动包含 sing-box 及
同目录的 DLL；任意一个必需文件不存在时只打包 singcli。安装后的目录会用
`LICENSE-singcli.txt`、`LICENSE-sing-box.txt` 和 `GPL-3.0.txt` 区分相关许可证。

安装器支持管理员或当前用户安装、可选添加 PATH，并会在卸载时清理对应 PATH。
Windows 安装包面向 AMD64，默认安装到 `C:\Program Files\singcli`。

## Windows 安装

Windows 下统一使用 Inno Setup 生成的安装器。运行 `dist\windows` 中的安装程序，
根据向导选择安装目录、是否包含 sing-box，以及是否将安装目录加入 PATH。卸载时使用
Windows“已安装的应用”中的 singcli 卸载项。

安装器会安装 GraalVM Native Image 生成的 `singcli.exe`，运行时不需要 Java 或 JDK。

## 命令行调用

Arch Linux 软件包会把 GraalVM 生成的原生程序直接安装到 `/usr/bin/singcli`，运行时不需要 Java 或 JDK：

```bash
singcli start
```

Windows 安装时如果选择将安装目录加入 PATH，安装后可以直接在终端运行：

```bat
singcli start
```

## 命令

```bash
singcli [command]
```

可用命令：

```text
get      获取配置并生成 config.json
start    启动 sing-box
status   显示 sing-box 进程状态和当前节点
switch   切换节点
stop     停止 sing-box
set      设置系统代理
unset    取消系统代理
version  显示 singcli 版本号和构建信息
help     显示帮助信息
```

不带命令运行时，会进入交互式菜单。

## 配置文件位置

singcli 不再把 `config.json` 放在当前工作目录，而是放在系统配置目录中。

Linux：

```text
$XDG_CONFIG_HOME/singcli/config.json
```

如果未设置 `XDG_CONFIG_HOME`：

```text
~/.config/singcli/config.json
```

Windows：

```text
%APPDATA%\singcli\config.json
```

`get`、`start` 和 `switch` 命令都会使用同一个配置文件路径。

## sing-box 查找规则

启动 `sing-box` 时，singcli 会按以下顺序查找主程序：

1. singcli 程序所在目录
2. `PATH` 中的目录

因此你可以把 `sing-box` 安装到系统路径中，也可以直接把 `sing-box` 可执行文件放到 singcli 程序旁边。

## 常见用法

获取并规范化配置：

```bash
singcli get
```

启动 `sing-box`：

```bash
singcli start
```

查看运行状态和当前节点：

```bash
singcli status
```

切换节点：

```bash
singcli switch
```

停止 `sing-box`：

```bash
singcli stop
```

设置系统代理：

```bash
singcli set
```

取消系统代理：

```bash
singcli unset
```

查看 singcli 版本号、构建时间和构建 JDK：

```bash
singcli version
```

如果同时检测到多个 `sing-box` 进程，`status` 只显示实际监听 Clash API 9090 端口的进程。

## 注意事项

- `switch` 命令依赖写入 `config.json` 的 Clash API。
- 默认本地代理地址是 `http://127.0.0.1:7897`，需要代理的应用可以手动使用这个地址。
- Windows 下，`set` 通过 WinINet 的 `InternetSetOptionW` 设置 LAN 和拨号/VPN 连接的手动代理，代理地址取自配置中的本地入口（默认 `127.0.0.1:7897`），同时禁用 PAC 和自动检测。代理选项和默认排除规则与 Clash Verge Rev 一致，排除列表为 `localhost;127.*;192.168.*;10.*;172.16.*;172.17.*;172.18.*;172.19.*;172.20.*;172.21.*;172.22.*;172.23.*;172.24.*;172.25.*;172.26.*;172.27.*;172.28.*;172.29.*;172.30.*;172.31.*;<local>`。
- Windows 下，`unset` 通过同一 API 将这些连接切换为直连，保留已写入的代理地址、排除规则和未启用的 PAC URL。`set` / `unset` 均在写入后通知并刷新 WinINet 设置，通过当前用户身份运行 PowerShell，不主动请求管理员提权。代理仅在命令执行时设置一次，不提供代理守护或旧设置备份恢复。
- Linux 下，`set` 仅调用 `kwriteconfig6`，在当前用户的 `kioslaverc` 中写入 HTTP、HTTPS、SOCKS 代理地址和本地/私网绕过列表，并设置 `ProxyType=1`；`unset` 仅设置 `ProxyType=0`，无需配置文件。
- Linux 系统代理仅在执行 `set` / `unset` 时写入，不提供其它桌面适配、代理守护或旧设置备份恢复；关闭后保留已写入的代理地址和绕过列表。
- 如果检测到多个 `sing-box` 进程，`switch` 会要求用户选择要操作的进程。
- `switch` 会检查选中的进程是否正在使用 singcli 管理的同一个 `config.json`。如果不一致，会中止切换，但不会停止该进程。
- 在 Windows 上，如果 `sing-box` 由 singcli 启动，或启动时使用绝对配置路径，配置路径校验最可靠。
