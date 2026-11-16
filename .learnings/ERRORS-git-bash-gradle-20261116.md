# Git Bash 调用 Gradle 批处理超时

- 日期：2026-11-16
- 状态：resolved
- 现象：从 Git Bash 使用 `cmd.exe /c gradlew.bat build --no-daemon` 启动构建时，命令超时且未取得构建结果。
- 处理：改用 `cmd.exe //c gradlew.bat build --no-daemon`，避免 MSYS 转换 `/c` 参数；Forge 1.20.1 和两个 NeoForge 版本均成功构建。
- 复用：Windows 的 Git Bash 调用 `cmd.exe` 时注意路径参数转换，检查实际退出码和构建输出，勿将超时当作构建成功。
