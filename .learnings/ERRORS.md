# 构建经验

- JEI 快捷键的翻译反馈需要着色时，保留 `Component.translatable` 返回的 `MutableComponent` 类型。`withStyle` 属于 `MutableComponent`，将三元表达式结果声明成 `Component` 会导致两端编译失败。

- Maven 发布：`MavenArtifact.builtBy` 需要任务或任务提供器。ForgeGradle 较晚注册 `reobfJar`，用闭包延迟查询任务提供器，确保发布时先重映射 JAR。
- Forge 1.20.1 的创造模式右键会把返回堆叠的数量恢复为使用前的数量。物品消耗自己并同步向背包发货时，发货可能填进手持槽。货匣在服务端的创造模式右键事件中完成拆封，避开这一步数量恢复。
- 在独立 JVM 中用 Rhino 验证货匣时，Forge 的原版物品注册表包装器在启动后锁定。探针通过 Forge 注册表注册临时测试物品，正式游戏仍由加载器的注册事件完成。
- 共用 HUD 编辑器自行绘制背景和控件，避免 1.21.1 的 `Screen.render` 在预览之上再画一次背景。原版容器默认把释放事件交给指针下的控件，跨边缘拖动需要把释放事件交还给开始拖动的控件。
- 两端 Gradle 项目位于公用库根目录的子目录，引用同级模组产物要从各子项目向上两级。两端的原版副手索引均由 `Inventory.SLOT_OFFHAND` 提供。
- SH-Better-UI 使用本地 JAR 依赖时，版本对应的文件缺失可能直到 Java 编译阶段才表现为大量类找不到；应核对两端配置版本及实际产物路径，并同步检查模组元数据中的最低版本。
- 移植 JEI 取无限盘时，需同时验证供给行为和元件工作台的编辑入口。AE2 创造存储元件可从工作台追加过滤项，单材料无限盘应使用独立物品、单目标数据组件和 AE2 存储元件处理器。交付时同步检查悬停提示和可复制的项目相对路径。
- 双端开发客户端并行运行时，Forge 1.20.1 曾在 `RenderSystem.limitDisplayFPS` 中触发 `glfw.dll` 访问冲突。保留 `hs_err_pid*.log`，关闭另一端后单独复测，再判断是否与功能改动有关。
- 使用同级 SH-Better-UI 产物时，Forge `reobfJar` 曾报 `zip END header not found`，NeoForge 开发客户端也曾在运行中找不到产物随后确认含有的 `NativeInput$Key`。先核对 JAR 的 ZIP 校验及所需类，再用 `-PshBetterUiJar` 固定校验过的副本跑构建和客户端，避免同级产物被重写。
