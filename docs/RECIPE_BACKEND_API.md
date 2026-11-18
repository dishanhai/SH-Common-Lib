# 配方后端 API

本轮补齐「脚本／其他模组提交配方 → 预检 → 生成资源」的公共能力，不启动客户端，不注册网络端点，不修改正在运行的 RecipeManager，也不自动执行 `/reload`。Forge 1.20.1、NeoForge 1.21.1 和 NeoForge 26.1.2 共用文档与批次模型；注册表访问由各版本适配。

## 批量预检与生成

```java
import com.dishanhai.sh_common_lib.api.ulk.recipe.*;

RecipeBatch.Result result = RecipeBatch.prepare(documents,
    RecipeTarget.NEOFORGE_1_21_1, RecipeFormat.JSON);
if (!result.valid()) {
    for (var error : result.issues()) {
        // index 为输入批次的零基下标，保留 recipeId、稳定错误 key 和 field。
        logger.warn("{} {} {}", error.index(), error.recipeId(), error.issue());
    }
} else {
    for (var artifact : result.artifacts()) {
        // 由调用者决定预览、下载或写入的位置和覆盖策略。
    }
}
```

- 上述重载只验证结构，不代表物品、标签、药水或自定义序列化器存在。
- 可传入第四个参数 `Function<RecipeDocument,List<RecipeIssue>>` 做注册表或业务校验。只对结构合法的文档调用；校验器异常直接传播，不伪装为成功。
- 最多 256 份文档，重复 ID、规范化输出路径冲突和超过工作区单文件 1 MiB 上限均返回诊断。输出顺序与输入一致，结果不可变。
- 任一文档无效时整个结果不提供产物；空批次是合法的零产物结果。
- 这是内存预检，不是文件事务。调用 `RecipeWorkspace.export` 分别保存多个产物时，后续 I/O 仍可能失败；不得宣称整批落盘原子性。
- CUSTOM 只保留并验证 JSON 基本结构；运行时自定义序列化器仍由消费模组验证。

## 草稿查询

```java
RecipeWorkspace workspace = new RecipeWorkspace(configDirectory.resolve("sh_common_lib/recipe_editor"));
RecipeWorkspace.Page page = workspace.query("mymod:", 0, 32);
// page.ids(), page.total(), page.hasMore()
```

按完整 ID 前缀过滤、字典序排序，offset 从零开始，limit 为 1–256；分页不解析草稿正文，损坏 JSON 仍可被发现并由 `load` 报错。每次查询都是新的目录快照，编辑期间跨页可能移动，调用方需要刷新，不能把 offset 当永久游标。

查询最多遍历 16,384 个目录项（包括不匹配前缀的项），超限明确失败，不返回伪完整列表。不跟随符号链接，缺失目录为空，其他 I/O 错误向调用方传播。现有 `list()` 保持兼容；大目录面板推荐使用 `query`。这是本地受信工作区接口，不是抵御其他进程并发替换目录的安全沙箱。

## 验证

`recipeBackendTest` 纳入三版本 `check`：所有目标版本 × 三种导出格式的顺序一致性、重复／无效批次无部分产物、校验器异常、不可变结果、分页／前缀／极大 offset、符号链接、不解析坏草稿以及目录扫描限额。

## 服务端注册表校验

`RecipeReferences` 不引用 Minecraft 客户端类。调用方可注入 `Lookup`，或使用真实世界注册表：

```java
// 在服务端线程执行；重载或切换世界后重新取得注册表适配器。
var lookup = MinecraftRecipeLookup.of(server.registryAccess());
var issues = RecipeReferences.validate(document, RecipeTarget.NEOFORGE_1_21_1, lookup);
```

应同时执行 `RecipeGenerator.validate` 的结构检查。查找结果区分 `PRESENT`、`MISSING` 和
`UNAVAILABLE`，未加载注册表会返回 `registry_unavailable`，不会误报物品不存在或标签为空。
没有世界时，`builtInOnly()` 可检查静态物品／药水，但标签与纹饰图案报告不可用。
`Lookup` 抛出的异常原样传播，不隐藏调用方错误。返回诊断列表不可变，保留稳定字段名。

覆盖物品、非空物品标签、药水、药水瓶型以及 26.1.2 锻造纹饰的 `pattern`。
1.20.1／1.21.1 锻造纹饰不使用 `result`，不会误查物品或纹饰注册表。
`CUSTOM` 文档保持不透明，不尝试理解第三方 serializer；空引用诊断列表不代表其可执行，
应由对应模组的 serializer 进一步验证。此 API 不加载区块、不安装数据包、不触发 `/reload`，
也不授予编辑权限或提供远端请求端点。服务端消费模组仍负责权限和线程调度。

既有客户端 `RecipeRegistryValidation.validate` 现在委托共用逻辑，保持原调用入口。
