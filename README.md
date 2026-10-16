# Stats Web — Minecraft Player Analyzer（服务端模组）

把「Minecraft Player Analyzer」网页做成服务端模组：服务器装上它，玩家用手机/电脑浏览器打开
`http://服务器IP:8080` 即可查看全服玩家数据（中文 `/`、英文 `/en`），**无需安装任何客户端模组、无需上传任何文件**。

**[简体中文](#简体中文) | [English](#english)**

- 当前版本：**v2.0.1**（Fabric 模组 + Paper/Spigot 插件，实测 Minecraft 26.2）
- **完全不调用 Minecraft 内部类**，只依赖平台 API（Fabric Loader / Bukkit）→ 同一份 jar 可跨游戏版本使用
- **一个 jar 打通新旧世界布局**：自动识别 `players/stats`（MC 26.1+）与 `stats`（1.18–1.21）等两代目录结构
- 零第三方依赖：HTTP 服务用 JDK 自带的 `com.sun.net.httpserver`，gson / slf4j 由游戏本体提供
- 相关仓库：[mc-stats-parser](https://github.com/s1791509659/mc-stats-parser)（离线 zip 分析网页版）

---

<a id="简体中文"></a>
## 简体中文

### 功能

- **页面只读**：数据全部由模组从服务器世界文件夹读取并定时刷新，页面上不再出现「上传文件 / 选择文件夹 / 载入示例数据 / 拖拽导入」等导入入口（离线网页版仍保留这些功能）
- **服务器总览**：KPI、分类分布、维度分布、附魔流行度、死亡榜、纪录榜
- **成就系统**：解锁总数/人均/最稀有、分类完成度、稀有度图、达成时间线、逐项成就明细
- **玩家档案**：游戏模式、维度坐标、生命/饱食/氧气、经验等级、重生点、背包 36 格 + 末影箱 27 格、装备栏、附魔汇总、状态效果
- **排行榜 / 玩家对比 / 数据表 / 趣味榜单**，以及**玩家进度时间线**与**战绩卡片导出**（PNG 分享图）
- 全站中英双语（`/` 与 `/en`），物品中文名与图标由页面本地解析

### 部署（服务器管理员）

1. 从 [Releases](https://github.com/s1791509659/statsweb/releases) 下载对应平台的包：
   - **Fabric**：`statsweb-2.0.1-fabric-mc1.18.2-26.x-loader0.14.jar` → 放进服务器的 `mods/` 文件夹
   - **Paper / Spigot**：`statsweb-2.0.1-paper-spigot-mc1.18.2-26.x-api1.18.jar` → 放进服务器的 `plugins/` 文件夹
2. 启动服务器
3. 首次启动会生成默认配置（Fabric：`config/statsweb.json`；Paper/Spigot：`plugins/StatsWeb/statsweb.json`，格式相同）：

```json
{
  "enabled": true,
  "port": 8080,
  "refreshSeconds": 60,
  "levelName": "",
  "statsDir": ""
}
```

- `port`：网页端口（记得在防火墙 / 端口映射里放行）
- `refreshSeconds`：扫描世界文件夹的间隔（原版统计文件在自动保存时才落盘，默认约 5 分钟一次，数据新鲜度以此为准）
- `levelName`：留空则自动读 `server.properties` 的 `level-name`
- `statsDir`：**留空即自动探测**；特殊布局可手动填路径（相对服务器根目录或绝对路径）
- `enabled`：设为 `false` 可整体停用

4. 玩家访问 `http://服务器IP:8080`（中文）或 `http://服务器IP:8080/en`（英文）

### 世界目录兼容（不同 MC 版本存放位置不同）

模组启动时会探测**所有**候选目录，命中哪一个用哪一个；启动日志逐条打印 `(found)` / `(missing)`，方便排查：

| 数据 | MC 1.18 – 1.21.x（旧布局） | MC 26.1+（新布局） |
| --- | --- | --- |
| 统计 stats | `<world>/stats` | `<world>/players/stats` |
| 成就 advancements | `<world>/advancements` | `<world>/players/advancements` |
| 玩家档案 playerdata | `<world>/playerdata` | `<world>/players/data` |

- 两代布局**同时探测**，都命中则合并（存档升级过渡期不会丢数据）
- 目录名匹配**不区分大小写**（Linux 服务端上的 `Stats`/`Advancements` 也能识别）
- 兜底候选：服务器根目录下的 `stats` / `advancements` / `playerdata`，以及 `statsDir` 手动指定路径的同级 `advancements` / `data`
- `usercache.json` 服务器根目录与 world 文件夹两处都会读

### API

- `GET /` / `/en` — 中文 / 英文页面
- `GET /translations.json` — jar 内置的中文物品名字典（页面已内嵌副本，此路由为可选刷新通道）
- `GET /api/version` — 探测模组（页面据此判断是否进入只读模式）
- `GET /api/data` — 全量快照 JSON（统计 + 成就 + 玩家档案 + 扫描计数）

### 构建

```bash
./gradlew :fabric:build :paper:build
# 产物 / artifacts:
#   fabric/build/libs/statsweb-2.0.1-fabric-mc1.18.2-26.x-loader0.14.jar
#   paper/build/libs/statsweb-2.0.1-paper-spigot-mc1.18.2-26.x-api1.18.jar
```

- 需要 **JDK 17+**（只编译，不需要装 Minecraft）
- 工程为 `core`（平台无关引擎）+ `fabric`（模组壳）+ `paper`（插件壳）三个模块，**不使用 Loom**
- 平台 jar 会把 core 的类与网页资源一起打包，交付物每个平台只有一个 jar
- Gradle wrapper 已指向腾讯镜像（services.gradle.org 在部分网络不可用）

### 平台支持

| 平台 | 包 | 状态 |
| --- | --- | --- |
| Fabric | `mods/`（loader ≥ 0.14 / MC ≥ 1.18.2） | ✅ 实测 MC 26.2 |
| Paper / Purpur | `plugins/`（Bukkit API ≥ 1.18） | ✅ 实测 Paper 26.2-134 |
| Spigot | `plugins/`（Bukkit API ≥ 1.18） | ✅ 与 Paper 同一个 jar |

因为核心不引用任何 Minecraft 类，同一 jar 理论上可在 1.18.2 以上的任意版本加载；26.2 是当前实测版本。

---

<a id="english"></a>
## English

Turn the "Minecraft Player Analyzer" page into a **server-side** mod: install it on your server
and every player can open `http://server-ip:8080` in a phone/PC browser to explore live, server-wide
player data (Chinese at `/`, English at `/en`) — **no client mod, no file uploads**.

- Version: **v2.0.1** (Fabric mod + Paper/Spigot plugin, verified on Minecraft 26.2)
- **Zero Minecraft classes** — only platform APIs (Fabric Loader / Bukkit), so one jar spans game versions
- **One jar, both world layouts**: auto-detects `players/stats` (MC 26.1+) and `stats` (1.18–1.21)
- Zero third-party dependencies: HTTP via JDK's `com.sun.net.httpserver`; gson / slf4j come from the game
- Related repo: [mc-stats-parser](https://github.com/s1791509659/mc-stats-parser) (the offline zip-based analyzer page)

### Features

- **Read-only page**: the mod reads the world folders and refreshes periodically — no upload / choose-folder / sample-data / drag-and-drop entry points (the offline web version keeps them)
- **Server overview**: KPIs, category & dimension distribution, enchantment popularity, death and record boards
- **Advancements**: totals / per-player / rarest, category completion, rarity chart, unlock timeline, per-advancement table
- **Player profiles**: gamemode, dimension & coordinates, health/hunger/air, XP level, spawn point, 36-slot inventory + 27-slot ender chest, equipment, enchantment summary, status effects
- **Ranking / comparison / data table / fun lists**, plus a **progress timeline** and an **exportable achievement card** (PNG share image)
- Fully bilingual (`/` and `/en`); Chinese item names and icons are resolved client-side

### Deployment (server admins)

1. Grab the package for your platform from [Releases](https://github.com/s1791509659/statsweb/releases):
   - **Fabric**: `statsweb-2.0.1-fabric-mc1.18.2-26.x-loader0.14.jar` → drop it into the server's `mods/` folder
   - **Paper / Spigot**: `statsweb-2.0.1-paper-spigot-mc1.18.2-26.x-api1.18.jar` → drop it into the server's `plugins/` folder
2. Start the server
3. On first launch a default config is created (Fabric: `config/statsweb.json`; Paper/Spigot: `plugins/StatsWeb/statsweb.json`, same format):

```json
{
  "enabled": true,
  "port": 8080,
  "refreshSeconds": 60,
  "levelName": "",
  "statsDir": ""
}
```

- `port`: web UI port (open it in your firewall / port forwarding)
- `refreshSeconds`: rescan interval (vanilla flushes stats on autosave, roughly every 5 minutes)
- `levelName`: leave empty to auto-read `level-name` from `server.properties`
- `statsDir`: **leave empty to auto-detect**; set an explicit path (relative to the server root, or absolute) for unusual layouts
- `enabled`: set to `false` to disable the mod

4. Players visit `http://server-ip:8080` (Chinese) or `http://server-ip:8080/en` (English)

### World layout compatibility

Every candidate directory is probed on startup; the startup log prints `(found)` / `(missing)` for each:

| Data | MC 1.18 – 1.21.x (legacy) | MC 26.1+ (modern) |
| --- | --- | --- |
| stats | `<world>/stats` | `<world>/players/stats` |
| advancements | `<world>/advancements` | `<world>/players/advancements` |
| playerdata | `<world>/playerdata` | `<world>/players/data` |

- Both layouts are probed **at once** and merged when both exist (no data loss while a world is upgraded)
- Directory matching **ignores case** (a Linux server may use `Stats` / `Advancements`)
- Fallbacks: `stats` / `advancements` / `playerdata` at the server root, plus the siblings of a manual `statsDir`
- `usercache.json` is read from both the server root and the world folder

### API

- `GET /` / `/en` — Chinese / English page
- `GET /translations.json` — bundled Chinese item-name dictionary (the page embeds a copy; this route is a refresh channel)
- `GET /api/version` — mod probe (the page uses it to switch to read-only mode)
- `GET /api/data` — full snapshot JSON (stats + advancements + player profiles + scan counters)

### Building

```bash
./gradlew :fabric:build :paper:build
# artifacts:
#   fabric/build/libs/statsweb-2.0.1-fabric-mc1.18.2-26.x-loader0.14.jar
#   paper/build/libs/statsweb-2.0.1-paper-spigot-mc1.18.2-26.x-api1.18.jar
```

- Requires **JDK 17+** (compiles only — no Minecraft installation needed)
- Three modules: `core` (platform-independent engine) + `fabric` (mod shell) + `paper` (plugin shell), **no Loom**
- Each platform jar packs the core classes and the web assets, so the deliverable per platform is a single jar
- The Gradle wrapper points to a Tencent mirror (services.gradle.org is unreachable on some networks)

### Platform support

| Platform | Package | Status |
| --- | --- | --- |
| Fabric | `mods/` (loader ≥ 0.14 / MC ≥ 1.18.2) | ✅ verified on MC 26.2 |
| Paper / Purpur | `plugins/` (Bukkit API ≥ 1.18) | ✅ verified on Paper 26.2-134 |
| Spigot | `plugins/` (Bukkit API ≥ 1.18) | ✅ same jar as Paper |

Because the core references no Minecraft class, the same jar should load on any version ≥ 1.18.2; 26.2 is the tested target.

---

## Verified / 已验证

v2.0.0 验证（真实 711 名玩家存档，`players.zip`，714 个统计/成就/档案文件组合）：

- **新布局**（`world/players/{stats,advancements,data}`）：714 玩家、708 统计文件、710 成就文件（3,368 次解锁 / 1,094 进行中 / 42,757 配方条目已排除）、712 份玩家档案、0 跳过 0 失败
- **旧布局**（`world/{stats,advancements,playerdata}`）：返回数据与新版布局**逐字节一致**（2,915,021 字节）
- 中英双页 jsdom 端到端：自动进入只读模式 → 导入 UI 与拖拽遮罩全部隐藏 → 服务器数据条与 16 个 KPI 正常渲染 → **0 报错**
- `/api/data` 2.9 MB，响应 0.09–0.24 s

v2.0.0 verified against a real 711-player archive (`players.zip`, 714 files across stats/advancements/playerdata):

- **Modern layout** (`world/players/...`): 714 players, 708 stats files, 710 advancement files (3,368 unlocks / 1,094 in progress / 42,757 recipe entries excluded), 712 profiles, 0 skipped / 0 failed
- **Legacy layout** (`world/{stats,advancements,playerdata}`): byte-for-byte identical output (2,915,021 bytes)
- zh + en jsdom end-to-end: read-only mode entered automatically → import UI and drop veil hidden → server data bar and 16 KPIs rendered → **0 errors**
- `/api/data` is 2.9 MB, served in 0.09–0.24 s

v2.0.1 验证 / v2.0.1 verified:

- 真实 **Fabric 26.2** 服务端与真实 **Paper 26.2-134** 服务端均加载成功、网页可访问
  / loads and serves the dashboard on a real **Fabric 26.2** server and a real **Paper 26.2-134** server
- 修复后 711 玩家存档：不再把 `<world>/data` 误判为玩家档案（幻影玩家）；损坏/空的 playerdata NBT 不再导致页面报「读取失败」
  / after the fixes: `<world>/data` is no longer mistaken for playerdata (phantom players); corrupt/empty playerdata NBT no longer breaks the page with a load error
- 玩家搜索补全下拉不再被卡片 `overflow` 裁剪 / the player-search autocomplete dropdown is no longer clipped by the card's `overflow`
- 711 玩家存档新/旧布局 `/api/data` 仍**逐字节一致**（2,915,021 字节） / both layouts still produce byte-for-byte identical `/api/data` output (2,915,021 bytes)
