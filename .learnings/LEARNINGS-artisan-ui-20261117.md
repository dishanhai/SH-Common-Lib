# 百工设置页跨版本 UI

- 状态：resolved
- 作者：dishanhai
- 日期：2026-11-17
- 26.1.2 的原生按钮使用 `onPress(InputWithModifiers)` 与 `extractContents(GuiGraphicsExtractor, ...)`，不能只替换 GuiGraphics 类名；原生设置行须保留独立适配。
- 小视口轮盘要同时验证顶部／底部操作位与两侧操作位的交叉重叠，不能只验证屏幕边界；UUK 几何测试已补边界约束。
- Forge 新增含 Minecraft 调用的 UI 类必须走正常重混淆构建。纯 API 增量打包不能当作已重混淆的 Forge 发行包；独立兼容任务限制为未混淆的 NeoForge 26。
- 多会话共用构建目录时，客户端必须读取构建完成后的固定依赖副本。2026-11-17 实机启动后，另一轮 SHBUI 构建重写了已挂载的 JAR，随后重新读取 pack.mcmeta 报 `ZipException: invalid distance too far back`；磁盘 JAR 的完整性检查正常，修改时间位于客户端加载与崩溃之间。不要据此修改正常的 pack.mcmeta 或压制 NeoForge 资源包校验。
