# 生存契约（Survival Contract）更新日志

模组版本：survival-contract-1.0.0（Minecraft 26.2 / Fabric）
部署产物：`survival-contract-1.0.0.jar`（构建后自动 deployJar 到仓库根目录）

---

## 2026-10-17（日志归档：原 `*.log` 文件合并）

> **约定变更**：此前每次构建/自测产出的 `*.log` 临时文件（49 个）以及 `sc-mod/run/logs/` 下 15 个游戏运行日志轮转归档（`*.log.gz`）已<b>全部删除</b>；自本批次起，构建与自测结果**统一记录在本文件**，不再单独落盘 log。原始逐行输出已随文件删除，仅保留结论（构建结果 + 自测 PASS/FAIL 计数）。

### 根目录（`survival-contract/`）

| 日志文件 | 时间 | 结论 |
|---|---|---|
| `mc262-final.log` | 10-16 23:58 | 自测 6480 PASS / 2 FAIL；BUILD SUCCESSFUL 1m4s |
| `mc262-self.log` | 10-16 23:50 | 仅构建；BUILD SUCCESSFUL 19s |
| `mc263-full.log` | 10-16 23:33 | 仅构建；BUILD SUCCESSFUL 13s |
| `mc263-full2.log` | 10-16 23:44 | 仅构建；BUILD SUCCESSFUL 16s |
| `mc263-retry.log` | 10-16 18:12 | BUILD FAILED 11m22s |
| `mc263-run.log` | 10-16 22:37 | BUILD FAILED 7s |
| `mc263-self.log` | 10-16 23:46 | 自测 6477 PASS / 4 FAIL；BUILD SUCCESSFUL 1m3s |
| `mc263-compile.log` | 10-16 17:26 | 26.3 编译探测（Loom 缓存锁重建），无自测 |
| `build-mc26.3.log` | 10-16 17:43 | BUILD FAILED 4m24s |
| `sc262.log` | 10-17 00:19 | 自测 6482 PASS / 0 FAIL；BUILD SUCCESSFUL 1m |
| `selftest_262.log` | 10-17 00:59 | 自测 6493 PASS / 0 FAIL；BUILD SUCCESSFUL 1m21s |
| `selftest_263.log` | 10-17 01:00 | 自测 6488 PASS / 2 FAIL；BUILD SUCCESSFUL 56s |
| `task-test.log` | 10-15 23:15 | 自测 6476 PASS / 0 FAIL；BUILD SUCCESSFUL 58s |
| `immunity-test3.log` | 10-15 22:38 | 自测 487 PASS / 0 FAIL；BUILD SUCCESSFUL 54s |
| `selftest_r5.log` | 10-03 15:35 | 自测 106 PASS / 0 FAIL；BUILD SUCCESSFUL 48s |
| `selftest_r12.log` | 10-03 17:50 | 自测 106 PASS / 0 FAIL；BUILD SUCCESSFUL 50s |

### `sc-mod/`

| 日志文件 | 时间 | 结论 |
|---|---|---|
| `clienttest.log` | 10-03 07:11 | 客户端启动测试；BUILD SUCCESSFUL 5m19s |
| `servertest.log` | 10-03 07:45 | 服务端自测 25 PASS / 0 FAIL；BUILD SUCCESSFUL 44s |
| `sc-client.log` | 10-03 13:33 | 客户端启动（MC 26.2 + Fabric 0.19.3 + 52 mods），SC 客户端初始化成功 |
| `sc-selftest.log` | 10-03 13:05 | 59 PASS / 1 FAIL；SUCCESS 44s |
| `sc-selftest2.log` | 10-03 13:08 | 59 PASS / 1 FAIL；SUCCESS 47s |
| `sc-selftest3.log` | 10-03 13:14 | 59 PASS / 1 FAIL；SUCCESS 43s |
| `sc-selftest4.log` | 10-03 13:16 | BUILD FAILED 8s |
| `sc-selftest5.log` | 10-03 13:17 | 59 PASS / 1 FAIL；SUCCESS 50s |
| `sc-selftest6.log` | 10-03 13:19 | 59 PASS / 1 FAIL；SUCCESS 47s |
| `sc-selftest7.log` | 10-03 13:23 | 59 PASS / 1 FAIL；SUCCESS 1m48s |
| `sc-selftest8.log` | 10-03 13:25 | 59 PASS / 1 FAIL；SUCCESS 1m39s |
| `sc-selftest9.log` | 10-03 13:28 | 59 PASS / 0 FAIL；SUCCESS 1m52s |
| `sc-selftest10.log` | 10-03 13:30 | 59 PASS / 0 FAIL；SUCCESS 44s |
| `sc-selftest11.log` | 10-03 13:32 | 59 PASS / 0 FAIL；SUCCESS 46s |
| `sc-selftest12.log` | 10-03 14:02 | 仅构建；SUCCESS 15s |
| `sc-selftest13.log` | 10-03 14:03 | 66 PASS / 0 FAIL；SUCCESS 48s |
| `sc-selftest14.log` | 10-03 14:35 | 仅构建；SUCCESS 15s |
| `sc-selftest15.log` | 10-03 14:37 | 86 PASS / 0 FAIL；SUCCESS 54s |
| `build-mc26.2.log` | 10-16 21:09 | 空文件（输出未重定向入文件） |
| `build-mc26.3.log` | 10-16 21:09 | 空文件（输出未重定向入文件） |

### `sc-mod/build/`

| 日志文件 | 时间 | 结论 |
|---|---|---|
| `build-262.log` | 10-17 01:33 | 仅构建；BUILD SUCCESSFUL 11s |
| `build-263.log` | 10-17 01:33 | 仅构建；BUILD SUCCESSFUL 10s |
| `selftest-262-diag.log` | 10-17 01:23 | 6490 PASS / 2 FAIL；SUCCESS 53s |
| `selftest-262-diag2.log` | 10-17 01:26 | 6489 PASS / 2 FAIL；SUCCESS 54s |
| `selftest-262-diag3.log` | 10-17 01:28 | 6488 PASS / 2 FAIL；SUCCESS 55s |
| `selftest-262-fix.log` | 10-17 01:18 | 6487 PASS / 2 FAIL；SUCCESS 53s |
| `selftest-262-fix2.log` | 10-17 01:19 | 6489 PASS / 2 FAIL；SUCCESS 53s |
| `selftest-262-final.log` | 10-17 01:31 | 6491 PASS / 0 FAIL；SUCCESS 51s（第七十一批 26.2 终验） |
| `selftest-263-diag.log` | 10-17 01:12 | 6492 PASS / 2 FAIL；SUCCESS 57s |
| `selftest-263-fix.log` | 10-17 01:16 | 6489 PASS / 0 FAIL；SUCCESS 57s |
| `selftest-263-final.log` | 10-17 01:32 | 6489 PASS / 0 FAIL；SUCCESS 56s（第七十一批 26.3 终验） |

### `sc-mod/run/logs/`

| 日志文件 | 时间 | 结论 |
|---|---|---|
| `latest.log` | 10-17 01:32 | MC 服务端运行日志；含自测终验 6489 PASS / 0 FAIL |
| `debug.log` | 10-17 01:32 | MC 服务端调试日志；含自测终验 6489 PASS / 0 FAIL |

---

## 2026-10-22（第九十四批）

### 任意游戏模式开局后上方白色加载进度条（100×4 缩小版），进度满后全员传送且进度条消失

- **① 需求**：点击"开始游戏"后屏幕上方立刻出现白色进度条（仅大厅地图内），进度满 100% 后立刻全员传送 + 进度条消失。所有玩家（含观战者）统一等待进度条；Addon 模式（地狱厨房）也有进度条。进度条尺寸适当缩小。
- **② 服务端 LoadingProgressManager**（新建文件 [`LoadingProgressManager.java`](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/dimension/LoadingProgressManager.java)）：单例，`begin()` 在 LobbyManager.startGame **所有路径末尾**（内置玩法 + Addon 早返回之前）都调用；`tick()` 由 MainEventListener 主 tick 循环分发，6 秒线性满载 + 8 秒超时兜底；`finish()` 区分 isAddon：SC 内置玩法调 `teleportAllToArena` 触发真正传送，Addon 模式玩家已由 provider.onGameStart 传到对应地图，只广播 hide 不重复传送。
- **③ LobbyManager 拆分传送**（[`LobbyManager.java`](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/dimension/LobbyManager.java)）：
  - 原 startGame 里观战者"同步立即传送"逻辑已**回退** → 所有玩家（含观战者）统一清状态 + 等进度条。
  - `teleportAllToArena(server)`：全员传送；观战者传送后立刻 `setGameMode(SPECTATOR)` 保留旁观模式；欢迎语（玩法说明/队伍标签/身份提示）仅参战者发；`sendSync` 全员发（观战者 HUD 也要更新）。
  - Addon 早返回路径（`provider.onGameStart` → `sendSync` → `return`）之间插入 `LoadingProgressManager.getInstance().begin(server)`，确保 HK 厨房也有进度条。
