<!-- 作者：山海／dishanhai -->
# 配方编辑器集成经验

## [ERR-20261116-001] 跨版本控件能力不能仅按同名类推断

**Logged**: 2026-11-16T10:15:00+08:00
**Priority**: high
**Status**: resolved
**Area**: client

### Summary

26.1.2 的 `UiScreen` 不包含旧版 context、button、dynamicTheme 等组合接口，直接消费 common 页面产生缺失符号错误；同版本号旧 SHBUI 也可能不含新 API。

### Root Cause

只查看旧版控件 API 后将其当作三端共有能力。

### Fix

UUK 提供两套同签名 `UiWorkbench` 组合入口及对应版本素材槽、多行代码控件。SHCLIB 在独立访问入口中用反射检查必要能力；未满足时显示更新提示，不实例化编辑器。三端均必须使用对应 JAR 编译。

26.1.2 的 Util 在 `net.minecraft.util`，旧版在 `net.minecraft`，目录操作用同路径的平台实现覆盖。Forge 携带的 Gson 2.8.9 没有 JsonObject.isEmpty()，共用代码改用 size()==0；生成器和导入测试在 Gson 2.8.9／2.10.1 分别验证。

### Metadata

- Source: compiler / code_review
- Related Files: client/recipe/RecipeEditorAccess.java、SHBUI client/api/UiWorkbench.java
- Tags: uuk, cross-version, capability

## [ERR-20261116-002] 固定槽数控件切换布局必须重建

**Logged**: 2026-11-16T10:15:00+08:00
**Priority**: high
**Status**: resolved
**Area**: client

### Summary

将九槽合成切为单槽加工时，旧控件的 setItems 拒绝新槽数。

### Fix

文档类型变更只写历史和模型，跳过旧控件同步，随后重建页面。选料子页返回后同样会重建控件，回调必须使用当前字段引用，不能更新已经卸载的输入框。未解析的数值属于待保存修改，必须纳入退出和替换保护。

### Metadata

- Source: code_review
- Related Files: client/recipe/RecipeEditor.java
- Tags: slots, lifecycle, dirty-state

## [ERR-20261116-003] 自定义验证 sourceSet 需要主依赖编译类路径

**Logged**: 2026-11-16T10:15:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: build

### Error

`compileControlCenterVerificationJava`: `package com.google.gson does not exist`。

### Fix

验证 sourceSet 的 compileClasspath 加入 sourceSets.main.compileClasspath；只加入 main.output 不会使 Gson 等依赖进入测试编译。JavaExec 的运行时也使用 main.runtimeClasspath。保留既有测试与依赖版本，不额外引入测试框架。

### Metadata

- Source: gradle
- Related Files: gradle/library.gradle
- Tags: gradle, gson, source-set

## [ERR-20261116-004] 单行控件的参数与最小视口必须运行时验证

**Logged**: 2026-11-16T02:35:00+08:00
**Priority**: high
**Status**: resolved
**Area**: client

### Error

`IllegalArgumentException: Invalid text field limit`，调用链为 `SingleLineEdit → UiTextField → RecipeEditor.field → openSlot`。用户点击槽位后原料页初始化失败。

### Root Cause

SHCLIB 给 UUK 单行控件传入 65536，上游只允许 1–4096；编译不会验证这个运行时契约。修正后，原有详细素材选择器在首次隔离客户端的 427×240 视口进入空间不足分支，不显示物品列表。只修改 GUI 缩放选项不能证明实际尺寸，原版可能限制缩放上限。

### Fix

单行字段改用 4096，JSON 大文本继续使用多行控件。按用户后续要求，UUK 工作台使用参照 GTSH 的资源选择器，搜索／分页与取消在小视口仍可达。新增隔离原生客户端回归，通过真实 Screen 初始化和 NeoForge 鼠标事件复现。最终测试记录窗口实际逻辑尺寸与缩放值；420×240／560×320 的原生选择、数量与手填标签流程通过，320×180 通过纯布局边界测试。

### Metadata

- Source: user_report / runtime_log / native_client_regression
- Related Files: client/recipe/RecipeEditor.java、gradle/recipe-editor-smoke.init.gradle、SHBUI client/api/UiWorkbench.java、SHBUI client/gui/scaled/UiResourcePicker.java
- Tags: uuk, runtime-contract, picker, gui-scale
- See Also: ERR-20261116-005

