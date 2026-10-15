# Stats Web

把「Minecraft Stats Analyzer」网页做成 Fabric 服务端模组：服务器装上它，玩家用手机/电脑浏览器打开
`http://服务器IP:8080` 即可实时查看全服统计分析（中文 `/`、英文 `/en`），**无需安装任何客户端模组**。

- 目标版本：Minecraft **26.2**（Fabric Loader 0.19.5+，Java 25）
- 零第三方依赖：HTTP 服务用 JDK 自带的 `com.sun.net.httpserver`
- 完全不调用 Minecraft 内部类，只依赖 Fabric Loader API —— 跨版本兼容性最好

## 部署（服务器管理员）

1. 把 `build/libs/statsweb-1.0.1.jar` 放进服务器的 `mods/` 文件夹（替换旧版本）
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

## 数据来源

- 启动时全量扫描统计 JSON + `usercache.json`（服务器根目录或 world 文件夹均可），之后每 `refreshSeconds` 秒重扫一次
- **v1.0.1 起兼容 MC 26.1 的世界存储结构调整**：玩家数据已从 `<world>/stats` 移到 `<world>/players/stats`（`playerdata`→`players/data`、`advancements`→`players/advancements`），模组新旧路径都认
- 解析器与网页版一致：任意目录层级、UUID/玩家名文件名、BOM、数字字符串、模组命名空间、多世界合并、非统计文件自动跳过
- API：
  - `GET /api/version` — 探测模组
  - `GET /api/data` — 全量快照 JSON
- 网页保留 zip 上传功能作为兜底（没有模组时就是原来的离线分析器）

## 开发

```bat
:: 需要 JDK 25（本机装在 C:\Users\张一波\.workbuddy\tools\jdk-25.0.4.1+1）
set JAVA_HOME=C:\Users\张一波\.workbuddy\tools\jdk-25.0.4.1+1
gradlew.bat build        :: 产物在 build\libs\statsweb-1.0.1.jar
gradlew.bat runServer    :: 本地起一个真实 26.2 服务端做端到端测试
```

Gradle wrapper 已指向腾讯镜像（services.gradle.org 在当前网络不可用）。

## 已验证

- 真实 26.2 服务端 + 本模组启动成功，`/api/data` 返回快照：698 个统计文件全部识别、0 跳过 0 失败、玩家名正确解析（Jokerish 等数据与网页版完全一致）
- 中英页面均由模组托管，jsdom 全链路测试：页面自动探测服务器模式 → 拉取 API → 701 位玩家 / 5291 统计项 → 六个标签页全部正常渲染
- v1.0.1：新旧 stats 路径并存时（`players/stats` 3 个文件 + `stats` 698 个文件）扫描合并正确，701 文件 701 玩家，数据无损
