<!-- Author: dishanhai -->
# JEI 中键拿取导致预测面板收起

- 日期：2026-11-16
- 状态：resolved（代码与自动化验证）；实际游戏验收见 TODO.md。
- 用户反馈：中键手持素材时，JEI 搜索预测／历史面板会收起。
- 根因：activeBox 依赖 JEI overlay.hasKeyboardFocus，素材拿取的原生输入路由会使搜索框失焦；render 随后调用 finish，结束候选显示并重置预览／面板动画状态。候选中键收藏虽未主动 dismissed，也需覆盖原生松开后可能的失焦。
- 修正：复用 UUK GestureFocusRetention 保存当前界面与搜索框身份，只在既有可见候选上的中键手势启用；素材点击仍交由 JEI 执行。手势期间及松开后的首次绘制恢复焦点，随后解除保留。其他按钮、界面／搜索框更换、隐藏、Esc／补全和窗口失焦会取消，不永久抢占焦点。
- 核心回归：修复前 3 项用例中 2 项在手持／快速点击恢复断言失败，修复后 3 项全部通过。实际游戏验收见 TODO.md。
- 验证结果：UUK 焦点回归 3/3 通过；SHBUI 三端发行包构建／verifyLibraryJar 通过且均含 GestureFocusRetention；SHCLIB Forge 1.20.1、NeoForge 1.21.1 和 NeoForge 26.1.2 的完整 build／check 均退出 0，既有拼音、快捷发放与搜索操作回归保持通过。本轮未启动游戏实例，所有构建子进程已退出。
