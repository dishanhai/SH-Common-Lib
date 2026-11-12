# SH-Common-Lib

山海的 Minecraft 公用库，用于维护跨模组复用的 API、物品和方块。各版本使用同一个模组 ID `sh_common_lib`，分别构建和安装。

| 构建目录 | Minecraft | 加载器 | JDK | Gradle |
| --- | --- | --- | --- | --- |
| `forge/` | 1.20.1 | Forge 47.4.16 | 21 | 8.8 |
| `neoforge/` | 1.21.1 | NeoForge 21.1.251 | 21 | 9.2.1 |
| `neoforge-26.1.2/` | 26.1.2 | NeoForge 26.1.2.126 | 25 | 9.2.1 |

各版本依赖独立安装的 SH-Better-UI 和对应版本的 KotlinForForge。客户端和服务端使用上表所列的 Java 版本。

| 平台 | SH-Better-UI | KotlinForForge |
| --- | --- | --- |
| Forge 1.20.1 | 1.0.6 或兼容的 1.x 版本 | 4.12.0～4.x |
| NeoForge 1.21.1 | 1.0.6 或兼容的 1.x 版本 | 5.10.0～5.x |
| NeoForge 26.1.2 | 1.0.7-alpha.1（Minecraft 26.1.2） | 6.2.0 |

构建默认从同级 `SH-Better-UI` 源码目录读取 Forge 的 `devJar` 或 NeoForge 的成品 JAR。也可用 `-PshBetterUiJar=<路径>` 指定。Forge 开发运行时的 KotlinForForge 默认来自该目录的 `libs/`，可用 `-PkotlinForForgeJar=<路径>` 指定。游戏内安装各平台的正式 JAR。

构建 Forge 1.20.1 或 NeoForge 1.21.1 时，将 `JAVA_HOME` 设为 Java 21，在对应目录运行：

```bash
./gradlew build
```

Windows 命令提示符可用 `gradlew.bat build`。成品在对应目录的 `build/libs/`，带 `-sources` 后缀的是源码包。开发环境可用 `./gradlew runClient` 或 `./gradlew runServer`。

构建 NeoForge 26.1.2 时，将 `JAVA_HOME` 设为 Java 25，在项目根目录使用 NeoForge 的 Gradle Wrapper：

```bash
./neoforge/gradlew -p neoforge-26.1.2 build
```

此构建读取同级 `SH-Better-UI/neoforge-26.1.2/build/libs/` 下的模组 JAR，也可用 `-PshBetterUiJar=<路径>` 指定。化学品无限元件集成等待 Mekanism 和 Applied Mekanistics 的兼容版本。

Windows 下可在项目根目录运行 `build-all.bat` 构建三个版本。将 `JAVA_HOME` 设为 Java 21，如果 Gradle 无法自动发现 Java 25，则用 `JAVA25_HOME` 指定其安装目录。脚本会把通用参数传给三个构建。单独指定 SH-Better-UI 版本或 JAR 时，请调用对应版本的 Gradle 构建命令。

## 代码放在哪里

- `common/src/main/java/`：共用 API 与实现。保持 Java 17 兼容；只使用两端均兼容的类型。公共入口为 `com.dishanhai.sh_common_lib.api`。
- `common/src/main/resources/`：两端共用的语言文件、模型、贴图。只有路径和格式一致的资源才放在这里。
- `forge/src/main/` 和 `neoforge/src/main/`：加载器入口、物品和方块注册、版本专用实现与资源。例如方块掉落表在 1.20.1 使用 `loot_tables/blocks/`，在 1.21.1 使用 `loot_table/blocks/`。
- `neoforge-26.1.2/src/main/`：26.1.2 的专用实现和资源。同路径的 Java 文件覆盖 `common` 或 `neoforge` 中的实现。

每个通用物品和方块在两端使用同一个注册 ID，由各自加载器在注册阶段创建。

