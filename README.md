# SH-Common-Lib

玩家資訊 HUD 使用 SHBUI 的文字效果與自適應對比。語言資源中的 HUD 字串可使用 SHBUI 格式標記；測量、截斷和分頁會處理標記後的文字，並跟隨高對比及減少動效設定。HUD 富文字入口與標記格式見 SH-Better-UI 的 `docs/RICH_TEXT_API.md`。

山海的 Minecraft 公用库，用于维护跨模组复用的 API、物品和方块。各版本使用同一个模组 ID `sh_common_lib`，分别构建和安装。

| 构建目录 | Minecraft | 加载器 | JDK | Gradle |
| --- | --- | --- | --- | --- |
| `forge/` | 1.20.1 | Forge 47.4.16 | 21 | 8.8 |
| `neoforge-1.21.1/` | 1.21.1 | NeoForge 21.1.251 | 21 | 9.2.1 |
| `neoforge-26.1.2/` | 26.1.2 | NeoForge 26.1.2.126 | 25 | 9.2.1 |

各版本依赖独立安装的 SH-Better-UI 和对应版本的 KotlinForForge。客户端和服务端使用上表所列的 Java 版本。

| 平台 | SH-Better-UI | KotlinForForge |
| --- | --- | --- |
| Forge 1.20.1 | 1.0.10 或兼容的 1.x 版本 | 4.12.0～4.x |
| NeoForge 1.21.1 | 1.0.10 或兼容的 1.x 版本 | 5.10.0～5.x |
| NeoForge 26.1.2 | 1.0.10-alpha.8 或兼容版本（Minecraft 26.1.2） | 6.2.0 |

构建默认从同级 `SH-Better-UI` 源码目录读取 Forge 的 `devJar` 或 NeoForge 的成品 JAR。也可用 `-PshBetterUiJar=<路径>` 指定。Forge 开发运行时的 KotlinForForge 默认来自该目录的 `libs/`，可用 `-PkotlinForForgeJar=<路径>` 指定。游戏内安装各平台的正式 JAR。

构建 Forge 1.20.1 或 NeoForge 1.21.1 时，将 `JAVA_HOME` 设为 Java 21，在对应目录运行：

```bash
./gradlew build
```

Windows 命令提示符可用 `gradlew.bat build`。成品在对应目录的 `build/libs/`，带 `-sources` 后缀的是源码包。开发环境可用 `./gradlew runClient` 或 `./gradlew runServer`。

构建 NeoForge 26.1.2 时，将 `JAVA_HOME` 设为 Java 25，在项目根目录使用 NeoForge 的 Gradle Wrapper：

```bash
./neoforge-1.21.1/gradlew -p neoforge-26.1.2 build
```

此构建读取同级 `SH-Better-UI/neoforge-26.1.2/build/libs/` 下的模组 JAR，也可用 `-PshBetterUiJar=<路径>` 指定。化学品无限元件集成等待 Mekanism 和 Applied Mekanistics 的兼容版本。

Windows 下可在项目根目录运行 `build-all.bat` 构建三个版本。将 `JAVA_HOME` 设为 Java 21，如果 Gradle 无法自动发现 Java 25，则用 `JAVA25_HOME` 指定其安装目录。脚本会把通用参数传给三个构建。单独指定 SH-Better-UI 版本或 JAR 时，请调用对应版本的 Gradle 构建命令。

## Unified LIB Kernel（ULK）

ULK 提供服务端只读玩家物品检查。`UnifiedLibKernel.inspect` 需要在目标玩家的服务器线程调用，返回当前时刻的快照；快照保留物品 NBT 或数据组件，数量使用 `long`。手持物品分别归入 `MAIN_HAND` 和 `OFF_HAND`，不再计入 `INVENTORY`；其余玩家槽位、Curios 饰品、精妙背包（包括嵌套和共享背包）、AE2 无线终端与随身元件、Mekanism 便携 QIO 和 QIO 驱动器的内容分别标记来源。此接口只检查物品，不统计流体或化学品。可选模组未安装时不影响其他来源。

