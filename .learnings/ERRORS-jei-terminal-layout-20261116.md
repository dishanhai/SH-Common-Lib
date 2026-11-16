# JEI 搜索浮层与不同尺寸 AE 终端

- 作者：山海／dishanhai
- 状态：resolved（代码与布局回归；真实客户端验收见 TODO.md）
- 用户截图：AE 终端下候选列表随容器尺寸缩小，原生物品 tooltip 与配方预览互相覆盖。
- 根因一：只用 64×30 的最低区域判断容器旁／底部是否可用，随后 UiHudPanel.fit 会把整张列表缩进窄条。应比较完整候选尺寸；放不下时通过 UUK PopupArea.fittingLeftOf 使用锚点左侧的浮层区域。
- 根因二：JeiShortcuts.hovered 已包含容器槽回退，但原逻辑仅关闭搜索选中项 tooltip，仍绘制配方预览。搜索聚焦且有有效候选时，由搜索层统一接管提示，取消底层原生 tooltip；搜索自身绘制不参与取消。不能按鼠标悬停槽位直接隐藏候选，否则鼠标停在物品上会使键盘搜索列表消失。
- 配方预览用屏幕边界约束，不沿用容器旁的候选区域高度；候选与配方共用前景深度，预览仍可按原约定覆盖书签／历史区域。
- 回归用例在修改前复现断言失败，修改后覆盖 12 种 AE 终端宽高及搜索框边界。构建与测试不替代实际整合包的 tooltip、GUI 缩放及键鼠验收。
- SH-Better-UI 根目录只有 gradlew.bat；在 Git Bash 可使用现有 SHCLIB Forge 包装器配合 -p 指定该项目，不能假定根目录存在 ./gradlew。
- SHCLIB 的独立 JavaExec 验证源集没有自动继承 main 的运行依赖。布局回归首次完整构建因缺少 PopupArea$Rect 失败；调用 UUK 的验证任务需将 sourceSets.main.runtimeClasspath 加入 classpath。