## SHCLIB 主控中心

在模组列表选择「SH-Common-Lib → 配置」，或进世界后按 **F10** 打开主控中心。已有按键绑定由 Minecraft 保留。如果旧配置仍使用 F9，可在「选项 → 控制 → 按键绑定」的「山海公用库」分类改绑。

「设置」里的「玩家信息 HUD」分组可以开关显示，并在世界中打开布局编辑器。开关立即保存，布局编辑器关闭后焦点回到原入口。「概览」提供密封货匣和 JEI 快捷键的使用提示。

「HUD · 玩家」「HUD · 世界」「HUD · 位置」「HUD · 服务器」和「HUD · 电脑」可逐项切换显示。TPS、MSPT、内存、CPU、GPU、磁盘默认关闭。

「设置 → 开发者 → 开发者配置」可进入开发者配置页，当前显示空状态。

SHCLIB 内部可在 `ControlCenterModule.settings` 中添加 `SettingsGroup`，用 `Toggle` 接入开关，用 `Page` 接入独立子页。有多个分组时，「设置」页会显示可滚动的分组导航。

## 加载动画与性能分析

在 SHCLIB 主控中心的「加载与性能」分组，可以独立开启加载动画和加载性能分析。两项默认关闭。进世界时在下一次进入单人或多人世界生效，启动时在下次重启客户端生效。

启动分析从 SHCLIB 客户端初始化开始，到初次加载浮层退出并进入主菜单为止。使用 Quick Play 时，开始进入世界即结束启动分析。进世界分析观察存档准备、连接、区块或地形等待，直到玩家可以操作、连接失败或加载中断。加载动画保留原生取消按钮和状态提示。启动卡片的进度条反映资源加载进度。

Forge 1.20.1 与 NeoForge 1.21.1 安装 AE2 时，加载界面显示实际观察到的客户端初始化状态。安装 JEI 时，进世界卡片显示世界数据等待、子类型、材料、配方分类、配方或运行时注册阶段。JEI 就绪后短暂显示注册材料数、配方分类数和回调间隔总耗时。JEI 在渲染线程连续完成的阶段可能跳过绘制，完成摘要仍可查看。

报告写入 `config/sh_common_lib/loading_reports/`，保留最近一次的 `latest-startup.json` 和 `latest-world.json`。JVM 支持时还生成对应的 `.jfr`。JSON 包含阶段耗时、进程 CPU、GC、观察到的堆用量、显式埋点及 JFR 热点。JFR 的 CPU 热点是样本计数，分配字节是估算值。各线程的并行耗时以及嵌套操作耗时不能相加当作总加载时间。

其他模组可通过客户端 API `LoadingAnalysis.currentSession()` 向当前加载过程提交埋点。使用 `PerformanceSession.measure` 记录执行、CPU 和分配量，使用 `trackReload` 区分 prepare/apply 执行与监听器等待，或用 `recordElapsed` 记录自己的阶段。客户端和服务端都可以创建独立的 `PerformanceSession` 对运行时操作采样，调用 `finish` 取出报告。`LoadingTemplate.render` 可在自己的加载界面中绘制使用 SH-Better-UI 主题的卡片。

## 玩家信息 HUD

Forge 1.20.1 和 NeoForge 1.21.1 的游戏 HUD 与位置编辑器通过 SH-Better-UI 绘制 Liquid Glass、轻微字形柔边和彩色像素图标。标签和强调文字按玻璃合成后的亮度范围调整对比度，优先保留原有色相。关闭背景模糊或暂时无法取样时，按面板的亮度范围选色。

NeoForge 26.1.2 的 HUD 配色由 SH-Better-UI 1.0.7-alpha.8 的同一套接口处理，以半透明底板合成后的亮度范围为依据。主控中心、货匣与 HUD 编辑器使用 Minecraft 原生控件。电脑读数包括内存、CPU、磁盘与渲染器名称，GPU 引擎利用率和显存读数等待对应的 SH-Better-UI 遥测服务。