```java
import com.dishanhai.sh_common_lib.api.ulk.*;
import net.minecraft.world.item.Items;
import java.util.Set;

InventorySnapshot snapshot = UnifiedLibKernel.inspect(serverPlayer);
long allDiamonds = snapshot.count(stack -> stack.is(Items.DIAMOND));
long carriedDiamonds = snapshot.count(Set.of(InventorySource.INVENTORY,
        InventorySource.MAIN_HAND, InventorySource.OFF_HAND), stack -> stack.is(Items.DIAMOND));
InventorySnapshot.Status aeStatus = snapshot.sources().get(InventorySource.AE2);
```

`AVAILABLE` 表示该来源已扫描；`NOT_FOUND` 表示模组存在但未找到对应终端或容器；`UNAVAILABLE` 表示未安装或未提供；`INCOMPLETE` 表示部分内容不可读取、终端无法连接、权限不足或达到扫描上限。不能把不完整的结果当作库存为空。每次扫描最多遍历 256 个独立容器／网络并返回 16,384 项；数量相加超过 `Long.MAX_VALUE` 时取最大值。重复链接同一背包、AE 网络或 QIO 频率只读取一次；AE 网络通过存储总线引用另一个网络等跨系统重叠无法自动识别，应按来源筛选后自行判断。

调用方也可在已有访问权限后，向 `inspect` 追加 `InventoryProvider`；安装对应模组并声明编译依赖时，可用 `Ae2Inventories.grid(grid)`、`Ae2Inventories.storage(identity, storage)` 和 `MekanismInventories.frequency(frequency)` 提供明确的库存。调用方负责验证这些显式对象的权限。自动终端查询检查 AE 连接范围与电量，以及 QIO 物品和频率权限。查询不会主动提取物品或扣除终端电量。

ULK 通过客户端 UUK 模块发布 `UnifiedLibUiService.KEY`，可使用 `UnifiedUI.get().require(UnifiedLibUiService.KEY)` 发现相同服务。服务仍要求传入 `ServerPlayer` 并在服务器线程执行；目前没有提供客户端到远程服务器的通用查询协议，客户端界面需要由消费模组实现必要的数据同步。

Forge 1.20.1 使用 Curios 5.14.1、Sophisticated Backpacks 3.26.20、AE2 15.4.10 和 Mekanism 10.4.16 的接口构建；NeoForge 1.21.1 使用 Curios 9.5.1、Sophisticated Backpacks 3.26.21、AE2 19.2.18 和 Mekanism 10.7.19。NeoForge 26.1.2 使用 Curios 15.0.0、Sophisticated Backpacks 3.26.20、AE2 26.1.13-beta，以及非官方社区移植版 Mekanism: Version Locked 2.11 的接口；其他 Mekanism 移植版需要单独验证。可选模组均需独立安装，不打包进 SHCLIB。

## 代码放在哪里

- `common/src/main/java/`：共用 API 与实现。保持 Java 17 兼容；只使用两端均兼容的类型。公共入口为 `com.dishanhai.sh_common_lib.api`。
- `common/src/main/resources/`：两端共用的语言文件、模型、贴图。只有路径和格式一致的资源才放在这里。
- `forge/src/main/` 和 `neoforge-1.21.1/src/main/`：加载器入口、物品和方块注册、版本专用实现与资源。例如方块掉落表在 1.20.1 使用 `loot_tables/blocks/`，在 1.21.1 使用 `loot_table/blocks/`。
- `neoforge-26.1.2/src/main/`：26.1.2 的专用实现和资源。同路径的 Java 文件覆盖 `common` 或 `neoforge-1.21.1` 中的实现。

每个通用物品和方块在两端使用同一个注册 ID，由各自加载器在注册阶段创建。

## SHCLIB 主控中心

在模组列表选择「SH-Common-Lib → 配置」，或进世界后按 **F10** 打开主控中心。已有按键绑定由 Minecraft 保留。如果旧配置仍使用 F9，可在「选项 → 控制 → 按键绑定」的「山海公用库」分类改绑。

