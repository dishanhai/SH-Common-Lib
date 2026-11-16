<!-- Author: dishanhai -->
# JEI 混合拼音预测

- 日期：2026-11-16
- 状态：resolved（实现和自动化回归）；游戏客户端验收见 TODO.md。
- 现象：输入 dah 可预测“打火石”，继续输入 dahs 后候选消失。
- 原因：ULK ClientSearch 委托 SHBUI SearchIndex；原索引只生成 dahuoshi 和 dhs，缺少按连续音节混合前缀的匹配。
- 修正：在 SHBUI 的 UUK kernel/api/search/PhoneticText 保存音节，用动态规划匹配各音节的非空前缀；保留全拼及首字母的包含匹配，拒绝跳字和错误字序，避免枚举指数数量的拼音组合。PinyinSearch／SearchIndex 继续负责汉字转换、查询语法和排序，ULK 保持调用边界。
- 依赖：修复实现在 SH-Better-UI；游戏需更新相同 Minecraft／加载器版本的 SHBUI，单换 SHCLIB 无法获得修复。
- 回归：修复前 SearchIndexTest 明确在 dahs 断言失败；修复后搜索框和索引 35 项测试、UUK 核心 4 项测试，SHBUI 三端搜索测试与打包校验及 SHCLIB 三端 clientSearchTest／build 已通过。覆盖 dahs、dahuos、dhuoshi、dhs、全拼、别名、模组过滤、候选顺序和异步补全。
- 构建入口：SHBUI 根目录使用 gradlew.bat（无 gradlew），NeoForge 使用 neoforge-1.21.1/gradlew.bat -p 对应模块；SHCLIB Forge 使用 forge-1.20.1/gradlew。