进世界后，左上角默认显示玩家名、帧率、联机延迟、现实与游戏时间、维度、客户端已加载的区块与实体、群系、坐标、朝向和光照。群系温度是 Minecraft 的基础温度值。使用 F1 隐藏界面或打开 F3 调试信息时，玩家信息也会隐藏。

HUD 末行显示主控中心当前绑定的按键，默认是 **F10**。改绑后提示会跟随更新，解绑后会隐藏。窗口空间不足时，省略提示显示在快捷键上方。

按 **F6** 打开布局编辑器，或在 F10 主控中心选择「编辑 HUD 布局」。拖动信息块调整位置，或聚焦后用方向键移动，按住 Shift 时每次移动 10 个 GUI 像素。「HUD 大小」滑杆支持 50%–200%，默认为 75%。「重置布局」恢复默认大小和位置。点击「完成」保存，或按 Esc 取消本次修改。

设置保存在客户端 `config/sh_common_lib-player-info.properties`。`scalePercent` 保存请求的大小。HUD 的排版预算为 GUI 宽度的 55% 和高度的 50%。自动缩放优先对齐到整像素，并以 100% 或玩家主动设定的更小比例作为省略数据行的阈值。HUD 会优先保留玩家状态和坐标，并提示省略的行数。小窗口和分数像素缩放时，文字使用清晰字形，仍可使用 Liquid Glass 背景。窗口空间恢复后，已开启的项目会自动恢复显示。位置按可用空间的比例保存，编辑器会给底部工具栏预留空间。使用 `show.tps=true`、`show.mspt=true`、`show.memory=true`、`show.cpu=true`、`show.gpu=true`、`show.disk=true` 启用相应的读数。

TPS 用服务器相邻样本的实际 Tick 数与经过时间计算。MSPT 读取服务器提供的平均 Tick 耗时。单人世界使用内置服务器，联机需要服务端安装支持该协议的 SHCLIB。等待样本或服务端不支持协议时显示 `—`。

电脑指标读取本机系统内存、Java 堆、系统及 Java 进程 CPU 负载，以及游戏目录所在磁盘的使用量和总量。GPU 优先显示 Windows 可读取的最忙引擎利用率，其余情况显示可取得的适配器及显存信息。客户端模组可通过 `com.dishanhai.sh_common_lib.api.client.ComputerStatus.read(Set<Metric>)` 使用与 SHBUI 共用的只读快照，`Metric` 来自 `ComputerStatusService`。

## 方块复制快捷键

进世界后，将准星对准方块并按下快捷键。可在「选项 → 控制 → 按键绑定 → 山海公用库」改绑。

| 默认键位 | 剪贴板内容 |
| --- | --- |
| `Ctrl+I` | 方块实体 SNBT。NeoForge 1.21.1 同时包含可持久化的数据组件（如有） |
| `Ctrl+K` | 方块 ID 和当前状态属性，例如 `minecraft:furnace[facing=north,lit=false]` |

快捷键读取客户端世界。箱子内容等未同步到客户端的数据需要在服务端读取。未命中方块、没有可复制的方块实体数据或序列化失败时，保留原剪贴板内容。聊天、容器等界面打开时暂停处理。

消费模组可在方块所属世界线程调用两端同名的 `com.dishanhai.sh_common_lib.api.block.BlockInspection`：

```java
String state = BlockInspection.serializeState(level.getBlockState(pos));
BlockEntity entity = level.getBlockEntity(pos);
if (entity != null) {
    CompoundTag data = BlockInspection.saveBlockEntityData(entity, level.registryAccess());
}
```

使用服务端世界可导出服务端保存的数据。返回的方块实体标签省略类型 ID 和位置。

## JEI 快捷键

安装 JEI 后，在物品列表、书签、配方槽或容器内悬停材料，提示框会显示可用的快捷键。输入框有焦点时，按键继续交给输入框处理。

