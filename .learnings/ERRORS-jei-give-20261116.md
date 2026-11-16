<!-- Author: dishanhai -->
# JEI 搜索候选快捷发放

- 日期：2026-11-16
- 状态：resolved（实现与回归）；实际客户端／联机验收见 TODO.md。
- 使用 ULK ClientIngredientGive 统一 ONE／STACK／INFINITY_CELL 及作弊模式门控；JEI 搜索界面只解析精确修饰键并提供当前可见候选，不修改原查询。
- 所有发放使用 JEI PacketGiveItemStack 的 INVENTORY 模式，服务端仍执行 JEI 的作弊权限检查。16 桶应拆成最多符合每个桶最大堆叠数的请求，不能反复使用鼠标拿取模式或构造 16 个不可堆叠桶的非法物品栈。
- 没有 JEI 服务端时仅保留原版创造模式权限：先合并相同物品及数据，再使用空的主背包槽；满背包按原版创造发放路径掉落。不覆盖已有物品，不能使用仅物品 ID 的相等判断合并带不同数据的材料。
- 无限盘复用各平台 InfinityCell.Target／create，缺少 AE2 或没有有效目标时反馈失败；流体无对应桶时不得退化为发放其他物品。
- 独立 controlCenterVerification 源集没有 GLFW 编译依赖；输入协议测试用命名的 GLFW 数值常量，实际按键解析仍引用 GLFW 常量，避免为独立契约加入原生依赖。首次测试因该依赖失败，修正后明确在 Ctrl+Enter gives one 断言看到红灯再实现映射。
- NeoForge 26 的 FluidUtil.getFilledBucket 已废弃，应使用 FluidType.getBucket。Forge 1.20.1 FluidStack 没有 NeoForge 的 getFluidType()；跨平台替换此调用导致一次编译失败，Forge 恢复 FluidUtil.getFilledBucket，NeoForge 保留新 hook。
- 验证：最终 Forge 1.20.1、NeoForge 1.21.1、NeoForge 26.1.2 的 build／check（含 searchGiveShortcutTest、clientSearchTest）全部退出 0；六个 SHCLIB／SHBUI 发行 JAR 均含对应新实现，SHCLIB 未嵌入 SHBUI 或 JEI 类。本轮未启动游戏客户端或服务器，所有本轮构建子进程已结束。
