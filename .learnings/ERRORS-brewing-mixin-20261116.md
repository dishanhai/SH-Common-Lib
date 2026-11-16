<!-- 作者：山海／dishanhai -->
# 酿造台数据包验证记录

## [ERR-20261116-001] NeoForge 的原版注册表不能在普通 JVM 直接引导

**Logged**: 2026-11-16T02:52:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: client

### Error

独立 JVM 调用 `Bootstrap.bootStrap()` 时，`FeatureFlagLoader.loadModdedFlags` 读取未初始化的 `LoadingModList`，出现 `ExceptionInInitializerError`。

### Root Cause

NeoForge 已修改原版启动流程，真实物品／药水注册表依赖 ModLauncher 和 FML；只有 Minecraft／NeoForge JAR 和编译类路径不足以模拟加载器生命周期。

### Fix

解析、文件优先级、坏文件隔离和不可变快照使用独立 JVM 验证；真实方块实体、酿造效果、Mixin 应用和远程客户端槽位仍须在加载器启动的实例中验证，不能把纯 JVM 测试当作游戏通过。

### Metadata

- Source: local_probe
- Related Files: internal/recipe/BrewingDatapack.java、neoforge/NeoForgeRecipeBrewing.java
- Tags: neoforge, bootstrap, datapack, runtime-test
