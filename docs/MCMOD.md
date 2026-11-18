# 山海公用库（SH-Common-Lib）MC 百科底稿

本文以 CurseForge 公开发行的 0.3.0 为范围。MC 百科主站将 AIGC 列为公共编辑内容的审核问题，本稿供作者核对事实并自行撰写投稿。 [MC 百科主站编辑规范](https://bbs.mcmod.cn/thread-646-1-1.html)

## 模组资料

| 字段 | 内容 |
| --- | --- |
| 中文名 | 山海公用库 |
| 原名 | SH-Common-Lib |
| 简写 | SHCLIB |
| 作者 | dishanhai |
| Mod ID | `sh_common_lib` |
| 运行环境 | 客户端、服务端 |
| 许可证 | MIT |

| Minecraft | 加载器版本范围 | Java | SH-Better-UI 最低版本 | 0.3.0 文件 |
| --- | --- | --- | --- | --- |
| 1.20.1 | Forge 47.4.16 至 48 之前 | 21 | 1.0.11 | [CurseForge 文件](https://www.curseforge.com/minecraft/mc-mods/sh-common-lib/files/9590313) |
| 1.21.1 | NeoForge 21.1.251 至 21.2 之前 | 21 | 1.0.11 | [CurseForge 文件](https://www.curseforge.com/minecraft/mc-mods/sh-common-lib/files/9590301) |
| 26.1.2 | NeoForge 26.1.2.126 至 26.1.3 之前 | 25 | 1.0.11-alpha.8 | [CurseForge 文件](https://www.curseforge.com/minecraft/mc-mods/sh-common-lib/files/9590327) |

安装与 Minecraft 版本及加载器相符的模组文件，同时安装对应平台的 SH-Better-UI 和 KotlinForForge。JEI、应用能源 2（AE2）、KubeJS 等用于启用各自的联动功能。 [官方发布页](https://www.curseforge.com/minecraft/mc-mods/sh-common-lib)

## 模组介绍

山海公用库是 dishanhai 开发的 Minecraft 模组公用库，为模组与整合包提供货匣生成、物品内容预览等接口。模组还加入了密封货匣、可配置的玩家信息 HUD、加载性能分析和信息复制快捷键。

### 主控中心与玩家信息 HUD

进入世界后默认按 **F10** 打开 SHCLIB 主控中心，也可从模组列表的配置入口打开。玩家信息 HUD 可显示帧率、延迟、维度、坐标、群系与光照等信息，可在主控中心逐项调整。默认按 **F6** 打开 HUD 布局编辑器，可通过拖动或方向键移动信息块，并调整显示大小。

0.2.0 的主控中心和 HUD 布局编辑器默认键分别为 **F9** 和 **F8**。升级后，已有按键绑定可在 Minecraft 的按键设置中调整。 [0.2.0 发布日志](https://www.curseforge.com/minecraft/mc-mods/sh-common-lib/files/9559231)

### 加载动画与性能分析

主控中心的「加载与性能」可分别开启加载动画与性能分析，两项默认关闭。性能分析记录启动和进入世界的阶段耗时。报告位于 `config/sh_common_lib/loading_reports/`，最近一次启动和进入世界的报告分别保存在 `latest-startup.json` 和 `latest-world.json`。

### 方块复制与 JEI 联动

面向方块时可用 **Ctrl + K** 复制方块 ID 与当前状态，用 **Ctrl + I** 复制方块实体的 SNBT 数据。安装 JEI 后，可将鼠标移到物品或流体上，按提示复制显示名、注册 ID、标签或附加数据，也可把名称和 ID 填入聊天输入框。

聚焦 JEI 搜索框时，山海公用库可显示搜索建议、历史与收藏。主控中心的「JEI 搜索」提供对应的设置入口。

### 模组与整合包接口

`Parcels` 可生成空货匣或预装并封存的货匣，供奖励、交易等功能使用。`ItemContentPreview` 允许其他物品接入内容预览。安装兼容的 KubeJS 后，可通过 `ShParcels` 在脚本中创建和读取货匣。

Unified LIB Kernel（ULK）提供服务端只读物品查询。调用方可按来源统计玩家背包、手持物品或兼容模组提供的库存。快照会记录各来源的扫描状态，供调用方判断结果是否完整。

全屏更新公告接口可在客户端显示模组的 Markdown 更新说明，并记录已读版本。

## 物品资料

### 密封货匣（Sealed Crate）

注册名为 `sh_common_lib:sealed_crate`。可在创造模式的「工具与实用物品」中找到。

手持未封存的货匣右击打开装填界面，最多装入 9 组物品。手持并潜行右击可封存已装入物品的货匣。手持已封存的货匣右击，或在玩家背包中将指针移至货匣上双击鼠标中键，即可拆封。拆封会消耗货匣，内容物优先进入背包，放不下的物品掉落在玩家身边。

将指针移至封存货匣上可预览内容。默认显示前 5 项，按住 Shift 可展开。

### ME 无限存储元件（ME Infinity Cell）

注册名为 `sh_common_lib:infinity_cell`。0.3.0 的 NeoForge 1.21.1 和 NeoForge 26.1.2 提供该元件。安装 AE2 并开启 JEI 作弊模式后，将指针移至物品或流体上，按 **Ctrl + Shift + 鼠标左键** 可请求发放绑定该材料的元件。发放受 JEI 和服务端权限控制。NeoForge 1.21.1 的化学品绑定还需安装兼容的 Mekanism 和 Applied Mekanistics。

每张元件固定绑定一种材料，放入 ME 驱动器后可无限取出该材料，存入的同种材料会被元件吸收。元件工作台无法更改绑定，每张元件待机消耗 8 AE/t。

## 投稿时使用的字段和素材

- 核心元素建议填「实用」，密封货匣可对应「存储」。MC 百科对「LIB」的定义要求库本身不增加游戏内容，山海公用库自带密封货匣。 [模组元素定义与优先级](https://bbs.mcmod.cn/thread-2704-1-1.html)
- 「模组关系」可将 SH-Better-UI 填为前置，将 JEI、AE2、KubeJS 填为联动。KotlinForForge 随 SH-Better-UI 的运行要求安装。 [模组关系的定义](https://bbs.mcmod.cn/thread-2771-1-1.html)
- 「相关链接」可填写项目的 [GitHub 源码仓库](https://github.com/dishanhai/SH-Common-Lib)和 [CurseForge 发布页](https://www.curseforge.com/minecraft/mc-mods/sh-common-lib)。
- 封面可用仓库根目录的 [项目封面](../sh-common-lib-cover-1024.png)。物品资料可配装填、封存和元件绑定后的游戏截图，并标明截图所用版本。
- 模组介绍和物品资料分别录入对应页面。按发布版本注明功能与键位差异，历史版本另有资料时保留其适用范围。 [百科帮助文档](https://bbs.mcmod.cn/thread-23845-1-1.html) [主站通用命名规则](https://bbs.mcmod.cn/thread-653-1-1.html)