| 快捷键 | 操作 |
| --- | --- |
| `Ctrl+A` | 复制物品或流体的 NBT（Forge 1.20.1）或数据组件改动（NeoForge 1.21.1），格式为 SNBT |
| `Ctrl+C` | 复制显示名称 |
| `Ctrl+D` | 复制按字典顺序排列的第一个标签，带 `#` 前缀 |
| `Ctrl+X` | 复制注册 ID |
| `Ctrl+L` | 将 `[名称] 注册 ID` 填入聊天输入框，等玩家手动发送 |

Forge 1.20.1 同时装有 `gt_shanhai` 时，`Ctrl+A` 仍由本库处理，其余键盘快捷键由 `gt_shanhai` 提供。目标没有可复制数据时，提示栏会反馈，剪贴板保留原内容。

NeoForge 1.21.1 安装 AE2 并开启 JEI 作弊模式后，悬停物品或流体按 `Ctrl+Shift+左键`，松手时通过 JEI 请求发放 `sh_common_lib:infinity_cell`。同时安装 Mekanism 和 Applied Mekanistics 后，也可以用同样的操作绑定化学品。每张元件只绑定一种材料，物品和流体会保留数据组件。物品栏图标的左上角显示绑定材料的模型或原材质。元件工作台无法调整或追加材料。放入 ME 驱动器后可无限取出绑定的材料，存入同一材料会被元件吸收，每张元件待机消耗 8 AE/t。发放权限由 JEI 和服务端决定。

此功能以 JEI 19.69.0.456、AE2 19.2.18、Mekanism 10.7.19.85 和 Applied Mekanistics 1.6.3 构建。离线构建时，可以用 `-PmekanismJar=<JAR 路径>` 和 `-PappliedMekanisticsJar=<JAR 路径>` 指向本地副本。

## 密封货匣

`sh_common_lib:sealed_crate` 位于创造模式「工具与实用物品」分类。手持未封存货匣右键打开装填界面，最多放入 9 组物品。支持普通槽位操作和 Shift 点击转移。打开时锁住手里的货匣，每次改动立即保存，按 Esc 或背包键关闭。

也可以将物品拿在鼠标上，再右键背包里的未封存货匣直接放入。鼠标空手右键可取出排在末尾的一组。

手持货匣，潜行右键封存。封存后可以手持右键，或在玩家背包界面悬停货匣并双击中键。拆封消耗货匣，物品进入背包，剩余物品掉到玩家身边。悬停时预览前 5 项，按住 Shift 展开。

奖励或交易可通过两端同名 API 直接生成货匣。请在物品注册完成后调用。

```java
import com.dishanhai.sh_common_lib.api.parcel.Parcels;

ItemStack reward = Parcels.createSealed(List.of(new ItemStack(Items.IRON_INGOT, 16)));
```

`Parcels.createEmpty()` 创建未封存的空匣。`Parcels.contents(stack)` 返回内容副本。生成封存货匣时，每项必须是合法的单组物品，且不能装入另一个货匣。

其它物品也可以在客户端通过 `ItemContentPreview.register(predicate, provider)` 接入预览。Provider 返回标题和条目，条目使用 `Component` 名称和 `BigInteger` 数量。第二个参数是当前要显示的条数，提供者可以跳过剩余条目的名称解析，但应保留对应数量，以便汇总。

### KubeJS

安装对应版本的 KubeJS 后，可以使用全局对象 `ShParcels`。NeoForge 26.1.2 已验证 KubeJS 8.0.8、Rhino 2101.2.8-build.91 和 Better Advanced Tooltips 2601.1.0-build.9 的组合。把下列脚本放到 `kubejs/server_scripts/sealed_crate.js`，可以用一个铁锭合成装有两个铁粒的示例货匣。