## [ERR-20261116-005] 同一原生控件重复聚焦会取消新按下

**Logged**: 2026-11-16T02:55:00+08:00
**Priority**: high
**Status**: resolved
**Area**: client

### Root Cause

Minecraft 的容器在点击已聚焦控件后仍先调用旧焦点 `setFocused(false)`，再调用新焦点 `setFocused(true)`，即使二者是同一对象。素材网格在前者立即取消手势，导致第二次选择不触发。

### Fix

按下回馈保持即时，在绘制／释放时检查最终焦点来取消实际失焦的手势。原生回归先复现“连续点击未替换选择草稿”，修正后验证第二项替换与确认。产物图标同时应显示文档中的实际数量，不能用注册表默认数量掩盖已提交值。富文本裁切必须保留样式及行内对象，不能经过 `getString()` 扁平化；静态类型为 `Component` 的文案需先 `copy()` 再调用 MutableComponent 的 `withStyle`。

截图还发现，直接在 `UiRichText.item(id)` 后追加名称会继承物品专用字体，中文显示成方框。新增 `UiRichText.withItem(id, label)` 用中性根组件组合图标与名称，隔离字体继承；中文样式回归在三端通过，原生截图也已复核“石头”显示正常。构建和交互断言不能代替缺字形的视觉检查。

### Metadata

- Source: native_client_regression / compiler
- Related Files: SHBUI client/gui/scaled/UiIngredientSlots.java、SHBUI client/gui/text/UiRichText.java、RecipeEditorSmoke.java
- Tags: uuk, focus, pointer-capture, rich-text
- See Also: ERR-20261116-004

## 2026-11-19 • 配方工作区的可读性与编辑效率

状态为 resolved。第一版客户端截图中，560×320 的画布把九宫格放在大块空白中央，通用配方需要打开一个子页才能修改一个数值。交互断言通过后，用户仍指出界面低效。此类页面应使用填好材料和参数的真实配方复核，检查改一个值到看见结果所需的点击次数，并检查中等视口能否同时显示正在编辑的数据及预览。

跨版本配色应检查各端实际 SHBUI JAR。NeoForge 26.1.2 的 UiTheme 提供 surface()，本次使用的版本缺少 secondarySurface()。

2026-11-19 补充：合并配方工作区与 Fumo 奖池时，从固定为 Forge 的通用草稿导入奖池 JSON，需保留奖池文档类型并采用当前客户端目标。奖池预览的校验、分页和自定义目录导出应走当前工作区入口。

校验静态 Fumo 资源时，Windows 默认 GBK 的 Python 环境需用 `python -X utf8 tools/verify_fake_fumo.py` 读取语言文件。

## 2026-11-21 • 配方弹窗与原生交互回归

屏幕内的 `UiOverlayHost` 独立持有弹窗按钮，客户端测试应通过浮层的焦点和按键事件操作。使用 `Screen.children()` 查找会误以为确认和导入按钮消失。测试脚本直接调用导航栏按键前应先将实际屏幕的焦点移到导航栏。拖动回归依次投递按下、拖动和释放事件，并在取消路径检查选择顺序。

### 固定槽位的批量拖放

批量填写的预览和配方画布都使用固定槽位。拖到空格保留目标索引，拖到已有原料的格子交换双方，原料的标签或手填 ID 与图标一起移动。回归覆盖跨行空位和不相邻的占用格，相邻交换无法区分旧的插入排序。客户端从属性页调用批量动作前，检查画布状态时须先切回画布。

SHCLIB 的原生客户端回归直接读取兄弟项目的 UUK JAR。运行依赖构建时等待 UUK 打包完成再启动客户端，避免 JVM 在该 JAR 被同路径覆写时找不到已存在的类。

### 只有 JEI 的实例

NeoForge 1.21.1 的 JEI 隔离存档在注册物品列表时触发 `JeiOrderableBadgeMixin`。Mixin 在转换类期间就会解析 `AEFluidKey::of`，所以方法体内的 AE2 加载判断无法阻止缺类崩溃。将带 AE2 符号的调用放进加载判断后的平台类。仅安装 JEI 的实例已能进入存档，对照了 1,324 个物品 ID 的原生排序。
