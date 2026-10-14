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
- 将 `sing-box` 的标准输出和错误输出追加到 `sing-box.log`
- 在 `singcli.log` 中记录用户操作、命令结果和错误详情
- 通过 Clash API 切换 selector 节点

项目以 Java 25 为编译目标（`javac --release 25`），刻意不使用 Maven 或 Gradle。统一使用 Python 脚本调用 GraalVM Native Image 构建原生程序。

仓库包含 VS Code Java 配置。安装工作区推荐的 `Extension Pack for Java` 后，可使用 Java LSP、实时错误检查和源码调试功能。

`.vscode/settings.json` 中的 `java.configuration.runtimes` 已将 GraalVM 配置为默认项目 JDK，当前使用 `JavaSE-25`，路径为 `/home/xinfoo/.local/lib/jvm/current`。这个路径属于当前开发机器；在其它机器或 Windows 上开发时，需要将 `path` 改为本机 GraalVM 安装目录，并将 `name` 与实际 JDK 版本对应，保持 `default: true`。可以通过 `Java: Configure Java Runtime` 查看项目使用的 JDK。

Java LSP 的项目 JDK 配置与 `scripts/build.py` 的 GraalVM 查找规则相互独立。普通 JDK 无法解析本项目使用的 Native Image SDK 类型；如果编辑器提示 `The import org.graalvm cannot be resolved`，请确认默认项目 JDK 指向 GraalVM。修改配置后如果仍显示旧错误，可执行 `Java: Clean Java Language Server Workspace` 并重新加载窗口。

按 `Ctrl+Shift+B`（macOS 为 `Cmd+Shift+B`）会执行默认构建任务并生成原生程序，`GraalVM: Run singcli` 任务会先构建再运行原生程序。运行和调试面板中的 `Debug singcli` 用于 JVM 下的 Java 源码调试；Windows 的 `set` / `unset` 需要使用原生程序运行。

## 环境要求

- Python 3
- 基于 JDK 25 或更高版本的 GraalVM Native Image（包含 `javac`、`jar`、`native-image` 和 `jmods/org.graalvm.nativeimage.jmod` SDK 模块）
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
构建环境需要基于 JDK 25 或更高版本的 Windows x64 版 GraalVM Native Image、MSVC 和 Inno Setup。

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

## sing-box 日志

`start` 会自动创建日志目录，将 sing-box 的标准输出和错误输出合并追加到日志文件。

Linux：

```text
~/.local/state/singcli/sing-box.log
```

Windows：

```text
%LOCALAPPDATA%\singcli\sing-box.log
```

Windows 默认对应 `C:\Users\<用户名>\AppData\Local\singcli\sing-box.log`。

`get` 和 `start` 会将配置中的 `log` 统一为启用日志、`info` 级别、带时间戳，并移除
原来的 `log.output`，让正常日志进入启动时的输出重定向。`start` 同样处理已有配置，
无需重新获取配置。启动后会显示日志文件路径。

日志由 sing-box 进程直接写入文件，singcli 退出后仍会继续记录，不需要常驻守护进程。
每次启动会保留并追加已有日志，目前不提供日志轮转或自动清理。

## singcli 操作日志

singcli 自动创建并追加操作日志，和 `sing-box.log` 放在同一目录：

- Linux：`~/.local/state/singcli/singcli.log`
- Windows：`%LOCALAPPDATA%\singcli\singcli.log`

每条日志包含当前系统时区的时间戳、日志级别和 singcli PID。
命令相关日志还包含命令名称、操作编号；命令结束时记录结果、退出码和耗时。
结果区分 `SUCCESS`（成功）、`FAILED`（失败）、`CANCELLED`（取消）和
`SKIPPED`（无需执行），用户取消不会记为成功。日志结果与原有进程退出码分别记录，
例如节点切换因配置不匹配中止时会记为失败，但保留原来的退出码行为。

记录内容包括：

- 程序启动和退出、命令行参数数量、未知命令、菜单选择和返回/退出。
- 获取配置的来源协议和主机、HTTP 状态、下载大小和耗时、配置处理和写入路径。
- sing-box 进程检测、用户确认、可执行文件选择、启动请求和子进程 PID、停止及强制终止。
- 状态查询、节点选择、配置路径校验、切换前后的节点、Clash API 方法和 HTTP 状态。
- 系统代理启用/关闭、代理地址、目标平台和版本信息查看。
- 失败时的异常类名、错误消息、直接原因及异常堆栈。

`get` 失败时还记录失败阶段和错误分类，包括 HTTP 错误、超时、DNS、TLS、无效 URL、
配置处理错误和文件写入错误；HTTP 错误会保留返回状态码。
订阅 URL 的路径、用户信息、查询参数以及配置内容不写入日志，异常中的 URL 会隐藏，
节点切换错误中的已知 Clash API secret 也会隐藏。日志字段中的换行会转义，便于逐行查看。

日志同步写入，无后台线程或常驻守护进程。每次启动时，在写入第一条日志之前检查
`singcli.log` 的大小：达到或超过 2 MiB（2,097,152 字节）时，将整份日志重命名为
`singcli-<最后一行的时间戳>.log`，随后向新的 `singcli.log` 写入日志；未达到阈值时继续追加。
例如，末行时间为 `2026-10-14T12:34:56.123456789+08:00` 时，归档文件名为
`singcli-2026-10-14_12-34-56+0800.log`，文件名精确到秒，保留时区并替换冒号以适用于 Windows。
如果同名归档已存在，会增加 `-1`、`-2` 等序号，避免覆盖；末行时间戳损坏时使用文件修改时间归档。

每次启动还会清理带时间戳的归档文件，最多保留 4 份，按文件名中的时间戳删除最早的文件，
不使用文件修改时间排序。大小检查仅在启动时执行，本次运行中日志仍可能超过 2 MiB。
归档和清理结果记录在当前日志中。如果日志维护或写入失败，只在终端提示一次，命令仍按原有流程执行。
启动成功日志表示已创建 sing-box 子进程，其后续运行状态与错误请查看 `sing-box.log` 或执行 `status`。

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
- Windows 下，`unset` 通过同一 API 将这些连接切换为直连，保留已写入的代理地址、排除规则和未启用的 PAC URL。`set` / `unset` 使用 GraalVM 原生绑定直接调用 Windows API，均在写入后通知并刷新 WinINet 设置，以当前用户身份执行，不主动请求管理员提权，也不启动 PowerShell。这两个命令仅支持 Windows 原生可执行文件，不支持 JVM 源码调试运行。代理仅在命令执行时设置一次，不提供代理守护或旧设置备份恢复。
- Linux 下，`set` 仅调用 `kwriteconfig6`，在当前用户的 `kioslaverc` 中写入 HTTP、HTTPS、SOCKS 代理地址和本地/私网绕过列表，并设置 `ProxyType=1`；`unset` 仅设置 `ProxyType=0`，无需配置文件。
- Linux 系统代理仅在执行 `set` / `unset` 时写入，不提供其它桌面适配、代理守护或旧设置备份恢复；关闭后保留已写入的代理地址和绕过列表。
- 如果检测到多个 `sing-box` 进程，`switch` 会要求用户选择要操作的进程。
- `switch` 会检查选中的进程是否正在使用 singcli 管理的同一个 `config.json`。如果不一致，会中止切换，但不会停止该进程。
- 在 Windows 上，如果 `sing-box` 由 singcli 启动，或启动时使用绝对配置路径，配置路径校验最可靠。
