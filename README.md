# SH-Common-Lib

山海的 Minecraft 公用库，用于维护跨模组复用的 API、物品和方块。两端使用同一个模组 ID：`sh_common_lib`，各自构建和安装。

| 构建目录 | Minecraft | 加载器 | JDK | Gradle |
| --- | --- | --- | --- | --- |
| `forge/` | 1.20.1 | Forge 47.4.16 | 21 | 8.8 |
| `neoforge/` | 1.21.1 | NeoForge 21.1.251 | 21 | 9.2.1 |

两端均依赖独立安装的 SH-Better-UI 和对应版本的 KotlinForForge，客户端与服务端均使用 Java 21。

| 平台 | SH-Better-UI | KotlinForForge |
| --- | --- | --- |
| Forge 1.20.1 | 1.0.6 或兼容的 1.x 版本 | 4.12.0～4.x |
| NeoForge 1.21.1 | 1.0.6 或兼容的 1.x 版本 | 5.10.0～5.x |

构建默认从同级 `SH-Better-UI` 源码目录读取 Forge 的 `devJar` 或 NeoForge 的成品 JAR。也可用 `-PshBetterUiJar=<路径>` 指定。Forge 开发运行时的 KotlinForForge 默认来自该目录的 `libs/`，可用 `-PkotlinForForgeJar=<路径>` 指定。游戏内安装各平台的正式 JAR。

在对应目录中，用 Java 21 设置 `JAVA_HOME`，再运行：

```bash
./gradlew build
```

Windows 命令提示符可用 `gradlew.bat build`。成品在对应目录的 `build/libs/`，带 `-sources` 后缀的是源码包。开发环境可用 `./gradlew runClient` 或 `./gradlew runServer`。

Windows 下也可在项目根目录运行 `build-all.bat`，依次构建 Forge 和 NeoForge；传入的参数会交给两端的 Gradle，例如 `build-all.bat --offline`。脚本会在某一端构建失败时停止，并在结束后等待按键，产物仍保存在各自的 `build/libs/`。

如果同级 SH-Better-UI 目录中的两端产物版本一致，也可以临时指定版本，例如 `build-all.bat -Psh_better_ui_version=1.0.6`；该版本的两端 JAR 必须已经构建好。

## 代码放在哪里

- `common/src/main/java/`：共用 API 与实现。保持 Java 17 兼容；只使用两端均兼容的类型。公共入口为 `com.dishanhai.sh_common_lib.api`。
- `common/src/main/resources/`：两端共用的语言文件、模型、贴图。只有路径和格式一致的资源才放在这里。
- `forge/src/main/` 和 `neoforge/src/main/`：加载器入口、物品和方块注册、版本专用实现与资源。例如方块掉落表在 1.20.1 使用 `loot_tables/blocks/`，在 1.21.1 使用 `loot_table/blocks/`。

每个通用物品和方块在两端使用同一个注册 ID，由各自加载器在注册阶段创建。

## SHCLIB 主控中心

在模组列表选择「SH-Common-Lib → 配置」，或进世界后按 **F9** 打开主控中心。快捷键可以在「选项 → 控制 → 按键绑定」的「山海公用库」分类修改。

「设置」里的「玩家信息 HUD」分组可以开关显示，并在世界中打开位置编辑器。开关立即保存，位置编辑器关闭后焦点回到原入口。「概览」提供密封货匣和 JEI 快捷键的使用提示。

「设置 → 开发者 → 开发者配置」可进入开发者配置页，当前显示空状态。

SHCLIB 内部可在 `ControlCenterModule.settings` 中添加 `SettingsGroup`，用 `Toggle` 接入开关，用 `Page` 接入独立子页。有多个分组时，「设置」页会显示可滚动的分组导航。

## 玩家信息 HUD

每行前的彩色像素图标由 SH-Better-UI 提供，位置编辑器使用同一套图标。旧版 SH-Better-UI 缺少小尺寸图标时会使用库中已有的图标。

进世界后，左上角默认显示玩家名、帧率、联机延迟、现实与游戏时间、维度、客户端已加载的区块与实体、群系、坐标、朝向和光照。群系温度是 Minecraft 的基础温度值。使用 F1 隐藏界面或打开 F3 调试信息时，玩家信息也会隐藏。

按 **F8** 打开位置编辑器。拖动信息块，或聚焦信息块后用方向键移动。按住 Shift 时每次移动 10 个 GUI 像素。编辑器也可以开关显示和重置位置。点击「完成」保存，或按 Esc 取消本次修改。快捷键可在「选项 → 控制 → 按键绑定」里的「山海公用库」分类修改。

设置保存在客户端 `config/sh_common_lib-player-info.properties`。位置按屏幕可用空间的比例保存，更改分辨率或 GUI 缩放后仍限制在屏幕内。

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

NeoForge 1.21.1 安装 AE2 并开启 JEI 作弊模式后，悬停物品或流体按 `Ctrl+Shift+左键`，松手时通过 JEI 请求发放 `sh_common_lib:infinity_cell`。每张盘只绑定一种物品或流体，绑定会保留材料的数据组件。元件工作台无法调整或追加材料。放入 ME 驱动器后可无限取出绑定的材料，存入同一材料会被元件吸收，每张盘待机消耗 8 AE/t。发放权限由 JEI 和服务端决定。此功能以 JEI 19.69.0.456 与 AE2 19.2.18 构建。

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

安装 KubeJS 后，Forge 1.20.1 和 NeoForge 1.21.1 都可以直接使用全局对象 `ShParcels`。例如把下列脚本放到 `kubejs/server_scripts/sealed_crate.js`，用一个铁锭合成装有两个铁粒的示例货匣。

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

SH-Common-Lib 以 [MIT License](LICENSE) 发布。