「设置」里的「玩家信息 HUD」集中提供显示开关、各项信息和布局编辑器。使用标题右侧的上一页和下一页浏览全部选项。主控中心的 HUD、加载和 JEI 搜索设置先暂存，点击「应用」后保存；「恢复默认」同时恢复 HUD 位置、大小、加载和 JEI 搜索设置，应用前可再次点击撤销。退出时会询问是否放弃未应用的修改。布局编辑器单独保存，返回主控后会保留其他待应用的修改。「概览」提供密封货匣和 JEI 快捷键的使用提示。

「玩家信息 HUD」中可逐项切换玩家、世界、位置、服务器和电脑信息。TPS、MSPT、内存、CPU、GPU、磁盘默认关闭。

世界天气默认显示，可在「玩家信息 HUD」中关闭；显示当前维度的晴朗、降水或雷暴状态，不代表玩家所在群系一定有降雨或降雪。不支持天气的维度显示「无天气」。

天气行同时显示服务端提供的下一次天气计时预告，单位为游戏刻；服务端未安装支持预告的 SHCLIB 时显示不可用。关闭天气循环时会给出提示，晴天保护结束后具体天气可能由游戏随机决定。预告会随服务端天气指令或规则变化而更新。

「玩家信息 HUD」可单独开启月相、生命和吸收、饥饿和饱和、护甲、经验、氧气及状态效果。效果显示数量及最多八项的名称、等级和剩余时间，长文本按 HUD 宽度截断。月相和玩家状态项目默认关闭。

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

NeoForge 26.1.2 的主控中心、HUD 编辑器和公告使用 SH-Better-UI 控件。货匣、HUD 和世界加载卡片也接入 SH-Better-UI 的玻璃面板。开启高对比度、关闭背景模糊或无法取得背景时，面板改用实色。HUD 字色按深色底板选择。电脑读数包括内存、CPU、磁盘与渲染器名称，GPU 引擎利用率和显存读数等待对应的 SH-Better-UI 遥测服务。

NeoForge 26.1.2 在遥测服务不可用时会后台刷新内存、CPU 和磁盘读数；首次采样完成前可能暂时显示不可用。

进世界后，左上角默认显示玩家名、帧率、联机延迟、现实与游戏时间、维度、客户端已加载的区块与实体、群系、坐标、朝向和光照。群系温度是 Minecraft 的基础温度值。使用 F1 隐藏界面或打开 F3 调试信息时，玩家信息也会隐藏。

HUD 数值的绝对值达到 100,000 时以三位有效数字显示为科学计数法，例如 `9223372036854775807` 显示为 `9.22E18`；较小数值保留原来的格式。此规则只影响显示，版本、时间和其他文本不会转换。

HUD 末尾分别显示 **SHBUI 控制面板（默认 F8）** 和 **SHCLIB 主控中心（默认 F10）**。两行会读取实际按键，解绑时显示「未绑定」。窗口空间不足时，优先保留这两行。

HUD 使用标题和细线区分玩家、世界、位置、服务器、电脑、网络、AE2、FTB、运行环境和快捷键等信息组。在主控中心的玩家信息 HUD 设置中，可分别切换各信息组、标题和分隔线；隐藏组不会清除其项目开关。分页和布局编辑器按当前页内容重新计算标题与分隔线，快捷键始终保留。

在 SHCLIB「设置 → 玩家信息 HUD」中，可单独开启 Java、Minecraft、当前加载器版本、当前光影和模组加载状态。新项目默认关闭。模组状态显示客户端加载器登记的已加载数量与总数。光影状态从 Iris 或 Oculus 的客户端接口读取，能取得名称时显示当前使用的光影包。

系统版本、游戏连接流量、本机网络流量和 AE2 项目默认关闭，可在「玩家信息 HUD」中开启。系统名称与版本由运行环境提供，Android 可用时读取系统发行版本；其他平台不支持的计数器显示不可用。

