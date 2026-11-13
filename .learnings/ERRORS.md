# 构建经验

- HUD 在空间不足时，逐个尝试保留数据行若每次都新建面板，会重复测量之前所有行的文字；每次刷新数据都会再次触发。行高一致且宽度由最长文字决定时，应缓存每行测量并复用尺寸计算，同时核对缩放边界和原有优先级。
- NeoForge 26.1.2 的硬件遥测回退路径与旧版本不同：CPU、内存和磁盘原先在调用线程读取。HUD 开启这些项目时，应在后台限频采样并返回缓存，不能只检查旧版本的异步服务。
- 无线终端的已绑定网络不等于玩家当前可连接的网络。查询 AE2 HUD 数据时应使用终端自己的宿主检查范围和供电，避免只调用 `getLinkedGrid` 就暴露远程网络；扩展终端可能覆盖宿主和量子连接行为。

- Forge 1.20.1 和 NeoForge 1.21.1 构建可能被公告模块 `AnnouncementCatalog` 中缺少 `Locale` 符号阻断。核对该模块的当前工作区改动后再修复，并重跑受影响的构建。

- 为隔离的 Minecraft 1.21.1 客户端手写 `options.txt` 且含现代按键名称时，需写入 `version:3955`。缺少数据版本会让旧版按键转换器把 `key.keyboard.f9` 当整数解析，导致帧率上限和首次引导等其余选项也未加载。

- NeoForge 26.1.2 首轮资源重载期间，`GuiGraphicsExtractor.text` 会提交尚未加载的 `gui_text` 管线，异常发生在稍后的绘制阶段。启动浮层仅提交原版已预加载的填充管线。独立服务端的物品堆叠烟测应在 `ServerStartedEvent` 执行，`FMLCommonSetupEvent` 阶段物品组件仍可能未绑定。
- NeoForge 26.1.2.126 上，KubeJS 8.0.6 的 `WorldLoaderMixin` 无法注入。用 KubeJS 8.0.8、Rhino 2101.2.8-build.91 和 Better Advanced Tooltips 2601.1.0-build.9 运行 GameTest 时，`ShParcels` 已能从脚本创建密封货匣。

- Forge 1.20.1 和 NeoForge 1.21.1 的启动浮层绘完早期窗口后保留了从下往上的 framebuffer 投影。后接 GUI 绘制时，要恢复进入浮层前的投影和模型视图，并在游戏内用高 GUI 缩放复验坐标。
- 原版 `LevelLoadingScreen` 会把尚未收到状态的区块绘成不透明黑色。替换区块图时应保留原版背景与旁白更新，并从原版进度监听器读取进度。
- 初次资源加载时，翻译表可能早于字形提供器生效。实测启动浮层的中英文都会显示方框，启动指示条应独立于 Minecraft 的字体绘制。
- 装入 AE2 等模组后，加载器可能在 Minecraft 进度条下方追加原生进度条。原生显示多个进度时先让出 SHCLIB 指示条的区域，避免盖住加载状态。

- SH-Better-UI 的 NeoForge 26.1.2 内核构建即使有 JAR，也可能缺少 `UiScreen` 等客户端 API。消费模组构建前需要核对 JAR 中的 `META-INF/neoforge.mods.toml` 与所用 API。

- 双加载器共用的加载卡片用 `Font.substrByWidth(Component, width)` 裁剪带样式文本时，返回值是 `FormattedText`。先用 `Language.getVisualOrder` 生成 `FormattedCharSequence`，再传给 `GuiGraphics.drawString`，以保留样式和文字方向。
- Quick Play 可能在启动浮层的完成回调内同步开始读档。启动分析在实际进世界前结束，避免把读档同时记入启动报告和世界报告。

- 小窗口 HUD 的分数缩放会破坏 Minecraft 位图字形的细笔画。自动排版优先按物理像素对齐，并以原版字高作为省略数据行的默认阈值。小窗口或分数像素缩放时关闭字形柔边，继续独立绘制玻璃背景。验收用 F2 原始帧比较，避免窗口截图的二次缩放掩盖差异。

- NeoForge 离线构建若缺少 Modrinth 的 Mekanism 和 Applied Mekanistics 缓存，可分别通过 `-PmekanismJar` 和 `-PappliedMekanisticsJar` 指向对应版本的本地 JAR 后重跑。

- SHBUI 主控面板默认占用 F8，SHCLIB 的 HUD 位置编辑器在两者共存时应使用其他默认键。已有绑定仍由原版按键配置管理，SHCLIB 主控中心提供 HUD 布局编辑入口。
- 隔离 NeoForge 开发客户端时设置 ModDev 的 `run.gameDirectory`，保留其生成的 `--gameDir .`。额外传入 `--gameDir` 会让 ModLauncher 拒绝启动。
- 用整合包 JAR 跑隔离 NeoForge GameTest 时，直接把本地 JAR 加入 `runtimeOnly` 可能不会进入 ModLauncher 的模组清单。将 JAR 放入测试用 `gameDirectory/mods`，启动后核对模组清单。
- NeoForge 1.21.1 单独运行时也出现过 `glfwWaitEventsTimeout` 原生崩溃，Java 堆栈位于 `RenderSystem.limitDisplayFPS`。可用隔离测试目录的无上限帧率配置绕过该调用，并保留崩溃日志。
- NeoForge 离线构建缺少 Mekanism 或 Applied Mekanistics 时，按当前 `neoforge/build.gradle` 固定的 Modrinth 版本下载两份 JAR 到临时目录，再用 `-PmekanismJar` 和 `-PappliedMekanisticsJar` 指定。两项都是编译依赖，单独验收 HUD 时无需把它们安装进客户端。
- Forge 1.20.1 读取 1.21.1 的世界副本时，随机序列存档会报反序列化错误。双端客户端验收应分别复制对应 Minecraft 版本的测试世界。

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