- **④ MainEventListener 接入 tick**（[`MainEventListener.java`](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/MainEventListener.java)）：主 tick 循环开头（try 块内、gsm 后）加 `LoadingProgressManager.getInstance().tick(server)`，无论什么模式都推进进度。
- **⑤ 网络 Payload**（[`NetworkRegistry.java`](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/network/NetworkRegistry.java)）：新增 `LoadingProgressPayload(float progress, boolean show)` 类型，路径 `survival-contract:loading_progress`，show=false 时客户端立刻隐藏进度条。
- **⑥ 客户端 HUD**（[`HudRenderer.java`](file:///f:/survivalcontract/sc-mod/src/client/java/com/survivalcontract/client/HudRenderer.java)）：新增 `renderLoadingBar()`，屏幕上方 **36px** 居中，**100×4 缩小版**白色进度条 + 1px 白边 + 半透明底，无文字（极简），进度满/起始时自动隐藏防闪烁。用 `HudElementRegistry.addLast` 最顶层绘制。
- **⑦ ClientState 接收**（[`ClientState.java`](file:///f:/survivalcontract/sc-mod/src/client/java/com/survivalcontract/client/ClientState.java)）：新增字段 `loadingVisible` / `loadingProgress` + registerNetworkHandlers 里注册 LoadingProgressPayload receiver。
- **⑧ 附带修复**：`CivilizationManager.produceBarrel` 里 `continue produceBarrel;` → `return;`（方法内无循环，continue produceBarrel 是无效标签语法）。
- **⑨ 构建**：SC **BUILD SUCCESSFUL 11s**，HK **BUILD SUCCESSFUL 5s**，deployJar 已跑（mc26.2 / mc26.3 同步部署）。
- **⑩ 客户端同步**：请**完全退出游戏后**手动把 `f:\survivalcontract\mc26.2\survival-contract-1.0.0-mc26_2.jar` 覆盖到 `E:\bingo\.minecraft\versions\生存合约\mods\`（26.3 同理），进入单人存档验证开局进度条效果（单人一位、多人两位均测试）。

### 【同日更正】地狱厨房回归秒传送；进度条下移 + 百分比

- **① 需求**：①地狱厨房是定制地图秒传送，**不需要加载进度条**，回归旧版流程（上一版把 phase 切换延迟到传送后，但 `finish()` 里 Addon 又跳过传送 → phase 永远停在 LOBBY：开局没倒计时、也无法结束游戏）；②其余非定制地图玩法保留进度条，往下稍微挪动，进度条**下方显示百分比**。
- **② Addon 回归旧版**（[`LobbyManager.java`](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/dimension/LobbyManager.java)）：startGame 的 Addon 分支恢复原同步逻辑——`setCurrentPhase(PLAYING)` + `setGameStartTime(+60)` + 参战者 setScore(0)/职业 NONE/清状态 + 观战者 SPECTATOR + clearPenaltyFlags/clearAll → `provider.onGameStart`（秒传送厨房维度）→ sendSync；**不调 LoadingProgressManager.begin**。
- **③ finish() 恢复**（[`LoadingProgressManager.java`](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/dimension/LoadingProgressManager.java)）：去掉 isAddon 跳过分支，进度满直接调 `teleportAllToArena`；teleportAllToArena 同步清理掉 Addon 死代码分支（仅内置玩法调用）。
- **④ 进度条调整**（[`HudRenderer.java`](file:///f:/survivalcontract/sc-mod/src/client/java/com/survivalcontract/client/HudRenderer.java)）：y=2 → **y=6**（往下挪），进度条下方 4px 处居中显示白色带阴影**百分比**（如 `42%`）；仍为 100×4 白色细条 + 1px 白边 + 半透明底。
- **⑤ 构建**：SC **BUILD SUCCESSFUL 11s**，deployJar 已跑（mc26.2 / mc26.3 同步部署）；HK 无改动。验证：地狱厨房开局应立刻有倒计时且能正常结束游戏；内置玩法开局大厅顶部细白进度条 + 百分比，满后传送、进度条消失。

### 【同日更正 2】修复 loading 期间大厅刷怪

- **① 需求**：开始游戏后（进度条 6 秒期间）玩家仍在大厅，却发现大厅开始刷怪——旧版里 startGame 的 0.9 步骤会立刻为竞技场重新开启 `SPAWN_MOBS`（共享游戏规则），旧版玩家马上被传走无感；时序重构后玩家在大厅停留 6 秒 → 大厅刷怪。
- **② 修复**（[`LobbyManager.java`](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/dimension/LobbyManager.java)）：`SPAWN_MOBS` 开启从 startGame（0.9）移至 `teleportAllToArena`（进度满、传送前一刻执行）——loading 期间大厅保持关闭，传送后竞技场正常刷怪；回大厅关闭逻辑（endGame）未动，闭环不变。
- **③ 自我复查**（用户要求）：逐项核查本批全部时序改动——queueTitle/clearPendingTitles 顺序正确；loading 期间 phase=LOBBY 时挑战/计分/任务等 PLAYING 逻辑不跑；观战者传送后才设 SPECTATOR；物品/血量/欢迎语全部延后到传送时；Addon（HK）路径完全恢复旧版（SPAWN_MOBS 原本就不在其路径）；无其他副作用。
- **④ 构建**：SC **BUILD SUCCESSFUL 10s**，deployJar 已跑（mc26.2 / mc26.3 同步部署）。

### 【同日更正 3】进度条 6 秒 → 4 秒；开始瞬间显示 0%

- **① 需求**：①满载时间 6 秒太长，改 4 秒；②点击开始游戏的瞬间就要出现 0% 进度条（之前 `p <= 0.01f` 拦截导致 begin 广播的 0% 不渲染，要等第一次节流广播才出现）。
- **② 改动**：[`LoadingProgressManager.java`](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/dimension/LoadingProgressManager.java) `LINEAR_FULL_TICKS` 120 → **80**（4 秒满载，超时 8 秒兜底不变）；[`HudRenderer.java`](file:///f:/survivalcontract/sc-mod/src/client/java/com/survivalcontract/client/HudRenderer.java) 去掉下限拦截，只挡已满（`p >= 0.999f`）——begin 即时广播的 `progress=0` 立即渲染出 0% 空条。
- **③ 构建**：SC **BUILD SUCCESSFUL 10s**，deployJar 已跑（mc26.2 / mc26.3 同步部署）。

---

## 2026-10-21（第九十二批）

### 厨房地图替换为「街角灶源 1.0.7」；地图类型新增「热气球」

- **① 需求**：①用上传的 `街角灶源1.0.7.zip` 存档替换 HK 内置厨房地图；②挑战书「地图类型」新增第三项「热气球」，每队分 A/B 两组，出生点：一队 A(35.5,1,-454.5) / B(19.4,1,-454.5)，另一队 A(37.5,3,-510.5) / B(21.5,3,-510.5)。
- **② 地图替换**：JAR 内置 `data/hell-kitchen/kitchen/{region,entities,poi}/*.mca` 共 **26 个**（region 10 / entities 6 / poi 10）全部替换为 1.0.7 版本，共 **25,702,400** 字节；逐文件 MD5 与上传存档一致。
- **③ 新地图类型**（[`KitchenStyleProvider`](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/KitchenStyleProvider.java)）：
  - `MAP_BALLOON = 2`；二级菜单「地图类型」变为 3 个取值（街角 / 水族馆 / 热气球），三语言新增 `hell-kitchen.map.balloon`（热气球 / 熱氣球 / Hot Air Balloon）。
  - 红蓝队随机分配两侧（沿用原设计）；热气球西侧 = z≈-454 基地、东侧 = z≈-510 基地。
  - 出生点（[`KitchenConfig.defaultSpawns`](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/KitchenConfig.java#L139-L153)）：`balloonWestA(35,1,-454)` / `balloonWestB(19,1,-454)` / `balloonEastA(37,3,-510)` / `balloonEastB(21,3,-510)`；传送时统一 +0.5 落到方块中心，实际落点 35.5/19.5/-454.5、37.5/21.5/-510.5（与需求误差 ≤0.1 格）。
- **④ 热气球交付漏斗**：新地图热气球区域自带 2 个 hopper —— 西侧方块 (27,0,-449)、东侧方块 (29,2,-505)。配置坐标按「方块中心」写成 `balloonWest(27.5,0.0,-448.5)`、`balloon(29.5,2.0,-504.5)`。
  - 两侧漏斗 x 均 >0，原「x<0 判西侧」规则会把两个都判给东侧队 → [`Delivery`](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/KitchenConfig.java#L65-L83) 新增显式 `side` 字段，[`OrderManager.start`](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/order/OrderManager.java#L68-L80) 改为 `side` 优先、未指定时回退 `x<0`。
  - 自测首轮暴露坐标写偏 1 格（-449.5 / -505.5 实际指向 -450 / -506 的空位），已按方块中心修正为 -448.5 / -504.5，并新增「地图自带两个热气球漏斗就位 2/2」断言防回归。
- **⑤ 地图快照版本标记**（[`KitchenMap`](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/KitchenMap.java#L55-L66)）：1.0.6 → 1.0.7 的 .mca **字节数完全相同、内容不同**，而 `FixedMapInjector.verify` 只比对大小 → 老存档里的 `hell-kitchen-pristine` 快照会被误判为有效、继续恢复旧图（热气球区域不存在）。
  - 新增 `MAP_VERSION = "1.0.7"` 与快照内 `map-version.txt`；`snapshotPristine` / `restoreTo` 改为「文件完整 **且** 版本一致」才复用，否则删除重建。老存档首次启动会自动把快照升级为新图。
- **⑥ 自测（新增/改断言 3 条）**：地图类型选项三项 + 10 个出生点坐标 + 6 个交付漏斗坐标；热气球漏斗按 `side` 归属（西/东各一队）；地图 1.0.7 自带两个热气球漏斗就位 2/2。
- **⑦ 自测结果**：HK **26.2 = 71 PASS / 0 FAIL / 0 SKIP**、**26.3 = 66 PASS / 0 FAIL / 3 SKIP**（26.3 跑前临时移出农夫乐事 jar，跑完已恢复）。SC 本批无改动，26.2 / 26.3 未重跑。
- **⑧ 产物**：`hell-kitchen-1.0.0-mc26_2.jar` **3970599** 字节、`mc26_3.jar` **3970598** 字节，已部署到 `mc26.2/`、`mc26.3/`。
- **⑨ 客户端同步**：请**完全退出游戏后**手动把 `f:\survivalcontract\mc26.2\hell-kitchen-1.0.0-mc26_2.jar` 覆盖到 `E:\bingo\.minecraft\versions\生存合约\mods\`（26.3 同理）。老存档无需删档：新版启动时会自动重建地图快照。
- **⑩ 日志**：本轮 `hk-mod/run/logs` 与临时解析脚本已删除，结论仅保留在本文件。

## 2026-10-21（第九十一批）

### 回大厅经验槽清零修复；神的谎言「神谕纸符」结算规则重做

- **① 需求**：①修复「游戏结束回到大厅后经验槽（等级/进度/总经验）没有清空」；②「神的谎言」符纸规则杂糅，按用户规则重做：
  - 仅剩 **1 名使者**时：使者使用纸符后，被指认 → **抵挡一次指认出局**，同时链接者受到一次神罚；
  - **至少 2 名使者**时：使用纸符的使者被指认 → **使者与链接者一同出局**；
  - **平民**使用纸符后：抵挡一次神罚，但**不能**抵挡指认失败的扣分。
- **② 经验槽根因**：对局结束回大厅的清理入口 [`clearPlayerState`](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/MainEventListener.java#L2762-L2789) 只清空物品栏 / 药水效果 / 成就 / 火焰，未重置原版经验三字段；经验（`experienceLevel` / `experienceProgress` / `totalExperience`）不随物品栏清空，于是残留到大厅与下一局。
- **③ 修复**：
  - `clearPlayerState` 中把三个经验字段统一归零（覆盖「结束回大厅」与「开局统一清理」两条主路径）。
  - [`LobbyManager.teleportToLobby`](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/dimension/LobbyManager.java#L124-L136) 同样清零，兜住任何"直接回大厅"路径（如开局失败回退、手动回大厅）。
- **④ 符纸结算重做**（[`resolveAccusation`](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/game/GameStateManager.java#L580-L615)）：
  - 指认未命中平民 → 指认者 -200（**不检查**平民纸符免疫，即纸符不挡指认失败）。
  - 命中使者且存在未出局链接者时，按**指认时刻的存活使者数**分支：
    - 存活使者 ≤ 1 → 使者不扣分、不淘汰、身份不公开（新增结果 `NULLIFIED`，不广播指认成功/失败）；链接者 -55 神罚（如链接者已武装平民纸符则免疫消耗、改发"挡下"提示）。
    - 存活使者 ≥ 2 → 使者与链接者**强制出局**（新增 `forceElimination`，不看分数；平民纸符免疫不能挡"出局"），并广播契约触发文案；新增结果 `BOTH_OUT`，不再叠加通用的"指认成功、-200 分"文案。
  - 命中使者但无链接（未用纸符 / 契约已触发 / 链接者已出局）→ 正常 -200，分数 ≤0 淘汰。
  - 契约链接一次性：触发即从 `envoyLinks` 移除。
- **⑤ 文案（三语言）**：`gui.relic.hint` / `relic_link_set` 改为按存活使者数说明两种契约效果；新增 `survival-contract.msg.godslies.relic_last_guard`（使者抵挡一次指认）、`link_both_out`（使者与链接者一同出局）。
- **⑥ 自测（神的谎言符纸 4 局 + 经验槽 1 条，共 5 条新/改断言）**：
  - 局1 双使者：契约触发 → 使者与已武装纸符的链接者均强制出局（分数不变）、链接清空、使者获胜判定；
  - 局2 最后使者 + 已武装链接者：第一次指认 `NULLIFIED`（使者 100 分未淘汰、链接者神罚被免疫挡下、对局继续），第二次指认正常 -200 淘汰、平民获胜；
  - 局3 最后使者 + 未武装链接者：`NULLIFIED`、链接者 100 → 45、契约清除；第二次指认正常淘汰；
  - 局4 平民纸符：指认平民失败仍 -200 并淘汰，神罚免疫未被消耗；
  - 回大厅：等级 12 / 进度 0.50 / 总经验 350 → 全部归零。
- **⑦ 自测结果**：SC **26.2 = 6545 PASS / 0 FAIL**、**26.3 = 6546 PASS / 0 FAIL**；HK **26.2 = 68 PASS / 0 FAIL / 0 SKIP**、**26.3 = 63 PASS / 0 FAIL / 3 SKIP**（26.3 跑前临时移出农夫乐事 jar，跑完已恢复）。
- **⑧ 产物**：SC `survival-contract-1.0.0-mc26_2.jar` **2857092** 字节、`mc26_3.jar` **2857118** 字节；HK `hell-kitchen-1.0.0-mc26_2.jar` 3863869、`mc26_3.jar` 3863868（HK 不内嵌 SC，体积不变）。已分别输出到 `mc26.2/`、`mc26.3/`，并同步到 `hk-mod/libs`。
- **⑨ 客户端同步**：请**完全退出游戏后**手动复制 `f:\survivalcontract\mc26.2\survival-contract-1.0.0-mc26_2.jar` 与 `hell-kitchen-1.0.0-mc26_2.jar` 到 `E:\bingo\.minecraft\versions\生存合约\mods\`（26.3 同理）。
- **⑩ 日志**：本轮 `sc-mod/run/logs`、`hk-mod/run/logs` 及临时自测日志已全部删除，结论仅保留在本文件。

## 2026-10-21（第九十批）

### 走私犯与检察官：受贿结算漏计修复 + 达标胜利条件；神的谎言描述 120 → 100

- **① 需求**：①修复「贿赂袋里有价值物品、对方接受贿赂后袋子没清空且没加分」的偶发问题；②为该模式新增胜利条件——率先获得「玩家数 × 300」分的队伍/个人直接获胜；③「神的谎言」二级菜单描述仍写 120 分（初始分第五十四批已改为 100），修正。
- **② 受贿漏计根因（两条并发路径）**：
  1. **内嵌收纳袋内容未结算**：受贿结算只读服务端虚拟袋容器 `bribeBags`；玩家把物品左键塞进「贿赂收纳袋」道具的内嵌原版 `BUNDLE_CONTENTS` 时，`spillVanillaBundleContents` 只在开袋/进室时执行，结算瞬间物品仍藏在道具内嵌组件里 → 不计分（表现为"袋里有货却 0 分"）。
  2. **菜单光标物品不在容器里**：被检查者进室后仍开着贿赂袋界面并"拎着"物品（光标），结算时该物品既不在容器（不计分），`clearContent()` 也清不到；界面关闭后物品回到物品栏 → 表现为"袋子没清空"。
- **③ 修复（[SmuggleManager](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/smuggle/SmuggleManager.java#L883-L909)）**：
  - `settleAcceptBribe`：结算前**再次** `spillVanillaBundleContents(initiator)`（兜住进室后塞入袋道具的物品）；新增 `takeBribeBagCursor`（[L493-L508](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/smuggle/SmuggleManager.java#L493-L508)）把光标上的贿赂物品按静态价值表计价、清空光标并关闭界面；两路价值与容器价值合计后再 `clearContent()`，随后照常兑换价值袋。
  - `settleInspect`：结算前同样补一次 spill（价值袋等同源漏计路径）。
- **④ 新增达标胜利（[GameStateManager](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/game/GameStateManager.java#L268-L311)）**：
  - `addScore` 内即时判定（非 tick 轮询），新增常量 `STOWAWAY_WIN_SCORE_PER_PLAYER = 300`；阈值 = `scores.size() × 300`（开局初始化的参战人数，观战者不计入）。
  - 个人模式：该玩家分数 ≥ 阈值 → 该玩家获胜；团队模式：`getTeamScore(team)` ≥ 阈值 → 全队成员获胜。
  - 命中后写入 `forcedWinners` 并 `endGame(null)`，沿用现有 FINISHED 广播（胜者列表 / 免罚特权 / 5 秒回大厅），并向全员发送 `survival-contract.msg.stowaway_score_victory`。
- **⑤ 神的谎言描述修正**：`zh_cn.json` / `zh_tw.json` / `en_us.json` 的 `survival-contract.gui.godslies.hidden_desc2` 与客户端 [ChallengeBookScreen](file:///f:/survivalcontract/sc-mod/src/client/java/com/survivalcontract/client/gui/ChallengeBookScreen.java#L953-L955) 兜底文案，`§f120 §7` 全部改为 `§f100 §7`。
- **⑥ 文案**：`stowaway.no_goal` 补充「率先达到（参战人数×300）分直接获胜」；三语言文件新增 `survival-contract.msg.stowaway_score_victory`。
- **⑦ 自测（新增 3 条断言）**：受贿结算「袋内钻石×2 + 内嵌绿宝石×1 + 光标金锭×2 = +32 分、袋清空、界面关闭、光标物品收走」；个人 3 人参赛 899 不结束 / 900 立即结束；团队红队 500+399 不结束 / +1=900 全队获胜。结果：SC **26.2 = 6543 PASS / 0 FAIL**、**26.3 = 6540 PASS / 0 FAIL**；HK **26.2 = 68 PASS / 0 FAIL / 0 SKIP**、**26.3 = 63 PASS / 0 FAIL / 3 SKIP**（26.3 跑前临时移出农夫乐事 jar，跑完已恢复）。
- **⑧ 产物**：SC `survival-contract-1.0.0-mc26_2.jar` **2855383** 字节、`mc26_3.jar` **2855399** 字节；HK `hell-kitchen-1.0.0-mc26_2.jar` 3863869、`mc26_3.jar` 3863868（HK 不内嵌 SC，体积不变）。`deployJar` 已分别输出到 `mc26.2/`、`mc26.3/`。
- **⑨ 客户端同步**：请**完全退出游戏后**手动复制 `f:\survivalcontract\mc26.2\survival-contract-1.0.0-mc26_2.jar` 与 `hell-kitchen-1.0.0-mc26_2.jar` 到 `E:\bingo\.minecraft\versions\生存合约\mods\`（26.3 同理）。
- **⑩ 日志**：本轮 `sc-mod/run/logs`、`hk-mod/run/logs` 及临时自测日志已全部删除，结论仅保留在本文件。

## 2026-10-20（第八十九批）

### 生存契约：全量排查「偶发即整局崩溃 / 数据损坏」—— 崩溃隔离 + 存档原子写 + 维度回滚

- **① 排查范围**：SC 全部 Java 源文件（主 tick、网络包、会话持久化、竞技场维度、大厅、文明时代、走私、附属玩法桥接）+ HK 全部 Java 源文件（开局 / 重置 / 套餐 / 点火 / 补给）。确认 `FixedMapInjector` 已有完整性校验、`FireSpawner` / `OrderManager` 无崩溃点、配置解析失败均回退默认且不覆盖用户文件，无需改动。
- **② 根因（7 类高危隐患）**：
  1. **任一子系统异常都会击穿服务端主循环**：`END_SERVER_TICK` 里直接串联十多个子系统（addon / 任务 / 随机事件 / 走私 / 文明 / 职业 / 大厅标题…），任何一处抛异常都会让整局 tick 中断 → 表现为「一旦触发整局玩不了」。
  2. **会话快照非原子写**：`NbtIo.writeCompressed` 直接覆盖正式文件，写盘中途崩溃/断电就留下半截 NBT，下次启动读取必抛异常。
  3. **恢复失败会删掉玩家整局竞技场存档**：`SessionPersistence.restore` 的 catch 里调用 `teardownArenaWorlds` —— 快照坏了就把磁盘上完好的 `dimensions/survival-contract/arena*` 一并删除，属于真实的数据损坏。
  4. **竞技场维度构造中途失败留下「半注册」维度**：`buildArenaLevels` 连续 `new ServerLevel` + `levels.put`，第二个/第三个失败时第一个已注册，后续传送/传送门会指向空维度或残留目录，导致下一局一直卡在「旧档未删除」。
  5. **大厅维度缺失退回主世界时把人送进虚空**：`teleportToLobby` 固定用 `LOBBY_SPAWN`（y=-63），回退到原版主世界时该坐标在地下/虚空；同时大厅虚空保护（y<-64 拉回）会误伤主世界正常挖矿的玩家。
  6. **附属玩法开局失败卡成「假对局」**：HK `onGameStart` 在厨房维度缺失时静默 `return`，SC 仍认为已开局，玩家停在大厅却处于 PLAYING。
  7. **网络包 handler 异常会踢人**：`StartGamePayload` / `LobbyVotePayload` 中 `startGame` 未捕获，异常直接中断连接。
- **③ 修复**：
  - [MainEventListener](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/MainEventListener.java)：新增 `tickSafely(name, action)` + `logTickError`（同一子系统 200 tick 内只打一条错误，防刷屏）；三个 `END_SERVER_TICK` 回调（凋灵检测 / 末地检测 / 主 tick）整体 `catch (Throwable)`；主 tick 内 addon-tick、addon-end、reset-to-lobby、pending-titles、profession-perks、random-events、tasks、smuggle、civilization **各自独立隔离**，一处失败不影响其余子系统。
  - [SessionPersistence](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/game/SessionPersistence.java)：新增 `writeAtomic`（先写 `.tmp` → `ATOMIC_MOVE`，不支持时回退 `REPLACE_EXISTING`，`finally` 清理临时文件）；`save()` 与 `saveImmunities()` 全部改走原子写。`restore()` 失败时**不再删除竞技场存档**，只清运行态、玩法回 NORMAL、阶段回 LOBBY，并把坏快照改名 `survival-contract-session.nbt.corrupt` 隔离（改名失败才删除）。
  - [ArenaWorldManager](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/dimension/ArenaWorldManager.java)：`buildArenaLevels` 三次维度构造 + 注册包 `try/catch`，失败即 `removeArenaLevelsFromRegistryOnly` 回滚并返回 false；`createArenaWorlds` 在构造失败后清理残留维度目录并把 `currentSeed` 归零。
  - [LobbyManager](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/dimension/LobbyManager.java)：新增 `safeLobbySpawn()` —— 仅当大厅回退到原版主世界时用 `Heightmap.MOTION_BLOCKING_NO_LEAVES` 取地面高度，取不到则回退默认点，绝不送进虚空；ADDON 开局 `provider.onGameStart` 包 `try/catch`，失败即回 LOBBY + 清运行态 + 全员传送回大厅 + 广播大厅状态。大厅虚空保护改为**只对自定义大厅维度生效**。
  - [NetworkRegistry](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/network/NetworkRegistry.java)：两个 payload 的 `startGame` 包 `try/catch`，失败记日志并向玩家提示 `survival-contract.msg.arena_create_failed`，不再中断连接。
  - [KitchenStyleProvider](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/KitchenStyleProvider.java)：厨房维度缺失由静默 `return` 改为抛 `IllegalStateException`，交由 SC 回退大厅。
- **④ 自测**：本轮不新增断言（纯加固，不改玩法行为）。SC **26.2 = 6537 PASS / 0 FAIL**、**26.3 = 6538 PASS / 0 FAIL**；HK **26.2 = 68 PASS / 0 FAIL / 0 SKIP**、**26.3 = 63 PASS / 0 FAIL / 3 SKIP**（26.3 无对应版本农夫乐事 jar，跑前临时移出、跑完已恢复）。
- **⑤ 验证**：SC / HK 双版本 `build` + `deployJar` 全部 `BUILD SUCCESSFUL`。产物：`survival-contract-1.0.0-mc26_2.jar` 2852640 字节、`mc26_3.jar` 2852666 字节；`hell-kitchen-1.0.0-mc26_2.jar` 3863869 字节、`mc26_3.jar` 3863868 字节。
- **⑥ 客户端同步**：本轮未自动覆盖客户端实例。请**完全退出游戏后**手动复制 `f:\survivalcontract\mc26.2\survival-contract-1.0.0-mc26_2.jar` 与 `hell-kitchen-1.0.0-mc26_2.jar` 到 `E:\bingo\.minecraft\versions\生存合约\mods\`。

## 2026-10-20（第八十八批）

### 生存契约：把玩家调好的文明时代产出 / 交易参数固化为默认值

- **① 需求**：把 `civilization-production.json`（configVersion 5）与 `civilization-trade.json`（configVersion 3）里玩家实测调好的参数写进代码默认值，使新存档开箱即用，不再依赖手动调配置。
- **② 产出默认值（[CivilizationProductionConfig.buildDefaults](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/civilization/CivilizationProductionConfig.java#L205-L261)，`CONFIG_VERSION` 5 → 6）**：
  - 全部资源区改为 **30 秒**（沙区保留 **20 秒**）结算；主产物数量：木头 橡木原木×8、石头 圆石×8、铜 铜锭×12、铁 铁锭×8、金 金锭×8、钻石×4、石英×12、青金石×8、合金 下界合金碎片×8、沙 沙子×8。
  - 副产品概率：石头 煤炭 20%（**每次 2 个**）/沙砾 15%；钻石 黑曜石 20%（单区上限 30）；石英 地狱疣 15% + 岩浆怪 30%×2；青金石 红石 30% / 经验瓶 15%；合金 烈焰棒 15% / 锻造模板 4%（单区上限 3）/ 下界岩 20% + 凋灵骷髅 25%×1；沙区 火药 10% / 甘蔗 10% / 玻璃 15%。
  - 铜 / 铁 / 金由「粗矿」改为「**锭**」（`copper_ingot` / `iron_ingot` / `gold_ingot`），各区说明文字同步更新。
  - 野生动物区 `intervalSeconds` 60 → **30**；`zoneLimits.limits` 改为 **ALLOY ≤3、DIAMOND ≤15**。
- **③ 交易默认值（[CivTradeConfig.defaultRecyclePrices](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/civilization/CivTradeConfig.java#L128-L154)，`CONFIG_VERSION` 3 → 4）**：煤炭 4 / 铜锭 2 / 铁锭 4 / 金锭 8 / 钻石 16 / 石英 4 / 青金石 4 / 红石 8 / 绿宝石 12 / 下界合金锭 64 / 木炭 2 / 土豆 1 / 原木 ×9 各 1 / 石类（圆石、深板岩圆石、深板岩、黑石、凝灰岩）各 1；移除 `stone`、`raw_copper`、`quartz_block`、`redstone_block` 旧键。商人价目与商品（附魔之瓶 30 / 回城卷轴 500 / 随机武器装备 ×2 500 / 随机药水 300）保持与上传文件一致。
- **④ 版本迁移**：旧 `configVersion` 文件在读取时自动备份为 `.bak` 并重新生成默认文件（沿用既有迁移机制），玩家自定义值可从 `.bak` 抄回。
- **⑤ 自测**：同步更新断言 8 处——沙区火药默认 5% → 10%、钻石区黑曜石 10% → 20%、石英区地狱疣 8% → 15%、资源区上限 ALLOY 3 / DIAMOND 15、临时产出配置 `configVersion` 5 → 6、临时交易配置 3 → 4、木头区 / 石头区产出数量与铁 / 金锭物品 id（`stone1 == 8`、`logs == 8`）。
- **⑥ 验证**：SC **26.2 = 6539 PASS / 0 FAIL**、**26.3 = 6537 PASS / 0 FAIL**；双版本 `build` + `deployJar` 全部 `BUILD SUCCESSFUL`（`survival-contract-1.0.0-mc26_2.jar` 2849730 字节、`mc26_3.jar` 2849756 字节）。HK 依赖新 SC jar 重建后 **26.2 = 68 PASS / 0 FAIL / 0 SKIP**、**26.3 = 63 PASS / 0 FAIL / 3 SKIP**（26.3 无对应版本农夫乐事 jar，跑前临时移出、跑完已恢复）。
- **⑦ 客户端同步**：本轮未自动覆盖客户端实例（游戏仍在运行，热替换会破坏 jar 资源读取）。新 jar 位于 `f:\survivalcontract\mc26.2\survival-contract-1.0.0-mc26_2.jar`，请**完全退出游戏后**手动复制到 `E:\bingo\.minecraft\versions\生存合约\mods\`。

## 2026-10-20（第八十七批）

### 生存契约：修复「文明时代四种地形预设全部变主世界」+「开局后在边界外 / 掉进其他世界 / 掉进虚空」

- **① 现象**：文明时代在挑战书里无论选「主世界 / 挖掘工的梦想 / 雪之王国 / 沙漠」，开局看到的都是主世界预设的超平坦；并且开局后部分玩家站在 240×240 疆域边界外、被丢进其他维度，甚至直接掉进虚空。
- **② 根因（自查出的 5 处冲突）**：
  1. **会话快照写盘 NPE（地形预设全部失效的主因）**：[SessionPersistence.save](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/game/SessionPersistence.java#L83-L201) 第 96 行旧实现直接 `gsm.getCurrentFinalGoal().name()`。文明时代 / 神的谎言 / 走私犯 / 附属玩法开局都把最终目标置 `null` → 每次退出存档都抛 NPE，**快照从未写出**（用户存档根目录确实没有 `survival-contract-session.nbt`）。
  2. **玩法未持久化**：快照里没有 `playStyle`，重启后 `GameStateManager` 是新实例、默认 `NORMAL` → [buildArenaLevels](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/dimension/ArenaWorldManager.java#L156-L166) 用原版主世界生成器建竞技场，任何 `terrainPreset` 都被忽略（`terrainStem` 只在 `isCivilization()` 时使用）。
  3. **旧档删除未校验**：[ArenaWorldManager.createArenaWorlds](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/dimension/ArenaWorldManager.java#L117-L136) 删档后不检查目录是否真的消失；Windows 句柄延迟释放时新维度直接复用旧区块，新预设不生效。
  4. **竞技场缺失时静默回退主世界**（5 处）：`LobbyManager.teleportToPlayingWorld`、`GameStateManager.setupNewRandomWorld`、`MainEventListener.teleportSpectatorToArena` / `teleportToPlayingSpawn`、`CivilizationManager.setup/tick/reset` 全部在 `arena == null` 时退回 `server.overworld()`。配合 PLAYING 每 2 秒的防逃跑扫描，玩家被反复丢进原版主世界；`CivilizationManager` 还会把 240×240 边界、`MOB_GRIEFING`/`ADVANCE_TIME` 锁写进大厅世界。
  5. **出生点算法缺陷**：团队模式红点 = `playingSpawn + 120z`、蓝点 = `red - 240z`，在文明时代正好贴在/越过 240×240 疆域边界；三处安全落点兜底直接返回 heightmap（未生成区块为 0 / -64），在 MINER / SNOW / DESERT（地表 y=23 / 39）会把玩家埋进石头或送进虚空。
- **③ 修复**：
  - `SessionPersistence`：`finalGoal` 空值安全（`""` 表示 null）；新增保存/恢复 `playStyle` 与 `addonStyleId`；恢复顺序改为 **先 `playStyle` + `terrainPreset`，再重建竞技场维度**；`finalGoal` 空串恢复为 `null`。文明时代 / 附属玩法的运行时状态（疆域、大本营实体、固定地图进度）无持久化，重启后`restore` 直接清快照回大厅，避免得到「没有疆域和边界」的假对局。
  - `ArenaWorldManager`：`createArenaWorlds` / `restoreArenaWorlds` / `buildArenaLevels` 改为返回 `boolean`；删档后逐一校验三个维度目录**必须已不存在**，否则报错并中止开局；建成后校验三个维度均已注册。
  - `LobbyManager` / `GameStateManager` / `MainEventListener` / `CivilizationManager` / `SmuggleManager`：删除全部 `arena == null → overworld()` 兜底，改为「记录错误 + 中止开局 / 跳过本次传送」；[LobbyManager.startGame](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/dimension/LobbyManager.java#L320-L343) 两步失败都向玩家提示新增文案 `survival-contract.msg.arena_create_failed`（中 / 英 / 繁三语言）。
  - `GameStateManager.assignRespawnPoints`：文明时代团队模式先把红/蓝队点锚定到疆域中心（`playingSpawn`），随后由 `CivilizationManager.initClaim` 覆盖为两个大本营中心（绝对在边界内）。
  - 新增 `GameStateManager.sanitizeSpawn`：三处 heightmap 兜底统一改为「原地向上找 → 向下找 → 16 格环边搜索 → 钳制到 `minY+2`」，杜绝埋进石头 / 虚空。
- **④ 自测断言（新增 6 条，另调整 2 处测试环境）**：`文明时代竞技场存在（禁止退回主世界）`、`文明时代重生点在疆域边界内`（border=240，越界/埋地 0）、`finalGoal=null 时快照仍能落盘`、`finalGoal=null 恢复`、`玩法/地形预设随快照恢复（重建竞技场前生效）`、`文明时代快照不跨重启恢复（回大厅并清快照）`。因 `CivilizationManager.setup` 现在拒绝退回主世界，自测新增 `ensureTestArena()` 在 case4 前先建竞技场；尘肺病跨 tick 测试改在竞技场负层执行（否则防逃跑扫描会打断 `y<0` 断言）。
- **⑤ 验证**：SC × 26.2/26.3 两个 `build` 全部 `BUILD SUCCESSFUL` + `deployJar`（`survival-contract-1.0.0-mc26_2.jar` 2849748 字节、`mc26_3.jar` 2849774 字节）。自测：**26.2 = 6541 PASS / 0 FAIL**、**26.3 = 6536 PASS / 0 FAIL**。HK 依赖新 SC jar 重新构建：**26.2 = 68 PASS / 0 FAIL / 0 SKIP**、**26.3 = 63 PASS / 0 FAIL / 3 SKIP**（26.3 无对应版本农夫乐事 jar，跑前临时移出、跑完已恢复）。
- **⑥ 客户端同步**：本轮未自动覆盖客户端实例。新 jar 位于 `f:\survivalcontract\mc26.2\survival-contract-1.0.0-mc26_2.jar`（2849748 字节），请**完全退出游戏后**手动复制到 `E:\bingo\.minecraft\versions\生存合约\mods\`（运行中的 JVM 持有旧 jar 句柄，热替换会导致后续读 jar 资源失败）。

## 2026-10-20（第八十六批）

### 地狱厨房：修复「第二局重置后掉进石头超平坦」—— 重置改为先校验再动档 + 初始地图快照

- **① 故障现象**：连开第二局时，[KitchenMap.resetDimension](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/KitchenMap.java#L119-L208) 重置后玩家被传送到**石头超平坦**，而不是已做好的厨房地图。检查用户存档 `saves/新的世界/dimensions/hell-kitchen/kitchen`：`region/*.mca` 全部 **0 字节**（仅 `r.0.0.mca` 94 KB，是超平坦实时生成的地形）→ 没有任何地图文件，维度只能由数据包里的 `minecraft:flat`（64 层石头）生成器接管。
- **② 根因**：重置时注入的原始地图**一个字节都没写进去**。旧 [FixedMapInjector](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/dimension/FixedMapInjector.java) 对 `getResourceAsStream` 的失败不校验：读到空流也照样用 `CREATE + TRUNCATE_EXISTING` 建出 0 字节文件，最后无条件打印「installed successfully」。触发条件是该局进行中 **mod jar 在游戏运行期间被覆盖**（本次客户端 21:00 启动、jar 于 21:46 被替换）：运行中的 JVM 持有的是旧文件句柄，再读内置 `.mca` 只能拿到空/半截数据（`r.0.0.mca` 恰好留下 94540 字节半截文件）。
- **③ 修复**：
  - `FixedMapInjector.inject` / 新增的 `injectInto` **返回布尔结果**；新增 `verify(dir, prefix, files)` 与 `resourceSize(path)` —— 每个 `.mca` 必须存在，且**字节数与 JAR 内置条目完全一致**；`copyFromJarResources` 逐文件比对写入字节数，不符即**删除半截文件并返回失败**，不再把损坏文件当地图用。
  - 重置流程改为**先准备、后动档**：先把初始地图恢复到 `<dim>.tmp` 暂存目录并校验，**校验通过才**移除旧维度、删旧档、把暂存图拷入并二次校验；任何一步失败都**保留当前维度**并打印错误，绝不会再把玩家丢进空地图。
  - 新增**初始地图快照** `<world>/hell-kitchen-pristine`：开局注入并通过校验后落一份（26 个 `.mca`），重置时优先从快照恢复 —— 即使运行中的 jar 已损坏，重置也不再依赖它，依然回到「已做好的厨房地图」的初始状态（外部 zip / 散装文件夹覆盖的场景仍走原三级优先注入）。
- **④ 自测断言（新增 1 条）**：`重置后地图文件完整且与内置地图一致` —— 重置后逐一比对 10 个 region 文件的磁盘大小与内置地图（结果 10/10，共 24 MB），并确认日志走的是「从初始地图快照恢复」路径。
- **⑤ 验证**：SC × HK × 26.2/26.3 全部 `build` + `deployJar` 成功（`hell-kitchen-1.0.0-mc26_2.jar` 3863806 字节、`mc26_3.jar` 3863805 字节；`survival-contract-1.0.0-mc26_2.jar` 2846267 字节、`mc26_3.jar` 2846293 字节）。自测：**HK 26.2 = 68 PASS / 0 FAIL / 0 SKIP**、**HK 26.3 = 63 PASS / 0 FAIL / 3 SKIP**。已同步客户端实例两个 MC 26.2 jar。
- **⑥ 附注**：用户当前那一局的存档地图已被写坏，**重启游戏即可自愈**（`SERVER_STARTING` 会重新注入完整地图并建立快照）。今后更新 mod jar 必须**先完全退出游戏**，否则运行中的实例会读到已被替换的 jar。

## 2026-10-20（第八十五批）

### 地狱厨房：随机点火生成的地板火焰 60 秒后自动熄灭

- **① 问题定位**：[FireSpawner](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/fire/FireSpawner.java) 每次随机点火只是 `level.setBlock(p, Blocks.FIRE.defaultBlockState(), ...)` 铺火焰（中心 + 十字四邻，至多 5 格），**既不记录也不清除**；虽然本玩法已把 `fire_spread_radius_around_player` 置 0 关闭了蔓延，但火焰方块落在混凝土 / 陶瓦这类不可燃底面上时，原版烧尽判定会被后续方块更新反复刷新，视觉上就是「地板着火后长期不灭」。
- **② 修复**：
  - 新增常量 `FIRE_LIFETIME_TICKS = 60 * 20`（60 秒）与登记表 `FIRES : Map<BlockPos, Long>`（火焰坐标 → 预定熄灭 tick）；`ignite()` 每成功放置一格就 `FIRES.put(pos.immutable(), now + FIRE_LIFETIME_TICKS)`。
  - `tick()` 改为**先熄后点**：首行调用新增的 `extinguishExpired(server, now)` —— 到点即 `setBlock(p, AIR, UPDATE_ALL)` 并移除登记；若该格已不是火焰（被原版烧尽 / 被其它逻辑替换）则只清记录；**区块未加载时跳过不删记录**，避免出现「记录丢了、火焰还在」的永久残留。
  - `reset()`（对局结束 / 维度重建）同步 `FIRES.clear()`，坐标随维度重建失效。
  - 熄灭由本类主动执行，**不依赖原版烧尽逻辑**，因此 60 秒到点即消失，行为稳定可预期。
- **③ 自测断言（新增 2 条）**：`随机点火：火焰已登记熄灭倒计时`（十字 5 格全部进登记表）、`地板火焰 60 秒后自动熄灭`（用反射把登记表内到期 tick 全部提前到 0 → 再跑一帧 `FireSpawner.tick` → 断言十字 5 格全部为空气且登记表清空）。为此新增两个反射辅助 `fireTrackedCount()` / `forceFiresExpired()`。
- **④ 验证**：HK × 26.2/26.3 两个 `build` 全部 `BUILD SUCCESSFUL` + `deployJar`（`hell-kitchen-1.0.0-mc26_2.jar` 3861088 字节、`mc26_3.jar` 3861087 字节）。自测：**26.2 = 67 PASS / 0 FAIL / 0 SKIP**（原 65 条 + 新增 2 条），**26.3 = 62 PASS / 0 FAIL / 3 SKIP**（26.3 无对应版本农夫乐事 jar，跑前临时移出 `run/mods`、跑完已恢复，3 条 FD 相关断言降级 SKIP）。已同步客户端实例 `E:\bingo\.minecraft\versions\生存合约\mods\hell-kitchen-1.0.0-mc26_2.jar`（SHA256 与构建产物一致）。

## 2026-10-20（第八十四批）

### 地狱厨房：补给改认「铜箱家族」—— 地图用涂蜡铜箱做补给箱，取出后现在会立即补回

- **① 问题定位**：补给登记的方块白名单 [SupplyManager.isResupplyBlock](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/supply/SupplyManager.java#L94-L101) 只认 `dispenser / dropper / chest / trapped_chest / barrel`，而地图里真正的补给箱是**涂蜡铜箱**（东西基地 `(-68,1,14)` / `(12,1,11)`，各装 27 瓶喷溅药水；交付漏斗下方的 `(-69,-1,23)` / `(11,-1,20)` 则是涂蜡氧化铜箱）。用 Anvil region 直解 `block_states` 确认了 4 个铜箱的方块名；自测探针实测 `isResupplyBlock=false`、`isRegistered=false` —— 即铜箱此前**从未进入补货登记**。
- **② 修复**：白名单新增 `block instanceof CopperChestBlock`（一次覆盖未氧化 / 氧化 / 涂蜡全家族，含 `waxed_copper_chest`、`waxed_oxidized_copper_chest`），与普通箱子同等对待；铜箱快照在区块加载时记录，取出后每 2 tick 检测到缺格即回填初始内容。
- **③ 自测断言（新增 3 条）**：`补给容器识别：涂蜡 / 氧化铜箱`（识别 4/4）、`地图铜箱补给点已登记（东西基地各 1）`（西=true 东=true）、`铜箱取出物品后自动补货`（阶段 1 把槽 0 的喷溅药水清空 → 阶段 2 断言已回填为 `1x minecraft:splash_potion`）。`SupplyManager` 为此补充了 `isRegistered(BlockPos)` / `size()` 两个只读访问器。
- **④ 验证**：HK × 26.2/26.3 两个 `build` 全部 `BUILD SUCCESSFUL` + `deployJar`（`hell-kitchen-1.0.0-mc26_2.jar` 3859817 字节、`mc26_3.jar` 3859816 字节）。自测：**26.2 = 65 PASS / 0 FAIL / 0 SKIP**（原 62 条 + 新增 3 条），**26.3 = 60 PASS / 0 FAIL / 3 SKIP**（26.3 无对应版本农夫乐事 jar，跑前临时移出、跑完已恢复，3 条 FD 相关断言降级 SKIP）。已同步客户端实例 `E:\bingo\.minecraft\versions\生存合约\mods\hell-kitchen-1.0.0-mc26_2.jar`。

## 2026-10-20（第八十三批）

### 地狱厨房：固定地图升级为「街角灶源 1.0.7（热气球更新）」+ 出生点回归修复（v2→v3 配置迁移）

- **① 地图全量替换（hk-mod 资源）**：用 SHA256 逐文件比对用户提供的 `街角灶源1.0.7(热气球更新！）.zip` 与 jar 内置地图，确认**文件清单完全一致**（region 10 + entities 6 + poi 10，共 26 个 `.mca`），但**内容变化**：region 6 个（`r.-1.-1` / `r.-1.-2` / `r.-1.0` / `r.0.-1` / `r.0.-2` / `r.0.0`）、entities 5 个（`r.-1.-1` / `r.-1.0` / `r.0.-1` / `r.0.-2` / `r.0.0`）、poi 4 个（`r.-1.-1` / `r.-1.0` / `r.0.-1` / `r.0.0`）。因清单不变且存在「尺寸相同、内容不同」的文件（不可只按大小判差异），故**全量覆盖** 26 个 `.mca` 到 [kitchen/](file:///f:/survivalcontract/hk-mod/src/main/resources/data/hell-kitchen/kitchen)；[KitchenMap.java](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/KitchenMap.java) 的 `MCA_FILES` **无需调整**，仅把 javadoc 版本号 `1.0.6` → `1.0.7`。
- **② 交付漏斗坐标复核（Python 直解 Anvil region 的 `block_states` 调色板 + 位打包 data）**：新图四个交付漏斗**全部命中配置坐标** —— 东街角 `(11,0,20)`、西街角 `(-69,0,23)`、东水族馆 `(26,0,-218)`、西水族馆 `(-55,0,-204)`，与 `KitchenConfig.deliveries` 的 `(11.5,20.5)` / `(-68.5,23.5)` / `(26.5,-217.5)` / `(-54.5,-203.5)` 一致（`BlockPos.containing` 语义）。
- **③ 出生点回归修复（1.0.7 新引入）**：6 个出生点逐一检测脚下/头顶方块，其中水族馆西侧 B 组 `aquaWestB` 的 `(-62,0,-206)` 在 **1.0.6 时为空气、1.0.7 变为 `minecraft:oak_log`**（该点上方 y=1 亦为原木，周围新增云杉原木与农夫乐事方块结构）。玩家对局中为**冒险模式、无法破坏方块**，卡在方块内会直接被困住 → 迁移到 `(-62,0,-207)`（相邻同款蓝釉陶瓦地板空位，位移 1.0 格），其余 5 点全部正常（脚下依次为 `yellow_concrete` / `orange_terracotta` / `blue_glazed_terracotta`，位置与头顶均为空气）。
- **④ 配置迁移（[KitchenConfig.java](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/KitchenConfig.java)）**：`CONFIG_VERSION` 由 `2` → `3`；`defaultSpawns()` 的 `aquaWestB` 改为 `(-62,0,-207)` 并补充 javadoc 说明；`migrate()` 重写为支持 `v1→v2`（旧 `batchSeconds=600`→`300`）与 **`v2→v3`**（仅当玩家配置里 `aquaWestB` 仍为旧默认值 `LEGACY_AQUA_WEST_B = (-62,0,-206)` 时才改写为 `(-62,0,-207)`，避免覆盖玩家自定义坐标）。[HellKitchenSelfTest.java](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/HellKitchenSelfTest.java) 的出生点断言同步为 `(-62,0,-207)`。
- **⑤ 验证**：HK × 26.2/26.3 两个 `build` 全部 `BUILD SUCCESSFUL` + `deployJar`（`mc26.2/hell-kitchen-1.0.0-mc26_2.jar` 3858681 字节、`mc26.3/hell-kitchen-1.0.0-mc26_3.jar` 3858680 字节）。自测：**26.2 = 62 PASS / 0 FAIL / 0 SKIP**（清空 `run/world` 后跑），**26.3 = 57 PASS / 0 FAIL / 3 SKIP**（26.3 无对应版本的农夫乐事 jar，临时把 26.2 的 FD jar 移出 `run/mods` 再跑、跑完已恢复；3 条 FD 相关断言降级 SKIP：开局发放/丢失补发铁厨刀、冒险放行：农夫乐事盛宴方块菜、冒险放行：菜品池内可放置方块菜）。两版本自测中「两套地图出生点坐标（街角 2 点 + 水族馆 4 点）」「交付漏斗 4 个」「四个漏斗按所在侧归属」「四个交付漏斗就位可用 — 就位 4/4」「送货漏斗（漏斗方块）就位」等关键断言**全部 PASS**。已同步客户端实例 MC 26.2 的 `E:\bingo\.minecraft\versions\生存合约\mods\hell-kitchen-1.0.0-mc26_2.jar`。

## 2026-10-20（第八十二批）

### 生存契约：「禁用背包格子」拾取缺陷修复（可用格占满即拒绝拾取 —— 不再落入锁定格、不再自动回流）

- **① 问题复现**：开启「禁用背包格子」规则后，**可用格（快捷栏 0~8）全部占满**时拾取物品，物品会**落进被锁定的背包格（9~35）**；随后一旦可用格出现空位，锁定格里的物品又被**每 tick 的兜底清扫**自动搬回可用格，表现为「禁用格里的东西自己跑出来」。根因是拾取入口 `ItemEntity.playerTouch` 调用的 `Inventory.add(ItemStack)` 完全走原版逻辑，**不感知锁定格**。
- **② 修复方案（新建 [PickupGuard.java](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/lock/PickupGuard.java) + [ItemEntityPickupMixin.java](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/mixin/ItemEntityPickupMixin.java)）**：用 mixin **单点重定向** `ItemEntity.playerTouch` 里唯一那次 `Inventory.add(Lnet/minecraft/world/item/ItemStack;)Z` 调用，交给 `PickupGuard.pickup(inv, stack)`：
  - **可用槽（快捷栏 + 未锁定的背包格）占满 → 返回 `false` 且不改动传入栈** → 拾取失败、物品留在地上（原版路径下 `playerTouch` 因 add 返回 false 不会 `discard` 实体）；
  - **未锁定槽里有同类且未达堆叠上限 → 先正常补堆**，余量继续留在实体里（用户要求「同一格子里未达到堆叠上限的物品不受影响」）；
  - **未开启规则 → 直接交回原版 `Inventory.add(stack)`**，行为与改动前完全一致。
  - 只改这一处调用点：其它 `Inventory.add` 调用方（补给发放、铁厨刀补发、合成产物回收等）行为不受影响。[survival-contract.mixins.json](file:///f:/survivalcontract/sc-mod/src/main/resources/survival-contract.mixins.json) 追加注册 `ItemEntityPickupMixin`。
  - 保留 `MainEventListener` 每 tick 兜底清扫作为其它非拾取路径（shift-click、漏斗等）的安全网——修复①后物品不会再进入锁定格，该清扫自然不再触发「自动搬移」。
- **③ 关键坑（由自测暴露并修正）**：最初按 `inv.getContainerSize()` 扫描槽位，但 26.2 的 `Inventory.getContainerSize()` 返回 **41**（36 个物品格 + 5 个装备/副手/躯体槽的 `EQUIPMENT_SLOT_MAPPING`），导致物品被塞进**护甲槽**。改用 `Inventory.INVENTORY_SIZE`（**36**，只含快捷栏 0~8 + 主背包 9~35），装备槽不再被误占。
- **④ 自测断言同步（`ServerSelfTest.testPickupLock`）**：新增 5 条——`拾取锁定：假人就绪`、`可用格占满 → 拒绝拾取（不进锁定格）`（收下=false / 地上剩余=5 / 锁定格全空=true）、`可用格有空位 → 正常拾取`、`同类未满栈继续补堆（余量留在地上）`（第 1 格 60+4=64 / 地上剩余=8）、`未开启规则 → 原版行为（可进 9~35）`。测试会保存/恢复假人阶段、挑战集、`inv_slot_lock` 的 `customValue` 与整包物品，不污染其它断言。
- **⑤ 验证**：SC/HK × 26.2/26.3 四个 `build` 全部 `BUILD SUCCESSFUL` + `deployJar`；自测（清空对应 `run/world` 后）**SC 26.2 = 6533 PASS / 0 FAIL**、**HK 26.2 = 62 PASS / 0 FAIL / 0 SKIP**、**HK 26.3 = 57 PASS / 0 FAIL / 3 SKIP**（26.3 临时移出 26.2 的农夫乐事 jar，FD 相关 3 条降级 SKIP，跑完已恢复）。已同步用户实例 MC 26.2 的 `E:\bingo\.minecraft\versions\生存合约\mods\survival-contract-1.0.0-mc26_2.jar` 与 `hell-kitchen-1.0.0-mc26_2.jar`。本次为服务端拾取逻辑改动，已由假人自测直接验证 `Inventory` 语义。

## 2026-10-20（第八十一批）

### 地狱厨房：计分板层级修正（降至 JEI 之下）+ 套餐组数新增「无尽」+ 每 15~20s 随机点火（十字火焰）+ 本玩法关闭火焰蔓延

- **① 计分板层级修正（SC，纠正第七十九批的错误方向）**：上一批把「放到 JEI 图层下层」误解为「屏幕位置下移贴底」（`Math.max(56, sh - panelH - 34)`），本批改为真正的**渲染层级**处理——[HudRenderer.java](file:///f:/survivalcontract/sc-mod/src/client/java/com/survivalcontract/client/HudRenderer.java) 的 `register()` 把右侧计分板由 `HudElementRegistry.addLast` 改为 **`addFirst`**（FIRST root layer：最先渲染 → 被后渲染的 JEI 物品栏 / InventoryHUD+ 叠加层覆盖，即位于其**之下**）；同时面板顶边恢复为**右侧垂直居中** `Math.max(56, (sh - panelH) / 2)`（不再靠下移避让）。
- **② 套餐组数新增「无尽」（hk-mod）**：挑战书二级菜单「需要完成的套餐组数」取值由 1~6 扩为 **1~6 或 7=无尽**——[KitchenStyleProvider.java](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/KitchenStyleProvider.java) 新增常量 `GROUPS_ENDLESS=7` / `GROUPS_HUD_ENDLESS=-1`；`options()` 的 `valueLabels` 追加 `hell-kitchen.groups.endless`。选「无尽」时**不因完成套数获胜**：[OrderManager.deliver](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/order/OrderManager.java) 的判胜分支前加 `!KitchenStyleProvider.isEndlessGroups()` 前置条件（只无限刷新下一组），一直玩到 SC 倒计时结束（按分数决胜负）；下发 HUD 的目标组数改用哨兵值 `-1`（[KitchenHud.java](file:///f:/survivalcontract/hk-mod/src/client/java/com/hellkitchen/client/KitchenHud.java) 显示「§d无尽」）。语言文件（`zh_cn` / `en_us` / `zh_tw`）新增 `hell-kitchen.groups.1`~`.6`、`hell-kitchen.groups.endless`、`hell-kitchen.msg.intro3.endless`。
- **③ 每 15~20 秒随机点火（hk-mod，新建 [FireSpawner.java](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/fire/FireSpawner.java)）**：在地图里的「**黄色混凝土 / 橙色陶瓦 / 蓝色带釉陶瓦**」中随机选一种、再随机选其中一个方块，在其**上方**生成燃烧的火焰，并以**十字形铺开**（中心 + 可放的四邻，至多 5 格）→ 火焰视觉上比单格更大（用户所述「膨胀」），范围不变。
  - 候选**不做全量地图扫描**（该地图约 6100 区块，全量加载会长时间卡顿并爆内存）：复用「区块加载即扫描」路径——[KitchenEvents.java](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/KitchenEvents.java) 的 `CHUNK_LOAD` 回调追加 `FireSpawner.scanChunk(chunk)`（`ChunkAccess.findBlocks` 按 section 调色板预筛，成本极低）；候选为空时兜底扫一遍随机玩家所在区块及八邻。
  - 生命周期接入：[KitchenStyleProvider.java](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/KitchenStyleProvider.java) 的 `onGameStart` → `FireSpawner.start(server)`（清空上局候选 + 排定首次点火）、`onServerTick` → `FireSpawner.tick(server)`（内部按 15×20~20×20 tick 排程门控）、`onGameEnd` → `FireSpawner.reset()`。
  - 26.x 彩色方块改由 `ColorCollection<Block>` 承载，取值用 `Blocks.CONCRETE.yellow()` / `Blocks.DYED_TERRACOTTA.orange()` / `Blocks.GLAZED_TERRACOTTA.blue()`。
- **④ 本玩法关闭火焰蔓延（hk-mod）**：[KitchenMap.applyRules](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/KitchenMap.java) 新增世界规则。注意 **26.2 起 gamerule `doFireTick` 已被 `fire_spread_radius_around_player`（整型）取代**，故置 `FIRE_SPREAD_RADIUS_AROUND_PLAYER = 0`（玩家周围 0 格内才可蔓延 → 实际不再扩散），随机点火生成的火焰不会扩散成灾。
- **自测断言同步（`HellKitchenSelfTest`）**：新增 6 条——`套餐组数选项含「无尽」（1~6 或 7=无尽）`、`无尽模式判定与 HUD 哨兵值（-1）`、`本玩法关闭火焰蔓延`、`随机点火：区块扫描收集候选（目标方块）`、`随机点火：目标上方起火 + 十字铺（火焰视觉更大）`、`无尽模式：完成套餐不判胜（继续游玩到倒计时结束）`。
- **验证**：SC/HK × 26.2/26.3 四个 `build` 全部 `BUILD SUCCESSFUL` + `deployJar`；自测（清空对应 `run/world` 后）**SC 26.2 = 6528 PASS / 0 FAIL**、**HK 26.2 = 62 PASS / 0 FAIL / 0 SKIP**、**HK 26.3 = 57 PASS / 0 FAIL / 3 SKIP**（26.3 临时移出 26.2 的 FD jar，FD 相关 3 条降级 SKIP，跑完已恢复）。关键新断言实测：`[PASS] 随机点火：目标上方起火 + 十字铺（火焰视觉更大） — 十字火焰 5/5 格`。已同步用户实例 MC 26.2 的 `E:\bingo\.minecraft\versions\生存合约\mods\survival-contract-1.0.0-mc26_2.jar` 与 `hell-kitchen-1.0.0-mc26_2.jar`。①为客户端渲染层级改动，服务端自测不覆盖（用户此前指示「暂不启动，先交付」）。

## 2026-10-19（第八十批）

### 地狱厨房：替换固定地图为「街角灶源 1.0.6」+ 每局重置地图到初始状态 + 自动开启「禁用 27 背包格」规则（初始不加分）

- **① 换图（hk-mod，用户上传「街角灶源1.0.6」）**：解压存档并与 jar 内置 `data/hell-kitchen/kitchen/` 逐文件比对后，按新存档原样覆盖 `region` / `entities` / `poi` 全部 `.mca`：
  - **region**：内容变化 4 个（`r.0.0` / `r.0.-1` / `r.0.-2` / `r.-1.-2`），**新增 2 个北侧远景区域** `r.0.-3.mca` / `r.-1.-3.mca`（1.0.6 相比 1.0.5 新扩的地块），其余 4 个（`r.-1.0` / `r.-1.-1` / `r.-2.0` / `r.-2.-1`）内容不变。
  - **entities**：`r.0.-2` 由空文件变为实体数据（0 → 20KB），其余不变。
  - **poi**：新增 2 个空占位 `r.0.-3.mca` / `r.-1.-3.mca`，其余内容不变。
  - [KitchenMap.java](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/KitchenMap.java#L31-L41)：`MCA_FILES` 清单同步——region 8→**10**、poi 8→**10**（entities 维持 6），确保 jar 内置地图与新版存档一致。
- **② 每局重置地图（用户要求：每把结束后地图回到初始状态，避免残留上一把的改动）**：[KitchenMap.java](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/KitchenMap.java#L100-L167) 新增 `resetDimension(server)`——参照 SC 侧 [ArenaWorldManager.java](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/dimension/ArenaWorldManager.java#L298-L349) 的重建范式：① 把仍在厨房维度内的玩家（异常流程兜底）送回大厅 → ② 关闭并从 `MinecraftServer.levels` 移除厨房 `ServerLevel` → ③ 递归删除 `<world>/dimensions/hell-kitchen/kitchen/`（Windows 句柄延迟释放，最多重试 10 次） → ④ 用 `FixedMapInjector` 重新注入原始 `.mca` → ⑤ 用数据包注册的 `LevelStem` 重新构造并注册 `ServerLevel` → ⑥ 重套世界规则（禁刷怪 + 锁晴天）。调用点在 [KitchenStyleProvider.onGameStart](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/KitchenStyleProvider.java#L118-L128) 最前（此时新一局尚未开始、玩家仍在大厅，最安全）。
- **③ 自动开启「禁用 27 背包格」规则、初始不加分（hk-mod + SC）**：
  - [KitchenStyleProvider.java](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/KitchenStyleProvider.java#L278-L303)：新增 `enableSlotLock(players)`——开局把 `inv_slot_lock` 的 `customValue` 强制为 **9**（语义：9 → 锁 27 格 = 只留快捷栏），并对每位参战者 `enableChallenge`；`onGameEnd` 调 `restoreSlotLockValue()` 还原原值（默认 18），避免污染其它玩法（玩家侧挑战集由 SC `performResetForLobby` 统一清空）。
  - 初始不加分由 SC 侧 [GameStateManager.calculateInitialScoreExcludingBans](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/game/GameStateManager.java#L784-L789) 保证：新增 `if (isAddon()) return 0;`（附属玩法无自选规则/职业/双禁 → 初始分恒为 0，与 `LobbyManager.startGame` 中的 `setScore(0)` 一致），屏蔽该规则本应带来的 +16 初始分；[getEventScoreBonus](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/game/GameStateManager.java#L146-L148) 同样对附属玩法返回 0（团队模式下全局事件集残留时也不会把事件分带入初始分）。
- **自测断言同步（`HellKitchenSelfTest`）**：新增 8 条——`地图升级 1.0.6（北侧远景 r.-3.*）`、`每局重置地图：维度已重建 / 原始地图重新注入 / 维度种子保持`、`自动开启禁用 27 背包格：customValue=9（锁 27 格）`、`参战者已启用该规则`、`本模式初始不加分（附属玩法初始分恒为 0）`、`对局结束还原背包锁定为默认值`。
- **验证**：SC/HK × 26.2/26.3 四个 `build` 全部 `BUILD SUCCESSFUL` + `deployJar`；自测（清空对应 `run/world` 后）**SC 26.2 = 6530 PASS / 0 FAIL**、**HK 26.2 = 56 PASS / 0 FAIL / 0 SKIP**、**HK 26.3 = 51 PASS / 0 FAIL / 3 SKIP**（26.3 临时移出 26.2 的 FD jar，FD 相关 3 条降级 SKIP，跑完已恢复）。已同步用户实例 MC 26.2 的 `E:\bingo\.minecraft\versions\生存合约\mods\survival-contract-1.0.0-mc26_2.jar` 与 `hell-kitchen-1.0.0-mc26_2.jar`。

## 2026-10-19（第七十九批）

### 地狱厨房附属模组图标 + 菜品菜单补全 30 道菜 + SC 本地化残留收尾 + 计分板下移避开 JEI

- **① 地狱厨房图标（hk-mod）**：仿照其它农夫乐事附属模组的做法，把 SC 的图标叠在农夫乐事（Farmer's Delight）图标的木质砧板/木板背景板之上——新建 [icon.png](file:///f:/survivalcontract/hk-mod/src/main/resources/assets/hell-kitchen/icon.png)（256×256：FD `icon.png` 缩放铺满作底，SC `icon.png` 按 FD 木板包围盒 `x[5..42] y[9..38]`（w=38 h=30）等比缩放至 `min(bw,bh)*0.88 ≈ 132px` 居中叠放）；[fabric.mod.json](file:///f:/survivalcontract/hk-mod/src/main/resources/fabric.mod.json) 增补 `"icon": "assets/hell-kitchen/icon.png"` 字段。
- **② 菜品菜单补全（hk-mod，用户点名「汉堡/三明治/包子/卷饼/沙拉」等遗漏）**：菜品池由 23 道扩至 **53 道**（新增 16 道 `FOOD` + 14 道 `BOWL`，共 **30 道**农夫乐事成品菜），覆盖汉堡/三明治/卷饼/寿司/饺子（用户口语「包子」）/点心/意面/汤/炖/沙拉等此前缺失品类：
  - `FOOD`（maxCount 3）：`honey_cookie` / `sweet_berry_cookie`(2)、`roasted_mutton_chops` / `smoked_ham`(3)、`hamburger`(4)、`bacon_sandwich` / `chicken_sandwich` / `egg_sandwich`(3)、`mutton_wrap`(4)、`kelp_roll` / `cod_roll` / `salmon_roll`(3)、`dumplings` / `cabbage_rolls` / `stuffed_potato`(3)、`melon_popsicle`(2)
  - `BOWL`（maxCount 1）：`fried_rice` / `pasta_with_meatballs` / `pasta_with_mutton_chop` / `steak_and_potatoes` / `baked_cod_stew` / `bone_broth` / `noodle_soup` / `pumpkin_soup` / `vegetable_soup` / `onion_soup`(4)、`mixed_salad` / `fruit_salad` / `nether_salad` / `gleaming_salad`(4)
  - [KitchenConfig.java](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/KitchenConfig.java#L131-L207)：内联 `dishes` 初始化改为 `defaultDishes()`，新增 `defaultDishes()` / `defaultDrinks()`；并新增 **读时合并** 方法 `dishPoints()` / `drinkPoints()`（`putIfAbsent` 合并默认值，与既有 `deliveryPoints()` 同范式）——**旧 `kitchen.json` 无需提升 `CONFIG_VERSION`（仍为 2）即可自动纳入新菜**；[DishPool.java](file:///f:/survivalcontract/hk-mod/src/main/java/com/hellkitchen/dish/DishPool.java#L35-L43) 的 `reload()` 由 `cfg.dishes` / `cfg.drinks` 改为 `cfg.dishPoints()` / `cfg.drinkPoints()`。
- **③ SC 本地化残留收尾（需求「补全英文」遗留的硬编码中文）**：
  - **羊毛放置拒绝理由**：[CivilizationManager.java](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/civilization/CivilizationManager.java#L1483-L1540) 重构为**单一判定来源** —— 新增 `private enum WoolDeny` 与 `woolDeny(player, pos)`，以 `validateWoolPlaceMsg(...)` 返回 `Component.translatable("survival-contract.msg.wool_deny." + 原因)`；原 `validateWoolPlace(...)` **保留完全一致的中文串**（自测断言 `contains("守护")` 语义不变）；[MainEventListener.java](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/MainEventListener.java) 的拦截处由 `Component.literal(woolErr)` 改为 `sendSystemMessage(validateWoolPlaceMsg(...))`。
  - **商家商品类别**：`displayStack(...)` 的 translatable 第二参数由类别字面量改走新增的 `categoryComponent(String)`（`"附魔书"/"武器装备"/"药水"/"回城卷轴"` → `civ_cat.book/gear/potion/scroll`）；`MerchantOffer.category` 仍为 String（服务端日志与自测断言 `"回城卷轴".equals(...)` 不变）。
  - **语言文件补键**：`en_us.json` / `zh_cn.json` / `zh_tw.json` 新增 `survival-contract.msg.civ_cat.{book,gear,potion,scroll}`（4 键）与 `survival-contract.msg.wool_deny.{out_of_range,no_team,already_owned,enemy_owned,hq,guardian,pending,not_adjacent}`（8 键，zh 值与源码中文串逐字一致，含 `§c/§7` 颜色码）。
- **④ 计分板下移避开 JEI（用户反馈遮挡物品栏）→ 【已被第八十一批纠正】**：当时把「放到 JEI 图层下层」误解为屏幕位置下移，[HudRenderer.java](file:///f:/survivalcontract/sc-mod/src/client/java/com/survivalcontract/client/HudRenderer.java) 面板顶边由固定偏移改为 `int top = Math.max(56, sh - panelH - 34);`（贴底上移 34px）。**该方向错误**——正确做法是把计分板的**渲染层级**降到 JEI 之下（`HudElementRegistry.addFirst`），详见第八十一批 ①。
- **验证**：四个构建全部 `BUILD SUCCESSFUL` + `deployJar`（SC/HK × 26.2/26.3）；自测（清空对应 `run/world` 后）**SC 26.2 = 6528 PASS / 0 FAIL**、**HK 26.2 = 48 PASS / 0 FAIL / 0 SKIP**、**HK 26.3 = 43 PASS / 0 FAIL / 3 SKIP**（26.3 临时移出 26.2 的 FD jar，FD 相关 3 条降级 SKIP，跑完已恢复）。已同步用户实例 MC 26.2 的 `E:\bingo\.minecraft\versions\生存合约\mods\survival-contract-1.0.0-mc26_2.jar` 与 `hell-kitchen-1.0.0-mc26_2.jar`。

## 2026-10-19（第七十八批）

### 文明时代：基地商人新增三号位固定商品「回城卷轴」+ 合金碎片区 10% 概率额外产出下界岩

- **① 回城卷轴（三号位固定商品，500 积分）**：基地商人兑换界面第 3 格（GUI slot 2，即「附魔之瓶」之后）固定上架「回城卷轴」；右键消耗后立刻传送到「队伍出生点」（`GameStateManager.getRespawnPoint`，文明 `initClaim` 已把每名队员重生点设为大本营中心区块地表）。物品沿用仓库既有范式——原版 `PAPER` + `DataComponents.CUSTOM_NAME`（名称 `§d回城卷轴`），不新增自定义物品/资源。
  - `CivilizationManager`：新增常量 `RETURN_SCROLL_ITEM_NAME`、`createReturnScroll()`（PAPER + 自定义名）、`isReturnScroll(ItemStack)`（纸 + 名称含「回城卷轴」）、`useReturnScroll(ServerPlayer, InteractionHand)`（守卫 PLAYING + `isCivilization()` + 存活非旁观 → 传送到本队出生点 → 消耗主手 1 枚 → 提示）；`rollGoodsOffers()` 的 `switch(t)` 新增 `case "回城卷轴"` 发放带标记本体（default 分支不加名，故需专用分支）。
  - `MainEventListener`：新增服务端 `UseItemCallback` 块——命中回城卷轴 → 调 `useReturnScroll` 并返回 SUCCESS 短路原版，其余 PASS。
  - `CivTradeConfig`：默认商品在「附魔之瓶」之后插入 `{type:"回城卷轴", count:1, cost:500}`（默认 5 条：附魔之瓶 / **回城卷轴** / 2 随机武器装备 / 随机药水）；`CONFIG_VERSION` **2→3**（旧配置自动备份 `.bak` 并重新生成默认）；补说明文案。
- **② 合金碎片区 10% 下界岩**：`CivilizationProductionConfig` 的 ALLOY 默认 `bonuses` 追加 `bonus("下界岩", 10, "netherrack")`（10% 概率额外产出 1 个下界岩），同步该区说明文案；`CONFIG_VERSION` **4→5**。
- **自测断言同步（`ServerSelfTest`）**：商人商品 4 条→**5 条** + 构成（1 附魔之瓶 / 1 回城卷轴 / 2 武器装备 / 1 药水）+ 价格默认（瓶 30 / 卷轴(三号位) 500 / 药水 300）；新增「回城卷轴物品判定」「兑换回城卷轴扣 500 分」「右键消耗并传送回队伍出生点」断言；交易配置默认值 5 条 + 临时 JSON `configVersion` 2→3；产出配置版本 v5 + 合金奖励含 `netherrack` + 临时 JSON `configVersion` 4→5。
- **验证**：双版本 `build` 成功 + `deployJar` 重新部署 `mc26.2/survival-contract-1.0.0-mc26_2.jar`、`mc26.3/survival-contract-1.0.0-mc26_3.jar`；自测（清空 `sc-mod/run/world` 后）**26.2 = 6526 PASS / 0 FAIL、26.3 = 6529 PASS / 0 FAIL**；关键新断言实测通过：`[PASS] 回城卷轴：右键消耗并传送回队伍出生点 — used=true 纸 9→8 落点 (-87,-87) 期望 (-88,-88)`。已同步用户实例 MC 26.2 的 `E:\bingo\.minecraft\versions\生存合约\mods\survival-contract-1.0.0-mc26_2.jar`。

## 2026-10-19（第七十七批）

### 替换固定地图「街角灶源 1.0.5（1）」+ 修复回大厅后顶部套餐 UI 残留 + 套餐刷新间隔 10 分钟 → 5 分钟

- **背景（用户反馈）**：① 用户上传新版「街角灶源 1.0.5 (1)」存档要求换图；② 地狱厨房对局结束回到大厅后，屏幕上方的本方套餐 UI 仍然挂在原地不消失；③ 套餐刷新间隔需从 10 分钟改为 5 分钟。
- **① 换图（hk-mod）**：解压用户上传存档，与 jar 内置 `data/hell-kitchen/kitchen/` 逐文件 SHA256 比对——region 8 个中 5 个内容变化（`r.0.0` / `r.0.-1` / `r.0.-2` / `r.-1.0` / `r.-1.-1`），entities、poi 由 4/6 个增至 6/8 个（新增若干 0 字节空文件）。按新存档原样覆盖 `region/entities/poi` 资源，并同步 `KitchenMap.MCA_FILES` 清单（entities 4→6、poi 6→8），确保 jar 内置地图与新版存档一致
- **② 套餐 HUD 残留（服务端下发清空同步）**：根因——客户端 [KitchenClientState.java](file:///f:/survivalcontract/hk-mod/src/client/java/com/hellkitchen/client/KitchenClientState.java#L17-L17) 是常驻单例，其 `entries` 只在收到 `OrderSyncPayload` 时更新；对局结束时 `OrderManager.reset()` 只清服务端状态、**未下发任何同步**，客户端仍保留上局套餐 → 顶部 HUD 不消失。新增 `OrderManager.stop(server)`：停用引擎并给每位在线玩家下发一次**空套餐**同步（`entries=[]`，`finished=true`），客户端据此立即收起 HUD；`KitchenStyleProvider.onGameEnd` 由调用 `reset()` 改为调用 `stop(server)`（该时机在 FINISHED 首 tick、玩家仍在线，同步可达）
- **③ 刷新间隔 10→5 分钟**：`KitchenConfig.batchSeconds` 默认 `600 → 300`（秒）；因旧配置为持久化文件，仅改默认值对已生成的 `config/hell-kitchen/kitchen.json` 无效 → 引入配置迁移：`CONFIG_VERSION` 升到 **2**，加载时若旧版本（`configVersion < 2`）且 `batchSeconds` 仍为旧默认值 `600` 才改写为 `300` 并回写文件（避免覆盖玩家自定义的其它时长）
- **验证**：HK 双版本 `build` 成功 + `deployJar`；自测 **HK 26.2 = 48 PASS / 0 FAIL / 0 SKIP**（装真实农夫乐事）、**HK 26.3 = 43 PASS / 0 FAIL / 3 SKIP**（临时移出 26.2 FD jar，FD 相关 3 条降级 SKIP）。新增 2 条断言：`套餐刷新间隔 5 分钟`（`batchSeconds==300`）、`对局结束停用套餐引擎（HUD 清空）`（`stop()` 后 `active=false`）；并实测日志确认旧配置迁移：`已升级地狱厨房配置至 v2（batchSeconds 600 → 300）`。已同步用户实例 MC 26.2 的 `hell-kitchen-1.0.0-mc26_2.jar`

## 2026-10-18（第七十六批）

### 冒险模式放行「右键放下菜肴」（附属侧 `PlaceGuard`）+ 中途加入观战者落点移交附属玩法

- **背景（用户反馈）**：① 部分菜肴（农夫乐事的派 / 盛宴方块，如 `farmersdelight:roast_chicken_block`、`apple_pie`）必须先**右键放下**才能用盘/碗盛装，但本玩法强制冒险模式 → 方块放不下去，菜无法交付；② 对局中中途加入服务器的玩家被观战分流送到了**竞技场兜底世界（≈普通主世界）**，没有像内置玩法那样进入「本玩法地图的观战状态」。

- **① 放置放行（hk-mod）**：
  - 新增 `com.hellkitchen.tool.PlaceGuard`：`isPlaceableDish`（白名单＝「是 `BlockItem`」且「注册名属 `farmersdelight:` 命名空间 / 菜品池成品 / `minecraft:cake`」）、`shouldAllowPlace(GameType, ItemStack)`（纯函数：仅「冒险模式 + 菜肴」）、`tryAllowPlace`（**同一 tick 临时切生存**放行并登记下一 tick 回滚）、`revertPending`（到点回滚，仅当仍是生存才切回冒险；玩家离线也能清理）、`reset`
  - `KitchenEvents`：`UseBlockCallback`（右键命中方块）与 `UseItemCallback`（瞄准空气的 `useItem` 路径）两处、仅在本玩法维度 + `PLAYING` 阶段调用放行
  - `KitchenStyleProvider`：`onServerTick` 每 tick 调 `revertPending`；`onGameEnd` 调 `reset`
  - **可行性依据（字节码级核对）**：限制由 `Player.mayBuild()`（读 `Abilities.mayBuild`）与 `ServerPlayer.blockActionRestricted(...)` 判定，两者都在服务端 `ServerPlayerGameMode.useItemOn`/`useItem` 内执行、且**晚于** Fabric 回调（`UseBlockCallback`/`UseItemCallback` 均 `@At("HEAD")` 注入）；客户端 `MultiPlayerGameMode` 无 `mayBuild`/ADVENTURE 判定（仅 SPECTATOR 早退）→ 限制纯服务端，回调内切模式即可放行
- **② 观战落点移交（sc-mod）**：
  - `com.survivalcontract.api.ScPlayStyleProvider` 新增默认钩子 `boolean onSpectatorJoin(MinecraftServer, ServerPlayer)`（返回 `true` = 附属玩法已接管）
  - `MainEventListener.teleportSpectatorToArena`：在登记 `MID_GAME_SPECTATORS` 之后、送竞技场之前，`gsm.isAddon()` 时征询附属玩法；接管则跳过竞技场默认落点（仍照常清挑战 + 发提示 + `sendSync`）
  - 旁观者「位置自愈」循环加 `!gsm.isAddon()`：附属玩法下不再把观战者拉回竞技场，避免与附属落点互相拉人
  - hk-mod 侧实现 `onSpectatorJoin`（SPECTATOR + 厨房维度西侧基地点上 1 格）；另在 `onServerTick` 每 10 tick 加观战者维度兜底（非管理员且不在厨房维度 → 拉回观战点）
- **验证**：SC/HK 双版本 `build` + `deployJar`；自测 **SC 26.2 = 6519 PASS / 0 FAIL、SC 26.3 = 6522 PASS / 0 FAIL**；**HK 26.2 = 46 PASS / 0 FAIL / 0 SKIP**（装真实农夫乐事）、**HK 26.3 = 41 PASS / 0 FAIL / 3 SKIP**（临时移出 26.2 FD jar，3 条 FD 相关断言降级 SKIP）；已同步用户实例 MC 26.2 的 SC/HK 两个 jar

## 2026-10-18（第七十五批）

### 管理员可在对局进行中切换玩法（`/sc style` + 玩法切换门控放宽）

- **门控放宽**：`SetPlayStylePayload` 收包由「仅大厅阶段」改为「大厅任何人可改 + 对局进行中仅管理员（GameMaster 权限）可改」；结算（FINISHED）阶段仍不可切换。新增 `isPlayStyleSwitchPhase()` / `canSwitchPlayStyle(player)`
- **新增指令 `/sc style [玩法键]`**（需 GameMaster 权限）：不带参数列出可选玩法（内置 4 项 + 已注册的附属玩法 id，带自动补全）；带参数直接切换；**大厅与对局进行中均可使用**
- **切换逻辑收拢**：原收包内的分支 switch 抽成 `NetworkRegistry.applyPlayStyle(server, player, key)`，收包与指令共用。关键顺序修正——**先校验目标玩法是否允许当前游戏模式，再清理旧附属玩法**（避免「校验失败却已把旧附属玩法停掉」）
- **附属玩法生命周期交接**：对局中切到附属玩法 → `startAddonMidGame`（补齐队伍 → 在线非旁观者职业锁定为「无」→ 调用 `onGameStart`，附属模组据此传送固定地图/生成套餐）+ 最终目标置 null；切离附属玩法 → 调用其 `onGameEnd` 清理运行时状态
- **同步**：切换后全员 `sendSync`（大厅阶段追加 `sendLobbyState`）+ `broadcastScores`
- 实现落点：`network/NetworkRegistry`（isPlayStyleSwitchPhase / canSwitchPlayStyle / applyPlayStyle / startAddonMidGame / syncAfterStyleSwitch / notifyPlayer / broadcastAll）、`command/ScCommands`（`/sc style` + 自动补全 + 列表）
- 自测：新增 `testBatch75Features`（9 断言：大厅任何人可切 / 对局中控制台源可切 / 对局中按管理员权限判定 / 结算阶段不可切 / 中局切到附属玩法生效 + `onGameStart` 被调用且参与者=在线非旁观者 / 中局切换后无最终目标 / 非法切换被拒绝且不清理旧玩法 / 切离附属玩法调用 `onGameEnd`）。**同时修复该用例的状态泄漏**（未还原 `currentFinalGoal`/属性选项/职业 → 后续会话持久化用例 5 条 FAIL；已在 finally 完整还原）
- 验证：双版本 `build` 成功 + `deployJar` 部署；自测（清空 `sc-mod/run/world` 后）**26.2 = 6519 PASS / 0 FAIL、26.3 = 6517 PASS / 0 FAIL**

## 2026-10-18（第七十四批）

### 附属玩法宿主扩展点（`com.survivalcontract.api`）+ 固定地图注入抽象（地狱厨房前置）

- **背景**：为「街角灶源」固定地图的厨房玩法（附属模组 `hk-mod`，第 2~4 批实现）提供注册与生命周期钩子。此前 SC 的 `fabric.mod.json` 无任何第三方扩展点，附属玩法无法出现在挑战书里 → 本批只做「宿主侧最小扩展」，玩法逻辑全部留给附属模组。
- **新增 API 包 `com.survivalcontract.api`**：
  - `ScPlayStyleProvider`：`id / displayNameKey / displayNameFallback / forceTeamMode / ui() / options() / onGameStart / onServerTick / onGameEnd / onPlayerJoin`
  - `StyleUi`（record）：`suppressRuleCards / lockProfession / disableTeamChest / noFinalGoal / noTasks` 五项抑制开关
  - `StyleOption`（record）：`key / labelKey / labelFallback / min / max / defaultValue / step` + `clamp()` / `next()`（循环切换）
  - `PlayStyleRegistry`：`register / loadEntrypoints / all / byId / selected / clear`；入口点名 `survival-contract:playstyle`，在 `SurvivalContract#onInitialize` 用 `FabricLoader.getEntrypointContainers` 采集（自定义入口点两端都加载 → 客户端挑战书屏可直接读取）
- **`GameStateManager`**：`PlayStyle` 新增 `ADDON`；新增 `addonStyleId / styleOptions` 与 `isAddon() / getAddonStyleId() / selectAddonStyle() / getPlayStyleKey() / getAddonProvider() / getStyleOption / setStyleOption / clearStyleOptions`。`isGodsLies/isStowaway/isCivilization` 对 ADDON 恒 `false` → 现有 if 链自动落入「默认/无规则」分支。`resetFlags()`（回大厅）与 `clearRuntimeState()`（会话恢复）清空附属选项
- **网络同步**：`LobbyStatePayload.playStyle` 改传 `getPlayStyleKey()`（附属玩法 = 其 id）、新增 `styleOptions`；新增 C2S `StyleOptionPayload(styleId, key, value)`（大厅阶段校验 styleId 命中 + 按 provider 选项夹取 `clamp`，广播 `sendLobbyState`）；`SetPlayStylePayload` key 上限 16→64；`ClientState` 缓存 `styleOptions` + `isAddonStyle()` + `styleOption()`
- **挑战书 UI（`ChallengeBookScreen`）**：大厅初始菜单玩法按钮由「内置 4 项」改为「内置 4 项 + 注册表动态项」（宽度按数量自适应重算，选中态 `✔`）；选中附属玩法时：规则卡/事件整块隐藏（占位说明）、职业/团队箱置灰锁定、最终目标占位、时长循环去掉「无限制」，第二行第 4 位「双禁」替换为注册表第一个选项的循环按钮（发 `StyleOptionPayload`）
- **抑制开关落点（防作弊）**：`ChallengeTogglePayload` 对 addon 仅放行 `rule_pvp/rule_night_vision/rule_keep_inventory`；`ProfessionSelectPayload` 对 addon 直接忽略；`TaskManager.issueNewTask` 对 addon 直接返回（无专属任务）；`TeamChestManager.openFor` 对 addon 提示已关闭；最终目标由开局置 `null` 天然抑制（`checkWinCondition` 系列 `null` 短路）
- **生命周期钩子**：
  - 开局：`LobbyManager.startGame` 顶部新增 `if (gsm.isAddon())` 分支——**不建竞技场、不转 SURVIVAL、不发职业/规则**，直接 `PLAYING` + 初始分 0 + 职业强制 `NONE` + 3 秒冻结 + `provider.onGameStart(server, 参战者)`（大厅观战者转旁观者）；provider 缺失时兜底回 `NORMAL`
  - tick：`MainEventListener` END_SERVER_TICK 转发 `provider.onServerTick(server)`
  - 结束：进入 `FINISHED` 首次 tick 调 `provider.onGameEnd(server)`（随后沿用既有 `setForcedWinners` + 广播 + 回大厅流程，附属玩法用 `setForcedWinners + endGame(null)` 结束对局）
  - 加入：JOIN 事件转发 `provider.onPlayerJoin(player)`（重连后重新施加冒险模式/饱和等）
- **附属玩法专属的既有流程适配**（避免内置流程把附属玩法带偏）：
  - `teleportToPlayingSpawn` 对 addon 直接短路——附属玩法没有竞技场，「防逃跑扫描」（每 2 秒）与「中途重连回出生点」不得把玩家传送到竞技场兜底世界（主世界）；玩家归位改由附属模组自己的 tick 守卫完成
  - 时间耗尽分支：`isStowaway() || isCivilization()` 扩为 **`|| isAddon()`**——附属玩法与走私犯/文明时代一致，走「分数最高者获胜」（否则会被当成「无人完成最终目标 → 全员分数归零」）
- **固定地图注入抽象**：新增 `dimension/FixedMapInjector`（`inject(server, namespace, dimId, jarResourcePrefix, jarFiles[][], zipOverride, folderOverride, label)`），保留「zip → 散装文件夹 → JAR 内置」三级优先；`LobbyDimensionSetup.injectRegionFiles` 改为委托调用（行为不变）；`LobbyManager.applyLobbyRules` 由 `private` 改 `public` 供附属玩法复用（禁刷怪 + 锁晴天）
- **自测**：新增 `testBatch74Features`（11 断言：注册表 register/byId/去重；ADDON 选中态与 `getPlayStyleKey`；三内置判定恒 false；`getAddonProvider`/`registry.selected` 命中；五项抑制开关 + `forceTeamMode`；选项默认值 3、`clamp(9)=6/clamp(0)=1/next(6)=1/next(3)=4`；addon 不发放专属任务；`onServerTick` 钩子契约；`FixedMapInjector` 复用后大厅 `region/*.mca` 注入到位）
- 实现落点：`api/ScPlayStyleProvider`、`api/StyleUi`、`api/StyleOption`、`api/PlayStyleRegistry`（新增）、`SurvivalContract`（入口点采集）、`game/GameStateManager`、`network/NetworkRegistry`、`client/ClientState`、`client/gui/ChallengeBookScreen`、`main/MainEventListener`、`task/TaskManager`、`game/TeamChestManager`、`dimension/LobbyManager`、`dimension/FixedMapInjector`（新增）、`dimension/LobbyDimensionSetup`、`ServerSelfTest`
- 验证：双版本 `build` 成功 + `deployJar` 重新部署 `mc26.2/survival-contract-1.0.0-mc26_2.jar`、`mc26.3/survival-contract-1.0.0-mc26_3.jar`；自测（清空 `sc-mod/run/world` 后）**26.2 = 6510 PASS / 0 FAIL、26.3 = 6507 PASS / 0 FAIL**（第七十三批 + 11 新断言；总数随环境/版本门控略有浮动）
- 附注（非本批回归）：自测中「青金石区 Y≤0 熔炉+末影箱满足前置」在 `run/world` 残留旧局面时偶发 FAIL（该断言在 `CivilizationManager` 反射逻辑，本批未改）；删除 `run/world` 重跑即 0 FAIL，属测试环境残留而非功能回归
- 兼容性：未安装任何附属模组时注册表为空 → 行为与以往完全一致

## 2026-10-18（第七十三批）

### 补全英文语言文件（en_us.json：补齐 27 个此前只有中文回退的 GUI 键）

- **问题**：`ClientL10n.tr(key, 中文回退)` 的解析顺序为「当前语言文件 → 缺键回退源码中文」，因此下列键在英语环境下直接显示中文，同时 `Component.literal` 会把它们渲染进按钮/文本（非翻译组件）
- **补齐键（27 个）**：
  - 走私犯与检察官 `ProsecuteScreen`：`gui.prosecute.` accept_bribe / inspect / close / confirm / cancel / desc_decision1（含 `%s %d`）/ desc_decision2 / desc_wait1 / desc_wait2 / desc_go1 / desc_go2 / decision_window / wait_line / ready_line / no_players / friend / hint_pending / hint_select / hint_confirm（含 `%s`）共 19 键
  - 神的谎言 `ChallengeBookScreen`：`gui.godslies.` hidden_title / hidden_desc / hidden_desc2 / hidden_desc3 共 4 键
  - 其他：`gui.stowaway.profession_locked`、`gui.stowaway.chest_locked`、`gui.civilization.pvp_locked`、`gui.terrain_preset` 共 4 键
- **保持原格式**：完整保留 `§` 颜色/样式码与 `%s`/`%d` 占位符，不新增原中文没有的颜色码
- **范围核对**：全量比对 Java 源码中 `"survival-contract.*"` 字面键（114 个，含动态前缀）与 en_us.json →
   内容键（challenge / profession / event / warn / goal）此前已全覆盖；本次补齐后，**再无 Java 引用而 en_us 缺失的键**
- **未纳入**：zh_cn.json 的 `gui.accuse.hint_no_charge_envoy` / `hint_no_charge_civilian` 为无代码引用的历史遗留键（`AccuseBookScreen` 实际用 `hint_no_charge`），不属于缺英文的真实文案，未补
- 实现落点：`assets/survival-contract/lang/en_us.json`（391 → 418 键）
- 验证：JSON 解析通过、值内无中文字符残留；双版本 `build` 成功（26.2 14s / 26.3 11s）并重新部署 jar；自测 **26.2 = 6497 PASS / 0 FAIL、26.3 = 6496 PASS / 0 FAIL**
- 遗留：zh_tw.json 现有 391 键，上述 27 键未同步（繁中环境下这 27 键会回退到简体中文回退串），本次仅按需求补英文

## 2026-10-18（第七十二批）

### 新增职业「盗贼」（Thief）

- **右键夺取装备**：靠近生物右键（沿用原版交互距离，能点到即算靠近）→ 一次性夺走其主手（武器/工具）、副手与四件护甲，全部进入盗贼背包，背包满则掉落脚下（不消失）。卫道士的铁斧、骷髅的弓、溺尸的三叉戟等一并夺走，目标对应装备槽清空。不对玩家生效（避免无限掠夺式 PvP）
- **一击必杀 + 夺取头颅**：对「可掉落头颅」的生物（僵尸/尸壳/溺尸/僵尸村民/骷髅/流浪者/沼骸/凋灵骷髅/苦力怕/猪灵）右键直接击杀并夺取其头颅。末影龙与玩家不计入（前者是最终目标、后者非生物）。击杀先清伤害冷却闸（26.3 `damageCooldownTime`/`lastHurt` 兼容）再以盗贼为击杀者 `hurtServer` 结算（正常掉落/经验），头颅直接入手；极端情况下伤害被拦截则兜底 `kill(level)`
- **2 分钟冷却**：`THIEF_COOLDOWN_TICKS = 2400`。冷却期内右键不接管（保留原版交互）；目标既无装备也无可夺取头颅时不接管、不消耗冷却
- **初始物品**：铁斧（抢夺 III + 经验修补）。`giveStartingItems` 新增 IRON_AXE 分支
- **规则免疫**：免疫「不能被 XX 敌对生物杀死」系列 9 条死亡规则（no_killed_by_zombie/skeleton/spider/creeper/witch/enderman/blaze/drowned/magma_cube）；不免疫 no_kill_iron_golem / 溺亡 / 岩浆 / 虚空等非生物击杀类规则
- **界面与本地化**：职业选择菜单自动列出新职业（新增第 11 项 → 6 行）；职业按钮高度按窗口自适应压缩，避免返回按钮出屏。en_us / zh_tw 补 THIEF name/desc/hud（zh_cn 走源码简中回退）
- 实现落点：`ProfessionManager`（THIEF 枚举 + `giveStartingItems` 铁斧附魔 + `isImmuneToChallenge` + `isThief`/`tryThiefSteal`/冷却与头颅表 + `giveOrDrop`）、`MainEventListener`（UseEntityCallback 盗贼右键接管）、`ProfessionSelectScreen`（6 行自适应按钮高度）、`assets/survival-contract/lang/{en_us,zh_tw}.json`
- 自测：新增 `testBatch72Features`（①9 条 no_killed_by_* 全部免疫且非生物击杀类不免疫 ②初始铁斧抢夺3+经验修补 ③夺取卫道士铁斧 + 2400t 冷却 + 冷却期不接管 ④一击必杀僵尸并夺取头颅）。**Minecraft 26.2 = 6499 PASS / 0 FAIL；26.3 = 6496 PASS / 0 FAIL**（7 条盗贼断言两版本全通过）
- 双版本构建与部署：`build` 成功（26.2 56s / 26.3 13s），`deployJar` 产出 `f:\survivalcontract\mc26.2\survival-contract-1.0.0-mc26_2.jar`、`f:\survivalcontract\mc26.3\survival-contract-1.0.0-mc26_3.jar`；三语言 JSON 校验通过（en_us / zh_tw 各 391 键）

## 2026-10-17（第七十一批）

### 神的谎言 2 项 + 走私犯与检察官 3 项 + 死亡重生/文明守护 2 项（共 8 项修复）+ 26.2/26.3 平台差异修复 + 双版本构建收尾

- **①【神的谎言】指认书死亡返还不一致（有的返还、有的直接消失）**：`GameStateManager` 的 `deathKeptBooks` 由 `Map<UUID,KeptBook>` 改为 `Map<UUID,List<KeptBook>>`——早期 `merge` 把同一次死亡暂存的多件模式道具（指认书 + 神谕纸符 + 任务刷新券）**错误合并成一种**（count 相加、name/item 取后者）→ 只返还一种、其余凭空消失。现逐件追加暂存、重生逐件返还（背包满则掉脚边），两种及以上道具同时暂存也能全部返还
- **②【神的谎言】结束扣分污染下一局**：`eliminatePlayerNow` 增加 `currentPhase != PLAYING → return` 守卫——大厅/结束阶段的加减分（大厅关规则扣分、结算扣分）**不再改身份或设旁观者**。根因：`playStyle` 结束时不立即复位而 `scores` 已清空（getScore=0），大厅阶段的规则扣分经 `checkElimination` 误把全员标记淘汰 + 设旁观者 → 污染下一局（亦为第 ⑦ 项"死亡后回不了大厅"的成因之一）
- **③【走私犯与检察官】左键放进收纳袋的物品没有分数**：`spillVanillaBundleContents` 由"把收纳袋道具内嵌的原版 BundleContents 倒回物品栏"改为"**倒入该钥匙对应的服务端虚拟袋容器**"（价值袋→valueBags / 贿赂袋→bribeBags）并清空内嵌存储——左键点选塞入的货品与右键开袋放入一致计入袋容器、参与结算计价；收纳袋钥匙不得入袋、贿赂袋禁放走私品、袋满退回物品栏不消失
- **④【走私犯与检察官】违禁品整组交易 / 刷新后旧违禁品仍有价值 / 袋内多个只按 1 个计分 / 移除违禁品列表里的工具与武器**：
  - `SMUGGLE_POOL` **移除全部工具与武器**，改为 16 件易生存日常原材料（泥土/圆石/原木/木板/木棍/沙子/沙砾/小麦/线/皮革/燧石/骨头/羽毛/甘蔗/黏土球/腐肉）
  - `processBagAndClear` 中 `total += SMUGGLE_VALUE * s.getCount()` → `total += SMUGGLE_VALUE`：**袋内违禁品无论整组多少个，清空后最多只按 1 个计 128 分**（不再按数量翻倍）
  - 每批每玩家仍只允许交易一次（`smuggleRedeemedThisBatch` 登记）；**换批后上批违禁品因 `it == curSmuggle` 判定不再计分**（旧货 0 分）
- **⑤【走私犯与检察官】检查发起书 2 分钟冷却 + 不能连续两次选同一位检察官**：`initiate` 新增 `PROSECUTE_COOLDOWN_TICKS = 2400`（同玩家 2 分钟内不得再次发起，提示剩余秒数）与 `lastProsecutorChosen` 去重（**不能连续两次选择同一位玩家作为自己的检察官**）；发起成功时记录冷却起点与本次检察官，`reset` 清空两 Map
- **⑥ 死亡后一直没重生 → 游戏结束后回不了大厅/进不了下一局**：结束阶段（FINISHED）首次进入时，对仍未复活的玩家先 `PlayerList.respawn(p, false, KILLED)` **强制重生**再切旁观者/放音效；`performResetForLobby` 玩家循环头部加同款兜底强制重生。修复"死亡界面卡到游戏结束、竞技场维度拆除后重生失败、回不了大厅也进不了下一局"
- **⑦【文明模式】僵尸马不自燃 + 区域守护怪物逃逸**：
  - 新增 `civZombieHorses` 登记 + ENTITY_LOAD 钩子（civ PLAYING + arena 世界 + ZombieHorse → 补挂无限火焰免疫并登记）；`guardianFireSweep` 除守护怪外**每 tick 还扑灭登记僵尸马的火**（覆盖非守护的事件骑兵「僵尸马骑兵」等）
  - `containMobs` 由"每秒分支"改为**每 tick 执行**；`entity == null` 分支区分两种情形：**所属区块未加载 → 保留登记**（否则重载后不再被拉回 → 逃逸，本次修复），区块已加载但实体检索不到 → 确已消失 → 自愈清理登记（保留第五十七批"击败后可占领"行为）
- **⑧ 26.2/26.3 平台差异修复（自测暴露）**：
  - **26.3 伤害冷却闸**：26.3 `LivingEntity.hurtServer` 把伤害冷却闸拆为 `damageCooldownTime` + `lastHurt`（26.2 用 `invulnerableTime`），`Entity.setInvulnerableTime` 只写 `invulnerableTime` 不清新闸 → 「取消原始伤害 + 按规则重打」的补伤（≤上次伤害）在 26.3 被静默吞掉（弓箭不命中/乐师音波 0 伤害）。新增 `McCompat.clearHurtCooldown`（反射清 `damageCooldownTime`/`lastHurt`），并在 `setInvulnerableTime(≤0)` 与 `MainEventListener.redealHurt` 内同步清闸——两版本伤害语义对齐
  - **商人/资源落点加固**：`CivilizationManager.surfaceY` 增加「高度图与实际方块不一致时向下探真实地表」兜底（陈旧高度图不再悬空）；自测商人断言改为「本列存在地表则必须落地，整列虚空无可判定」——测试环境未生成地形导致的高度图陈旧不再误报（非本批功能回归）
- 实现落点：`GameStateManager`（deathKeptBooks List 化 + eliminatePlayerNow PLAYING 守卫）、`SmuggleManager`（SMUGGLE_POOL / processBagAndClear / spillVanillaBundleContents / initiate 冷却与去重 + 自测钩子）、`MainEventListener`（FINISHED + performResetForLobby 强制重生 + redealHurt 清闸）、`CivilizationManager`（civZombieHorses / guardianFireSweep / containMobs 每 tick + 区块加载判定 + surfaceY 落点加固）、`McCompat`（clearHurtCooldown 26.3 冷却闸兼容）
- 自测：`ServerSelfTest` 同步适配——testBatch61（走私品池改日常原材料 + 工具武器不再属违禁品；发起书冷却/去重/清除后恢复三断言）、testBatch62（跳过上一步发起记录，避免新增冷却致 NPE）、testBatch64（工具武器移出价值池与违禁品池 + 内嵌货品倒入服务端袋容器断言）、testGodsLies（新增多道具同帧暂存→重生全部返还断言，并清残留防泄漏）；新增 `testBatch71Features`（①非 PLAYING 阶段加减分不淘汰/不改旁观者 ②袋内违禁品×64 只计 128 ③文明僵尸马登记后每 tick 扑灭）。**Minecraft 26.2 = 6491 PASS / 0 FAIL；26.3 = 6489 PASS / 0 FAIL**（断言总数版本/环境门控略有浮动）

## 2026-10-15（第七十批）

### 神的谎言次数上限（使者降罪权 / 平民指认权同一时刻最多持有 1 次）

- **次数上限钳制**：`grantGodsLiesTaskReward` 发放奖励时若已持有可用次数（≥1）**不再累积**，仅提示「最多 1 次，不累积——用掉后完成任务可再次获得」（新增 `charge_capped` 文案，三语言同步）；发放改为 `put(id, 1)` 钳制。根因是原 `merge(+1)` 累积式发放——连续完成多个任务可囤积多次神罚/指认一次性连发，破坏身份博弈平衡
- **消耗路径不变**：指认（命中/失败均消耗 1 次）与使者降罪（-55）照常扣减次数；用掉后再次完成任务即可重新获得——上限只限制「同时持有」，不限制「累计获得」
- 实现落点：`GameStateManager`（grantGodsLiesTaskReward 使者/平民两分支上限检查 + 钳制发放）、语言文件 ×3（`survival-contract.msg.godslies.charge_capped`）
- 自测：新增 `testBatch70Features`（6 断言：使者连续两次任务奖励降罪权=1 不累积 / 降罪消耗 100→45 不淘汰 / 用掉后完成任务恢复 1 / 平民连续两次任务奖励指认权=1 不累积 / 指认使者命中→对方 -200 淘汰且次数归 0）。另修复一处**固有测试泄漏**（非本批回归）：此前各测试给三假人启用的挑战规则未清空，`startGame` 按残留规则计入开局初始分（上轮 13/13/19 恰逢随机分队使同队两人同分而侥幸通过，本轮分队变化暴露为 4 FAIL）——`testBatch70Features` 收尾在 `startGame` 前清空三假人已启用规则，开局初始分回归每人 10 分（0 规则分 + 无职业 +10），**480 PASS / 0 FAIL**（第六十九批 474 + 6）

## 2026-10-14（第六十九批）

### 文明永昼 + 猪灵回收计分修复 + 基地商人附魔之瓶 + 神的谎言指认改 -200 计分制

- **文明模式改永昼**：`setup` 关闭昼夜推进（26.2 的 doDaylightCycle = `GameRules.ADVANCE_TIME`，字符串指令 gamerule 在 26.2 无效必须走 API）+ 命令源方式执行 `time set day` 立即设为白天；`reset`（对局结束）恢复进入前的昼夜推进状态
- **猪灵回收计分修复（提交 9 个只得 2~3 分的根因）**：原结算"全队平分 + 四舍五入"——多人队提交 9 圆石每人只得 2~3 分（9 ÷ 人数取整）。现改为**积分全额记给开启回收箱的提交者**（`openRecycle` 记录提交者，结算时按价格表逐个累计后一次性入分）；无提交者记录（理论不可达）保留全队平分兜底
- **基地商人换货**：取消随机附魔书售卖，新增**附魔之瓶 30 积分/个（固定价不涨价）**——默认商品 = 1 附魔之瓶 + 2 随机武器装备 + 1 随机药水；`configVersion` 1 → 2（旧配置文件自动备份 .bak 并重新生成默认，自定义参数可从 .bak 抄回；「随机附魔书」类型仍支持配置自定义）
- **神的谎言指认改 -200 计分制（修复"使者被指认多次不出局"）**：根因并非任务奖励免死，而是旧"仅剩自己使者"分支（NULLIFIED）——默认 1 使者时指认永远无事发生。现整体重写：**指认命中使者 → 对方 -200 分**（分数 ≤0 自动淘汰，无任何免死特例）；**指认失败 → 自己 -200 分**；命中同时结算使者链接（链接者未武装 → 契约神罚 -55，已武装纸符免疫 → 挡下）；**分数小于 0 时显示为 0 分**（`getScore` 钳制显示，内部负分仍触发淘汰判定）；删除 NULLIFIED 枚举与相关广播
- 实现落点：`CivilizationManager`（永昼开关/恢复 + recycleOpeners 提交者记录 + settleRecycle 全额计分）、`CivTradeConfig`（CONFIG_VERSION 2 + defaultGoods）、`GameStateManager`（resolveAccusation -200 计分制 + getScore 显示钳制 + AccuseResult 精简为 HIT/MISS）、`NetworkRegistry`（指认结果广播双分支）、语言文件 ×3（指认文案 -200）、`AccuseBookScreen`（javadoc 同步）
- 自测：新增 `testBatch69Features`（4 断言：ADVANCE_TIME 关闭 / 9 圆石 → 提交者全额 +9 且队友不得分 / 默认商品附魔之瓶 cost=30 无附魔书 / reset 恢复昼夜推进），testGodsLiesRelic 三局指认断言全面重写（-200 淘汰/免疫挡下/链接双方结算/获胜判定），testCivilization 商人断言适配 4 条新商品与临时配置 configVersion 2，testGodsLies 指认失败文案同步，**474 PASS / 0 FAIL**（第六十八批 471 + 3）

## 2026-10-14（第六十八批）

### 文明产出 3 项修复（石头区前置失效 / 木头区产物 / 设施登记漏检）+ 基地商人落点修正

- **石头区放了箱子+橡木不产圆石（根因：前置设施漏登记）**：`MainEventListener` 原本只对 `isFunctionalBlock` 白名单方块（熔炉/高炉等）的放置登记设施——oak_wood（石头区前置）不在白名单 → **永不登记 → 前置永不满足 → 不产出**。现任意方块物品放置都 `registerFacility`（注入/前置判定按方块状态过滤，冗余登记无害）；箱子/木桶的容器归属登记保留原位
- **木头区产物修正**：默认配置 WOOD.output `oak_wood` → `oak_log`（橡木**原木**），说明同步；`configVersion` 3 → 4（旧配置文件自动备份 .bak 并重新生成默认值）
- **前置规则口径确认（设计保留）**：前置设施**不需挨着资源区块、无先后顺序**——放置即全图登记，每秒产出结算时按全图扫描判定；**原木（\*_log）不算前置**，六面皮木块（\*_wood，任意树种）才算——与配置说明一致。实测桥接修复：`produceZoneForTest` 补 `cachedLevel` 设置/清理（否则带前置区块在测试桥下永远检测不到方块）
- **基地商人悬空修正（同类问题自检发现）**：商人生成于地表高度 +1.0——NoAI 商人无 AI **不受重力**、无法自行落回地表，新世界首局生成与检查同 tick 时商人悬空。改为直接站上地表高度（与野生动物/动物生成同款点位口径）
- 实现落点：`MainEventListener`（任意方块物品放置登记设施）、`CivilizationProductionConfig`（WOOD 产物 + 配置版本）、`CivilizationManager`（商人落点 + produceZoneForTest cachedLevel）
- 自测：新增 `testBatch68Features`（5 断言：木头区产出橡木原木×6 无前置 / 石头区无前置 0 产出 → 登记 OAK_WOOD 后产出圆石×10——端到端覆盖「放置→登记→前置→产出」链路；测试方块与区块归属精确保存还原防跨局世界残留），testBatch53 木头区断言同步 oak_log，testCivilization 临时配置版本升 4，**471 PASS / 0 FAIL**（第六十七批 466 + 5）

## 2026-10-14（第六十七批）

### 走私犯玩法 8 项漏洞修复（检查结算 / 收纳袋互存 / 批次计价 / 重连同步）

- **① 检查后不再误清物品栏**：检查结束（受贿/放行/查获）不再无条件清除被检查者物品栏——只有散装价值物品按新规则兑换入分后移除；杂物与过轮走私品保留原位（删除 clearLooseValuablesAfterInspection）
- **② 收纳袋不再经方块/实体路径倾倒**：手持收纳袋右键方块/实体改发 OpenBagPayload 打开对应袋界面（服务端 UseBlock/UseEntity 双兜底拦截原版收纳袋行为），修复「价值袋有货时右键贿赂袋导致货物倒进物品栏」
- **③ 散装物品计价**：检查结算时物品栏中散装价值物品（非走私品）整组按价值表兑换入分并移除——不再视为无价值；受贿与放行两种结算统一走 `redeemGoodsAndLoose`
- **④ 过轮走私品不再换分**：计价改批次感知——只有**当前批次**走私品计 128 分；换批后的旧走私品按普通价值表计价（多为 0 分）且保留在物品栏不清除
- **⑤ 走私品兑换名额**：袋内+散装共享 128 分名额——当前走私品仅首件计 128 分，袋内超额部分随袋清空消失、散装超额保留不再换分
- **⑥ 两队走私品强制不同**：refreshBatch 抽取走私品时避开对方组当前走私品（≤20 次重掷 + 遍历兜底）
- **⑦ 两收纳袋互存禁放**：双袋菜单（价值袋/贿赂袋）内手持收纳袋道具的放置/数字键换位/SWAP/拖拽（QUICK_CRAFT）/背包 shift 存入全部**双端拦截（点击无反馈）**，修复互存后双袋内容互相吞没消失；新增 `ValueBagContainer` 标记类与 `clientValueBagMenu` 客户端引用（与贿赂袋对称）；从袋里拿取始终放行
- **⑧ 重进补发 HUD**：参战者中途掉线重连（PLAYING 阶段 JOIN）补发走私犯 HUD sync——重进后恢复收集清单与物品价值显示
- 实现落点：`SmuggleManager`（ValueBagContainer + clientValueBagMenu + isBagKeyItem + blockBagKeyDeposit + itemValue/goodsTotal/bagTotal 批次感知 + redeemGoodsAndLoose 统一结算 + refreshBatch 避让对方 + JOIN 补发 + 测试桥 smuggleItemForTest/redeemGoodsAndLooseForTest）、`AbstractContainerMenuMixin`（袋钥匙禁放段）、`ClientState`（价值袋菜单引用接收）、`ChallengeBookOpener`（方块/实体路径开袋）、`MainEventListener`（服务端开袋兜底 + JOIN 补发）
- 自测：新增 `testBatch67Features`（16 断言：散装换分 4+8=12 / 兑换名额 128+4=132 且散装超额保留 / 过轮走私品不计 128 分且保留 / 双队走私品去重 / blockBagKeyDeposit 直测 8 项——袋区 PICKUP 泥土拦截·铁锭放行·空手放行·SWAP 双向·背包 shift 拦截·QUICK_CRAFT 拖拽拦截），testBatch61 受贿/放行断言更新为新结算口径，**466 PASS / 0 FAIL**（第六十六批 450 + 16）

## 2026-10-14（第六十六批）

### 贿赂袋兼容两边价值物品（走私品除外）+ 检察书显示对方贿赂袋价值

- **两边价值物品本就兼容**：贿赂袋计价与收纳均走全局价值表（公开商品 8/4/2 档），红蓝两队的价值物品都能放入贿赂袋、都会被计入贿赂袋价值——此部分无需改池
- **走私品禁入贿赂袋（新拦截，修复刷分漏洞）**：原版点击可将走私品（如泥土 128 分）塞进贿赂袋刷高贿赂总价诱检察官受贿——现按既有「锁定格」同款 `doClick` HEAD 双端拦截（**点击无反馈**，不是放进去弹回）：
  - 袋区放置/拖拽：手持走私品 → 取消（QUICK_CRAFT 整个拖拽动作一律取消）
  - 袋区数字键（快捷栏 0-8）/F（副手 40）换位：目标格为走私品 → 取消
  - 背包区 shift 快速移动：该格为走私品 → 取消（shift 是存入动作）
  - **从袋里拿取始终放行**（空手点取、shift 取出不受影响）
  - 双端识别：服务端按贿赂袋容器标记类 `BribeBagContainer`（新增 SimpleContainer 子类，四处创建点换用）；客户端菜单底层容器是原版新建的裸 SimpleContainer（javap 证实无标记可用）→ 新增 S2C `BagOpenPayload` 在界面包后紧随发送，客户端记录袋菜单引用 `clientBribeBagMenu`，拦截守卫按引用相等比对（换开其他界面自然失效，无需清理钩子）
- **计价防御**：`bribeBagTotal` 改为跳过走私品（`bribeBagValue`）——即使历史存档袋里残留走私品（旧版已塞入）也不计入受贿结算与检察书显示，受贿时一并清除但不给分
- **检察书口径同步**：`ProsecuteStatePayload` 决策模式的 bribeTotal 取值即 `bribeBagTotal`（排除走私品后口径自动正确）；系统提示与检察书文案「贿赂物品总价」→「**贿赂袋价值**」（`enterInspectionRoom` + `ProsecuteScreen` desc_decision1）
- **实现落点**：`SmuggleManager`（BribeBagContainer 标记类 + clientBribeBagMenu 静态引用 + blockBribeDeposit 守卫 + bribeBagValue + openBag 发 BagOpenPayload + 文案）、`NetworkRegistry`（BagOpenPayload S2C 注册）、`ClientState`（接收器记录/清除菜单引用）、`AbstractContainerMenuMixin`（贿赂袋禁放段）
- 自测：新增 `testBatch66Features`（10 断言：计价排除走私品 8 分 + 守卫直测——泥土放置拦截/铁锭放行/空手拿取放行/袋区 shift 取出放行/背包 shift 存入泥土拦截/数字键换位双向/拖拽拦截/价值袋不受守卫影响），**450 PASS / 0 FAIL**（第六十五批 440 + 10）

## 2026-10-14（第六十五批）

### 文明模式怪物继续减负：生成速度再砍半 + 新增世界怪物总量上限

- **生成速度砍半**：自然刷怪概率闸 1/4 → **1/8**（每次 spawnForChunk 有 7/8 概率被整体跳过；第五十二批原为 1/4，本次再减半）
- **世界怪物总量砍半（新机制）**：文明模式对局中世界敌对生物总量达到上限后**拒绝全部自然刷怪**
  - 上限公式 = 原版怪物刷怪容量（70 × 已加载区块 / 289）的**一半**
  - 敌对生物按 Enemy 接口计（含幽灵/恶魂/史莱姆等非 Monster 敌对实体，与 MainEventListener 敌对判定一致）
  - 计数缓存：spawnForChunk 每 tick 每区块调用，不能现场遍历——按维度每 100 tick（5 秒）重计一次（`countWorldMonsters`）
- **实现落点**：`CivilizationManager`（naturalSpawnAllowed 拆为概率闸 `spawnChanceAllowed` + 总量闸 `worldMonsterCap`/`countWorldMonsters`）、`NaturalSpawnerMixin`（javadoc 同步双闸）
- 自测：原 1/4 比例断言改 1/8（期望 1250/10000，范围 800–1800），新增「世界怪物总量上限 = 原版刷怪容量的一半」断言，**440 PASS / 0 FAIL**（第六十四批 439 + 1）

## 2026-10-14（第六十四批）

### 收纳袋改用原版收纳袋（Bundle）+ 走私品池换易得物品 + 公开商品池删除全部工具武器

- **收纳袋道具改用原版收纳袋形态**（按用户指令「我说的是这个收纳袋！」，原为皮革/兔子皮）：
  - **价值收纳袋 = 棕色原版收纳袋**（`minecraft:bundle`）+ 自定义名「§6价值收纳袋」
  - **贿赂收纳袋 = 红色原版收纳袋**（注册表直查 `minecraft:red_bundle`——26.2 无 Items.RED_BUNDLE 常量，DYED_BUNDLE 是染色集合，CivilBlocks 同款注册表直查模式）+ 自定义名「§c贿赂收纳袋」（红色 = 红包寓意）
  - 服务端虚拟容器机制（27 格双袋、开袋界面、检查结算）全部保留——收纳袋本体只是钥匙道具，右键 SUCCESS 拦截短路原版收纳袋行为
- **防原版夹带**（`spillVanillaBundleContents`，新）：原版收纳袋可经物品栏点选/对掉落物使用塞入物品（BundleContents 组件），藏在道具里会绕过检查计价——**开袋与进检查室时**自动把收纳袋道具内嵌的原版物品全部倒回物品栏（放不下的掉落）并清空内嵌存储，倒出的泥土等散装价值物品随后按既有规则在检查结束时清除（走私品不能散装携带，规则自洽）
- **走私品池换为容易生存获得的物品**（原：TNT/黑曜石/末影珍珠/经验瓶/命名牌/鞍/望远镜/时钟 8 件）：
  - 新池 **14 件**（固定 128 分）：泥土、圆石、橡木原木、橡木板、木棍 + 木剑/木镐/木斧/木锹、石剑/石镐/石斧/石锹、弓
  - 与公开商品池完全不相交保持不变
- **公开商品池删除全部工具与武器**（按用户指令，走私品池可出现）：
  - 8 分档：钻石/绿宝石/下界合金锭/金苹果（原含钻石套工具）
  - 4 分档：铁锭/金锭/紫水晶碎片/金胡萝卜（原含铁剑/铁镐/弓/弩）
  - 2 分档：煤/红石/青金石/铜锭/面包/熟牛肉/熟猪排（原含石剑/铁斧/铁锹）
  - 顺带修复旧池 IRON_AXE 重复登记（POOL_4 与 POOL_2 并存，后者覆盖前者）问题
- **规则连带影响**：泥土/圆石/木棍等常见物品现为 128 分走私品 → 检查结束时散装清除范围扩大到它们（须装入价值收纳袋）；受贿/没收计价均按 128 分/件
- **实现落点**：`SmuggleManager`（双池重写 + initPlayerKit/grantKitIfMissing 换 Bundle + redBundleItem/isBundleItem 注册表直查 + spillVanillaBundleContents + openBag/enterInspectionRoom 调用点）、`ChallengeBookOpener`（袋判定改 bundle/red_bundle 注册表 id）、`LobbyManager`（袋名消息同步棕/红色说明）
- 自测：新增 `testBatch64Features`（3 断言：袋形态棕色+红色各 1 / 工具武器移池 / 防夹带倒出并清空内嵌），`testBatch61/62` 注入走私品 TNT→泥土、非价值杂物泥土→石块、袋断言改棕色+红色收纳袋，**439 PASS / 0 FAIL**（基线 436 + 3）

## 2026-10-14（第六十三批）

### 检查室扩建（5×7×3 装修）+ 红蓝地毯锁站位 + 检查结束回原位 + 超时自动视为受贿

- **检查室扩建**（`SmuggleManager.buildCabin` 重写）：内部 **5×7×3**（长5 × 宽7 × 高3，外壳含双墙 7×5×9）：
  - **石英墙壁**（四周含四角）+ **白桦木板**底板与天花板
  - 顶部中央**蛙明灯**（赭黄蛙明灯替换天花板中央一块，向室内照明）
  - 两侧（两条长墙内侧中央）各一盆**杜鹃花树苗盆栽**（POTTED_AZALEA）
  - 长方向两端各一块地毯（一红一蓝，**中间相隔 3 方块**）；26.2 无 Blocks.RED_CARPET 常量（CARPET 是 ColorCollection）→ 注册表直查（CivilBlocks 同款模式）
- **红蓝地毯站位**：进室传送——**被检查者站红地毯、检察官站蓝地毯**（cabinSpotA/B 即两地毯位）
- **进室后不能移动**：tick 每 tick 钳制双方回各自地毯站位（水平 >0.25 格或垂直 >0.25 格即拉回，保留视角）；检查结束回原位后自动恢复自由
- **检查结束双方回原位**：decide/超时收尾统一走 `finishInspection`（teleportBack 双方 from 位，原有行为保持并明确）
- **超时自动视为受贿**（原：超时双方放行无结算）：检察官 2 分钟内未定夺 → 系统自动执行受贿结算（`timeoutAutoAcceptBribe`：提示双方 + settleAcceptBribe + finishInspection）——贿赂袋清空给检察官加分、货物袋同步清空兑换成分、双方回原位
- **实现落点**：`SmuggleManager`（buildCabin 重写 + tick 超时分支改受贿 + 钳制段 + decide 拆分 settleAcceptBribe/settleInspect/finishInspection + releaseInspection 删除 + enterInspectionRoom public 化 + cabinXxxForTest 访问器）、`ProsecuteScreen`（「超时自动视为受贿」文案）、`LobbyManager`（玩法说明补检查室/锁站位/超时受贿/回原位）、`MainEventListener` 注释
- 自测：新增 `testBatch63Features`（6 断言：外壳尺寸/石英墙/白桦板/蛙明灯/盆栽/红蓝地毯相隔3格/进室各就位/tick 钳制拉回），`testBatch62Features` 断言 3 改为「超时自动视为受贿（含回原位）」，**436 PASS / 0 FAIL**（基线 428 + 8）

## 2026-10-14（第六十二批）

### 走私犯与检察官：移除收集末影箱与开箱机会，检查通行直接兑换货物袋

- **整体移除「收集末影箱 + 开箱机会」机制**（按用户指令，检查机制和方式全部保留）：
  - 删除收集末影箱（placeStash/isStashChest/tryOpenStash 与右键拦截）、开箱机会（tryConsumeOpenChance/openChanceOf/grantOpenChance 与开局/受贿发放）、末影箱禁用方块拦截（isForbiddenOpenBlock 及其 UseBlockCallback 段）
  - 拾取与合成恢复正常：删除 ItemEntityPickupMixin/ResultSlotPickupMixin（mixins.json 同步移除，现 20 项）——玩家可正常吸引掉落物、使用工作台/熔炉/箱子等全部原版功能方块
  - 删除 StashOpenPayload/StashDepositPayload 网络包与客户端 StashScreen（NetworkRegistry/ClientState 同步清理）
- **检查通行直接兑换货物袋**（`SmuggleManager.redeemValueBag`）：检查后若是**通行**（两种情形），被检查者**价值收纳袋直接清空**并按价值表逐件兑换成分（给自己加分）：
  1. **受贿放行**：检察官清空被检查者贿赂袋获得对应分数；被检查者货物袋同步清空兑换成分
  2. **检查无走私品放行**：检察官先按兑换前货品总值（背包价值物品+价值袋）赔偿 1/4，货物袋再全额清空兑换
- **设计决策**：
  - **超时放行不清袋不兑换**（用户仅列受贿/检查无走私品两情形为通行）
  - **全额兑换**：无「每件 64 上限/走私品每轮 1 件」限制（均为随末影箱一并删除的交易规则）；价值表按物品查表（清单外价值物品如钻石也按 8 分兑换，非价值杂物清空不加分）
  - **赔偿按兑换前货品总值 1/4 计**（含袋内货物），货物袋另全额兑换
  - 走私品在袋内必然走查获分支（hasSmuggleGood 检查背包+袋），兑换分支袋内必无走私品
  - 查获分支不变：没收全部货物（confiscateAllGoods 已清袋），贿赂袋不受影响
  - 保留规则：检查结束清除物品栏散装价值物品（货物须装入价值收纳袋）、换批刷新三件公开商品+走私品
- **实现落点**：`SmuggleManager`（删容器结算链+redeemValueBag+decide 两分支接入+reset 清理+类 javadoc）、`NetworkRegistry`/`ClientState`（payload 删除）、删除 `StashScreen`/`ItemEntityPickupMixin`/`ResultSlotPickupMixin`、`survival-contract.mixins.json`、`MainEventListener`（删收集段）、`ProsecuteScreen`/`LobbyManager`/`TeamChestManager`（文案更新）
- 自测：`testStowaway` 步骤 3/3.5 合并为袋容器存取检查、`testBatch61` 删开箱机会断言+新增受贿/无走私品两分支袋兑换断言、`testBatch62Features` 重写为 3 断言（拾取恢复/清单外价值物品按价值表兑换/超时放行不动袋），**428 PASS / 0 FAIL**（基线 434 − 6）

## 2026-10-14（第六十一批）

### 「偷渡客与检察官」改名「走私犯与检察官」+ 检查规则全面重做

- **改名**：玩法名「偷渡客与检察官」→「**走私犯与检察官**」（zh_cn/zh_tw/en_us 三语言文件 + 全部 Java 回退文案/注释/枚举注释同步）；道具「检察发起书」→「**检查发起书**」、「检察室」→「**检查室**」（简中玩家实际显示的是 Java 回退文本，必须改 Java 侧）
- **开局双收纳袋**（`SmuggleManager.initPlayerKit`）：每人获得两个不同的收纳袋——**§6价值收纳袋**（皮革，存货品，检查时可能被没收）与**§d贿赂收纳袋**（兔子皮，存贿赂物品，唯一不被没收的存放处）。袋内容物保存在服务端（27 格虚拟容器），袋本体只是钥匙道具，右键打开对应袋界面；死亡后缺失自动补发（`grantKitIfMissing` 挂重生事件），袋内物品不随死亡丢失
- **走私品**（仅我方可见，固定 128 分）：公开商品（三件 8/4/2 档）之外，每组还有一个走私品（走私品池 8 件与公开池完全不相交：TNT/黑曜石/末影珍珠/经验瓶/命名牌/鞍/望远镜/时钟，**全部固定 128 分**）；每轮（换批）刷新，**每人每轮最多交易 1 件**（放入收集箱即 +128 并消耗，超出的件数留箱不结算；跨轮重新可交易）；HUD 与团队模式只对我方下发走私品条目
- **开箱机会**：每次开启模式专属收集末影箱前都需要 1 次开箱机会（开启即消耗）；开局每人 1 次；同一时刻最多持有 1 次（上限不叠加）；被检查且检察官**受贿放行**后再获得 1 次。无机会时无法开启（原版个人末影箱不受限）
- **检查流程重做**（检查发起书不再有冷却时间）：
  1. 持检查发起书右键选一名**非友方**玩家作为自己的检察官（持书人=被检查者）→ **双方屏幕 3 秒倒计时**（3/2/1 大字）→ 倒计时结束传送进检查室
  2. 进室时**系统提示检察官对方的贿赂物品总价**（不再有总分提示区间/估价输入）；检察官 **2 分钟**内定夺，超时未定夺=双方放行无任何结算
  3. **受贿**：清空被检查者贿赂收纳袋 → 检察官获得对应分数；检查结束，**被检查者获得一次开箱机会**
  4. **不接受贿赂（进行检查）**：情况一，对方**没有走私品** → 检察官**赔偿对方货品 1/4 的分数**并放行（货品=背包价值物品+价值袋，贿赂袋不计价也不构成走私证据）；情况二，对方**有走私品** → **没收对方所有货物**（背包价值物品+价值收纳袋整体清空）给检察官加分，**贿赂袋不受影响**
  5. 同一时间只能进行一场检查（检查室互斥）；检查阶段双方禁丢弃物品
- **实现落点**：`SmuggleManager`（全量重写：双袋容器/走私品池与轮次/开箱机会/3 秒倒计时+2 分钟窗口/三分支结算/无冷却）、`NetworkRegistry`（ProsecuteStatePayload 决策/等待/发起三模式 + OpenBagPayload 开袋 + 决策动作受贿/检查）、客户端 `ProsecuteScreen` 全量重写（决策两按钮「受贿/进行检查」+ 等待模式 + 发起清单，删除估价输入框与冷却）、`ChallengeBookOpener`（书名判定+收纳袋右键开袋）、`LobbyManager`/`MainEventListener`（开局发袋+重生补发+改名）、`GameStateManager`/`ScCommands`/`TeamChestManager`/`ChallengeBookScreen`/`ServerPlayerDropMixin`（改名与文案）
- 自测：新增 `testBatch61Features`（24 断言：双袋发放/走私品 128 分与池不相交/每轮 1 件+跨轮重置/开箱机会门槛消耗上限/受贿分支/无走私品赔 1/4/有走私品没收且贿赂袋保留/无冷却二连发起/检查互斥），`testStowaway` 旧断言同步重写（删除估价容差/冷却/零分拒检，保留清单三档/64 上限/检查室禁丢弃/书死亡保护），**434 PASS / 0 FAIL**（基线 421 − 11 + 24）

## 2026-10-14（第六十批）

### 文明守护怪白天不自燃（僵尸马坐骑防火）

- **修复：文明模式下守护怪（僵尸马骑兵的坐骑等亡灵守护怪）白天被阳光点燃**。26.2 骑兵构成中僵尸马坐骑没有护甲槽（骑手僵尸戴铁头盔可吸热不受影响），白天必然自燃——守护怪被烧得火光冲天还持续掉血。双层修复（`CivilizationManager`）：
  1. **无限时长火焰免疫**：`spawnGuardian` 生成即挂 + `containMobs` 每 2 秒补挂（与抗性提升 2 / 迅捷 2 同款保鲜机制，防牛奶/效果清除后失效）——阳光与火焰伤害归零
  2. **每 tick 火焰抑制**（`guardianFireSweep`，挂在 `tick()` 每 tick 主循环）：发现守护怪（GUARDIAN_TAG）身上有火立即 `setRemainingFireTicks(0)` 扑灭——燃烧视觉也一并消除，白天完全看不到守护怪着火
- 适用范围：全部守护怪（钻石区僵尸马+骑手、合金区骆驼尸壳+尸壳骑手+焦骸），非守护怪行为不变
- 自测：新增 `testBatch60Features`（生成即挂无限火抗 / 点燃坐骑→火焰抑制立即扑灭 / 骆驼尸壳守护同款），**421 PASS / 0 FAIL**（基线 416 + 5）

## 2026-10-14（第五十九批）

### 神的谎言新增消耗道具「神谕纸符」+ 低血糖描述排查结论 + 60 分残留顺带修复

- **新增消耗道具「§6神谕纸符」（材质为纸，仅神的谎言模式发放/可用）**：
  - **获得**：首次完成一个 **A/S 级**专属任务后发放（B 级不发，重复完成不重复发放）；**每人每局最多获得一次、使用一次**（两套独立标志）
  - **平民使用（右键）**：立即消耗 → **武装 1 次神罚免疫**——挡住使者降罪（-55）与链接神罚；**不挡指认失败惩罚**
  - **使者使用（右键）**：服务端下发存活玩家清单 → 打开「神谕纸符 · 链接」选人界面（金色系配色，禁自链/须存活）→ 确认后才消耗并记录链接（**链接者不收到任何通知，保持隐蔽**）
  - **链接触发（使者首次被指认命中时，一次性）**：
    - **还有队友存活** → 使者与链接者**双双淘汰**（死亡≠神罚，已武装纸符免疫的平民照常淘汰）；先双双标记淘汰再统一判定胜负，避免中间态误判使者获胜
    - **仅剩自己** → 使者**完全不受影响**（不清零、不移除、身份不公开）；链接者**自动获得一次神罚（-55 分）**——已武装纸符的平民则被挡下 → **整体无事发生，仅消耗指认次数**（无任何公开广播， AccuseResult=NULLIFIED）
  - **平民道具优先级高**：最后一名使者链接了已用纸符的平民 → 被指认后无事发生，仅消耗指认次数
  - **死亡保护**：神谕纸符加入 modeBookOf 死亡保留（死亡不掉落、重生按原物品原名牌返还，与指认书/检察发起书同机制）
- **实现落点**：`GameStateManager`（纸符状态/发放/使用/链接/AccuseResult 三态指认/免疫挡降罪）、`TaskManager.checkCompletion`（A/S 首次发放）、`NetworkRegistry`（UseRelic/RelicLinkState/ConfirmRelicLink 三 payload + 指认 receiver 三态广播：NULLIFIED 无事发生不广播）、客户端 `ChallengeBookOpener`（右键纸符拦截）+ `RelicLinkScreen`（新链接选人界面）+ `ClientState`（payload 接收）
- **语言文件**：zh_cn/zh_tw/en_us 三文件新增 `msg.godslies.relic_*` / `link_*` 共 10 键 + `gui.relic.*` 3 键
- **顺带修复（第五十八批遗漏）**：简中客户端实际显示的 **Java 回退文案**残留 3 处「60 分」（`AccuseBookScreen` 类注释/降罪确认按钮回退、`ChallengeBookScreen` 使者说明行）——`ClientL10n.tr` 在 zh_cn 环境直接返回 Java 回退，语言文件的 55 分修正对简中玩家无效，故必须改 Java 侧
- **低血糖描述排查结论：无需改动**。全仓已一致（第五十二批完成）：食物白名单 `HYPOGLYCEMIA_ALLOWED_FOOD` 含**苹果/甜浆果/发光浆果**、Java 描述（ChallengeManager）、zh_tw/en_us 语言文件、游戏内阻止提示、自测断言（苹果/浆果放行 + 肉类拦截）共 5 处证据；zh_cn 客户端显示 Java 回退文案（即含苹果浆果的新文案）。若游戏内仍见旧描述，请确认 mods 目录加载的是本次构建的 `survival-contract-1.0.0.jar`
- 自测：新增 `testGodsLiesRelic`（三局假人测试 17 断言：发放规则/使者链接消耗/平民武装一次性/多使者双亡含已武装平民照死/最后使者链接已武装平民无事发生/免疫挡降罪/纸符死亡保留/链接未武装平民 -55），**416 PASS / 0 FAIL**（基线 399 + 17）；本批 build 部署与第五十八批合并执行

## 2026-10-14（第五十八批）

### 文本对齐修正（乐师/神的谎言）+ 沙漠预设地表无沙修复

- **乐师攻击技能描述更正**（`ProfessionManager`）：音波攻击描述「半径32格内除自身外的所有生物」→「**半径16格内除自身与队友外**的所有生物」——第五十四批实现已改为 16 格半径 + 队友免伤，描述未同步
- **神的谎言分数描述更正**（三个语言文件 zh_cn/zh_tw/en_us 共 12 处）：玩家可见文本仍写「扣 60 分」，实现已是扣 **55** 分（第五十四批 60→55）——全部更正的键：`gui.accuse.confirm_deduct`（降罪确认按钮）、`msg.godslies.intro`（开局简介）、`msg.godslies.envoy_you`（使者身份私发）、`msg.godslies.task_reward_deduct`（降罪权提示）
- **修复：文明时代自定义地形「预设-沙漠」开局后地表没有沙子**（`ArenaWorldManager.terrainStem`）。根因：超平坦层序列是**自底向上**填充（`FlatLayerInfo.startHeight` 按列表顺序从 y=0 累加，列表末层 = 顶面方块；原版超平坦预设 `bedrock,dirt,grass` 即此顺序），而沙漠预设按「自顶向下」书写 `[基岩,沙,砂岩,石头]`——实际生成顶面是石头（y=102），沙子被埋在 y=1~4。现改为自底向上 `[基岩→石头→砂岩→顶面4格沙]`；**同病同修**：雪之王国（原顶面是石头）改为顶面 1 格雪块、挖掘工的梦想（原草方块被埋在 y=1）改为顶面草方块 + 深层绿宝石/钻石 → 浅层煤的正规矿层序
- **顺带修复：会话恢复时地形预设被重置**（`SessionPersistence.restore`）：`clearRuntimeState()`（会把预设重置回主世界）原先在快照预设恢复**之后**调用，恢复出的沙漠/雪原/矿工预设立即被清掉。现改为先清运行态、再恢复快照预设、最后重建竞技场维度
- 自测：新增雪之王国「顶层雪块」、沙漠「层构成（基岩→石头→砂岩→顶面沙）」断言，挖掘工梦想矿层断言并入「顶层草方块」；**399 PASS / 0 FAIL**（基线 397 + 2）

## 2026-10-14（第五十七批）

### 守护者构成修订 + 占领永久锁死修复 + 作弊加分指令

- **守护者构成按需求修订**（`CivilizationManager.spawnGuardian`）：
  1. **僵尸骑兵（钻石区）**：骑僵尸马 + 骑手手持**铁长矛**（`minecraft:iron_spear`，26.2 长矛注册名即 spear），铁头盔保留
  2. **骆驼尸壳（合金区）**：背上**固定两个怪物**——手持**金长矛**（`minecraft:golden_spear`）的尸壳骑手 + **焦骸**（Parched）第二乘客；生成时清除原版 finalizeSpawn 的随机骑手，构成完全确定
- **守护者双增益**：全员自带**无限时长**抗性提升 2 + **迅捷 2**；`containMobs` 每 2 秒补挂一次（防牛奶/机制清除后失效）
- **守护者禁足区块**：守护怪仍登记 managedMobs，越出守护区块即拉回区块中心地表（含玩家骑乘守护马出界场景）
- **修复：守护者已被击败但仍无法占领区块**。根因：`guardianAlive` 把"实体检索不到"保守视为"仍在守护"（仅"检索到且已死"才解除），而 `containMobs` 的死亡清理在**区块恰好处于未加载瞬间**会跳过 `removeGuardianId`（旧条件 `ent != null || chunkLoaded`）——实体从此永远查不到、登记永不清理，区块被永久锁死。现改为：**区块已加载 → 实体检索不到即视为已彻底消失，立即清理登记解除封锁**；区块未加载 → 保留登记待重载后处置（且不再把守护实体从 managedMobs 丢掉）
- **新增作弊指令 `/sc addscore <player> <score>`**（`ScCommands`，需 OP 管理员权限）：给指定玩家加任意分数（≥1），全员广播加分消息并立即刷新计分板；**常规模式下无效**——拒绝执行、分数不变，仅特殊玩法（神的谎言/偷渡客与检察官/文明时代）生效
- 自测：新增 `testBatch57Features`（铁长矛/金长矛+焦骸构成断言、双无限增益、越界拉回、实体消失后登记自愈、陈旧 UUID 不再锁死、加分指令常规拒绝/文明生效），第五十三批骆驼断言同步修订（2→3 实体）

## 2026-10-14（第五十六批）

### 中途进入自动观战（自愈式）+ 回大厅全员自动解除观战

- **修复：游戏进行中其他玩家进不来（只能等回大厅）**。根因有三，全部修复（`MainEventListener`）：
  1. **废弃实例传送**：JOIN 与首次 respawn 事件可能为同一玩家放入两个 `ServerPlayer` 实例，旧队列直接对快照实例传送——传送落在已被原版替换的废弃实体上，真实玩家永远停在原版出生点。现改为按 UUID 从玩家列表解析**当前在线实例**再处理
  2. **传送竞态无重试**：中途观战队列为一次性处理且无异常保护，传送被登录竞态/异常打断后玩家永久卡死。现单玩家 try-catch（失败不阻断队列）
  3. **观战标记后置**：`teleportSpectatorToArena` 原先在传送**之后**才登记 `MID_GAME_SPECTATORS`，传送失败即彻底丢失"观战者"身份。现改为**先登记再传送**
- **新增：观战者位置自愈**（旁观者保持循环，直接对治"进不来"）：已登记的观战者若不在竞技场维度（加入竞态等任何原因），冷却 2 秒后自动重新传送至竞技场观战——"中途进入自动切换至观战状态"永远可达
- **新增：回大厅全员自动解除观战**（`performResetForLobby` + `GameStateManager.clearLobbySpectators`）：游戏结束回大厅时清空全部大厅观战标记（含离线观战者，旧实现标记残留会导致下一局被错误排除出参战池），全员恢复冒险模式；下一局想观战需重新输入 `/sc spectate`
- 自测新增 8 项断言（中途加入注入旁观队列 → 旁观者模式/竞技场维度/观战追踪 → 人为移出竞技场模拟传送丢失 → 自愈拉回 → 反射调用回大厅重置 → 大厅观战/中途观战（含离线）标记全清 + 全员冒险模式），假人清理覆盖 FakeLate

## 2026-10-13（第五十五批）

### 文明时代资源区数量修订：沙 8 / 铜 34 / 铁 24 / 金 20（配额精确放置）

- **空白区资源区改为配额精确数量**（图1 修订）：沙区 **32 → 8**、铜区 **22 → 34**、铁区 **16 → 24**、金区 **16 → 20**——每局开局这四类资源区数量**严格精确**（不再是加权随机期望值）
- **实现：空白区两阶段填充**（`CivilizationManager.setup`）：
  - 阶段1：洗牌空白区块 + 洗牌配额序列（沙8/铜34/铁24/金20）后精确放置——数量保证、位置随机
  - 阶段2：剩余空白（136 − 86 = **50 格**）按图2 权重随机（石英24/青金石16/野生动物16），仍受 `zoneLimits` 数量上限约束（上限重抽 + 兜底野生动物区逻辑不变）
- **配置上限仍可压缩配额**：`zoneLimits.limits` 若对沙/铜/铁/金设置了上限，则放置 `min(配额, 上限)`，被压缩的余量回落到阶段2 随机池；默认配置（仅 ALLOY:5 / DIAMOND:20）不影响四类配额
- 木头/石头/钻石/合金区仍由图1 布局表固定位置（HQ2/木16/石54/钻14/合金3 不变）
- 自测改写 3 项断言（配额表 8/34/24/20、剩余随机池 56 项权重、开局实际分布含四类精确数量 + 石英/青金石/野生合计 50 格），基线 364 → **366 项全部通过**

## 2026-10-13（第五十四批）

### 神的谎言数值调整 + 单人三种疾病上限 + 乐师音波 16 格/友伤关闭 + 文明模式全局队内友伤关闭

- **神的谎言：初始总分 120 → 100**：大厅开局随机规则分配目标分改为 100（`LobbyManager` 规则预算、开局计分注释同步）；随机分配（子集和 DP + 随机回溯）仍保证总分**恰好** 100
- **神的谎言：使者神罚 60 → 55**：使者完成任务获得降罪权后，指定一名玩家扣 **55 分**（`GameStateManager.envoyDeduct`；100 → 45，未淘汰）
- **单人最多同时三种疾病**（双向限制）：
  - **随机分配**：神的谎言开局随机规则回溯时疾病规则最多选 3 条（`ChallengeManager.randomRuleSetForScore` 按板块计数，DP 保证剩余仍可达目标分）
  - **手动开启**：挑战书开关入口（`NetworkRegistry.applyChallengeToggle`）开启疾病规则前校验——已满 3 种则拒绝并提示「疾病已满：单个玩家身上最多同时存在三种疾病！」；关闭一种后即可再开新疾病（开启者规则集即受影响集合：团队模式同队共享、偷渡客全员共享，语义一致）
- **乐师音波攻击范围 32 → 16 格**：底鼓/颤音琴/小号（含氧化变体）的音波攻击判定盒与距离均改为**半径 16 格**（原 32 格）
- **乐师音波友伤关闭**：音波目标筛选排除同队队友——团队模式下乐师的队友不再被自家音波误伤（魔法伤害无来源实体，原版友伤机制管不到，故在实体选择谓词中排除；自身与敌方照常）
- **文明模式全局队内友伤关闭**：`TeamManager.applyTeamColors` 按当前模式设置原版记分板队伍友伤——文明模式 `survival_red`/`survival_blue` 均 `setAllowFriendlyFire(false)`（原版 `Player.hurt` 链路拦截同队玩家的一切伤害，含箭矢；跨队伍不受影响），其他模式保持允许；文明开局流程（`LobbyManager`）显式再设置一次。每次重建队伍按当前模式重置，防跨局遗留
- 自测数值断言改写（神的谎言总分 120→100、神罚剩余分 60→45）+ 新增 12 项断言（随机分配 50 次抽样总分恰好 100 + 疾病 ≤3、手动开三病成功/第四病被拒/关一病可再开、文明模式记分板队伍 ff=false、同队 canHarmPlayer=false/跨队=true、乐师音波 17 格外无伤 + 队友免伤、半径内敌人受 2 点真实伤害、队友二次确认免伤），基线 352 → **364 项全部通过**

## 2026-10-13（第五十三批）

### 文明时代：图1固定布局 + 钻石/合金区原版守护骑兵（击败才能占领）+ 图2产出/设施全面改版 + 铜箱子家族互认

- **资源区改为图1固定布局**：15×15 区块疆域按固定坐标表生成（`CivilizationManager.FIXED_LAYOUT`）——两队大本营固定在 (1,1)/(13,13)；木头区 = 两家各 3×3 去家角共 **16**；石头区共 **54**（两家外侧各 16 + 中心十字带 22，含被钻石/合金覆盖的排布）；**钻石区固定 14 格**（含中心 2×2×2 组与两角三角）；**合金区固定 3 格**（(0,14)/(7,7)/(14,0) 两对角 + 正中心）；其余 **136 个空白区块按图2权重加权随机**填充资源区（沙 32/石英 24/铜 22/铁 16/金 16/青金石 16/野生动物 16，共 142 权重）——随机池**不再出现木头/石头/钻石/合金区**。原 zoneLimits 数量上限保留，但仅作用于随机池类型（钻石/合金已由固定布局定死）
- **钻石区/合金区守护怪物**（遵循原版生成方式，自带**无限抗性提升 2**）：
  - 钻石区 = **僵尸马骑兵**：原版僵尸马（setTamed）+ 骑乘的僵尸，骑手**戴铁头盔**（Mounts of Mayhem 构成）
  - 合金区 = **骆驼尸壳骑兵**：原版骆驼尸壳（finalizeSpawn 自带尸壳骑手，缺失时手动补骑）
  - 全体登记 managedMobs **禁跨区块**（越界拉回区块中心地表）、persistent 不消失；骑手与坐骑一并登记管控
  - **守护未清空时该区块拒绝放置占领羊毛**（提示「先击败守护怪物」）；守护全部死亡后解除封锁可正常占领。保守判定：区块已加载但实体消失也解锁，防永久锁区
- **图2 产出/设施全面改版**（配置版本 v2 → **v3**，旧配置文件自动备份 `.bak` 并重新生成默认）：
  - **木头区**：主产物改为 **oak_wood（六面树皮木块，不是原木！）×6 / 60 秒**；苹果 30% + 随机蔬果 10%（西瓜片/南瓜/胡萝卜/小麦/马铃薯）；无前置
  - **石头区**：圆石 ×10 / 60 秒；煤 20% + 沙砾 15%；前置 = 任意 `*_wood` 木头（各树种皮木块，**原木不算**）
  - **石英区**：前置仅熔炉；地狱疣 8%（**删除烈焰棒**）；岩浆怪刷怪保留
  - **铜区**：熔炉 + 煤炭块 → 粗铜 ×12
  - **铁区**：熔炉 + 铜块 + 铜箱子 → 粗铁 ×8
  - **金区**：高炉 + 铁块 + 铜箱子 → 粗金 ×10
  - **钻石区**：高炉 + 金块 + 铜箱子 → 钻石 ×5 + 黑曜石 10%（单区累计 ≤30）
  - **合金区**：高炉 + 钻石块 + 铜箱子 → **下界合金碎片 ×10** + 烈焰棒 8% + **下界合金升级锻造模板 4%（单区累计 ≤3）**；25% 刷凋灵骷髅
  - **沙区**：前置改为**末影箱**（末影箱仅作前置，产出照常进区内箱子）→ 沙子 ×8 / 20 秒；火药 5% + 甘蔗 10% + **玻璃 15%**
  - **青金石区**：熔炉（**必须 Y≤0 层**）+ 末影箱 → 青金石 ×10 + 红石粉 30% + 经验瓶 15%
- **铜箱子家族互认**：铁/金/钻石/合金区前置「铜箱子」匹配**全部 8 个变体**（4 档氧化 × 涂蜡与否，`WeatheringCopperCollection.asList()`）——任意氧化阶段、是否涂蜡均有效，氧化不会中断收获；铜箱子本身也是容器（产出可进）
- 自测新增/改写 40 项断言（图1布局表数量/合金三格、随机池 142 权重组成与排除项、开局实际分布 HQ2/WOOD16/STONE54/DIAMOND14/ALLOY3+随机仅 7 类、17 个钻/合区块守护全覆盖、僵尸马骑兵/骆驼尸壳骑兵实体构成与抗性 2、守护拦截文案与击败解除、铜箱子家族互认/原木不算/`*_wood` 算、图2 全区配置、青金石熔炉 Y≤0 规则、配置 v3 迁移与上限作用于随机池），基线 312 → **352 项全部通过**

## 2026-10-13（第五十二批）

### 文明模式完善：自然刷怪 1/4 + 基地盔甲架 4 秒限伤 + HQ 区块无限回血 + 愚者禁用 + 工匠附魔/箭矢弹开修复 + 低血糖白名单扩展

- **文明模式自然刷怪降至四分之一**：文明模式（CIVILIZATION）下原版自然刷怪每次尝试仅 25% 概率放行（`CivilizationManager.naturalSpawnAllowed`，由 `NaturalSpawnerMixin` 挂接刷怪判定生效）；非文明模式刷怪完全不受影响
- **基地盔甲架每 4 秒最多受击一次**：我方大本营核心盔甲架受击后进入 80 tick（4 秒）冷却，冷却内的攻击不再扣血（也不触发判负），冷却结束后恢复正常受击
- **我方玩家在己方基地区块获得无限生命回复1**：己方玩家位于本队大本营所属区块期间，持续获得**无限时长**生命回复 I（amplifier 0，进入即给、每 tick 续期）；离开区块后效果立即消失。 CivilizationManager 每 tick 扫描各队 HQ 区块内成员完成授予/移除
- **愚者职业文明模式禁用**：文明模式下愚者（JESTER）不可选择——服务端拒绝选择、客户端职业界面按钮置灰不可点击、开局兜底（已持愚者者自动回落为无职业）；其他职业选择不受影响
- **修复工匠合成物附魔不容易触发**：旧实现挂 `ResultSlot.onTake` 掷附魔——26.2 中 shift-click 取出且与背包同类物品合并时，onTake 拿到的是已被 `setCount(0)` 的空壳实例，附魔全部丢失。改为新增 `CraftingResultMixin` 注入 `CraftingMenu.slotChangedCraftingGrid` 尾部，直接对装配结果本体（resultContainer 0 号位）掷骰附魔（耐久2 50%、经验修补 10%、工具效率4 10%、武器锋利3 10%，可叠加）——工作台 3×3 与背包 2×2 全覆盖，结果预览即显示附魔，落入空槽或合并进背包附魔都随物品走
- **修复弓箭命中目标后箭矢弹开可捡回**：弓手特权/嗜痛人/受到伤害增加等规则以「取消原始伤害 + 补伤」实现，会使箭矢/三叉戟 `onHitEntity` 内的 `hurtOrSimulate` 返回 false → 原版按未命中处理（箭矢弹开、可捡回、无命中效果）。新增 `ArrowHurtRedirectMixin`/`TridentHurtRedirectMixin` 重定向该调用：目标刚被规则补伤（REDEALT_TARGET 标记）时改判为命中成功 → 箭矢正常消耗、伤害按加成后生效
- **低血糖规则白名单扩展**：低血糖规则下还可以吃**苹果、甜浆果、发光浆果**（连同原有南瓜派/曲奇饼/蛋糕/烤马铃薯/面包），规则描述文案同步更新；吃其他食物仍被阻止
- 自测新增 21 项断言（刷怪 1/4 阈值与非文明模式不受影响、HQ 区块无限回血授予与离开消失、盔甲架引用/首击扣血/冷却内无效/冷却后再击、愚者禁用与其他职业可选、工匠概率附魔 20 次重掷/shift-click 保留/无职业不附魔、⑥A 实体层直伤/⑥B 弓手补伤链/箭矢命中消耗、低血糖白名单扩展与仍拦截肉类），基线 291 → **312 项全部通过**

## 2026-10-13（第五十一批）

### 文明时代：羊毛放置消耗 + 基地商人替换中央商人 + 交易配置文件 + 傀儡不索敌猪灵

- **羊毛放置改为正常消耗**：取消第四十八批「占领羊毛放置不消耗（自动返还）」设置——放置羊毛由原版正常扣物品，不再返还；对应地，**开局每名玩家发放 8 个本队颜色羊毛**（原为 64 个）+ 1 个箱子（发放逻辑提取为 `LobbyManager.giveCivStartKit`）
- **中央商人替换为基地商人**：删除第四十九批中央区块的「文明商人」，改为**红队/蓝队大本营中心区块地表各生成一个基地商人**（名称「红队/蓝队基地商人」常显）——不能行动（NoAI）、不消失（persistent）、**永久抗性提升 5**（amplifier=4 无限时长，与回收猪灵同款伤害免疫），站于地表不悬空（位置偏移避开中心玻璃基座与猪灵物资桶）
- **基地商人商品栏第一位 = 本队颜色羊毛（积分兑换动态价）**：初始 **80 积分**，每购买一个上涨 **4 积分**，**上限 320 积分**封顶（红蓝两队**各自独立计价**）；购买后 0 号格价格标注自动刷新，打开中的界面由容器同步更新。后方商品位保留随机 5 条商品结构（默认 2 随机附魔书 300 分 + 2 随机武器装备 500 分 + 1 随机药水 300 分，每局掷定、红蓝一致），兑换扣个人积分、积分不足拒绝
- **新增独立交易配置文件** `config/survival-contract/civilization-trade.json`（每局开始时读取，缺失自动生成默认）：
  - `merchant.woolInitialCost / woolCostStep / woolMaxCost`：羊毛初始积分 / 每买上涨 / 上限（默认 80 / 4 / 320）
  - `merchant.goods`：基地商人货物列表——`type`（随机附魔书 / 随机武器装备 / 随机药水 / 具体物品 id）、`count`（单次兑换数量）、`cost`（兑换积分）
  - `recyclePrices`：**猪灵回收价目表**（物品 id → 每个兑换的积分，支持小数；未列入不回收）——原硬编码 9 种原木 1.0、木炭 2.0、马铃薯 0.5、石类 1.0、煤 3.0、粗铜 1.5、铁/金锭 3.0、钻石/石英块/红石块 10.0、青金石/绿宝石 2.0、下界合金锭 20.0 迁入配置，默认值不变
- **玩家造的傀儡不索敌回收猪灵**：新增 `MobSetTargetMixin`（注入 `Mob.setTarget` 头部）——目标为大本营回收猪灵时拦截索敌（覆盖铁傀儡/雪傀儡及一切 Mob 的全部索敌路径，猪灵表为空时自动放行）；对其他目标索敌不受影响
- 自测重写商人断言并新增 33 项（开局 8 羊毛、双队基地商人生成/NoAI/抗性 5/立于地表、羊毛初始 80 价、积分不足拒绝、动态 +4 涨价、320 封顶后不再上涨、兑换扣分/发放、商品构成/价格/红蓝一致/本局固定、交易配置默认值/自定义/还原、猪灵已生成/傀儡不索敌/普通索敌不受影响），基线 258 → **291 项全部通过**

## 2026-10-12（第五十批）

### 文明时代：羊毛挖掘高亮 + 大本营受击警报

- **羊毛被破坏过程中保持高亮**：任意玩家（不分敌我）正在挖掘占领羊毛时，该羊毛实时**发光高亮**（同款方块状态的发光 BlockDisplay 覆盖其上，微下沉防面重合闪烁）——红队羊毛高亮红色轮廓、蓝队蓝色（独立 scoreboard 发光队 `sc_wool_glow_red/blue` 仅含高亮实体）。持续挖掘保持高亮（不重复创建），挖掘中断后短暂保留约 0.5 秒防抖再消失，羊毛被挖掉（区块回归无主）时高亮立即移除。实现：`ServerPlayerGameModeAccessor`（isDestroyingBlock/destroyPos）+ 每 2 tick 扫描挖掘状态；游戏结束/新局自动清理高亮实体与 scoreboard 队伍
- **大本营受击警报**：敌方玩家攻击我方大本营核心盔甲架（原 onHqStandAttacked 扣血路径）时，**我方全部在线成员屏幕正中央**弹出醒目警报「§4⚠ 红队/蓝队大本营正遭受攻击！⚠」（title 立即出现、停留 2 秒、淡出）——每队每 **5 秒**最多触发一次（按队伍独立冷却），连续围攻不会刷屏；原有的受击粒子/音效/全员聊天播报/扣血判负逻辑不变
- 自测新增 8 项断言（高亮点亮/队伍上色/持续保持/中断短暂保留/超时移除/挖掉立即移除/警报触发+5 秒冷却/冷却结束再触发），基线 250 → **258 项全部通过**

## 2026-10-12（第四十九批）

### 文明时代：大本营资源环 + 概率调整 + 中央商人 + 自定义地形

- **大本营周围八区块变初始资源区**：两队大本营 3×3 中，中心保持大本营（玻璃基座/信标/回收猪灵不变），周围 8 区块改为**木头/石头资源区**（随机各半，归属本队）——**占领羊毛照常放置/登记**（可被破坏抢夺），仅悬浮图标不再显示羊毛而显示资源类型（原木/石头），产出与普通资源区一致（木/石区无前置，开局即产）
- **概率调整**（配置默认值，旧配置文件自动迁移——版本号低于 2 时备份为 `.bak` 并重新生成默认，自定义参数可从备份抄回）：
  - 木头区：马铃薯 25%→**30%**；随机农作物 3%→**10%**
  - 石头区：煤炭 1.5%→**20%**
  - 青金石区：经验瓶 1%→**15%**
  - 钻石区：黑曜石 0.5%→**10%**，且新增 `totalCap`（单区块累计上限）——**每个钻石区最多产出 30 个黑曜石**，产满后不再产出（bonuses 每项支持 `totalCap`，0 = 无上限）
  - 石英区：地狱疣 1%→**8%**；新增**烈焰棒 6%** 副产品
  - 大本营战斗物资：改为**基础 5 分钟/组**，每占领 5 个区块间隔 **-15 秒**，最低 **1 分钟/组**（替换旧"每占领 +10% 产量、上限 5 组"机制；配置字段 claimsPerStep/reducePerStepSeconds/minSeconds）
- **中央文明商人**：中央区块地表生成固定商人村民（名称「文明商人」常显）——NoAI 不移动、persistent 不消失、永久抗性提升 5（与回收猪灵同款，amplifier=4 无限时长的伤害免疫）。右键打开 9×1 兑换界面：**2 条随机附魔书（300 分）+ 2 条随机武器装备（500 分）+ 1 条随机药水（300 分）**，每局开始随机固定；点击商品格 = 兑换（服务端拦截点击，扣**个人积分**后发放无标注本体，背包满掉落脚边）；积分不足拒绝并提示；同一商品可重复购买；价格可配置（`merchant.bookCost/gearCost/potionCost`）。武器装备按类别附魔（盔甲→保护、弓弩→力量、剑斧镐→锋利、盾牌→耐久），附魔书从 16 种常见附魔随机（等级 1-3）
- **自定义地形（文明二级菜单「双禁」→「自定义地形」）**：文明时代挑战书第二行的双禁按钮替换为「自定义地形」，点开进入与职业栏同款的二级菜单，可选 **预设-主世界（默认）/ 预设-挖掘工的梦想 / 预设-雪之王国 / 预设-沙漠**；四个预设均为无结构超平坦变体（世界规则/边界/维度类型完全通用），开局建竞技场时按预设生成：挖掘工的梦想 = 厚矿层（煤/铁/铜/金/红石/青金石/钻石/绿宝石直接裸露成层），雪之王国 = 雪原群系雪块表层，沙漠 = 沙漠群系沙/砂岩表层；选择经 LobbyState 同步全员，会话持久化保存/恢复
- 自测新增 31 项断言（大本营周围资源区 16 区块归属+羊毛照常放置、概率默认 6 组、大本营间隔配置与公式、黑曜石单区上限实测（100%×10 触发 5 次恰好 30 个）、商人实体/抗性/商品构成/价格/兑换扣分/积分不足拒绝/商品固定、地形预设群系与矿层 4 组+状态存取），基线 219 → **250 项全部通过**

## 2026-10-12（第四十八批）

### 文明时代：沙区 + 资源区数量上限 + 前置方块可配 + 初始占领区改角

- **新增沙区**：每 20 秒产出 8 个沙子；5% 概率额外产出 1 个火药；10% 概率额外产出 1 个甘蔗。**收获前置：铜箱子**——放置后才产出（`copper_chest`，氧化/涂蜡变体同样有效：匹配按 `CopperChestBlock` 家族判定，铜箱子自然氧化不会中断收获）
- **资源区数量上限**：全图（15×15 区块）默认合金区 ≤5、钻石区 ≤20，其余无限制；分配时随机到受限且已满的类型会自动重抽（32 次仍全部受限则兜底木头区），合金区照旧不落疆域边界
- **配置文件全部可自定义**（`config/survival-contract/civilization-production.json`，修改后下一局生效）：
  - 沙区产出参数与其他资源区一致：`intervalSeconds` / `output` / `count` / `bonuses`（含 `count` 单次数量）
  - 每个资源区新增 `requireBlocks`：收获前置方块 id 列表（如 `copper_chest`；缺省用各区默认：铜=熔炉、铁=熔炉+煤炭块、金=高炉+煤炭块、钻石=高炉、石英=熔炉、青金石=高炉（Y≤0）、合金=高炉+钻石块、沙=铜箱子、木/石=无前置；无效方块 id 丢弃并告警回退）
  - 新增 `zoneLimits.limits`：键 = 资源区类型（含 SAND），值 = 全图最多数量（缺省或 0 = 无限制；键缺省时回退默认表同名值）
- **两队初始占领区改角**：由南北两侧中间改为**红队西北角（x,z = -6,-6 附近）、蓝队东南角（x,z = +6,+6 附近）**，对角布局拉开双方开局距离
- 自测新增 14 项断言（沙区默认产出/副产物/前置校验、上限默认值、setup 分配统计含沙区存在与合金/钻石不超上限、配置自定义沙区产出+副产物+前置、铜区自定义前置、钻石区前置缺省回退、上限自定义并重新分配验证、大本营坐标改角后蓝队领地动作坐标修正），基线 205 → **219 项全部通过**

## 2026-10-12（第四十七批）

### 偷渡客与检察官 / 神的谎言 5 项修改

- **道具书死亡不掉落（检察发起书 + 指认书）**：`ServerPlayerDropMixin` 死亡分支新增道具书拦截——死亡整包掉落时，当前模式的道具书（偷渡客与检察官 = 检察发起书；神的谎言 = 指认书，按自定义名匹配）被取消掉落并暂存数量与原名（`GameStateManager.handleModeBookDeathDrop`），重生时经 `AFTER_RESPAWN` 按原名返还背包（背包满则掉在重生点脚边）；普通物品与异模式道具书照常掉落；keepInventory 开启时不走掉落路径、不受影响；暂存返还后清零、无复制，新一局自动清空
- **检查互斥（检察室使用中禁止新发起）**：`SmuggleManager.initiate` 头部新增检查——只要存在进行中的检察（已有两人待检查），任何人的新发起一律拒绝并提示「检察室使用中」，尤其不能对检察室内的检察官/被检察者发起；被拒不进入冷却；检察结束（结算/超时）后立即恢复可发起
- **检察失败赔偿仅限己方当前批次**：`payCompensation` 改为仅从检察方己方队伍「当前批次」价值物品中按价值凑赔偿额（大价值优先、按需拆组，不足则该批全部上交）——非当前批次的价值物品（如旧批次的绿宝石）一律不动用
- **神的谎言：指认失败不再显示指认了谁**：失败广播改为单参数消息「§c「%s」指认失败！分数清零！」（zh_cn/zh_tw/en_us 三语同步），不再泄露指认者指认的目标；指认成功广播不变
- 自测新增 12 项断言（检察发起书死亡拦截/异模式书不保护/普通物品放行/重生返还/无复制 5 项，指认书拦截/普通物品放行/重生返还/无复制 4 项，检查互斥拒绝+解除 2 项，赔偿批次外物品不动用 1 项），基线 193 → **205 项全部通过**

## 2026-10-12（第四十六批）

### 文明时代：开局箱子 + 死亡羊毛保留 + 岩浆怪修复 + 产出配置增强

- **开局获得箱子**：文明时代开局发放占领羊毛时每名玩家追加 1 个箱子物品（背包满则掉落），用于收纳资源区产出
- **死亡时本队颜色羊毛必定不掉落**：玩家死亡整包掉落走 `Inventory.dropAll → ServerPlayer#drop`——`ServerPlayerDropMixin` 新增死亡分支：文明时代对局内死亡时，本队颜色的占领羊毛被拦截暂存数量（`handleDeathDrop`），重生时返还背包（`onRespawn`，背包满则掉在重生点脚边并提示）；异色羊毛与普通物品照常掉落；keepInventory 开启时不走掉落路径、不受影响；暂存数量返还后清零、无复制。（根因注：26.2 `ServerPlayer.die` 完全覆写不调 super，Fabric `AFTER_DEATH` 对玩家不触发，只能拦截丢弃路径）
- **石英区小型岩浆怪血量异常高且无掉落（双根因修复）**：
  - 血量根因：26.2 `EntityType.create` 不调用 `finalizeSpawn`——直接刷出的岩浆怪保持 Monster 注册属性（血量 20），cube 类的 `setSize` 小型属性未应用（size=1 应为 1 点血）。修复：`prepareSpawn` 统一生成后初始化——cube 类固定 `setSize(1, true)`（完整应用小型血量/攻击/护甲/速度），其余 Mob 调用 `finalizeSpawn`（凋灵骷髅获得默认装备、村民获得职业，全部 `spawnMob` 生成怪物一并覆盖检查）
  - 掉落根因：26.2 原版 `magma_cube` 战利品表的岩浆奶油条目带 `size≥2` 条件——小型岩浆怪按原版表必空手。修复：新增自定义战利品表 `survival-contract:civilization/zone_mob`（岩浆奶油 0-1 + 抢夺加成），小型岩浆怪生成时经 `MobAccessor`（Mob 无公开 setter）覆盖战利品表，死亡掉落结算后自动还原
- **产出配置增强**（`config/survival-contract/civilization-production.json`，修改后下一局生效，缺字段自动回退默认）：
  - 每个资源区新增：`outputChancePercent`（主产物本次产出概率）、`spawnIntervalSeconds`（刷怪间隔，0 = 跟随产出间隔，与产出节拍解耦独立计时）、`spawnMaxHealth`（刷出生物最大生命值，0 = 原版默认）、`spawnCap`（该区块本系统生成生物上限，0 = 默认 12）；`bonuses` 每项支持 `count`（单次命中产出数量）
  - 大本营战斗物资：`chancePercent`（结算概率）+ `items`（每组产出物品池，替代固定风弹+金苹果）
  - 大本营物资桶：`rareItems`（稀有物资候选池，替代固定鞘翅/重锤）
  - 顺带修复：配置解析 `zoneIntervalTicks` 存的是秒未乘 20——产出间隔被缩短 20 倍（如木头区 60 秒变 3 秒），已改为秒→tick 换算正确
- 自测新增 20 项断言（产出配置自定义字段 9 项、死亡羊毛拦截/放行/返还/无复制 5 项、岩浆怪生成参数与战利品覆盖 6 项，含配置覆盖血量路径），基线 170 → **193 项全部通过**

## 2026-10-11（第四十五批）

### 观战/偷渡检察/神的谎言 7 项修复
- **观战者开局失效（保持生存模式 + 名单仍包含观战者）——根因**：`/sc spectate` 进入观战时曾以 `setScore(id, 0)` 把观战者写进分数表（大厅计分板显示用），导致 `hasPlayerState` 误判其为参战者——开局 `teleportSpectatorToArena` 的防御分支把它当作"掉线重连的参战者"送回重生点（强制 SURVIVAL），对局内 PLAYING tick 的"误锁救援"再强制一次。修复：`setupNewRandomWorld` 计分前 `lobbySpectators.forEach(scores::remove)` 清出分数表；并**删除对局内"误锁救援"**（解除游戏过程中不能切观察者的限制，仅保留被追踪的强制旁观者）；同时挑战书名单两条收集路径均过滤 `isSpectator()`，观战者不再出现在书中名单
- **偷渡客与检察官：每批最多 1 个 8 分物品**：`refreshBatch` 抽取时若已出现 8 分档，后续 8 分档降级为 4/2 档随机，杜绝单批多件 8 分物品拉爆估价
- **检察估价区间不再总含正中间**：提示区间改为随机偏移——偏移量 r ∈ 1..maxR（`maxR = min(19, max(1, actual))`，保证 r ≤ actual 无钳制失真）且 r ≠ 10，实际总分必不落在 [low, low+20] 正中间
- **总分口径修正（仅计被检察方批次物品）**：新增 `batchTotalValue`——估价提示与成功判定（|估价−实际|≤2 没收）均只统计被检察方当前批次三件价值物品，不再把两队的价值物品都算进总分
- **检查室完全禁丢弃**：Fabric 0.161.0+26.2 已移除 `ItemTossEvent`，改为 Mixin 拦截 `ServerPlayer.drop(ItemStack,ZZ)`（Q 键/背包拖出丢弃的唯一服务端入口）——检查室内（检察官与被检察者）丢弃被取消、物品退回背包并提示，防止检察期间靠丢弃干扰实际总分；非检查室丢弃不受影响
- **神的谎言：降罪扣分描述 -10 → -60**：使者书回退文案、服务端 payload 注释、语言文件（zh_cn/en_us/zh_tw `confirm_deduct`）全部改为 60 分（与实际扣 60 分行为一致）
- **神的谎言规则书包含遭遇事件**：开局发放规则时 `clearPlayerEnabledEvents` + 事件池取全部 `EventType.values()`——团队模式发 1 个全局事件、个人模式每人 1 个随机事件，事件分从规则总分中扣除后再随机分配（开局总分仍恰好 120）
- 自测新增 5 项断言（每批最多 1 个 8 分物品 / 检查室判定 / 检查室禁丢弃×2 / 非检查室对照），基线 165 → **170 项全部通过**

## 2026-10-11（第四十四批）

### 文明时代（往草丛/花位置放羊毛不进占领修复）
- **根因（三点叠加）**：① 26.2 原版 `BlockPlaceContext` 用**带放置上下文的 `canBeReplaced(上下文)`** 决定落点且构造中**已无潜行检查**（潜行与否均替换被点击的短草/花草）——模组用 `canBeReplaced() && !潜行` 推测落点，与原版判定不同源；② **点击脚边/脚下的草丛花草时，原版把落点定为被点击格 = 玩家自己的碰撞箱内 → 原版 `isUnobstructed` 实体阻挡检查拒绝放置（静默失败）**——羊毛根本没落地，下一 tick 找不到羊毛；③ 找不到羊毛时只发「未能放置成功」提示、**没有兜底**——每区块羊毛索引不登记，于是「还能放置其他羊毛」
- **修复**：
  - `replaceTarget` 改为与原版完全同源：`canBeReplaced(放置上下文)`（移除手工潜行项）
  - 羊毛校验队列新增**服务端兜底放置**：原版拒绝落地时，按原版落点优先级在两处候选格兜底——仅当候选格**可替换（短草/花草/空气）**且通过占领校验时用 setBlock 放置（玩家会被推到羊毛上），成功即登记每区块羊毛索引 + 发起占领；原版未消耗物品故不返还；两处候选都放不下（均为实体方块或校验不通过）才提示「未能放置成功」
- 效果：往草丛/花位置放羊毛（含点脚边的草）与放在普通方块表面行为完全一致——羊毛落地、进入 15 秒占领计时、该区块不能再放第二个羊毛
- 自测新增 3 项断言（候选格空气前置 / 服务端兜底放置成功 / 兜底登记占领），基线 162 → **165 项全部通过**

## 2026-10-11（第四十三批）

### 文明时代（边界对齐区块 + 产出修复补充 + 隐藏无限饱和清除）
- **WorldBorder 严格按区块边界**：此前边界中心设在疆域西北角（中心区块的区块角）——边界范围 [minX-120, minX+120)，而疆域方块范围是 [minX-112, minX+128)，导致**北/西多出半个区块、南/东少了半个区块**；现边界中心改为疆域几何中心（区块角 +8 格），边界与 15×15 疆域的区块边界完全重合
- **隐藏的无限饱和（死亡重回对局后仍在）**：大厅阶段每 tick 给所有玩家施加**无限时长**且隐藏的 饱和 + 生命恢复 + 夜视（大厅 buff），开局进入对局后**从不清除**，效果一直隐藏跟随（饥饿/血量不正常）；修复：大厅 buff 改为 **5 秒有限时长**（大厅内每 tick 重施、等效无限，离开大厅后自然到期），进入对局传送时再主动清除三个效果，双保险
- **部分资源区不产出（同第四十二批根因补充）**：点击短草等可替换方块放置箱子/熔炉时，原版真实落点是被点击格而设施登记在预测位置 → 注入跳过 → 不产出；此前的双位置登记已覆盖（含箱子/木桶归属登记），超平坦草地任意点击放置均能正常登记产出

## 2026-10-11（第四十二批）

### 文明时代（产出系统重构：独立配置文件 + 两大产出 bug 修复）
- **新增产出配置文件** `config/survival-contract/civilization-production.json`（首次启动自动生成）：
  - 覆盖全部 9 类资源区（木头/石头/铜/铁/金/钻石/石英/青金石/合金）的 **产出间隔（intervalSeconds）/ 数量（count）/ 主产物（output）/ 概率副产品（bonuses：chancePercent + items 候选池）/ 概率刷怪（spawnMob + spawnChancePercent + spawnCount）**
  - 野生动物区：刷怪间隔 / 每次数量 / 上限 / 村民概率 / 绿宝石数量
  - 大本营战斗物资：间隔 / 基础数量 / 每区块加成百分比 / 上限
  - 大本营物资桶：间隔 / 每区块减秒 / 最低间隔 / 珍珠与风弹数量 / 稀有概率
  - 每个字段附中文「说明」；物品 id 可省略 `minecraft:` 前缀；count=0 关闭该区产出
  - **每局开始重新读取——修改后下一局生效**；文件缺失自动生成默认值，解析失败用默认值且不覆盖用户文件；默认值与旧硬编码行为完全一致
- **每区块独立产出计时**：取消全局"每分钟统一结算"（所有区块同时产出、后占领的区块跟着全局节拍）→ 每区块/每队独立计时，占领后一个间隔首次产出
- **石头区（及所有点击短草放置的设施）不产出——根因**：放置设施/容器登记用的是**预测位置**，点击短草等可替换方块时原版真实落点是被点击格 → 设施登记错位 → 产出注入按登记位置查方块状态查不到箱子 → 跳过（"合成大箱子后突然可以产"是因为新大箱子按正确流程登记）。修复：**placePos + clickedPos 双位置登记**（错误位置登记无害——注入/前置校验都按方块状态过滤）
- **大本营产物不再进其他区块的箱子——根因**：战斗物资注入遍历的是玩家放置容器的登记表（facilities），系统开局放置的物资桶不在其中 → 产物进了大本营 3×3 内玩家放的箱子（甚至其他区块登记错位的箱子）。修复：战斗物资**仅注入本队物资桶**（末影珍珠/风弹同理由物资桶配置控制数量），桶满溢出丢弃，累积器封顶 10 防堆积
- 石英区岩浆怪（30%×2）/ 合金区凋灵骷髅（25%×1）、野生动物区动物（6 只/次上限 12）/ 村民（15%）/ 绿宝石（2 个）全部改为配置驱动（默认值 = 旧行为）
- 自测基线 **162 项全部通过**

## 2026-10-11（第四十一批）

### 文明时代（生物破坏/孤儿羊毛/放置规则修复）
- **苦力怕爆炸破坏方块的根因**：26.2 移除了 `doFireTick` 游戏规则（改为玩家半径限制机制）、规则 id 改为 snake_case——之前用字符串指令 `"gamerule doFireTick false"`/`"gamerule doMobGriefing false"` 设置环境规则**全部无效且被异常捕获静默吞掉**，生物破坏与火蔓延从未真正关闭
- **改用 API 直接设置**：`MOB_GRIEFING=false`（苦力怕/末影人等生物不再破坏/搬动方块）+ `FIRE_SPREAD_RADIUS_AROUND_PLAYER=0`（火不蔓延，26.2 的 doFireTick 替代）；进入文明时代前保存原值，结束时恢复，不影响其他模式
- **不相邻不可占领的区块不能放置羊毛（用户要求落地）**：新增 `validateWoolPlace` 放置前置校验（疆域外/无队伍/本方领土/敌方占领/大本营/已在占领中/**不相邻**），不通过**直接取消原版放置**并提示
- **孤儿羊毛（此前所有占领异常的总根源）**：之前校验不通过时羊毛照样落地——该区块被"已存在占领羊毛"索引锁死（再次放置提示已占领）、挖掉羊毛因非占领羊毛而不触发回归无主提示、等领土连接过去后表现为"被未知队伍占领"。现在前置取消 + `tryPlaceWool` 未受理时移除羊毛与索引双兜底，孤儿羊毛不可能再产生
- 占领校验规则统一：`tryPlaceWool` 委托 `validateWoolPlace`，两处规则永不分歧
- 自测基线 **162 项全部通过**

## 2026-10-11（第四十批）

### 文明时代（蓝队放置羊毛无占领进度/无悬浮物修复）
- **静默失败根因**：占领校验只检查「点击面相邻格」，但超平坦主世界草地遍布短草/花草——点击这些**可替换方块**时原版会把羊毛放进被点击格本身，校验位置与真实落点不一致 → 静默跳过，无提示、无 bossbar、无悬浮物
- **双位置校验**：右键时记录被点击格与可替换标记（`canBeReplaced() && !潜行`），下一 tick 按原版真实落点优先校验、另一格兜底；每区块羊毛索引与占领登记均按真实落点
- **失败不再静默**：羊毛未能真实放置（脚下被自身碰撞箱阻挡/被其它处理器取消）时明确提示「羊毛未能放置成功，请重新放置以发起占领」
- **陈旧羊毛索引修复**（放置被「已存在占领羊毛」误拦的根因）：
  - 占领中羊毛被摧毁（爆炸等）取消占领时，未清除每区块羊毛索引 → 该区块此后永久无法放置；已改为同步清除
  - 破坏处理器用 `BlockPos` 当 `ChunkPos` 键删除索引（永远删不掉）→ 改为按区块键且仅清除被索引的那块羊毛
- **bossbar 全队可见**：占领进度条此前只对发起者显示，队友会误以为没有占领进度——改为同队全体在线玩家可见
- 每区块羊毛预检改为按原版真实落点区块判断（可替换目标时为被点击格所在区块）
- 自测基线 **162 项全部通过**

## 2026-10-11（第三十九批）

### Xaero 兼容修复（存点/地图清理终于生效）
- **旧实现命不中的根因**：Xaero 的维度目录名带**容器/世界前缀**（如 `新的世界$survival-contract$arena`），旧实现用 `startsWith("survival-contract$arena")` 永远匹配不到
- **归一化包含匹配**：目录名中 `$`/`%`/`.` 统一归一化为 `:` 后匹配 `survival-contract:arena`——覆盖所有版本的编码变体与容器前缀；大厅维度（lobby）不清理
- **多轮防回写清扫**：Xaero 可能把内存中的旧数据延迟回写磁盘——结束回大厅后按 5s/20s/60s/120s/240s 清扫 5 轮，断开连接（退档/退服）3 秒后再兜底清扫一次；新一局开始前也立即清扫
- 扫描根目录：`xaero/`（新版小写）、`XaeroWaypoints/`、`XaeroWorldMap/`，深度 8 覆盖 backup 与多层容器

### 偷渡客与检察官（出生间距 + 追踪指南针）
- **个人模式出生点间距 5 区块**：单人模式出生点由 2 区块（32 格）改为**5 区块（80 格）**——仅偷渡客与检察官模式生效（其他个人模式保持 2 区块），且逐点用最小间距搜索防止安全落点漂移缩短间距
- **追踪指南针（已移除）**：先后尝试 lodestone_tracker 组件 + 真实磁石 + POI 注册 + Mixin 跳校验四种方案，26.2 客户端渲染层对指南针指向有自己的世界校验，服务端手段均无法稳定定向，且手动 POI 操作曾损坏区块数据——**按用户要求整体移除该功能**，恢复仅发放检察发起书
- 构建环境修复：Loom 版本由 `1.18-SNAPSHOT` 锁定为 **1.18.3**（快照刷新解析到未发布的 1.18.4 且网络瞬时故障导致构建失败）
- 自测基线 **162 项全部通过**

## 2026-10-11（第三十八批）

### 文明时代（信标光柱激活修复）
- **光柱未激活根因**：染色玻璃不是有效的信标基座方块（基座仅限铁/金/绿宝石/钻石/下界合金块）——原玻璃金字塔无法激活信标
- **结构修正**：信标正下方改为 **3×3 铁块**（有效基座，激活光柱）；队伍颜色光柱由信标正上方的**染色玻璃基座对光束染色**实现（红/蓝）
- **保护**：9 个铁块、信标、染色玻璃基座全部不可破坏；信标不可右键交互（防打开 GUI）
- 自测更新：受保护方块断言 74→**24**（每队 12：玻璃基座+信标+9 铁块+物资桶）；**162 项全部通过**

## 2026-10-11（第三十七批）

### 文明时代（大本营中心基座重构）
- **盔甲架下方基座**：占领羊毛替换为**红/蓝染色玻璃**（队伍颜色），玻璃正下方即**被激活的信标**（金字塔位于信标下方地下，beam 颜色仍由金字塔玻璃决定）
- 中心区块不再登记占领羊毛（每区块羊毛索引/完整性检查均跳过 HQ 中心——玻璃基座不可破坏、不可作为占领目标）；大本营侧翼 8 区块羊毛不变
- 受保护设施 72→**74**（新增两队玻璃基座）
- 自测更新：大本营中心无羊毛 + 侧翼区块有羊毛 + 保护方块计数断言；**162 项全部通过**

## 2026-10-11（第三十六批）

### 文明时代（大本营物资桶）
- **物资桶**：大本营猪灵脚下放置木桶，**猪灵站在木桶上**
- **物资产出**：每 **10 分钟**生成 **末影珍珠 + 风弹各 1**（注入木桶）；每次生成时 **5% 概率**额外产出稀有物资——**鞘翅或重锤**（随机其一，产出时全队提示"快去领取"）
- **刷新提速**：我方每多占领 1 个区块，木桶刷新间隔 **-10 秒**，**最低 2 分钟**（超出初始 9 区块的部分计入，与战斗物资产量规则一致）
- 木桶列入大本营受保护设施（不可破坏）；仅归属队可打开（走容器归属锁定规则）
- 自测更新：受保护方块 70→**72**；新增"物资桶初始刷新间隔 600 秒"断言；**161 项全部通过**

## 2026-10-11（第三十五批）

### 文明时代（实测反馈三项调整）
- **不自然生成史莱姆**：超平坦"主世界"预设地下 slime chunk 会自然刷史莱姆——ENTITY_LOAD 事件拦截，加载即清除（**岩浆怪不受影响**，石英区刷怪照常）
- **真信标光柱**：大本营盔甲架位置的粒子光柱改为**真实信标光柱**——盔甲架正下方埋设信标 + 队伍颜色染色玻璃金字塔（3×3+5×5 两层，隐藏地下），vanilla 机制驱动全高光柱且颜色随玻璃（红/蓝）；全部 35×2 个设施方块受保护（不可破坏），信标不可交互（防打开 GUI）；粒子光柱移除
- **边界粒子 6 倍密集 + 降半格**：每 4 格一段发 1 包、count=6 沿边界轴 ±2 格散布（每区块 8→48 粒 = 6 倍），显示高度从地表上方 1 格降为 **0.5 格**；发包数不变（无性能开销增加）
- 自测更新：新增"大本营信标光柱设施"断言（70 个受保护方块）；**160 项全部通过**

## 2026-10-11（第三十四批）

### 文明时代（材质修复 + 超平坦预设 + 每区块限一羊毛）
- **材质修复**：专属占领羊毛缺失模型/贴图（世界与物品全部紫黑格子）——补齐资源：`blockstates/claim_wool_red|blue.json` + `models/block/*.json`（`cube_all` 复用原版红/蓝羊毛贴图）+ `items/*.json` 物品模型定义；补充方块名称（三语：红队/蓝队占领羊毛）
- **超平坦预设改为「主世界」**：文明时代竞技场主世界生成器由默认超平坦（经典平坦：表面贴 bedrock）改为超平坦**自定义-预设-主世界**（Overworld preset——层厚与主世界一致），从原版 `FlatLevelGeneratorPresets.OVERWORLD` 读取设置
- **每区块最多一个羊毛**：新增每区块羊毛索引（初始占领 + 运行时放置统一登记）——区块已存在任意颜色占领羊毛时，**再次放置被取消**并提示"该区块已存在占领羊毛——每个区块最多放置一个（破坏原有羊毛后才能再放置）"；羊毛被破坏（含爆炸摧毁）后索引清除、可重新放置
- 自测更新：新增 3 项断言（索引初始登记/破坏后清除/归属判定沿用）；**159 项全部通过**

## 2026-10-11（第三十三批）

### 文明时代（羊毛消耗/破坏 + 容器归属体系）
- **专属占领羊毛方块**：新增 `survival-contract:claim_wool_red/blue`（替代原版红/蓝羊毛）——
  - **破坏时长 ×5**：硬度 0.8 → 4.0（原版破坏速度系统自动生效，服务端权威）
  - **破坏不掉落**：无战利品表（noLootTable），羊毛无法被"打下来"再利用
  - **放置不消耗**：红/蓝羊毛放置成功后自动返还 1 个对应颜色（下一 tick 验证真实放置成功后返还；往脚下右键失败放置也不会扣）
- **容器队伍归属体系**：箱子/木桶按**放置时所属队伍**登记归属——
  - **敌方反占后依然锁定**：归属容器的开启判定 = 归属队 == 玩家队 **且** 区块归该队——敌方占领区块也打不开我方的箱子/木桶
  - **上锁不进产出**：产出注入只进**归当前占领队所有**的容器——敌方反占后，旧队的上锁箱子/木桶不再有新资源进入（大本营战斗物资同理）
  - **夺回即恢复**：重新被我方占领后，归属箱子/木桶自动解锁可开，资源产出继续进入
  - 容器被破坏 → 归属记录清除（重新放置重新登记）；无记录的旧容器按区块归属队判定（兼容）
- 自测更新：羊毛方块映射断言改为 `claim_wool_red`；新增 2 项容器归属断言（本队可开 / 敌方锁定）；**157 项全部通过**

## 2026-10-11（第三十二批）

### 文明时代（大批量规则修订 + 实测问题修复）
- **疆域 15×15**：区块范围由 32×32（1024）改为 **15×15（225）**，WorldBorder 同步改为 240×240
- **合金碎片区避开边界**：随机分配时距边界 2 区块以内的位置不出合金区（改为野生动物区）
- **区块边界颜色粒子**：玩家附近区块边界每 0.5 秒用地表 dust 粒子圈出——**无主=白色 / 红队=红色 / 蓝队=蓝色**
- **占领进度 bossbar**：放置羊毛发起占领后显示队伍颜色 Boss Bar（进度 0→100% + 剩余秒数实时刷新），完成/取消/羊毛被毁时移除
- **占领链路三修复**：① 同区块放置第二个同色羊毛不再导致失去占领——只有**区块的占领羊毛**被破坏才回归无主，多余羊毛不影响归属；② 敌方占领区块必须**先破坏其占领羊毛**才能放自己的羊毛（此前可直接抢占）；③ 往自己脚底下右键羊毛不再"假占领"——改为**下一 tick 验证方块真实放置成功**才登记占领
- **羊毛消失回归无主**：每秒自检所有已占领区块的羊毛方块——被生物/爆炸等方式破坏后**立即回归无主**（功能方块锁定），pending 占领同步取消
- **悬浮物缺失自愈**：每 5 秒检查已占领区块的悬浮图标实体，丢失自动重建（修复"部分区块被占领后不显示悬浮物"）
- **大本营**：① 回收猪灵获得**永久抗性提升 5**（不再死亡）；② 核心盔甲架位置出现**队伍颜色信标光柱**（粒子光柱直上天空）；③ 盔甲架受击反馈（暴击粒子 + 受击音效 + 血量广播）；④ **大本营羊毛无法破坏**（只能通过击破 100 血核心盔甲架判负）；⑤ 大本营生产**风弹 + 金苹果**战斗物资——基础 1 个/分钟，每多占 1 个区块产量 +10%，上限 5 个/分钟（累积取整，仅注入大本营 3×3 内的箱子/木桶）
- **产出系统改造**：① 产出位置**仅限箱子/木桶**（熔炉/高炉不再接收产出）；② 概率表全面调整——石头区煤炭 1.5%、钻石区 0.5% 黑曜石、石英区 1% 下界疣、合金区 2% 烈焰棒、木头区 3% 任意农作物（西瓜/南瓜/萝卜/小麦，保留马铃薯 25%）；③ **取消红石区**——红石移入青金石区（30% 概率产出红石粉），经验之瓶概率降为 1%
- **生物管控**：区块生成的生物**禁止跨越所属区块**（越界自动拉回区块中心）；每区块生成生物上限 **12 个**；石英区改刷**岩浆怪**（原僵尸猪灵）
- **环境**：竞技场 gamerule **doFireTick=false**（火蔓延关闭）+ **doMobGriefing=false**（苦力怕不再破坏方块，兼防末影人搬羊毛）；文明时代竞技场主世界改为**超平坦-主世界类型**（原版默认超平坦预设，其他玩法仍为常规地形）
- **移动限制**：双方玩家只能位于**本方领土**或**与本方领土相邻的无主/敌方区块**——深入无主/敌方纵深会被立即传回最近的己方区块（提示有 3 秒节流）
- 自测更新：疆域 225 区块、大本营 ±6 中心、WorldBorder 240、新增"合金区避开边界"断言；**155 项全部通过**

## 2026-10-11（第三十一批）

### 偷渡客与检察官（批次价值随机化）
- **每批价值档位随机**：三件价值物品不再固定 8/4/2 各一件——**每件独立随机 8/4/2 档**，同批允许出现多个 4 或多个 2（如 8/4/4、2/2/2、8/8/2 均可能出现）；同档位不重复取同一种物品（三件互不相同），换批逻辑与 HUD 显示（每件自带分值）天然兼容
- 自测适配：档位断言改为"三件均为 8/4/2 档 + 批内互不重复"两条，放入收集箱加分断言改为按实际分值自适应；**154 项全部通过**

## 2026-10-11（第三十批）

### 神的谎言（规则修订 + 淘汰修复）
- **开局身份白色大字**：开局时屏幕中央闪出全屏身份提示白色大字——「你 是 神 的 使 者」/「你 是 平 民」（粗体白字，短暂停留后淡出）；延迟到 3 秒开局倒计时结束后弹出（避免被 3/2/1 倒计时覆盖），掉线重连不重发
- **使者单次扣分 10 → 60**：降罪权单次扣分由 -10 分改为 **-60 分**（`envoyDeduct` 底层数值 + 三语 intro/envoy_you/task_reward_deduct 文案同步）
- **分数归零进观察者修复**（真根因）：每 tick 的"旁观者误锁救援"循环会把分数表里仍存在的玩家强制拉回生存模式——淘汰者（分数 0）的 SPECTATOR 在下一 tick 即被覆盖回去；修复为循环开头跳过已淘汰玩家（`isGodsLies && isEliminated → continue`），配合此前已加的死亡重生守卫与传送守卫（重生/防逃跑流程均保持旁观者），淘汰后稳定进入并保持观察者模式

### 偷渡客与检察官（规则修订）
- **锁定无职业**：本模式下职业锁定为「无职业」——挑战书职业按钮置灰"无职业（已锁定）"不可点；服务端拒绝职业选择包（强制回 NONE 并提示）；开局强制全员 NONE（覆盖大厅所选职业），无职业 +10 分全员一致
- **每批每种物品兑换上限 64**：收集箱每批每种物品最多兑换 **64 个**——按组按批记录每物品已兑换数量，超出部分不加分不消耗（留在箱中）并提示"超出部分未消耗"；换批后计数清零
- **检察容差 4 → 2**：估价成功判定差值由 ≤4 收紧为 **≤2**（检察书说明文案同步）
- **估价系统提示区间**：进入检查室后（估价阶段），检察书上显示**系统提示总价区间 [实际-10, 实际+10]**——系统按对方身上价值物品总分提供前后波动 10 的区间，真实值保证在范围内； ProsecuteStatePayload 新增 hintLow/hintHigh 字段
- **零分拒检**：发起检察时若对方分数为 0——**不进入检查室**（不传送双方），提示"对方分数为 0 无法发起"，发起者的检察发起书照常进入 5 分钟冷却
- 自测更新：使者降罪断言 120 → **60**；新增 **6 项断言**（每物品兑换上限 64 两段式、容差边界差值 2 成功/差值 3 失败、零分拒检拒绝+冷却）；修复回收平分断言的随机分队假设（动态取实际队伍与人数）；基线 148 → **153 项全部通过**

## 2026-10-10（第二十九批）

### 文明时代（新玩法，正式开放——仅团队模式）
- **疆域**：32×32 区块（以出生点区块为中心 ±16），**WorldBorder 512×512 围住**
- **规则限制**：仅环境恶化和遭遇事件可选择（全员共享、**无分数**——初始 0 分起步，环境加减分全部跳过）；**PVP 强制开启**（按钮置灰"已锁定开启"）；无最终目标；无专属任务；时长禁止无限制
- **开局**：两队大本营在疆域**南北中间区块**（红北/蓝南），初始各占 **3×3=9 区块**（每区块中心自动放置本队颜色羊毛+悬浮队标）；每人初始获得**一组本队颜色羊毛**；大本营有 **100 血盔甲架核心**（每次命中 -5，击杀即判负）+ **友好猪灵**（右键打开回收箱）
- **占领机制**：在**与本方领土相邻**的无主区块放置本队羊毛 **15 秒**后占领（不能跨越敌方已占领土攻占内部区块——只能沿最外层连线推进）；对方区块需**先破坏其羊毛**（回归无主）再放羊毛 15 秒；**大本营区块被对方占领 → 直接判负**
- **资源区**：占领后羊毛上方**悬浮资源图标**（marker 盔甲架头戴，不可吸引/不可交互）；11 种资源区（木头/石头/铜/铁/金/钻石/石英/青金石/红石/野生动物/合金碎片）各自随机分配，产出速率/前置条件/概率副产品按规则表实现（铜=熔炉、铁=熔炉+煤炭块、金/钻石/青金石/红石=高炉（青金石须 Y≤0）、合金=高炉+钻石块）；**区块内放置箱子/木桶后产出自动注入**（每分钟结算）；野生动物区每分钟刷 6 头动物（上限 12）+ 概率村民（有村民时产绿宝石 2/分钟）；石英区刷僵尸猪灵、合金区概率刷凋灵骷髅
- **锁定机制**：区块回归无主 → 区块内全部功能方块与容器**锁定**（不可使用不可破坏），重新占领后权限归属新队伍；已占领区块内仅本队可使用/破坏
- **回收**：大本营右键猪灵 → 打开本队回收箱（9 格），放入价格表物品即结算积分**平分全队**（原木 1/木炭 2/马铃薯 0.5/石头 1/煤炭 3/原铜 1.5/铁锭 3/金锭 3/钻石 10/石英块 10/青金石 2/红石块 10/绿宝石 2/下界合金锭 20）；价格表外物品不回收
- **胜负**：时间结束**分数最高者获胜**（个人最高分/团队总分最高队伍），大本营被攻破/核心被击杀直接判负；结算专属文案
- **悬浮图标飘天修复**：ItemEntity 掉落物有物理——在水中/受气泡柱等外力时被向上推飞出；改为 **ItemDisplay 渲染实体**（**无物理，绝对静止**）：`GROUND` 掉落物变换（旋转漂浮样式）稳定停在羊毛上方中央 1.1 格；物品与变换通过反射写入 entity data（26.2 无公开 setter，失败仅失去旋转不影响悬浮）
- **悬浮图标旋转浮动**：ItemDisplay 由服务端 tick 驱动——每 tick 自转 2°（约 3 秒一圈）+ 正弦上下浮动（Display 插值平滑同步），呈现掉落物旋转漂浮效果
- **放置羊毛反馈修复**：在**本方已占领区块**（含大本营 3×3）内放羊毛此前静默无反应（易误判占领失效）——现提示"该区块已是本方领土，无需占领"；占领判定羊毛映射改为注册表直查（消除 ColorCollection 颜色顺序不确定性）；`woolBlock` 映射/放置登记链路新增 3 项自测断言（基线 145 → **148 项全部通过**）
- **初始 9 区块类型修正**：此前 3×3 全设为大本营类型（8 个区块悬浮羊毛）——改为**仅中心 = 大本营（HQ，悬浮本队羊毛）**，其余 8 个 = **随机资源区**（悬浮对应资源图标，开局即有产出）
- **悬浮资源图标修复**：此前用 marker 盔甲架承载图标——**marker 连装备都不渲染**（所有悬浮图标"消失"的根因，导致资源区分配看起来未生效）；改为**隐形盔甲架头戴资源方块/物品**（3D 渲染正常悬浮），攻击/右键交互全部拦截（不可打掉/换装备/吸引）；大本营悬浮本队颜色羊毛、资源区悬浮对应代表方块（原木/石头/原铜/铁锭等）
- **大本营位置修正**：疆域网格改为以出生点区块为原点（此前固定网格与 WorldBorder 中心错位，导致大本营偏离边界）；红队大本营 3×3 **贴住北边界**（中心行 z-15）、蓝队**贴住南边界**（中心行 z+14）；WorldBorder 中心对齐疆域几何中心，恰好围住整片 32×32 疆域
- **开局卡虚空修复**：大本营在疆域边缘（距出生点数百格），开局时区块尚未生成——heightmap 对未生成区块返回虚空值（-63），导致 respawn 卡在虚空、羊毛/悬浮图标/动物（窒息音效）全部错位；修复为**取地表高度前强制生成所在区块**（`surfaceY` 统一封装，含虚空兜底），羊毛/大本营 respawn/核心盔甲架/猪灵/资源区动物刷新全部走该逻辑
- 服务端自测新增 **8 项断言**（疆域 1024 区块、双队大本营初始占领、WorldBorder、邻接可占/纵深拒、羊毛无主化、跳占 HQ 被拒、回收平分积分），基线 135 → **145 项全部通过**

## 2026-10-10（第二十八批）

### 偷渡客与检察官（新玩法，正式开放）
- **规则共享**：所有玩家规则共享——任何玩家在挑战书点亮/关闭规则即全员生效；开局将大厅阶段各玩家的规则集合并为并集应用到每个人
- **无最终目标、无专属任务**：任务系统（含任务抢夺规则）在该模式下整体停用
- **收集系统**：出生点附近生成不可破坏的专属末影箱（团队模式每队一个共享 / 个人模式每人一个，收集箱界面 9 格）；屏幕上方显示三个随机价值物品（**8/4/2 分各一档**，限原材料/食物饮品/武器/工具四大类），收集对应物品放入箱子**立即加分并消耗**；价值物品每 **10 分钟更换一批**（HUD 显示换批倒计时），旧批次物品放入不再加分
- **检察系统**：每人开局一本「§b检察发起书」，右键打开玩家列表——团队模式友方灰色不可选，选非友方玩家并**写下对方价值物品总价**（估价输入框）确认后：
  - 双方强制传送进高空**密闭不可破坏的检察木屋**（破坏拦截 + 每 2 秒自愈兜底），2 秒后自动判定交换并各自回原位
  - **估对**（对方确有价值物品且 |估价−实际| ≤ 4）→ 检察方没收对方全部价值物品
  - **估错** → 检察方赔偿「估价一半」价值的物品（大价值优先、按需拆组；不足时全部价值物品上交）
  - 全员广播发起与结果，数额仅双方私发可见；每人每 **5 分钟**可发起一次（冷却精确提示）
- **团队情报**：团队模式下 HUD 额外显示对方三种物品——仅一件名称可见，其余两件显示问号，且不显示对方分值
- **胜负**：时长不可选无限制（客户端按钮跳过 + 服务端拒绝兜底），时间结束**分数高者获胜**（走超时结算专属分支，不归零分数）
- **上交反馈**：放入收集末影箱的清单物品加分后**立即消耗消失**（不返还），上交成功私发确认提示
- **团队箱强制关闭**：本模式下团队箱无法开启（快捷键打开被拒并提示走收集末影箱，挑战书按钮置灰"已关闭"）
- **实时倒计时修复**：HUD 换批倒计时与检察发起书冷却倒计时改为**本地实时递减**（此前只在 sync 时刻取快照静止不变）；检察书标题下新增冷却状态行（"下次可发起：X 秒"/"可以发起检察"），冷却归零时确认按钮自动解锁

### 实测反馈修复（第二十八批追加）
- **指令结束闪退修复**：`/sc endgame` 在神的谎言/偷渡客与检察官模式下崩溃——根因是无最终目标玩法 `finalGoal == null`，指令路径 `endGame(null)` 与结算文案直接访问 `goal.displayName` NPE；全链路空保护（endGame 日志 / 指令回显 / 结算文案），超时结算不再误设"无人获胜"标志
- **检察流程改为两阶段**：① 右键书选非友方玩家确认 → **双方立即传送进检察木屋**（进入 60 秒估价窗口）；② 检察官再次右键书 → 界面切为估价模式（仅显示被检察者 + 估价输入框）→ 确认即结算（没收/赔偿）→ 双方回原位；60 秒未估价自动取消（不赔偿）；估价阶段目标不可更换
- **估价阶段确认修复**：估价界面误把"发起冷却"当确认前置条件（确认按钮禁用 → 必然超时取消）——发起冷却只约束下一次发起，估价阶段确认按钮始终可用；状态行改为估价提示（60 秒内须结算）
- **对方价值物品全可见**：团队模式下 HUD"对方"行三件物品全部显示（名称+图标），仅分值不显示（此前仅暴露一件、其余问号）
- **检察书排版修复**：说明文字拆为两行居中显示（原单行超宽溢出面板），发起/估价阶段各自独立文案
- **获胜判定明确化**：偷渡客与检察官结算使用专属文案（个人取最高分 / 团队取总分最高的队伍），与神的谎言胜负条件（使者博弈/前二平民）区分；底层判定本就走常规分数路径，仅文案显式化

### 实现明细
- 新增服务端核心 [SmuggleManager]：价值物品表（累积，供检察判定）/ 组清单批次 / 收集箱容器结算 / 检察冷却与待执行队列 / 检察木屋搭建与保护
- 新增网络链路：OpenProsecute / ProsecuteState / ProsecuteAction / SmuggleSync 四个 payload（C2S+S2C 全套）
- 客户端：ProsecuteScreen（目标列表+估价输入+冷却禁用）、HUD 价值物品清单、检察书右键拦截（ChallengeBookOpener）
- 服务端自测新增 **11 项断言**（收集箱/木屋生成、三档分值、开箱会话、放入即加分+消耗、检察成功没收、失败检察官赔偿、失败不没收被检察方、冷却记录与拒绝），基线 124 → **135 项全部通过**

## 2026-10-10（第二十七批）

### 玩法四选一（挑战书初始菜单）
- 初始菜单新增**第二行玩法选择**：**常规模式**（默认）/ **神的谎言** / **偷渡客与检察官** / **文明时代**，选中显示 ✔ 并全员同步
- 偷渡客与检察官、文明时代为占位（选择时提示"未开放"）

### 神的谎言（新玩法，仅单人模式可选）
- **规则隐藏**：本模式下二级菜单不显示任何规则与奇遇事件，不可自选；队伍箱按钮替换为**使者数量**（1~4 循环）；最终目标固定显示"无最终目标"；团队模式不可切换（守护）
- **系统分配规则**：开局为每位玩家随机分配规则组合，**总分恰好 120 分**（子集和动态规划 + 随机回溯，排除玩法开关/物品栏锁定/环境类与事件）
- **神的使者**：开局随机任命对应数量的使者（至少留 1 名平民），使者**免疫所有规则**（规则集清空），开局私发身份提示，全员广播模式开场
- **淘汰机制**：分数 ≤ 0 的玩家立即淘汰并进入旁观者模式（加分/扣分双入口实时检查）
- **指认书**：开局每人发放一本「§5指认书」，右键打开存活玩家列表；平民完成专属任务获得**指认权**——指认成功 → 该使者分数清零淘汰，指认失败 → 自己分数清零淘汰（全员广播，仅显示结果）
- **降罪权**：使者每完成一个专属任务获得一次扣分权——指定一名玩家扣 10 分（系统提示仅显示谁被扣分）
- **获胜条件**：使者全部淘汰 → 分数最高的两位平民获胜；剩余平民数 = 剩余使者数 → 使者获胜；本模式无最终目标，胜负由专属文案结算
- 新增网络链路：玩法选择 / 使者数量 / 指认书打开 / 指认与降罪动作（C2S+S2C 全套），客户端指认书界面（紫色系、点选确认、权限计数）

### 界面调整（实测反馈）
- **神的谎言二级菜单**：底部 PVP / 全局夜视 / 保留物品栏三个全局开关按钮恢复显示（gamerule 类全局状态，开局照常应用；双禁仍隐藏）
- **初始菜单重排**：四种玩法按钮移至第三行（任务抢夺/猎人游戏之下），三行按钮统一高度 24px 并各自水平居中，提示文字与玩家面板同步下移
- **玩家列表**：初始菜单玩家面板由双列改为**五列**显示

### 神的谎言规则修订（实测反馈）
- **开局后规则完全隐藏**：游戏开始后挑战书同样只显示占位说明（原设计"开局后在挑战书中查看"废除），玩家全程无法查看系统分配给自己的规则
- **使者可自我降罪**：使者使用降罪权时可以选择自己（扣自己 10 分，可主动触发淘汰的战术选择）
- 使者身份信息确认：使者仅私发知晓自己身份，全局广播只含使者数量，不泄露其他使者身份（已满足，无改动）

### 质量保障
- 服务端假人自测新增神的谎言 6 项断言（随机规则总分×10、使者任命、规则免疫、降罪扣分、指认失败淘汰、使者胜利判定），自测基线 118 → **124 项全部通过**
- 修复自测污染：使者任命清空规则集后未还原，导致后续团队初始分断言漂移（快照 + finally 还原规则集）

## 2026-10-10（第二十六批）

### 愚者重做
- 初始携带改为 **1 个绿宝石**（原 16 个石头按钮）
- 投掷物改为**钻石或绿宝石**：丢出一颗后随机获得 **6~8 件**生存模式可非作弊获得的物品/方块（原 1~2 件）
- 0.5% 抽中涂蜡的氧化切制铜楼梯触发爆炸彩蛋保留

### 乐师强化
- **自受益**：正面音色同时作用于乐师自身（时长仅刷新基础值，不叠加）
- **敌我区分**：正面音色只对友方生效（团队模式同队；个人模式其余玩家均为敌方），负面音色（黑暗/不祥之兆/虚弱）只对敌方生效；观战者免疫
- **持续演奏**：间隔 ≤2 秒视为连续演奏，友方每次演奏时长 +15 秒（可叠加，上限 10 分钟）；停止后再奏不会缩短已有时长
- **合奏奖励**：5 秒内演奏 3 种不同音色 → 全体友方获得 15 秒随机正面状态（从所奏音色的正面效果中随机，不限距离），触发后需重新凑齐
- **音波攻击**：敲击底鼓/颤音琴/小号（含氧化变体）音色 → 半径 32 格内除自身外所有生物受到 2 点真实伤害（无视护甲）并小幅击退，每 3 秒最多一次
- **手鼓**：获得成就「与火共舞」（获得烈焰棒）后解锁——主手持音符盒、副手持任意方块，主手右键空气或音符盒即以副手方块对应的音色演奏（等同木棍效果，含音波攻击与合奏奖励）

## 2026-10-09（第二十五批）

### 计分板扩容
- HUD 计分板显示上限由 8 人提升至 **16 人**：个人模式最多显示 16 名玩家；团队模式每队成员明细由 4 行扩至 **8 行**（红蓝两队共 16 人），面板高度随行数自适应

### 质量保障
- 服务端假人自测接入观战流程（真实 `/sc spectate` 指令路径）：开启清分出队、旁观者模式、不参与分队与投票人数计算、解除恢复冒险并重新归队，自测基线 107 → **118 项全部通过**

## 2026-10-09（第二十四批）

### 大厅观战模式指令
- 新增非作弊指令 `/sc spectate`（所有人可用，仅大厅可用）：输入后进入旁观者模式，**分数清零**、退出红蓝分队（团队模式计分板队色同步移除）、不参与"我决定好了/开始游戏"等投票；再次输入解除并回大厅，团队模式立即重新分队
- 开局时观战者不参战：不给初始分数/职业物品、不传送参战，自动以旁观者模式跟随进入竞技场观战（与游戏进行中加入的旁观者同流程锁定，结束随全员回大厅，回大厅后保持旁观直到自行解除）
- 大厅计分板：观战者显示 0 分、无队伍标记

## 2026-10-09（第二十三批）

### 乐师
- 按要求改回：乐师的负面效果免疫**包含漂浮**（失重规则触发的漂浮对乐师同样无效，仅凋零例外）

## 2026-10-09（第二十二批）

### 久病成患新增
- 「**失重**」（10 分）：1.5 秒内连续两次起跳后获得 **10 秒漂浮**效果（触发后需重新连跳两次）

### 环境恶化新增
- 「**午夜凶铃**」（10 分）：游戏时刻零点（竞技场主世界时钟）时，随机一名玩家受到一次**无来源的音波尖啸**伤害（10 点，无视护甲）；**每 20 分钟最多触发一次**，连续两次触发不会选中同一位玩家；永夜时间锁定在零点时依靠冷却仍只触发一次

## 2026-10-09（第二十一批）

### 职业描述双轨
- **游戏开始后屏幕上的职业行**改为只显示「自身免疫扣分」精简说明（新增职业 hudDesc 字段与 profession.<KEY>.hud 翻译键）
- **职业选择栏的悬停描述保持完整**（含初始物品与其他机制说明），不受精简影响

## 2026-10-09（第二十批）

### 职业
- **职业描述精简**：所有职业描述改为只显示「自身免疫扣分」相关说明（无职业显示"无免疫特权，初始 +10 分"），起始物品/其他机制不再展示；简中/繁中/英文同步更新

### 大厅计分板
- 大厅地图右侧计分板改为**实时显示**所有人当前选中规则累计的初始分数（含遭遇事件加分与无职业加成），随规则点亮/熄灭每 2 秒刷新，不再等游戏开始后才出现；游戏中仍显示实际分数

## 2026-10-09（第十九批）

### 职业与计分
- **无职业加成**：初始职业为「无职业」时额外 **+10 分**，其余职业不加分（挑战书显示的初始分数与开局实际分数同步生效）

### 遭遇事件修复
- 修复事件生物在矿洞中生成在方块里窒息死亡：出生点现在要求 **3 格净空**（脚/头/头顶均非固体、非液体、不窒息，脚下实心），不满足时自动向上寻找净空位置（最多 8 格），仍失败回退地表安全点——骆驼/马等高体形生物不再卡进洞穴顶

### 多语言补全
- **违规警告**：全部 38 条违规提示（不能使用附魔台/铁砧/箱子/药水、没收物品、各类死限触发等）改按规则 id 走翻译键，三语齐备
- **专属任务名称**：改用原版实体/物品/方块的翻译键动态拼接（击杀/合成/使用/挖掘 ×数量），随客户端语言渲染；灵魂沙峡谷驻留任务同步翻译；任务同步包改为传输组件
- **开局横幅**：游戏开始/最终目标/时间限制（含无限制）/初始分数改走翻译键
- **界面残留**：返回上级菜单、返回按钮、背包格锁定卡片的分值/格数单位补全翻译

## 2026-10-09（第十八批）

### 新增职业「乐师」
- 新增职业「乐师」（Musician）：初始携带 **1 个音符盒**
- **演奏**：手持**木棍**或**烈焰棒**右键互动音符盒，为半径 16 格内（以音符盒为中心）**除自身外**的所有玩家施加与**音色**对应的药水效果，**不分敌我**——木棍为等级 1 / 持续 15 秒，烈焰棒为等级 2 / 持续 30 秒（17 种音色各对应一种效果；生物头颅类音色无效果；铜小号的氧化变体与小号相同）
- **特权**：免疫一切负面状态效果（**凋零除外**，仍会受到凋零）；开局 3 秒冻结期不受该特权影响
- 右键保留原版行为（调音 + 发声）作为听觉反馈

## 2026-10-08（第十七批）

### 界面
- 任务抢夺/猎人游戏按钮新增悬浮说明：悬停显示规则名 + 描述（按宽度折行；简中显示原文，英文/繁中按语言文件显示）
- 修复悬浮说明首版在简中下缺失描述的问题：描述的简中回退值此前传了空串，现已传入规则注册时的简中原文

### 多语言修复
- 修复简体中文环境下规则/职业/事件描述显示为英文的问题：原版语言加载以 en_us 为基底合并，zh_cn.json 缺失的键会抢先解析成英文值；现简中环境直接返回源码简中文案（内容键不落 zh_cn.json 的设计不变，源码简中仍为唯一权威）

## 2026-10-08（第十六批）

### 多语言支持（i18n）
- 新增语言文件 `en_us.json` / `zh_tw.json` / `zh_cn.json`（assets/survival-contract/lang/）：覆盖全部 79 条规则、9 个职业、11 个遭遇事件的名称与描述，以及挑战书/职业/双禁界面与 HUD 的界面文案、常用系统消息
- **语言切换响应**：游戏语言设为非简体中文时自动显示英文；繁体中文（zh_tw）显示繁体文案；简体中文维持原版简中文案
- 实现方式：客户端新增 `ClientL10n` 解析器——规则/职业/事件内容按翻译键解析（无键时回退服务端下发的简中原文）；界面静态文案与常用服务端广播改用 translatable 组件（由各客户端按自身语言渲染）
- 简中环境内容键不落在 zh_cn.json（源码简中为唯一权威，避免双语维护漂移）

## 2026-10-08（第十五批）

### 职业系统
- 新增职业「**无职业**」并设为默认（原默认为大厨）：无特殊能力、不携带任何初始物品，未主动选择职业的玩家一律为无职业

### 最终目标
- 目标按钮新增「**随机**」选项（与三个目标循环切换）：大厅中显示"随机"，**游戏开始时**才从解放末地/结束了/凋零山庄三个目标中随机揭晓一个，开场公告与 HUD 显示真实目标

### 初始模式界面（个人/团队模式下方两个新开关，可互相独立点亮）
- 「**任务抢夺**」（个人/团队均可开启）：游戏开始后所有人的专属任务变为相同（按全员规则并集挑选，保证对每个人都可行）；任意玩家完成后，所有人立刻刷新为同一个新任务；前 2 轮任务仍强制 B 级
- 「**猎人游戏**」（仅团队模式可开启，个人模式下按钮置灰）：开启 PVP 后，主动击杀其他玩家夺取其 2 分（凶手 +2 / 受害者 -2）；误杀队友则全队每人扣除 2 分；与任务抢夺可共同开启

## 2026-10-08（第十四批）

### 愚者奖励数量修复
- 修复投掷按钮仍可能获得超过 2 个物品的问题：此前「1~2 件」是抽取次数，但堆叠类物品每次抽取会随机 1~8 个同种物品（如一次抽出 8 个沙子）；现改为**每件恰好 1 个**，投掷一次按钮总计只获得 1~2 个物品；职业描述同步移除过时的「不可堆叠物品至多1个」说明

## 2026-10-08（第十三批）

### 职业「愚者」彩蛋概率
- 每次投掷石头按钮时有 **0.5%** 概率额外获得一个「涂蜡的氧化切制铜楼梯」（直接放入物品栏），该物品会立即触发已有的彩蛋效果——清除物品并爆炸身亡，附带「神跟你开了个玩笑，你裂开了！」系统消息

## 2026-10-08（第十二批）

### 职业「愚者」调整
- 愚者的投掷物由拉杆改为**石头按钮**：初始携带 **16 个**石头按钮，丢出石头按钮触发随机 1~2 件物品奖励（判定与奖励机制不变）；职业描述同步更新

## 2026-10-08（第十一批）

### 职业「愚者」奖池扩充
- 拉杆奖池新增四类内容：**建筑方块**（木板/石砖/玻璃/梯子/脚手架/灯笼等 19 种）、**自然方块**（泥土/草方块/原木/沙/冰/苔藓/蛛网/仙人掌等 22 种）、**红石组件**（活塞/观察者/漏斗/发射器/中继器/比较器/铁轨/TNT/Crafter 等 26 种）、**药水**（抗火/治疗/迅捷/力量/再生/夜视/水下呼吸/缓降/隐身/跳跃等 11 种），奖池总量约 130 种

### 矿工加强
- 初始金镐去除**经验修补**附魔，改为赋予**不可破坏**标签（永不损耗耐久）；保留效率4与耐久3；职业描述同步更新

### 弓手加强
- 初始弓在力量3基础上额外获得**耐久5**附魔；职业描述同步更新

### 工匠加强
- 合成特性新增：合成**工具**另有 10% 概率获得**效率4**，合成**武器**另有 10% 概率获得**锋利3**（斧同时按工具与武器参与两次掷骰；与耐久2/经验修补各掷骰互相独立、可叠加）；职业描述同步更新

## 2026-10-08（第十批）

### 职业「愚者」削弱
- 每次丢出拉杆改为随机获得 **1~2 个**物品（原 1~8 个）；初始拉杆改为 **8 个**（原 16 个）；职业描述文本同步更新

### 愚者彩蛋
- 职业为愚者时，身上出现「涂蜡的氧化切制铜楼梯」→ 物品被立即清除，自身原地爆炸身亡（TNT 威力，必死），并收到系统消息：**「神跟你开了个玩笑，你裂开了！」**（该效果不在职业描述中，属于隐藏彩蛋）

## 2026-10-08（第九批）

### 新增职业「愚者」
- 新增职业「愚者」（Fool）：初始携带 **16 个拉杆**
- 丢出一个拉杆时，随机获得 **1~8 个**生存模式可非作弊获得的物品/方块（奖池约 70 种：矿物与矿物块、宝石、装备、弓弩盾、图腾、鞘翅、金苹果/附魔金苹果、下界之星、末影珍珠/末影之眼、烈焰棒、桶、床、信标、重生锚、潮涌核心、砂轮/铁砧/附魔台、鞍、命名牌、拴绳、经验瓶、附魔书、海之心、鹦鹉螺壳、凋灵骷髅头、重锤、试用钥匙等）；不可堆叠物品至多 1 个，堆叠物品 1~8 个，背包放不下自动丢在脚下
- **免疫任意"获得物品会扣分"的规则**：钻石/金锭/下界合金/青金石/药水获得后不会被没收、不会扣分（其余职业照常没收）
- 拉杆按归属判定：只有本人丢出的拉杆会触发奖励（死亡掉落、他人捡走再丢的不算），每次结算消耗 1 个拉杆

### 恢复说明
- MainEventListener.java 曾因编辑事故丢失 10-07/08 的全部改动：已从本地历史快照恢复并逐项重放补齐（减压症加强、无法自然回血死亡修复、挑战书死亡不掉落、白月/金月/赤月+迷彩实体直挂重构、恶意感知、不能使用重生锚、每局结束清空规则、贫瘠铁矿连锁独立掷骰、失眠症修复、尘肺病/高原水肿/减压症、背包格锁定兜底清扫），功能与此前各批次描述一致

## 2026-10-08（第八批）

### 月相效果修复
- 修复金月/赤月下自然生成的怪物没有药水效果：夜间判定改用**各竞技场维度自己的时钟**（此前用主世界时钟，与竞技场不同步导致竞技场夜间被误判为白天）；效果粒子与图标改为可见，便于确认生效

### 无法自然回血修复
- 修复选中「无法自然回血」后首次死亡整局无法复活：死亡后 prev 归 0，重生满血被误判为"自然回血"而回退为 0 血，导致重生即死循环；现跳过死亡/濒死与 prev≤0 的场景

## 2026-10-08（第七批）

### 界面
- 修复挑战书/双禁界面 tooltip 宽度异常（长描述不折行横贯全屏）：所有界面 tooltip 统一按 170px 折行（中文逐字、ASCII 单词不拆断），宽度保持一致
- 修复「恶意感知」卡片图标显示"?"：刷怪蛋 id 更正为 zombified_piglin_spawn_egg

### 职业描述与实际对齐
- 工匠：初始物品描述更正为「工作台、砂轮、铁砧」（此前仍写附魔台）
- 行商：猪灵交易描述更正为实际机制——每次猪灵交易结束时 15% 概率随机获得四种礼物之一并直接放入物品栏（删除已废弃的"概率三倍提升"说法）

## 2026-10-08（第六批）

### 合约规则
- 「不能使用信标」改为「不能使用重生锚」：判定、冲突键、图标同步更换，分值不变（+6/违反-4）

### 环境恶化板块
- 「中立生物变为敌对生物」移入环境恶化板块并改名「恶意感知」：从个人规则改为全员共享开关，效果不变（中立生物主动攻击所有玩家）
- 新增「白月」（12 分）：夜间自然生成的敌对生物首次被杀死时，立即按原 NBT 生成一个相同个体（装备/属性完整保留）；克隆体不会链式再生；遭遇事件生物不受影响
- 新增「金月」（10 分）：夜间自然生成的敌对生物获得抗性提升 2，白天自动解除
- 新增「赤月」（16 分）：夜间自然生成的敌对生物生成时分裂出一个相同个体，且均拥有力量 2，白天自动解除；月相克隆与事件生物不参与分裂/重生

### 专属任务
- 任务分值全部翻倍：S 级 +6 / A 级 +4 / B 级 +2（最低 2 分）

## 2026-10-08（第五批）

### 挑战书规则重置
- 每局结束重新选择模式（个人/团队）时，挑战书里所有点亮的规则全部熄灭（清空所有玩家已选规则与违规记录），避免重新分队后队内各自残留上局选的不同规则
- 仅在模式实际发生变化时清空；重复点击当前模式不会误清
- 补充修复：**每局结束回大厅时也统一清空全部点亮规则**（此前仅重选不同模式才清空，重选同一模式时旧规则仍残留）

## 2026-10-08（第四批）

### /sc tp 指令
- 玩名参数改用原版 `EntityArgument`（与 /tp 一致）：输入时自动弹出在线玩家名选择框，也支持 @ 选择器
- 同队校验、无参数传送最近队友的行为保持不变

## 2026-10-08（第三批）

### 大厅挑战书死亡不掉落
- 修复大厅地图反复死亡导致挑战书反复掉落一地的问题：死亡时移除尸体背包中的挑战书并清扫死亡点刚生成的挑战书掉落物，挑战书不再掉出
- 根因：死亡掉落物没有 owner 归属，原有的"按丢出者返还"防丢扫描永远扫不回死亡掉的书；重生/回大厅的自动补发逻辑保持不变

## 2026-10-08（第二批）

### 遭遇事件预警 Boss Bar 重构
- 修复顶部 Boss Bar 堆积问题：预警条改为**全局唯一一条**，新预警直接替换旧条（改名+重置进度+仅展示新预警的观众），倒计时取最新一次预警
- 根因：旧实现按"目标玩家"每人各建一条血条，多人多事件同时触发时全部堆在屏幕顶部（同名事件还会因不同玩家重复出现）
- 事件实际生成时序不受影响（由独立的 pendingNotifications 驱动）；游戏结束的清理逻辑同步适配

## 2026-10-08

### 遭遇事件
- 「骆驼骑士」骆驼尸壳移速还原为原版速度（此前对齐尸壳移速导致过快）

### 模组图标
- 图标升级为 128x128（附魔书底图 + 右上喷溅抗火药水，构图不变），提升启动器兼容性

## 2026-10-07（第四批）

### 挑战书界面
- 「死亡扣分」按钮拆分为左右两个：左侧仍是死亡扣分（0/1/2 循环），右侧新增「返回上级菜单」（回大厅初始菜单）；多人模式下返回同样需要 2 名不同玩家确认，确认不足时聊天栏提示进度

### 团队计分板
- 团队模式计分板改为两队明细互相可见：领先队总分置顶（★ 标记），其下显示各队成员个人分数（每队最多 4 行，按分数降序）

### 减压症加强
- 减压症惩罚效果加强：缓慢 1 + 虚弱 1 → **缓慢 2 + 虚弱 2 + 挖掘疲劳 1**，时长不变（15 秒）

## 2026-10-07（第三批）

### 模组图标
- 新增 64x64 模组图标（`assets/survival-contract/icon.png`）：附魔书做底，右上方叠加喷溅型抗火药水（原版贴图合成，药液染抗火橙色）

### 遭遇事件
- 「骆驼长矛尸壳」改名「骆驼骑士」：生成 1.21.10+ 的骆驼尸壳，固定搭载两名骑手——铁长矛尸壳 + 焦骸（骆驼双座位），移速与尸壳对齐
- 新增遭遇事件「僵尸马骑兵」（S 级）：已驯服的僵尸马搭载手持铁长矛的僵尸疾驰冲来

### 职业
- 「愚者」完成改名「行商」：职业显示名/英文名、村民交易双倍奖励提示、相关描述与注释全部更新

### 久病成患新增疾病
- 「尘肺病」（10 分）：高度处于负层（Y<0）连续 1 分钟获得无限时间中毒效果，离开负层后清除（进出均有系统消息）
- 「高原水肿」（10 分）：高度高于 128 层连续 1 分钟获得无限时间中毒效果，低于 128 层后清除（进出均有系统消息）
- 「减压症」（8 分）：完全进入水中后，若 5 秒之内就离开水面，获得 15 秒缓慢 1 与虚弱 1（入水满 5 秒再出水无碍）

## 2026-10-07（第二批）

### 贫瘠铁矿：连锁采掘概率修复
- 修复连锁采掘时整串矿脉共用一次概率的 bug：旧实现单块判定框为 [pos-0.5, pos+1.5]（正向多出 1 格），前一块的判定（清空/双倍）会把相邻块的掉落物一并处理，相邻块自己的判定随即空转
- 现改为：先把本 tick 全部新生粗铁掉落物按"最近矿块中心"唯一归属，再逐块独立掷骰（50% 空矿 / 10% 双倍 / 40% 正常）——无论一次挖多少个，每个铁矿都单独计算掉落概率、互不影响

## 2026-10-07

### 失眠症修复
- 夜间上床睡觉时立即移除残留凋零效果：不再在睡觉中受到凋零伤害、不再被强制下床（根因：上床前施加的凋零最长残留 3 秒，会在睡觉中结算伤害并触发原版"受伤强制醒来"）
- 睡眠计时改为累计制：当天分段睡觉也能凑满 15 秒后状态移出（与规则描述一致）

### 迷彩修复
- 改为实体生成事件直挂：敌对/中立生物一进入竞技场世界立即获得 30 秒隐身，生成即生效无遗漏
- 废弃旧实现（每秒用 3000 万格宽巨型 AABB 扫全场实体，查询几乎扫不到、覆盖率极低）

## 2026-10-06 深夜 ～ 10-07 凌晨

### 背包格子锁定重做
- 锁定格以屏障图标标记（原版屏障贴图，取自原版资源），点击完全无反馈：放入/拿取/Shift/数字键/Q 全部拦截，双端一致，杜绝"放入后弹回"
- 移除旧的"屏障占位物品"方案（不再有真实屏障物品占据格子，大厅重置同步清理）
- 新增 27 格档，三档分数翻倍：27格 +4分 / 18格 +8分 / 9格 +16分；挑战书卡片按钮循环切换 27 → 18 → 9
- 快捷栏（0-8）永不禁用；18格=锁背包槽 9-26，9格=锁槽 9-35，27格=锁槽 9-17
- 服务端每 tick 兜底清扫非点击途径进入锁定格的物品（移回可用区，放不下丢地上）

### 配套修复
- 修复单机模式下锁定判定整体失效：集成服务器与客户端共用 JVM，服务端注册会覆写共用的静态判定字段 → 拆分为 clientChecker / serverChecker 双实现，按玩家所在侧分流；同时修复了客户端预测不拦截导致的"放入弹回"
- 修复客户端 mixin 配置：客户端 mixin 移入独立 `survival-contract.client.mixins.json`（修正包路径与资源重复导致的启动崩溃/打包失败）

## 2026-10-06

### 规则与玩法
- 死限规则（「不能被xx杀死」系）判定重做：受伤/氧气触发 + 首次回满血 + 描述更新（修复岩浆不死）
- 团队模式遭遇事件改为每玩家独立计时，只触发自己点亮的事件
- 旁观者模式锁定：游戏进行中加入或管理员切换后锁定旁观（规则为空，结束随全员回大厅）
- 所有玩家的加分/扣分均在聊天栏广播
- 「久病成患」板块规则分值翻倍（红字除外）
- 「血量上限下降」「挖掘速度下降」支持自定义百分比数值
- 「低血糖」改为不扣分，删除扣分文本说明
- 「环境恶化」选择后正确加分，且分值提高
- 「脆骨症」修复"主动攻击概率反弹自身一半伤害"未生效
- 系统消息不再播报时间/天气变化提醒（防刷屏）

### 职业与界面
- 「愚者」改名「行商」：猪灵交易结束 15% 概率随机获得四种礼物之一（哭泣黑曜石 2-4 / 末影珍珠 2-4 / 灵魂急行 I 铁靴 / 抗火药水），礼物直接放入物品栏、不生成掉落物（修复原交易掉落复制刷物卡服）
- 修复愚者不能右键村民打开交易界面
- 工匠初始物品：附魔台改为砂轮
- 挑战书初始菜单：支持鼠标滚轮滚动、"开始游戏"改投票制、新增团队箱按钮
- 职业选择界面改为双列布局，适配窗口显示
