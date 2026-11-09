# SH-Common-Lib

山海的 Minecraft 公用库，用于维护跨模组复用的 API、物品和方块。两端使用同一个模组 ID：`sh_common_lib`，各自构建和安装。

| 构建目录 | Minecraft | 加载器 | JDK | Gradle |
| --- | --- | --- | --- | --- |
| `forge/` | 1.20.1 | Forge 47.4.16 | 17 | 8.8 |
| `neoforge/` | 1.21.1 | NeoForge 21.1.251 | 21 | 9.2.1 |

在对应目录中，用该版本的 JDK 设置 `JAVA_HOME`，再运行：

```bash
./gradlew build
```

Windows 命令提示符可用 `gradlew.bat build`。成品在对应目录的 `build/libs/`，带 `-sources` 后缀的是源码包。开发环境可用 `./gradlew runClient` 或 `./gradlew runServer`。

## 代码放在哪里

- `common/src/main/java/`：共用 API 与实现。保持 Java 17 兼容；只使用两端均兼容的类型。公共入口为 `com.dishanhai.sh_common_lib.api`。
- `common/src/main/resources/`：两端共用的语言文件、模型、贴图。只有路径和格式一致的资源才放在这里。
- `forge/src/main/` 和 `neoforge/src/main/`：加载器入口、物品和方块注册、版本专用实现与资源。例如方块掉落表在 1.20.1 使用 `loot_tables/blocks/`，在 1.21.1 使用 `loot_table/blocks/`。

每个通用物品和方块在两端使用同一个注册 ID，由各自加载器在注册阶段创建。当前版本已预留注册入口，尚未注册具体内容。

## 供其他模组使用

在对应加载器目录运行 `./gradlew publishToMavenLocal`。消费项目可通过 `mavenLocal()` 解析以下坐标：

| 平台 | 坐标 |
| --- | --- |
| Forge 1.20.1 | `com.dishanhai:sh-common-lib-forge-1.20.1:0.1.0` |
| NeoForge 1.21.1 | `com.dishanhai:sh-common-lib-neoforge-1.21.1:0.1.0` |

ForgeGradle 消费项目使用 `implementation fg.deobf("com.dishanhai:sh-common-lib-forge-1.20.1:0.1.0")`；NeoForge 消费项目直接使用对应的 `implementation` 坐标。
消费模组在自己的模组元数据中声明对 `sh_common_lib` 的依赖。游戏客户端和服务器在各自的 `mods` 目录安装对应版本的库 JAR。

版权归 dishanhai 所有。对外分发前再确定发布许可证。