- 为部分 Minecraft 版本单独增加语言资源目录时，避免与共用资源的 `assets/<namespace>/lang/<locale>.json` 同路径。Gradle 不会合并两份语言 JSON。公告使用独立资源命名空间，由 Minecraft 汇总翻译键。

- Forge 在 Windows 上执行 `processResources` 时曾报输出目录中的 `assets` 不存在，命令结束后该目录可正常读取。使用 `--no-watch-fs` 重跑构建成功，并从重新生成的 JAR 验证了预设资源。
- NeoForge 26.1.2 子项目没有单独的 Gradle Wrapper。从项目根目录用 `./neoforge/gradlew -p neoforge-26.1.2`，并为该构建指定 JDK 25。
- Minecraft 26.1.2 移除了 `Minecraft.tell` 与 `StringSplitter.componentStyleAtWidth`。需要等当前屏幕或输入事件结束时，用 `schedule(wrapRunnable(task))` 排队。点击已排版的公告文本时，用 `StringSplitter.stringWidth` 累加带样式字形的宽度，使行内图标也参与命中。
- 隔离 26.1.2 客户端验证主菜单自动公告时，将该实例的 `onboardAccessibility` 设为 `false`，否则游戏先停在辅助功能引导。验证 SHBUI 滑杆的方向键操作前，先给滑杆焦点并按原版输入模式进入调整状态。

- 更新公告的 `<!-- lang:en -->` 分区应保留正文中的 `<details>` 与 `<summary>`。只解开包住整段英文的旧语言切换外层，否则新版公告中的中文和英文折叠都会丢失。

- 离线验收公告位图字体时，Forge 1.20.1 的 `GlyphProviderDefinition.CODEC` 在 NeoForge 1.21.1 中不可用。两端可用 `BitmapProvider.Definition.CODEC.codec()` 读取位图定义，再用原版加载器检查字形宽度与 SHBUI 贴图资源。

- Git Bash 调用 Windows 命令时，MSYS 可能把 `tasklist /FI` 的选项转换成路径；可为该命令设置 `MSYS_NO_PATHCONV=1`。若 PATH 中的 Codex `rg` 在 Git Bash 下无法执行，先改用可执行的 Windows `rg.exe` 或其他只读查找方式。

- NeoForge 目录从 `neoforge` 改名为 `neoforge-1.21.1` 后，除批处理构建入口外，还要同步 26.1.2 的共享源码和资源路径，以及 1.21.1 使用的同级 SHBUI JAR 路径；分别无额外路径参数重跑两端构建。

- 多个 Codex 聊天共用工作区时，共享 Java 源码和 SHBUI 本地 JAR 可能暂处于接口迁移的中间状态。确认相关改动与依赖构建完成后再验证消费方，不能用迁移前的编译结果代表最终工作区。

- Minecraft 26.1.2 的 `ServerPlayer` 不再提供旧版的 `getServer()`；跨版本服务端线程检查应由平台实现分别通过旧版 `player.getServer()` 和新版 `player.level().getServer()` 获取服务器。
- 多来源物品扫描中，饰品栏或背包读取不完整也可能隐藏背包及无线终端。快照需要把不完整状态传播到依赖其发现过程的可用来源，不能将这些来源误报为未找到。
- Forge 1.20.1 的 `FriendlyByteBuf.writeBoolean` 返回 Netty `ByteBuf`；写完布尔值后重新使用原 `FriendlyByteBuf` 写 VarInt，不能继续链式调用 `writeVarInt`。
- Neo ECO AE 21.2.17 的 `NEComputationCluster.getActiveCPUCount()` 在联网时汇总整个运算网络；逐台 HUD 使用 `getActiveCPUs().size()` 表示该机器本地活动 CPU。

- 查询正在运行的 Minecraft JVM 时，完整启动参数可能含访问令牌；应只检查进程存在性或先在本地过滤所需非敏感字段，绝不将完整命令行写入工具输出。
- 多个聊天同时构建同一 NeoForge 输出目录会导致 Gradle 清理与写入冲突；等待其他构建结束后串行重跑。Minecraft 26.1.2 的 `AbstractWidget` 自定义绘制实现应使用 `extractWidgetRenderState`。

- 在 `Minecraft.setScreen` 触发的屏幕 `init` 中同步打开 UUK 页面，会在原屏幕切换尚未完成时挂载，触发 `A UI tree can only mount on the active native Screen`。将切换排队到当前切屏完成之后，并用实际客户端复验。

- Gradle 的自定义 `JavaExec` 任务可能使用运行 Gradle 的 Java 17，而项目源码由 Java 21 或 25 工具链编译；应显式设置 `javaLauncher` 为项目工具链，再运行测试。

- 当前 Windows 命令工具即使指定 Git Bash 也可能误触发 WSL 占位程序；可通过 Node 的 `execFile` 显式启动 `C:\Program Files\Git\bin\bash.exe`，继续使用 Git Bash。