「玩家信息 HUD」提供四个默认关闭的 FTB 选项：当前玩家可见任务的完成数量、团队名称与成员数、区块认领和强制加载请求配额，以及所在区块的可见归属与强制加载请求。只读取 FTB 已同步到客户端的数据；任务统计分批进行。模组或数据不可用时会显示提示。所在区块归属需要 FTB Chunks 提供客户端查询 API；旧版仍可显示配额。强制加载请求不代表服务端当前一定正在加载该区块。

ULK 库存汇总、来源详情和 Neo ECO AE 网络节点均默认关闭，可在玩家信息 HUD 设置中分别启用，并可单独隐藏分组。ULK 显示服务端对当前玩家可读取来源的快照条目和物品数量，以及可用、未找到、不可用或不完整状态；不发送物品明细。汇总是观察到的条目数量而非不同物品的种类，跨系统引用的库存可能重复，数量超过 `long` 上限时饱和。扫描仍可能耗费服务器时间，启用后约每 30 秒刷新一次；服务端未安装相应版本时显示不可用。

NeoForge 1.21.1 的 Neo ECO AE 项目读取玩家背包中首个已连接 AE2 无线终端的网络，统计节点归属为 Neo ECO AE 的总数及其中活跃数；遍历上限为 1024 个网络节点，超限时提示部分结果。节点统计不等同于机器数量，也不代表合成网络全部性能指标。其他版本显示未安装或不支持，不引入额外运行时依赖。

四种 Neo ECO AE 多方块 HUD 项目可以独立开启：合成系统显示本机线程与工作者数；运算系统显示活动 CPU、本机线程和本机计算存储；存储系统显示驱动器、能源单元与无限模式；大型工作站显示并行、温度、工作和过热状态。每台机器显示维度和坐标，以及其已扫描网络节点的在线状态。仅发现当前无线终端网络内已成型且已加载的机器；同一多方块的多个节点会去重。每次最多遍历 1024 个节点并显示 32 台机器，超限或机器数据无法完整读取时提示不完整。约每 30 秒刷新，不强制加载区块。服务端需要支持该功能的 SHCLIB；旧服务端和不兼容版本显示不可用。

游戏连接流量统计客户端连接的实际收发字节；单人世界使用内存连接，不显示网络字节速率。本机流量是非回环网卡的总收发速率，多网卡、虚拟网卡或 VPN 可能导致同一流量重复计入，第一次采样需要等待。AE2 信息读取背包中第一个能连接的无线终端，包括网络状态、已用频道、接入节点、控制器、节点数量与能源；需要服务端安装支持该功能的 SHCLIB 和 AE2。查询不打开终端界面或主动扣电；标准 AE2 查询不会强制加载区块，扩展终端遵循其自身的连接实现。当前不扫描 Curios 等饰品栏。

按 **F6** 打开布局编辑器，或在 F10 主控中心选择「编辑 HUD 布局」。拖动信息块调整位置，或聚焦后用方向键移动，按住 Shift 时每次移动 10 个 GUI 像素。「HUD 大小」滑杆支持 50%–200%，默认为 75%。「重置布局」恢复默认大小和位置。点击「完成」保存，或按 Esc 取消本次修改。

设置保存在客户端 `config/sh_common_lib-player-info.properties`。`scalePercent` 保存请求的大小。HUD 的排版预算为 GUI 宽度的 55% 和高度的 50%。自动缩放优先对齐到整像素；显示项过多时按优先级分页，默认使用 **Alt＋←／→** 切页，按键可在原版按键设置中修改。每页保留两个控制面板快捷键；空间极小时无法显示的内容仍会提示省略。小窗口和分数像素缩放时，文字使用清晰字形，仍可使用 Liquid Glass 背景。窗口空间恢复后，已开启的项目会自动恢复显示。位置按可用空间的比例保存，编辑器会给底部工具栏预留空间。使用 `show.tps=true`、`show.mspt=true`、`show.memory=true`、`show.cpu=true`、`show.gpu=true`、`show.disk=true` 启用相应的读数。

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

### JEI 搜索增强

