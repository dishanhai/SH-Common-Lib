# SHCLIB 构建依赖与 JDK

- 状态：resolved（已定位验证条件）
- 范围：NeoForge 1.21.1 设置提供器验证。
- 默认构建需要匹配当前源码的 SHBUI JAR。缺少 `neoforge-1.21.1/build/libs` 产物时，旧的 1.0.11 JAR 虽可通过 `-PshBetterUiJar` 指定，但缺少当前源码所需的 UI API，不能证明完整构建通过；应先构建匹配版本再重跑。
- 直接调用系统 `javac` 可能命中 JDK 17，而 NeoForge 1.21.1 已编译类需要 Java 21；独立验证应使用项目工具链 JDK 21。
