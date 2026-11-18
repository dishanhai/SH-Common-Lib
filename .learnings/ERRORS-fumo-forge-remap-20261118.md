# Forge 1.20.1 缺失映射的公开访问方式

- 日期：2026-11-18
- 状态：resolved
- 现象：Forge 47.4.16 的 `MissingMappingsEvent.Mapping.key` 在模组侧不可见，直接访问会导致编译失败。
- 处理：使用 `Mapping#getKey()` 比对完整注册 ID，再通过 `Mapping#remap` 迁移方块、物品及方块实体。
- 验证：Forge 1.20.1 完整构建通过。隔离服务端将 SHBUI 单装存档切到 SHCLIB 和 SHBUI 双装，方块实体、朝向、含水状态、舞动状态与掉落均通过检查。