搜索框聚焦且内容为空时，启用持久历史会显示最近搜索；`Ctrl+Tab` 可切换最近、收藏和常用来源。点击、`Tab` 或单独按 `Enter` 可恢复所选内容。`Alt+←/→` 翻页，`Ctrl+←/→` 切换配方预览，`Alt+Enter` 打开所选物品的 JEI 合成页。每次查询最多显示 500 条候选，每项最多提供 128 个配方预览。仅选中行使用强模糊及边框，主面板使用原液态玻璃 HUD。以上功能需要本次更新的对应版本 SH-Better-UI。

聚焦 JEI 的原搜索框后，SHCLIB 使用 ULK 搜索接口提供候选词，并以 UUK 液态玻璃 HUD 显示候选、图标和快捷键。在有足够空间时，选中候选的首个获取配方会显示在旁边。使用 `↑/↓` 选择候选，鼠标位于面板或搜索框上时也可滚轮选择；越过当前页的首尾会翻页，越过整个列表的首尾会循环。搜索增强启用且搜索框聚焦时，无修饰键上下箭头由增强层处理。`Ctrl+Z` 撤销最近一次补全或删除，`Esc` 收起候选，`Ctrl+↑/↓` 浏览已保存的查询历史。输入改变后，补全撤销状态会失效。

候选行中键或 `Ctrl+Shift+F` 切换收藏；右键复制候选物品、模组或标签 ID，历史行右键复制查询文字。历史来源中可用 `Shift+Delete` 删除选中条目，再用 `Ctrl+Z` 撤销。`F1` 展开或收起更多按键提示。收藏与最近历史分开保存，清除历史保留收藏。搜索结果优先按匹配质量排序，相同质量再参考本地使用次数和最近使用顺序；名称中有直接匹配时会高亮匹配部分。输入为候选的字面前缀、光标在末尾且输入尚未横向滚动时，搜索框会显示灰色补全后缀。

候选面板位于搜索框左侧，按容器边界和剩余空间调整尺寸；空间不足时会隐藏，避免覆盖物品栏。面板使用可中断的胶囊揭示，减少动效设置会立即完成动画。素材快照在客户端线程采集，索引和匹配交由后台处理，快速输入只显示当前查询的结果；资源重载后的首次采集仍可能耗时。

其他客户端模组可以通过 `AsyncClientSearch` 提交不可变搜索快照、更新查询并读取最新结果；宿主负责在客户端线程采集游戏对象数据，并在使用结果时检查当前查询。ULK 的 `SearchHistory`、`SearchRanking` 和 `SearchMatch` 分别提供历史来源、同质量排序和字面匹配范围；UUK 搜索候选提供匹配等级，`PopupArea` 和 `PopupMotion` 可分别复用避让布局和可中断浮层动画。使用新搜索接口时需要同步安装本次构建的对应 SH-Better-UI。

可选的客户端文件 `config/sh_common_lib/jei-search-aliases.json` 使用 UTF-8，按 JEI 材料 ID 添加搜索别名，例如 `{"minecraft:iron_ingot":["铁锭","fe"]}`。别名与 JEI 自身提供的别名合并，重启客户端后加载；格式错误时会记录日志并忽略该文件。其他宿主可复用 ULK 的 `SearchAliases`。

普通名称没有匹配结果时，搜索会在后台尝试少量拼写差异，提供可手动选择的修正候选；不会自动更改搜索框，也不会修正带筛选符号的查询。其他宿主可使用 ULK 的 `SearchCorrection`，或在创建 `AsyncClientSearch` 时显式启用纠错。

空搜索时可用 `Ctrl+Tab` 切换最近、收藏、常用、随机物品、任务物品和聊天提及来源；禁用的来源会跳过。随机候选来自可见 JEI 素材，索引刷新时重新抽取。任务候选来自 FTB Quests 客户端已同步、可见、前置条件已满足且未完成的物品任务；未安装或尚未同步时为空。聊天来源默认关闭，开启后只记录玩家消息中识别出的已知物品名称，忽略系统消息，不保存原文，断开连接时清空。三种来源在主控中心可分别开关；其他宿主可复用 ULK 的 `SearchSources` 并自行提供可见名称、任务数据和连接生命周期。来源名称能唯一对应 JEI 材料时支持预览和 `Alt+Enter`，重名时仅补全名称。

