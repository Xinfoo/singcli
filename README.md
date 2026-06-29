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

## 环境要求

- Java 17 或更新版本
- Python 3
- `javac` 和 `jar` 已加入 `PATH`
- `sing-box` 位于 `PATH` 中，或放在生成后的 `singcli.jar` 同目录

## 构建

在仓库根目录运行：

```bash
python3 build.py
```

生成的 jar 文件位于：

```text
dist/singcli.jar
```

运行方式：

```bash
java -jar dist/singcli.jar
```

## Windows 安装

Windows 下使用根目录的 PowerShell 安装脚本安装预编译好的 jar。安装前需要确认项目目录中已经存在：

```text
dist\singcli.jar
```

安装脚本不会执行编译过程。因为这里安装的是 jar 和调用脚本，Windows 机器仍然需要已经安装 Java 17 或更新版本，并且 `java` 在 PATH 中。

默认安装目录是：

```text
C:\Program Files\singcli
```

不附带 `sing-box.exe` 的安装方式只会复制 `dist\singcli.jar`、`scripts\windows\singcli.cmd` 和卸载脚本到安装目录，并把安装目录加入 PATH。以管理员身份打开 PowerShell 后运行：

```powershell
.\install-windows.ps1
```

附带 `sing-box.exe` 的安装方式还会把项目 `sing-box` 目录中的 Windows 版 `sing-box.exe` 及其同目录 DLL 一起复制到安装目录：

```powershell
.\install-windows-with-sing-box.ps1
```

自定义安装目录：

```powershell
.\install-windows.ps1 -InstallDir "D:\apps\singcli"
```

如果不想写入系统 PATH，只写入当前用户 PATH：

```powershell
.\install-windows.ps1 -PathScope User
```

两个安装脚本都支持 `-InstallDir`、`-PathScope User` 和 `-Force`。安装脚本会检查 PATH 中是否已经存在其它 `singcli` 命令；如果存在，会中止以避免命令冲突。

卸载：

```powershell
.\uninstall-windows.ps1
```

如果安装到了自定义目录，卸载时也需要指定同一个目录：

```powershell
.\uninstall-windows.ps1 -InstallDir "D:\apps\singcli"
```

安装脚本也会把 `uninstall-windows.ps1` 复制到安装目录，之后也可以从安装目录运行卸载脚本。

## 命令行调用脚本

Linux 下可以使用 `scripts/linux/singcli` 作为包装脚本，把它放到 `/usr/bin/singcli` 后即可直接运行：

```bash
singcli start
```

Windows 下可以使用 `scripts/windows/singcli.cmd` 作为包装脚本。默认 jar 路径是：

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
switch   切换节点
set      设置 Windows 系统代理
unset    取消 Windows 系统代理
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

## 注意事项

- `switch` 命令依赖写入 `config.json` 的 Clash API。
- 默认本地代理地址是 `http://127.0.0.1:7897`，需要代理的应用可以手动使用这个地址。
- `set` 命令只在 Windows 下工作，会把配置里的本地代理地址写入当前用户的系统代理注册表，并刷新系统代理设置。
- `unset` 命令只在 Windows 下工作，会关闭当前用户的系统代理、清理自动配置 URL，并刷新系统代理设置。
- 如果检测到多个 `sing-box` 进程，`switch` 会要求用户选择要操作的进程。
- `switch` 会检查选中的进程是否正在使用 singcli 管理的同一个 `config.json`。如果不一致，会中止切换，但不会停止该进程。
- 在 Windows 上，如果 `sing-box` 由 singcli 启动，或启动时使用绝对配置路径，配置路径校验最可靠。