```js
ServerEvents.recipes(event => {
  const reward = ShParcels.createSealed([
    Item.of('minecraft:iron_nugget', 2)
  ])
  event.shapeless(reward, ['minecraft:iron_ingot'])
    .id('kubejs:sealed_crate_example')
})
```

`ShParcels.createSealed([...])` 接收 1～9 组 `Item.of(...)` 生成的物品堆叠，并保留数量和附加数据。它返回普通物品堆叠，可作为合成产物或传给 `player.give(...)`。

`ShParcels.createEmpty()` 创建空匣。`ShParcels.isParcel(stack)` 检查物品类型，`ShParcels.isSealed(stack)` 检查封存状态，`ShParcels.contents(stack)` 返回内容副本数组，可用 `.length` 读取长度。

## 供其他模组使用

在对应加载器目录运行 `./gradlew publishToMavenLocal`。消费项目可通过 `mavenLocal()` 解析以下坐标：

| 平台 | 坐标 |
| --- | --- |
| Forge 1.20.1 | `com.dishanhai:sh-common-lib-forge-1.20.1:0.2.0` |
| NeoForge 1.21.1 | `com.dishanhai:sh-common-lib-neoforge-1.21.1:0.2.0` |

ForgeGradle 消费项目使用 `implementation fg.deobf("com.dishanhai:sh-common-lib-forge-1.20.1:0.2.0")`；NeoForge 消费项目直接使用对应的 `implementation` 坐标。
消费模组在自己的模组元数据中声明对 `sh_common_lib` 的依赖。游戏客户端和服务器在各自的 `mods` 目录安装对应版本的库 JAR。

## 全屏更新公告

Forge 1.20.1 与 NeoForge 1.21.1 的客户端可使用 `com.dishanhai.sh_common_lib.api.client.announcement.FullscreenAnnouncements`。将自己的公告目录传入构造器，在客户端线程调用 `openUnread(parent)` 自动展示未读版本，或调用 `openLatest(parent)`、`openDocument(parent, document)`、`openHistory(parent)` 主动打开。

公告按文件名中的三段版本号降序排列，支持 `Update-4.9.1.md` 这类文件名、`enabled`、`version`、`title`、`title_en` 和重复的 `link` 字段。英文正文放在 `<!-- lang:en -->` 后。点击「不再提示」会将当前版本写入同目录的 `changelog_seen.txt`。GT-shanhai 继续使用 `config/gt_shanhai/changelog/`，已有 Markdown 和已读记录可直接复用。

正文支持分级标题、列表、引用、代码块、强调和 Markdown 链接。目录可写成 `[更新内容](#更新内容)`。标题内的空格换成连字符，也可用 `<a id="example"></a>` 定义锚点。双语公告中的 `[English](#english)` 会切换到英文正文。使用 `<details>` 与 `<summary>标题</summary>` 添加折叠块，`<details open>` 可默认展开。外部链接写成 `[链接文字](https://example.com)`，按游戏的链接设置打开，也可右键复制地址。

正文和标题会自动在 GitHub、Issue / Issues、NeoForge、Forge 等识别到的词前显示 SHBUI 行内图标。也支持 AE2、JEI、KubeJS、FTB Quests、Mekanism、MCMOD、爱发电及 GitHub 子页面名称等。保留原文和链接，网址字面量及围栏代码块保留原样。使用 `<quote:github>` 可直接引用图标。名称可用 SHBUI 图标 ID，例如 `<quote:github_pull_request>`。输入图标可写为 `<quote:input>`、`<quote:输入>` 或 `<quote:intput>`。未知名称会保留原文。

首次进入主菜单时，SH-Common-Lib 会在 `config/sh_common_lib/changelog/Update-0.0.0.md` 放入一份双语预设。默认 `enabled: false`。编辑正文并改成 `enabled: true` 后，重启客户端即可在主菜单看到公告。游戏会保留已有的同名文件。

SH-Common-Lib 以 [MIT License](LICENSE) 发布。