主控中心的「JEI 搜索」可分别设置增强预测、持久历史、HUD、配方预览、按键提示和液态玻璃，修改需点击「应用」。历史保存在客户端 `config/sh_common_lib/jei-search-history.properties`，可在同一设置页确认清除；关闭持久历史不会删除已保存的记录。设置保存在 `config/sh_common_lib/jei-search.properties`。这些开关不改变 JEI 自带的搜索或历史；在 SHCLIB 有候选时，增强层优先显示。若当前 JEI 版本无法识别原搜索框，增强层会回退，JEI 原功能仍可使用。

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
| Forge 1.20.1 | `com.dishanhai:sh-common-lib-forge-1.20.1:0.3.0` |
| NeoForge 1.21.1 | `com.dishanhai:sh-common-lib-neoforge-1.21.1:0.3.0` |

ForgeGradle 消费项目使用 `implementation fg.deobf("com.dishanhai:sh-common-lib-forge-1.20.1:0.3.0")`；NeoForge 消费项目直接使用对应的 `implementation` 坐标。
消费模组在自己的模组元数据中声明对 `sh_common_lib` 的依赖。游戏客户端和服务器在各自的 `mods` 目录安装对应版本的库 JAR。

## 全屏更新公告

Forge 1.20.1、NeoForge 1.21.1 和 NeoForge 26.1.2 的客户端可使用 `com.dishanhai.sh_common_lib.api.client.announcement.FullscreenAnnouncements`。将自己的公告目录传入构造器，在客户端线程调用 `openUnread(parent)` 自动展示未读版本，或调用 `openLatest(parent)`、`openDocument(parent, document)`、`openHistory(parent)` 主动打开。

公告按文件名中的三段版本号降序排列，支持 `Update-4.9.1.md` 这类文件名、`enabled`、`version`、`title`、`title_en` 和重复的 `link` 字段。英文正文放在 `<!-- lang:en -->` 后。点击「不再提示」会将当前版本写入同目录的 `changelog_seen.txt`。GT-shanhai 继续使用 `config/gt_shanhai/changelog/`，已有 Markdown 和已读记录可直接复用。

公告和历史页默认采用 SHBUI 的液态玻璃。从公告底部或 SH-Common-Lib 主控中心进入「公告外观」，可预览并选择液态玻璃、通透液态玻璃或实色。Unified UI Kernel 在本机保存公告专用偏好，同一客户端打开不同模组的公告时会共用该偏好。

正文支持分级标题、列表、引用、代码块、强调和 Markdown 链接。目录可写成 `[更新内容](#更新内容)`。标题内的空格换成连字符，也可用 `<a id="example"></a>` 定义锚点。双语公告中的 `[English](#english)` 会切换到英文正文。使用 `<details>` 与 `<summary>标题</summary>` 添加折叠块，`<details open>` 可默认展开。外部链接写成 `[链接文字](https://example.com)`，按游戏的链接设置打开，也可右键复制地址。

正文和标题会自动在 GitHub、Issue / Issues、NeoForge、Forge 等识别到的词前显示 SHBUI 行内图标。也支持 AE2、JEI、KubeJS、FTB Quests、Mekanism、MCMOD、爱发电及 GitHub 子页面名称等。保留原文和链接，网址字面量及围栏代码块保留原样。使用 `<quote:github>` 可直接引用图标。名称可用 SHBUI 图标 ID，例如 `<quote:github_pull_request>`。输入图标可写为 `<quote:input>`、`<quote:输入>` 或 `<quote:intput>`。未知名称会保留原文。

首次进入主菜单时，SH-Common-Lib 会在 `config/sh_common_lib/changelog/Update-0.0.0.md` 放入一份双语预设。默认 `enabled: false`。编辑正文并改成 `enabled: true` 后，重启客户端即可在主菜单看到公告。游戏会保留已有的同名文件。

SH-Common-Lib 以 [MIT License](LICENSE) 发布。
