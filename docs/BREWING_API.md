<!-- 作者：山海／dishanhai -->
# 酿造配方公共 API

其他模组可以依赖 SH-Common-Lib，在服务端通过 `com.dishanhai.sh_common_lib.api.ulk.recipe` 注册酿造定义。API 签名只使用 Java 类型，不依赖 Forge／NeoForge 类型；消费方应把 SHCLIB 作为独立模组依赖，不把它的类打包进自己的 JAR。

```java
import com.dishanhai.sh_common_lib.api.ulk.recipe.BrewingRecipe;
import com.dishanhai.sh_common_lib.api.ulk.recipe.BrewingRecipes;

// 可在模组初始化期间调用，不要求物品和药水注册表已就绪。
BrewingRecipes.register(new BrewingRecipe(
        "example:diamond_awkward",
        BrewingRecipe.Kind.POTION,
        "minecraft:water",          // 输入药水 ID
        "minecraft:diamond",        // 材料物品 ID；也可写成 #namespace:tag
        "minecraft:awkward"));      // 输出药水 ID

// 瓶型转换保留药水内容和其他数据；输入／输出是药水容器物品 ID。
BrewingRecipes.register(new BrewingRecipe(
        "example:splash_conversion",
        BrewingRecipe.Kind.CONTAINER,
        "minecraft:potion",
        "#example:splash_ingredients",
        "minecraft:splash_potion"));

var previous = BrewingRecipes.find("example:diamond_awkward");
var snapshot = BrewingRecipes.snapshot(); // revision 和 recipes 属于同一不可变快照
BrewingRecipes.remove("example:diamond_awkward"); // 仅移除程序注册
```

`register` 按配方 ID 添加或替换，返回被替换的定义；`remove` 返回被移除的定义；`find` 只查询程序注册，不表示最终服务端生效规则。快照按 ID 排序，后续注册不会改变已取得的快照。同一程序定义重复注册不会推进 revision。接口线程安全，可从初始化代码或运行中的服务端调用。

构造定义时立即检查 ID、标签格式和路径，并拒绝用空气作为材料或瓶型。未找到的物品／药水、不是药水瓶的容器等需要在注册表就绪后检查：服务端会记录警告并跳过该条；语法通过不代表对应注册表值一定存在。不要在客户端单独注册并期待远程服务器生效，规则由服务端决定。

初始化期间注册的规则会在服务端数据资源就绪后解析；运行中注册、替换或移除后，酿造系统下一次查询时自动更新，不需要重启或 `/reload`。注册信息保留至显式移除或进程退出；服务器关闭会清除已解析的运行快照，下一次启动时重新解析。

相同配方 ID 的优先级依次为数据包、实例配置、程序注册。数据包与配置的变更需要服务端执行 `/reload`；无效单条会被跳过。若高优先级定义通过语法检查但引用未知注册表值，它会被跳过，不会自动启用被覆盖的低优先级同 ID 定义。不同 ID 但输入与材料相同的规则，按加载器和注册顺序匹配，避免依靠冲突来覆盖其他模组配方。

数据包路径为 `data/<namespace>/sh_common_lib/brewing/<path>.json`；实例配置路径为 `config/sh_common_lib/recipe_editor/brewing/<namespace>/<path>.json`。两种格式与可视化配方编辑器生成的酿造 JSON 兼容。API 当前支持药水转换与药水瓶型转换，每次输出一个瓶；普通物品充当瓶位输入或输出不在此接口的支持范围内。
