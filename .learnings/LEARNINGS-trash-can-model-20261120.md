## [LRN-20261120-001] 垃圾桶方块采用哑光塑料外观

**Logged** 2026-11-20T00:25:56+08:00  
**Status** resolved  
**Area** client

用户把带有百叶、格栅和金属高光的初版认成焚烧炉，要求用哑光塑料，并指出只在整方块表面加图标缺少垃圾桶的结构。调整为较小的圆桶，保留翻盖、桶袋卷边和脚踏，用一条青色指示灯表现科幻风格。检查正面和俯视角，并随外形更新碰撞范围与相邻遮挡。

- 来源 `user_correction`
- 关联文件 `artwork/trash_can/trash_can.bbmodel`

脚踏默认朝南的模型加入水平朝向时，旧方块以南为默认值，模型和选取框都以南为旋转起点。共享代码以 `EnumProperty<Direction>` 接收 `HORIZONTAL_FACING`，兼容早期版本的 `DirectionProperty` 和 26.1.2。矿镐等级由 `requiresCorrectToolForDrops`、`mineable/pickaxe` 和 `needs_iron_tool` 共同决定。Forge 1.20.1 的方块标签位于 `tags/blocks`，当前两个 NeoForge 目标使用 `tags/block`。
