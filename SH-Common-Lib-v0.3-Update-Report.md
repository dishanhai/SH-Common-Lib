<a id="top"></a>
# 🚀 SH-Common-Lib v0.3.0 — Update Report

[🇬🇧 English](#english) | [🇨🇳 简体中文](#chinese)

---

<a id="english"></a>
## 🇬🇧 English

### ✨ What's new

- **JEI search:** Explore suggestions, recipe previews, recent searches and favorites in a Liquid Glass panel. Search supports custom aliases, spelling suggestions and asynchronous indexing. Its display and history options can be configured in the control center.
- **Player information HUD:** Customize information groups, headings and separators, with automatic pagination and rich text. Optional information includes player status, weather forecasts, runtime, network traffic, AE2, FTB and ULK inventory summaries. NeoForge 1.21.1 also supports Neo ECO AE network and multiblock information.
- **Control center:** Stage HUD, loading and JEI search settings before applying them. Restore defaults, undo a reset before applying, and confirm before discarding unapplied changes.
- **Unified LIB Kernel (ULK):** Inspect read-only item snapshots from player slots and supported Curios, Sophisticated Backpacks, AE2 and Mekanism storage. Each source reports its availability or an incomplete scan.
- **Fullscreen announcements:** Choose Liquid Glass, Clear Liquid Glass or a solid appearance. The shared announcement API is also available on NeoForge 26.1.2.

### 🎮 Quick start

- Press **F10** to open the control center or **F6** to edit the HUD layout. The default HUD page keys are **Alt + ← / →**; bindings can be changed.
- In the focused JEI search box, use **↑ / ↓** to select a suggestion, **Tab** or **Enter** to complete, **Ctrl + Z** to undo, and **Esc** to close suggestions. Use **Ctrl + Tab** to switch history sources and **Alt + ← / →** to change suggestion pages.
- Many new HUD items are disabled by default. Some features require their optional mods and a compatible SH-Common-Lib installation on the server.

<details>
<summary>🔧 Compatibility and developer notes</summary>

| Minecraft | Loader | Java |
| --- | --- | --- |
| 1.20.1 | Forge | 21 |
| 1.21.1 | NeoForge | 21 |
| 26.1.2 | NeoForge | 25 |

- Install matching **SH-Better-UI** and **KotlinForForge** versions separately. The search enhancements require a matching SH-Better-UI build with the new search and popup APIs.
- ULK inventory inspection runs on the server thread and reads items only. An incomplete result does not mean the inventory is empty. A general remote client query protocol is not yet provided.
- NeoForge 26.1.2 chemical infinity-cell integration and GPU telemetry remain pending compatible dependencies. Client, multiplayer and integration acceptance remains in progress; see [TODO.md](TODO.md).

</details>

[↑ Back to top](#top)

---

<a id="chinese"></a>
## 🇨🇳 简体中文

### ✨ 更新内容

- **JEI 搜索：** 在液态玻璃面板中查看搜索建议、配方预览、最近搜索和收藏；支持自定义别名、拼写建议和异步索引，可在主控中心调整显示与历史设置。
- **玩家信息 HUD：** 自定义信息分组、标题和分隔线，支持自动分页与富文本。可选项目包括玩家状态、天气预告、运行环境、网络流量、AE2、FTB 和 ULK 库存汇总。NeoForge 1.21.1 还支持 Neo ECO AE 网络及多方块信息。
- **主控中心：** HUD、加载和 JEI 搜索设置可先暂存再应用；支持恢复默认、应用前撤销重置，并在放弃未应用的修改前确认。
- **Unified LIB Kernel（ULK）：** 提供只读物品快照，可检查玩家槽位和受支持的 Curios、精妙背包、AE2 与 Mekanism 存储；各来源会标明可用状态或扫描不完整。
- **全屏公告：** 可选择液态玻璃、通透液态玻璃或实色外观；NeoForge 26.1.2 也可使用共享公告 API。

### 🎮 快速上手

- 按 **F10** 打开主控中心，按 **F6** 编辑 HUD 布局。HUD 默认使用 **Alt + ← / →** 切页，按键可重新绑定。
- 聚焦 JEI 搜索框后，使用 **↑ / ↓** 选择候选、**Tab** 或 **Enter** 补全、**Ctrl + Z** 撤销、**Esc** 收起候选；使用 **Ctrl + Tab** 切换历史来源，**Alt + ← / →** 切换候选页。
- 多项新增 HUD 信息默认关闭；部分功能需要安装对应的可选模组，并在服务器安装兼容版本的 SH-Common-Lib。

<details>
<summary>🔧 兼容性与开发者说明</summary>

| Minecraft | 加载器 | Java |
| --- | --- | --- |
| 1.20.1 | Forge | 21 |
| 1.21.1 | NeoForge | 21 |
| 26.1.2 | NeoForge | 25 |

- 请分别安装匹配的 **SH-Better-UI** 和 **KotlinForForge**；搜索增强需要包含新搜索与弹层 API 的对应 SH-Better-UI 构建。
- ULK 库存检查在服务器线程运行，仅查询物品。扫描不完整不代表库存为空；目前尚未提供通用的远程客户端查询协议。
- NeoForge 26.1.2 的化学品无限元件集成与 GPU 遥测仍等待兼容依赖。客户端、多人及模组集成验收仍在进行，详见 [TODO.md](TODO.md)。

</details>

[↑ 返回顶部](#top)
