# Stats Web

把「Minecraft Stats Analyzer」网页做成 Fabric 服务端模组：服务器装上它，玩家用手机/电脑浏览器打开
`http://服务器IP:8080` 即可实时查看全服统计分析（中文 `/`、英文 `/en`），**无需安装任何客户端模组**。

**[简体中文](#简体中文) | [English](#english)**

- 目标版本：Minecraft **26.2**（Fabric Loader 0.19.5+，Java 25）
- 零第三方依赖：HTTP 服务用 JDK 自带的 `com.sun.net.httpserver`
- 完全不调用 Minecraft 内部类，只依赖 Fabric Loader API —— 跨版本兼容性最好
- 相关仓库：[mc-stats-parser](https://github.com/s1791509659/mc-stats-parser)（离线 zip 分析网页版）

---

<a id="简体中文"></a>
## 简体中文

### 部署（服务器管理员）

1. 从 [Releases](https://github.com/s1791509659/statsweb/releases) 下载最新的 `statsweb-x.y.z.jar`，放进服务器的 `mods/` 文件夹
2. 启动服务器（需要 Java 25）
3. 首次启动会在 `config/statsweb.json` 生成默认配置：

```json
{
  "enabled": true,
  "port": 8080,
  "refreshSeconds": 60,
  "levelName": "",
  "statsDir": ""
}
```

- `port`：网页端口（记得在防火墙/端口映射里放行）
- `refreshSeconds`：扫描统计文件的间隔（原版统计文件在自动保存时才落盘，默认约 5 分钟一次，所以数据新鲜度以此为准）
- `levelName`：留空则自动读 `server.properties` 的 `level-name`
- `statsDir`：**留空自动探测**。模组按顺序尝试 `world/players/stats`（MC 26.1+ 新布局）和 `world/stats`（旧版布局），两个目录都存在时数据会合并。特殊布局可手动填路径（相对服务器根目录或绝对路径）
- `enabled`：设为 `false` 可整体停用

4. 玩家访问 `http://服务器IP:8080`（中文）或 `http://服务器IP:8080/en`（英文）

### 数据来源

- 启动时全量扫描统计 JSON + `usercache.json`（服务器根目录或 world 文件夹均可），之后每 `refreshSeconds` 秒重扫一次
- **v1.0.1 起兼容 MC 26.1 的世界存储结构调整**：玩家数据已从 `<world>/stats` 移到 `<world>/players/stats`（`playerdata`→`players/data`、`advancements`→`players/advancements`），模组新旧路径都认
- 解析器与网页版一致：任意目录层级、UUID/玩家名文件名、BOM、数字字符串、模组命名空间、多世界合并、非统计文件自动跳过
- API：
  - `GET /api/version` — 探测模组
  - `GET /api/data` — 全量快照 JSON
- 网页保留 zip 上传功能作为兜底（没有模组时就是原来的离线分析器）

### 开发

```bat
:: 需要 JDK 25
set JAVA_HOME=<JDK 25 路径>
gradlew.bat build        :: 产物在 build\libs\statsweb-x.y.z.jar
gradlew.bat runServer    :: 本地起一个真实 26.2 服务端做端到端测试
```

Gradle wrapper 已指向腾讯镜像（services.gradle.org 在部分网络不可用）。

---

<a id="english"></a>
## English

Turn the "Minecraft Stats Analyzer" web page into a Fabric **server-side** mod: install it on your
server and every player can open `http://server-ip:8080` in a phone/PC browser to explore live,
server-wide stats analysis (Chinese at `/`, English at `/en`) — **no client mod required**.

- Target: Minecraft **26.2** (Fabric Loader 0.19.5+, Java 25)
- Zero third-party dependencies: the HTTP server is the JDK built-in `com.sun.net.httpserver`
- Never touches Minecraft internals — only the Fabric Loader API, so it is naturally resilient across game versions
- Related repo: [mc-stats-parser](https://github.com/s1791509659/mc-stats-parser) (the offline zip-based analyzer page)

### Deployment (server admins)

1. Grab the latest `statsweb-x.y.z.jar` from [Releases](https://github.com/s1791509659/statsweb/releases) and drop it into the server's `mods/` folder
2. Start the server (requires Java 25)
3. On first launch a default `config/statsweb.json` is created:

```json
{
  "enabled": true,
  "port": 8080,
  "refreshSeconds": 60,
  "levelName": "",
  "statsDir": ""
}
```

- `port`: web UI port (remember to open it in your firewall / port forwarding)
- `refreshSeconds`: rescan interval for the stats files (vanilla flushes stats to disk on autosave, roughly every 5 minutes by default — data freshness follows that)
- `levelName`: leave empty to auto-read `level-name` from `server.properties`
- `statsDir`: **leave empty to auto-detect**. The mod tries `world/players/stats` (MC 26.1+ layout) and `world/stats` (legacy layout) in order; if both exist the data is merged. For unusual layouts you can set an explicit path (relative to the server root, or absolute)
- `enabled`: set to `false` to disable the mod entirely

4. Players visit `http://server-ip:8080` (Chinese) or `http://server-ip:8080/en` (English)

### Data source

- Full scan of the stats JSON files + `usercache.json` (server root or world folder both work) on startup, then a rescan every `refreshSeconds` seconds
- **Since v1.0.1 the mod is compatible with MC 26.1's world storage restructuring**: player data moved from `<world>/stats` to `<world>/players/stats` (`playerdata`→`players/data`, `advancements`→`players/advancements`) — both old and new paths are supported
- The parser matches the web analyzer: any directory depth, UUID or player-name file names, BOMs, numeric strings, modded namespaces, multi-world merging, non-stat JSON skipped automatically
- API:
  - `GET /api/version` — mod probe
  - `GET /api/data` — full snapshot JSON
- The page keeps the zip-upload flow as a fallback (without the mod it behaves as the original offline analyzer)

### Development

```bat
:: requires JDK 25
set JAVA_HOME=<path to JDK 25>
gradlew.bat build        :: artifact at build\libs\statsweb-x.y.z.jar
gradlew.bat runServer    :: spins up a real 26.2 dedicated server for e2e testing
```

The Gradle wrapper points to a Tencent mirror (services.gradle.org is unreachable on some networks).

---

## Verified / 已验证

- 真实 26.2 服务端 + 本模组启动成功，`/api/data` 返回快照：698 个统计文件全部识别、0 跳过 0 失败、玩家名正确解析（数据与网页版完全一致）
  Real 26.2 dedicated server + this mod: `/api/data` returns a snapshot with all 698 stat files recognized, 0 skipped / 0 failed, player names resolved correctly (data matches the web version exactly)
- 中英页面均由模组托管，jsdom 全链路测试：页面自动探测服务器模式 → 拉取 API → 701 位玩家 / 5291 统计项 → 六个标签页全部正常渲染
  Both zh/en pages served by the mod; full jsdom e2e test: server-mode auto-detection → API fetch → 701 players / 5,291 stat keys → all six tabs render correctly
- v1.0.1：新旧 stats 路径并存时（`players/stats` 3 个文件 + `stats` 698 个文件）扫描合并正确，701 文件 701 玩家，数据无损
  v1.0.1: with both stats layouts present (`players/stats` 3 files + `stats` 698 files) the scan merges correctly — 701 files, 701 players, no data loss
