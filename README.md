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

项目刻意不使用 Maven 或 Gradle。构建过程由一个简单的 Python 脚本调用 JDK 自带工具完成。

仓库包含 VS Code Java 配置。安装工作区推荐的 `Extension Pack for Java` 后，可直接使用 Java LSP、实时错误检查和调试功能；按 `Ctrl+Shift+B`（macOS 为 `Cmd+Shift+B`）会执行默认构建任务并生成 `dist/singcli.jar`，运行和调试面板中的 `Debug singcli` 可启动调试。

## 环境要求

- Java 17 或更新版本
- Python 3
- `javac` 和 `jar` 已加入 `PATH`
- `sing-box` 位于 `PATH` 中，或放在生成后的 `singcli.jar` 同目录

## 构建

在仓库根目录运行：

```bash
python3 scripts/build/build.py
```

生成的 jar 文件位于：

```text
dist/singcli.jar
```

运行方式：

```bash
java -jar dist/singcli.jar
```

## Windows 安装包构建

Windows 安装器使用 `scripts/build/windows-installer.iss`，需要先构建
`dist/singcli.jar`，再通过 Inno Setup 命令行编译器生成安装包：

```powershell
python scripts\build\build.py
ISCC.exe scripts\build\windows-installer.iss
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

安装内容仍然需要 Java 17 或更新版本，并且 `java` 位于 PATH 中。

## 命令行调用脚本

Linux 下可以使用 `scripts/linux/singcli` 作为包装脚本，把它放到 `/usr/bin/singcli` 后即可直接运行：

```bash
singcli start
```

Windows 下可以使用 `scripts/windows/singcli.cmd` 作为包装脚本。普通命令会在当前终端中
直接运行；只有设置或取消系统代理时才会请求 UAC 授权，并在管理员 PowerShell 中执行。
默认 jar 路径是：

```text
C:\Program Files\singcli\singcli.jar
```

把 `singcli.cmd` 放到 `PATH` 中的目录后，即可在终端运行：

```bat
singcli start
```

如果 jar 不在默认路径，可以设置环境变量 `SINGCLI_JAR`：

```bat
set SINGCLI_JAR=D:\apps\singcli\singcli.jar
singcli start
```

## 命令

```bash
java -jar singcli.jar [command]
```

可用命令：

```text
get      获取配置并生成 config.json
start    启动 sing-box
stop     停止 sing-box
status   显示 sing-box 进程状态和当前节点
switch   切换节点
set      设置 Windows 系统代理
unset    取消 Windows 系统代理
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

1. `singcli.jar` 所在目录
2. `PATH` 中的目录

因此你可以把 `sing-box` 安装到系统路径中，也可以直接把 `sing-box` 可执行文件放到 `singcli.jar` 旁边。

## 常见用法

获取并规范化配置：

```bash
java -jar singcli.jar get
```

启动 `sing-box`：

```bash
java -jar singcli.jar start
```

切换节点：

```bash
java -jar singcli.jar switch
```

设置 Windows 系统代理：

```bash
java -jar singcli.jar set
```

取消 Windows 系统代理：

```bash
java -jar singcli.jar unset
```

停止 `sing-box`：

```bash
java -jar singcli.jar stop
```

查看运行状态和当前节点：

```bash
java -jar singcli.jar status
```

查看 singcli 版本号、构建时间和构建 JDK：

```bash
java -jar singcli.jar version
```

如果同时检测到多个 `sing-box` 进程，`status` 只显示实际监听 Clash API 9090 端口的进程。

## 注意事项

- `switch` 命令依赖写入 `config.json` 的 Clash API。
- 默认本地代理地址是 `http://127.0.0.1:7897`，需要代理的应用可以手动使用这个地址。
- `set` 命令只在 Windows 下工作，会把配置里的本地代理地址写入当前用户的系统代理注册表，并刷新系统代理设置。
- `unset` 命令只在 Windows 下工作，会关闭当前用户的系统代理、清理自动配置 URL，并刷新系统代理设置。
- 如果检测到多个 `sing-box` 进程，`switch` 会要求用户选择要操作的进程。
- `switch` 会检查选中的进程是否正在使用 singcli 管理的同一个 `config.json`。如果不一致，会中止切换，但不会停止该进程。
- 在 Windows 上，如果 `sing-box` 由 singcli 启动，或启动时使用绝对配置路径，配置路径校验最可靠。
