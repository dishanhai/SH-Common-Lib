# Git Bash 调用 Gradle 批处理超时

- 日期：2026-11-16
- 状态：resolved
- 现象：从 Git Bash 使用 `cmd.exe /c gradlew.bat build --no-daemon` 启动构建时，命令超时且未取得构建结果。
- 处理：改用 `cmd.exe //c gradlew.bat build --no-daemon`，避免 MSYS 转换 `/c` 参数；Forge 1.20.1 和两个 NeoForge 版本均成功构建。
- 复用：Windows 的 Git Bash 调用 `cmd.exe` 时注意路径参数转换，检查实际退出码和构建输出，勿将超时当作构建成功。

2026-11-18 补充：执行器的 `shell` 参数选择 Bash 时实际启动了 WSL。在未安装 WSL 发行版的环境，可通过 Node `execFile` 直接指定 Git 的 `bin/bash.exe` 完整路径，将 `-c` 和命令作为两个参数传入。用这种方式执行项目的 `./gradlew build --no-daemon` 已通过三端验证。

2026-11-19 补充：若 Git Bash 包装脚本把 `GRADLE_OPTS` 中的 `-Djava.io.tmpdir=C:\jtmp` 传成 `C:jtmp`，本次调用用 `GRADLE_OPTS="-Djava.io.tmpdir=C:/jtmp"` 即可，保留 Windows JVM 能识别的绝对路径。

2026-11-20 补充：SH-Better-UI Forge 在线构建时，MCPRepo 下载 Minecraft 客户端 POM 连接超时，随后 Kotlin 编译找不到 JOML、LWJGL 等类。依赖缓存已齐备时，用仓库的 Gradle 8.8 以 `build --offline --no-daemon --console=plain` 重跑可通过编译、测试和重映射打包。

2026-11-21 补充：在 Git Bash 用 `rg -Fx` 核对 Windows JDK 的 `jar tf` 输出时，行末的 CR 会导致精确匹配失败。先将列表经 `tr -d '\r'` 转为 LF，再匹配完整条目。SHCLIB 三个版本的 JAR 均已用此方法确认包含 ULK Markdown API。
