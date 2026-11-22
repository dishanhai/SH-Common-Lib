## [LRN-20261122-001] 手机圆角、拉丝边框与灵动岛

**Logged** 2026-11-22  
**Status** resolved  
**Area** artwork

用户要求机身为连续圆角、边框有钛合金质感，灵动岛为贴近屏幕上沿的扁胶囊，两端为半圆。手机源模型采用 Blockbench 通用网格，为外轮廓和厚度方向的倒角分别建模。中框以沿边框走向的拉丝纹理和倒角高光表现材质。灵动岛用独立网格控制圆角和位置，收矮时按新高度重算端头半径，横向加宽时延长两端之间的直线段。验收时从正面核对灵动岛比例及与状态栏的间距，再以斜侧近景检查转角、高光和边框色差。

- 来源 `user_correction`
- 关联文件 `artwork/phone/phone.bbmodel`

游戏端通过 Forge 和 NeoForge 内置的 OBJ 加载器共用网格和两个材质。NeoForge 26.1.2 的客户端验收需先进入世界，等物品组件绑定后再创建 `ItemStack`。第一人称变换要用实际客户端检查，预览里居中的大尺寸模型在窄窗口手持时可能被裁切。

Forge 1.20.1 的本机 SH-Better-UI 开发包缺少 Skija 类，客户端会在 UI 初始化时抛出 `NoClassDefFoundError`。隔离验收通过项目的 `shBetterUiJar` 覆盖入口，使用附有同版本 Skija Jar-in-Jar 依赖的临时开发包。NeoForge 1.21.1 的本机验收使用可用的 SH-Better-UI 1.0.12，默认 1.0.13 的复验记在 `TODO.md`。

NeoForge 1.21.1 使用较旧 SHBUI 做隔离实机检查时，`-PshBetterUiJar` 只改变依赖 JAR，运行时仍会按照生成的模组元数据拒绝低于最低版本的依赖。本机 1.0.12 可用 `-Psh_better_ui_version=1.0.12` 一次性覆盖开发客户端的依赖下限，项目默认值仍按发布要求保留。

## 手机界面与备忘录

用户确认手机是 App 入口。以 `artwork/phone/phone_screen.png` 的九宫格核对应用名称与顺序，验收点击后实际产生的效果。玩家生命、饥饿、坐标等信息由 SHCLIB 的 HUD 提供。

UUK 用户配置写入应提交到调度器的 `STATE_MUTATION` 阶段。隔离测试从渲染回调模拟点击时，直接提交事务会抛出阶段异常。备忘录通过模块作用域调度写入，并在 `flush` 完成后更新保存反馈。
