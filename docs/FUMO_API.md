<!-- 作者：山海／dishanhai -->
# ULK Fumo API

支持 Forge 1.20.1、NeoForge 1.21.1 与 NeoForge 26.1.2。将对应版本的 SH-Common-Lib 添加为模组依赖，使用自己模组的延迟注册表和事件总线。

## 右键奖励配置

服务端每次有效空手右键有 20% 概率发放 1 件物品。每位玩家共用所有 Fumo 的 15 秒抽奖冷却。物品按相对权重抽取，例如权重 9 和 1 对应中奖后的 90% 和 10%。

### JSON

在实例目录新建 `config/sh_common_lib/fumo_rewards.json`。也可以在数据包中提供 `data/sh_common_lib/fumo_rewards/pool.json`，格式相同。

```json
{
  "rewards": [
    { "item": "minecraft:iron_ingot", "weight": 9 },
    { "item": "minecraft:diamond", "weight": 1 }
  ]
}
```

填写已注册的完整物品 ID 和正整数权重，每个物品出现一次。`{"rewards":[]}` 会关闭发放。服务器启动时读取配置，也可执行 `/reload`。优先使用最高优先级的数据包资源，其次使用 `config` 文件，最后使用内置等权奖池。无效配置会写入服务端日志并回退。

### KubeJS

将对应版本的脚本保存到 `kubejs/server_scripts/fumo_rewards.js`。它会生成上述数据包资源，按数据包优先级替换整张奖池。

**Forge 1.20.1**

```js
ServerEvents.highPriorityData(event => {
  event.add('sh_common_lib:fumo_rewards/pool.json', JSON.stringify({
    rewards: [
      { item: 'minecraft:iron_ingot', weight: 9 },
      { item: 'minecraft:diamond', weight: 1 }
    ]
  }))
})
```

**NeoForge 1.21.1 与 26.1.2**

```js
ServerEvents.generateData('last', event => {
  event.text('sh_common_lib:fumo_rewards/pool.json', JSON.stringify({
    rewards: [
      { item: 'minecraft:iron_ingot', weight: 9 },
      { item: 'minecraft:diamond', weight: 1 }
    ]
  }))
})
```

## 注册

```java
import com.dishanhai.sh_common_lib.api.ulk.fumo.Fumos;

var fumo = Fumos.register(modBus, BLOCKS, ITEMS, BLOCK_ENTITIES,
        "fumo_dance", "example_fumo", "another_fumo");

BLOCKS.register(modBus);
ITEMS.register(modBus);
BLOCK_ENTITIES.register(modBus);

// 在创意栏内容事件中读取已完成注册的物品。
event.accept(fumo.variant("example_fumo").item().get());
```

Forge 的 `BLOCKS` 和 `ITEMS` 分别为 `DeferredRegister<Block>` 和 `DeferredRegister<Item>`。两个 NeoForge 版本使用 `DeferredRegister.Blocks` 和 `DeferredRegister.Items`。三端的 `BLOCK_ENTITIES` 均为 `DeferredRegister<BlockEntityType<?>>`。

`fumo_dance` 是这组玩偶共用的方块实体路径，之后每个参数为一个独立的方块与物品路径。保持这些路径稳定即可让现有存档继续读取。API 会绑定客户端舞动渲染器。放置时按玩家方向转向，空手右键切换舞动并循环播放对应音效。使用 `fumo.variant("example_fumo").block()` 获取方块句柄，使用 `fumo.blockEntityType()` 在注册后解析共用实体类型。

## 坐姿模型

以 `example_mod:example_fumo` 为例，将经典宽臂 64×64 Minecraft 皮肤保存为 `assets/example_mod/textures/block/example_fumo.png`。模板会读取基础皮肤及透明外层。要制作专用造型，可在自己的方块模型中提供几何。

`assets/example_mod/models/block/example_fumo.json`

```json
{
  "parent": "sh_common_lib:block/fumo/skin_64",
  "render_type": "minecraft:cutout",
  "textures": {
    "skin": "example_mod:block/example_fumo"
  }
}
```

`assets/example_mod/blockstates/example_fumo.json`

```json
{
  "variants": {
    "facing=north": { "model": "example_mod:block/example_fumo" },
    "facing=east": { "model": "example_mod:block/example_fumo", "y": 90 },
    "facing=south": { "model": "example_mod:block/example_fumo", "y": 180 },
    "facing=west": { "model": "example_mod:block/example_fumo", "y": 270 }
  }
}
```

物品模型 `assets/example_mod/models/item/example_fumo.json` 的 `parent` 指向 `example_mod:block/example_fumo`。NeoForge 26.1.2 还要添加物品定义。

`assets/example_mod/items/example_fumo.json`

```json
{
  "model": {
    "type": "minecraft:model",
    "model": "example_mod:item/example_fumo"
  }
}
```

在语言文件中加入 `block.example_mod.example_fumo`。掉落表放在 Forge 的 `data/example_mod/loot_tables/blocks/` 或 NeoForge 的 `data/example_mod/loot_table/blocks/`。合成配方可按游戏版本选用 `recipes/` 或 `recipe/`。

## 空间音效

将单声道 Ogg Vorbis 保存在 `assets/example_mod/sounds/`。在 `assets/example_mod/sounds.json` 里用方块路径作为音效键，字幕键另写入语言文件。音效跟随「唱片机／音符盒」音量滑块，播放距离上限为 16 格。

```json
{
  "example_fumo": {
    "subtitle": "subtitles.example_mod.example_fumo",
    "sounds": [
      { "name": "example_mod:plushie_music", "stream": true, "attenuation_distance": 16 }
    ]
  }
}
```
