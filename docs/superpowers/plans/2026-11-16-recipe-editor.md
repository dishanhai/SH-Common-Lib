<!-- 作者：山海／dishanhai -->
# 可视化配方工作台实现计划

> **For agentic workers:** Use subagent-driven-development for isolated implementation and review. Preserve all unrelated workspace changes; do not commit or deploy automatically.

**Goal:** 在 SHCLIB 三端提供原版配方及自定义 KubeJS 配方编辑，支持 JSON、KubeJS 和 Java 导出。

**Architecture:** ULK 提供不可变配方文档、版本生成器与本地工作区；UUK 提供通用可交互素材槽；SHCLIB 页面组合现有 SHBUI 控件。酿造 JSON 单独由加载器桥接注册，区别于原版数据包配方。

**Tech Stack:** Java、现有 Gradle Wrapper、Gson、SHBUI/UUK，Forge 1.20.1 与 NeoForge 1.21.1/26.1.2。

## 用户确认的范围

用户于 2026-11-16 确认首版包含原版合成、熔炉、高炉、酿造等，以及 KJS 内容、JSON 和 Java 导出。此范围替代早期设计草案中“首版仅原版、酿造与锻造不包含”的建议。原版类型包含有序、无序、四类烹饪、切石、锻造转换、锻造纹饰、药水酿造；自定义 JSON 用于其他序列化器并可生成 KubeJS `event.custom` 脚本。此轮不自行增加 GTCEu 硬依赖。

## 任务 1：ULK 文档、生成与工作区

文件：`common/src/main/java/com/dishanhai/sh_common_lib/api/ulk/recipe/`；测试：`common/src/test/java/com/dishanhai/sh_common_lib/api/ulk/recipe/`。

- [x] 先建立生成正确性与安全存储测试，确认缺少实现时失败。
- [x] 实现以下契约（所有 ID、路径、数字错误返回可本地化错误码）：

```java
record RecipeDocument(String id, RecipeType type, List<String> inputs,
        String result, int count, float experience, int cookingTime,
        String group, String rawJson) {}
record RecipeArtifact(String relativePath, String content) {}
record RecipeIssue(String key, String field) {}
// RecipeGenerator.validate(document, target, format)
// RecipeGenerator.generate(document, target, format)
// RecipeWorkspace.save/load/list/export
```

- [x] 验证三端原料/产物 schema、有序网格裁剪及 key 复用、纹饰无固定产物、药水酿造独立 schema、自定义 JSON 保真、脚本字符转义和非法数据拒绝。
- [x] 验证草稿读写往返、导出重名保护、路径穿越拒绝、损坏文件处理及无半成品写盘。

## 任务 2：UUK 通用素材槽

文件：SHBUI `UiIngredientSlots` 及纯状态 `IngredientSlotsState`，必要的平台对应实现与工厂。

- [x] 先验证拖放状态测试：按下、跨阈值、目标、释放提交、外部释放、Esc 和失焦取消。
- [x] 提供 ItemStack 网格、槽位名称、选择/清空/移动回调和 setItems；注册为一个键盘可聚焦控件。
- [x] 构建三个 SHBUI 版本，供 SHCLIB 依赖，不重复打包。

## 任务 3：酿造加载桥

文件：`common/.../internal/recipe/` 与各平台的新酿造桥接类，局部添加加载器初始化。

- [x] 从 `config/sh_common_lib/recipe_editor/brewing/` 读取 `{id,input,ingredient,output}`；input/output 是药水注册 ID，ingredient 是物品或标签。
- [x] 用各端真实酿造 API 注册；无效文件带路径与原因记录日志，不注册错误内容。
- [x] 与生成器核对 KubeJS 事件、JSON 安装方法、Java 注册 helper 的真实签名。

## 任务 4：工作台与入口

文件：`common/.../client/recipe/`，`ControlCenterModule` 两份实现，语言 JSON。

- [x] 按可用宽度使用三栏或分页布局；左侧类型与草稿，中间 UUK 网格，右侧属性与代码预览。
- [x] 支持手填 ID/标签、SHBUI 素材选择、数量/经验/耗时、自定义 JSON 编辑、撤销重做、保存与加载草稿。
- [x] 动态校验并使无效导出不可用；复制与导出均使用当前文档；写盘失败保留数据，重名覆盖需确认。
- [x] 返回保存保护、主题/可见焦点/旁白名称、中英文资源与帮助信息。

## 任务 5：集成与交付

- [x] 局部增加 `recipeEditorTest` JavaExec 验证任务；使用现有 wrapper 运行该任务与三端 `build`，预期退出码 0。
- [x] 检查三个 JAR 无 `com/dishanhai/sh_better_ui/` 类。
- [x] 审查规格范围与代码质量，修复后重跑相关验证。
- [x] 更新 README、设计实际范围及 TODO；未经游戏内验收不声明交互已验收。

构建命令（Git Bash；匹配 JDK 21/25）：

```bash
./gradlew recipeEditorTest build --no-daemon
# 在两个旧版构建目录运行上述命令。
./neoforge-1.21.1/gradlew -p neoforge-26.1.2 recipeEditorTest build --no-daemon
```

最终验证：SHCLIB 三端 `recipeEditorTest recipeImporterTest brewingDefinitionsTest build --no-daemon` 均退出 0；三个正式 JAR 均包含配方编辑器／导入器／注册表校验，均不包含 SHBUI 类。配套 SHBUI 三端完整构建通过。未启动客户端或部署产物，实际交互与数据加载验收见项目 TODO.md。
