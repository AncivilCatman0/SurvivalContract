# 代码改动日志

> 约定：自 2026-10-15 起，每次代码改动在本文件顶部追加一条记录（最新在最上）。
> 每条包含：日期、改动文件、改动内容、验证方式。

## 2026-10-18 ｜ 附属玩法观战落点委派（`onSpectatorJoin`）+ 旁观者自愈排除附属

**背景**：对局进行中中途加入服务器的玩家，被 SC 的观战分流送到了竞技场兜底世界（≈普通主世界），没有像内置玩法那样进入「本玩法地图的观战状态」。附属玩法（地狱厨房）用固定地图、**没有竞技场**，其观战落点必须由附属模组自己决定。

**改动**
- [ScPlayStyleProvider.java](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/api/ScPlayStyleProvider.java#L55-L64)：新增默认钩子 `boolean onSpectatorJoin(MinecraftServer server, ServerPlayer player)`——返回 `true` 表示附属玩法已接管（自行设旁观者模式并传送到本玩法地图观战点），`false` 走宿主默认（竞技场出生点上空观战）
- [MainEventListener.java](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/MainEventListener.java#L1532-L1548)：`teleportSpectatorToArena` 在「登记 `MID_GAME_SPECTATORS`」之后、送竞技场之前插入附属分支——`gsm.isAddon()` 且 `onSpectatorJoin` 返回 true 时跳过竞技场默认落点（仍照常清挑战 + 发提示 + `sendSync`），与 `teleportToPlayingSpawn` 既有的 `isAddon()` 短路同一思路
- 旁观者「位置自愈」循环（`MainEventListener` ~L880）加 `!gsm.isAddon()` 条件：附属玩法下不再把观战者拉回竞技场，避免与附属落点互相拉人
- `ServerSelfTest.testBatch74Features` 新增 3 条断言：`观战委派前置`（两名假人均不在分数表）、`观战分流委派`（返回 true → 钩子被调用且宿主默认落点未执行）、`观战分流回退`（返回 false → 玩家被置为旁观者）

**验证**
- `build "-Pmc_target=26.2"` / `"-Pmc_target=26.3"` ✅ 并 `deployJar`
- 自测（`SC_SERVERTEST=1` + `runServer`）：**26.2 = 6519 PASS / 0 FAIL**、**26.3 = 6522 PASS / 0 FAIL**
- 已同步用户客户端实例 `E:\bingo\.minecraft\versions\生存合约\mods\survival-contract-1.0.0-mc26_2.jar`

---

## 2026-10-18 ｜ 附属玩法扩展点（`com.survivalcontract.api`）+ 挑战书「最终目标」位让位给附属选项取值名

**背景**：地狱厨房（HK，独立工程 `f:\survivalcontract\hk-mod`）作为 SC 附属玩法，需要 SC 提供注册入口与二级菜单选项渲染能力。

**附属玩法扩展点（本次一并记录，此前改动未入日志）**
- 新增 `com.survivalcontract.api`：`ScPlayStyleProvider`（`id` / `displayNameKey` / `displayNameFallback` / `forceTeamMode` / `ui()` / `options()` / 生命周期 `onGameStart` `onServerTick` `onPlayerJoin` `onGameEnd`）、`StyleUi`（5 项抑制开关：规则卡 / 职业 / 团队箱 / 最终目标 / 专属任务）、`StyleOption`（整数循环按钮）、`PlayStyleRegistry`
- Fabric 自定义入口点 `survival-contract:playstyle`：附属模组在 `fabric.mod.json` 声明入口即被 SC 扫描注册（日志 `Registered addon play style: <id>`）
- `GameStateManager` 增补：`selectAddonStyle(id)` / `isAddon()` / `getAddonProvider()` / `getStyleOption(key, default)` / `setStyleOption(key, value)`
- 网络：`LobbyStatePayload.styleOptions`（S2C 同步选项值）+ C2S `StyleOptionPayload(styleId, key, value)`（服务端按 `StyleOption.clamp` 校验，越界丢弃）
- 客户端 [ChallengeBookScreen.java](file:///f:/survivalcontract/sc-mod/src/client/java/com/survivalcontract/client/gui/ChallengeBookScreen.java) 大厅初始菜单 + 二级菜单——第一行 4 列（职业 / 团队箱 / 「最终目标」位 / 时长），第二行 3 个规则开关 + 第 4 位（原「双禁」位）

**本次改动：`StyleOption` 支持取值名 + 「最终目标」位渲染附属选项**
- [StyleOption.java](file:///f:/survivalcontract/sc-mod/src/main/java/com/survivalcontract/api/StyleOption.java)：record 扩为 8 参 `(key, labelKey, labelFallback, min, max, defaultValue, step, List<String> valueLabels)`，保留 7 参便捷构造（`valueLabels = List.of()`）；新增 `valueLabelKey(int)` —— 按下标 `clamp(value) - min` 取本地化键，越界/空列表返回 `null`
- 二级菜单第一行「最终目标」位：选中附属玩法且其 `options().size() >= 2` 时渲染 `options().get(1)`（此前该位只有「§8无最终目标」占位）→ 地狱厨房在此显示「地图类型：街角 / 水族馆」
- 第二行第 4 位（原「双禁」位）内联逻辑抽为 `buildStyleOptionButton(StyleOption, ScPlayStyleProvider, x, y, w)` 并复用（乐观更新 `ClientState.styleOptions` + 发 `StyleOptionPayload`）
- `styleOptionLabel(StyleOption)`：`valueLabelKey(v)` 非空 → `Component.translatable(labelKey, Component.translatable(valueLabel))` 渲染取值名；失败回退 `labelFallback.replace("%s", 取值)`；为 `null` 时保持原数字显示（`groupsToWin` 等不受影响）
- `ServerSelfTest.testBatch74Features` 新增 2 条断言：`选项取值名映射`（8 参构造 `valueLabelKey(0/1)` 命中、`next(1)=0` 回落、`clamp(7)=1`）、`无取值名时回退数字显示`（7 参构造 `valueLabelKey(3)` 返回 `null`）

**验证**
- `gradlew build -Pmc_target=26.2` / `-Pmc_target=26.3` ✅ BUILD SUCCESSFUL（产物已 `deployJar` 到 `f:\survivalcontract\mc26.2|mc26.3\`）
- 自测（`SC_SERVERTEST=1` + `runServer`）：**26.2 = 6522 PASS / 0 FAIL**、**26.3 = 6518 PASS / 0 FAIL**
- 已同步用户客户端实例 `E:\bingo\.minecraft\versions\生存合约\mods\survival-contract-1.0.0-mc26_2.jar`（MC 26.2）

---

## 2026-10-16 ｜ 双版本构建体系（MC 26.2 + 26.3）+ McCompat 反射兼容层

**核心改动**：模组同时支持 Minecraft 26.2 和 26.3，默认一次构建两个版本，产物分目录/分后缀存放。

**构建体系**
- `build.gradle` 新增 `ext.mcVersions` 版本映射表（26.2→loader 0.19.3/fabric-api 0.161.0+26.2；26.3→loader 0.19.5/fabric-api 0.162.0+26.3），读 `-Pmc_target=26.2/26.3` 参数切换
- `gradle.properties` 新增 `mc_target=26.2` 默认值
- 新建 `build-all.ps1`：顺序打 26.2 + 26.3，失败即停
- `deployJar` 任务自动部署到 `f:\survivalcontract\mc{版本号}\`，jar 名带 `mc{版本}` 后缀

**API 兼容层（McCompat.java）**
Minecraft 26.2→26.3 原版 API 断裂 6 处，全部用反射封装进 `McCompat`：
1. `Player.drop(s, boolean)` → 26.3 Player 基类无 drop()，ServerPlayer 有 `drop(s, boolean, Prediction)`；26.2 ServerPlayer 有 `drop(s, boolean, boolean)`
2. `entity.setInvulnerable(boolean)` → 26.3 拆成 `setPermanentlyInvulnerable(boolean)` + `setInvulnerableTime(int)`
3. `BundleContents.itemCopyStream()` → 重命名为 `itemCopies()`
4. `entity.invulnerableTime`（public 字段）→ 变成 private，只能 `getInvulnerableTime()`/`setInvulnerableTime(int)`
5. `new BundleContents.Mutable(bc)` → 26.3 换 `bc.asMutable()`
6. Client 侧 `GLFW.GLFW_KEY_*` → 26.3 去掉 lwjgl-glfw 改用 SDL3，两个版本都用 `InputConstants.KEY_*`

**改的文件**：McCompat.java（新建）、CivilizationManager、GameStateManager、LobbyManager、MainEventListener、MerchantResultSlotMixin、NetworkRegistry、ServerSelfTest、SmuggleManager、RulePreviewScreen.java（client）、SurvivalContractClient.java（client）

**网络问题**：fabricmc.net 对当前 IP 系统阻断 GET 大文件（HEAD 200 OK 但 GET 全超时）。26.3 fabric-api jar（1.7MB）通过 Modrinth CDN（`cdn.modrinth.com`）成功下载进 Gradle 缓存，hash 校验通过。

**验证**：
- `gradlew build -Pmc_target=26.2` ✅ BUILD SUCCESSFUL
- `gradlew build -Pmc_target=26.3` ✅ BUILD SUCCESSFUL
- `build-all.ps1` ✅ MC 26.2 OK + MC 26.3 OK
- 产物：`f:\survivalcontract\mc26.2\survival-contract-1.0.0-mc26_2.jar`（2717 KB）、`f:\survivalcontract\mc26.3\survival-contract-1.0.0-mc26_3.jar`（2717 KB）

---

## 2026-10-16 ｜ 走私犯检察官玩法重写：只看价值袋 + 批次感知过期 + 走私品批次上限

**需求**（用户 6 条规则）
1. 玩家所有价值物品（含走私品）**仅检测价值收纳袋**里的；物品栏散装不动、不计价、不被清除。
2. 受贿：贿赂袋清空→检察官加分；价值袋清空→被检查者加分。
3. 检查无走私品：价值袋清空→被检查者加分；检察官赔价值袋总价 1/4。
4. 检查有走私品：没收价值袋所有物品给检察官加分；贿赂袋不受影响。
5. 批次更换后，之前出现的货物（公开商品+走私品）**全部失效 0 分**（袋子仍清空——袋子语义）。
6. 走私品每批次每位玩家**最多交易一次**（受贿换出/查获没收均算一次，第二次 0 分）。

**核心精简**（与旧实现的本质区别）
- 砍：所有 `inv.getContainerSize()` 散装遍历（goodsTotal/hasSmuggleGood/confiscateAllGoods/redeemGoodsAndLoose）。
- 砍：静态 `VALUE_TABLE`（旧模型"公开商品池全量 8/4/2 + 走私品 128，批次只用来换走私品和 3 件公开展示"）。
- 保留：贿赂袋静态 `BRIBE_VALUE_TABLE`（公开商品 8/4/2，不受批次约束；用户未改贿赂袋规则）。

**改动文件**
- 改 `smuggle/SmuggleManager.java`：
  - 新增字段 `Map<String, int[]> batchScores`（每批次 3 件公开商品的独立分值）+ `Map<String, Set<UUID>> smuggleRedeemedThisBatch`（走私品批次上限登记）。
  - 改 `refreshBatch()`：同步生成分值数组，每批最多 1 个 8 分桶（独立随机 8/4/2 降级同原规则），换批时清空走私品交易记录。
  - 新方法 `itemValueForGroup(groupKey, item)`：只认该组当前批次 3 件公开商品（按独立分值）+ 当前走私品（128）；其余全 0。
  - 改 `goodsTotal(player)`：只算 `bagTotal(valueBags)`，砍物品栏循环。
  - 改 `hasSmuggleGood(player)`：只查价值袋里是否有当前批次走私品。**物品栏里塞 100 个走私品不算走私证据**，必须进价值袋才暴露。
  - 改 `settleAcceptBribe` / `settleInspect` / `confiscateValueBagOnly` / `redeemValueBag`：全部砍散装遍历，仅清价值袋容器+计价。
  - 抽通用结算 `processBagAndClear(bag, groupKey, playerId)`：批次感知计价 + 袋子清空 + 走私品批次上限登记（一场结算后本批该玩家走私品就锁定 0 分）。
  - 改 `sendSync()`：公开商品分值从 `batchScores` 取独立分值（非静态表），客户端显示每件物品的实际批次分值。
  - 改 `reset()` / `setBatchForTest()`：同步清/设 batchScores + smuggleRedeemedThisBatch。
- 改 `ServerSelfTest.java`：
  - 重写受贿/无走私品放行/查获走私 3 个检查分支：固定批次 → 断言价值袋兑换分/散装保留 → 6 条 PASS。
  - 新增 4 条批次感知自测：① 价值袋兑换批次感知 ② 散装全程不动（hasSmuggleGood 只看价值袋）③ 走私品批次上限（本批两次结算第二次 0 分）④ 批次更换后旧货物 0 分（袋子仍清空）。
  - 修 setBatchForTest 默认批次分值、valueOf→publicValueOf、redeemGoodsAndLooseForTest→redeemValueBagForTest。

**验证**
- `gradlew build` 通过；`SC_SERVERTEST=1 gradlew runServer` **6479 PASS / 0 FAIL**（比之前 +3 条 PASS——新增批次感知/散装不动/批次过期断言）。
- 客户端右键检查发起书发包链路未变（沿用原 OpenProsecutePayload），服务端结算逻辑全自动验证通过。

## 2026-10-15 ｜ 专属任务不与已选规则冲突 + 神的谎言开局发放任务刷新券

**需求**
1. 游戏开始后的玩家专属任务不得与预先选好的规则冲突（例：选了「不能吃烤马铃薯」，就不能刷出「吃 N 个烤马铃薯」类任务，诸如此类）。
2. 神的谎言模式每位玩家开局额外获得 1 个一次性道具，右键消耗后立即刷新自己的当前任务。

**改动文件**
- 改 `challenge/ChallengeManager.java`（`setConflictKeys`）：补全规则→任务冲突键覆盖——附魔台/铁砧/箱子/熔炉/酿造台/重生锚追加 `item:` 键，箱子/熔炉/酿造台的 `craft:`/`item:` 键补齐（任务 CRAFT 同时产出 craft+item 两键；与原有食物禁令、钻石/金/下界合金/青金石挖掘键共同构成完整过滤表）。
- 任务过滤主链路（已有的候选池 `removeIf` 交集过滤，本次保留并验证）：`task/TaskManager.java` 的 `issueNewTask()`（个人）与 `pickRushTaskTemplate()`（任务抢夺按全员规则并集）在候选入池后按 `PlayerTask.conflictKeys()`（item/craft/mine 前缀；KILL_MOB 击杀与"被 XX 杀死"语义不同不产键）逐条比对已选规则，命中即剔除；空池兜底任务「击杀 3 只僵尸」天然无冲突。
- 新增 `TaskManager.refreshTaskNow(ServerPlayer)`：立即重掷当前专属任务（跳过 10 分钟过期等待；旧任务进近期列表防刷回同一个；同 id 兜底时自动再掷一次）；走私犯/文明模式无任务、任务抢夺为全员共享任务，均拒绝单人刷新。
- 改 `game/GameStateManager.java`：新增一次性道具「§e任务刷新券」（时钟 + 自定义名，同神谕纸符的改名原版物品模式）——`createTaskRefreshToken()` 工厂、`consumeTaskRefreshToken()` 背包匹配消耗；死亡保留链路 `modeBookOf()` 追加刷新券（与指认书/神谕纸符一样死亡不掉落、重生返还）。
- 改 `dimension/LobbyManager.java`：神的谎言开局给每位参战玩家（观战者除外）在指认书之后发放 1 个任务刷新券 + 私发提示。
- 改 `network/NetworkRegistry.java`：新增 C2S `use_task_refresh` payload 与服务端处理器——神的谎言 PLAYING 阶段、未淘汰玩家，先复验背包持有并消耗，再 `refreshTaskNow`；刷新失败（无任务等）原样返还；成功播放音效并私发新旧任务对照消息。
- 改客户端 `client/gui/ChallengeBookOpener.java`：手持任务刷新券右键 → 发 `UseTaskRefreshPayload` 并短路原版时钟行为（时钟本身无右键功能，沿用指认书/神谕纸符的客户端探测发包模式）。
- 三份语言文件新增 `godslies.task_refresh_grant`、`godslies.task_refreshed`（zh_cn/zh_tw/en_us）。
- 改 `ServerSelfTest.java`：新增 `testTaskConflictFiltering()`——逐条开启全部约 90 条规则、每条 3 轮 ×12 次发任务，断言所有发出任务与该规则冲突键不相交；全部食物禁令 + 低血糖组合下 USE_ITEM 任务只剩放置石头/火把；96 次发任务内不出现烤马铃薯。神的谎言自测新增刷新券全链路：材质命名、背包消耗一次性、`refreshTaskNow` 旧→新任务切换、无任务失败、死亡拦截/普通时钟不保护/重生返还。

**验证**
- `gradlew build` 通过；`SC_SERVERTEST=1 gradlew runServer` 全套服务端自测 **6476 PASS / 0 FAIL**。
- 客户端右键链路（发包 + 音效 + 消息）与实机开局发放留待实机确认。

## 2026-10-15 ｜ 职业免疫调整：斗士免疫低血糖/脆骨症，矿工免疫尘肺病

**需求**：职业与挑战联动补强——斗士（WARRIOR）免疫「低血糖」「脆骨症」两种疾病；矿工（MINER）免疫「尘肺病」。

**改动文件**
- 改 `profession/ProfessionManager.java`（`isImmuneToChallenge`）：WARRIOR 豁免表追加 `hypoglycemia`、`brittle_bones`（原有 4 项肉食豁免保留）；MINER 豁免表追加 `dust_lung`（原有 no_diamond/no_netherite/no_gold 保留）。
- 改 `MainEventListener.java`，6 个效果施加点统一以 `isImmuneToChallenge` 收口（与斥候/大厨既有豁免模式一致）：
  - 低血糖：进食拦截（低血糖专属白名单外食物阻止进食）、每 tick `addExhaustion(0.0025f)` 消耗翻倍、饱食度≤6 时无限失明+缓慢3，共 3 处斗士免疫门控；
  - 脆骨症：落地摔落补伤（1 格起判）、攻击他人 30% 反伤，共 2 处斗士免疫门控；
  - 尘肺病：Y<0 连续 60 秒施加无限 POISON，1 处矿工免疫门控。
- 改 `ServerSelfTest.java`（第五十四批 + 新增 step 41）：
  - 新增 3 条职业→疾病免疫映射断言（NONE 全不免疫 / 斗士免低血糖+脆骨症不免尘肺 / 矿工免尘肺）；
  - 低血糖行为级对照：反射私有字段 `FoodData.exhaustionLevel`，斗士一次板块 tick 后 exhaustion=0.0、无职业=0.0025；
  - 尘肺行为级验证：尘肺逻辑在 `tickSectionRules` 的 perSecond 门控（tickCount%20==0）之后，反射连调无法推进 tickCount，故改为跨真实整秒 tick 的状态机（反射预置 `dustLungTicks=59` 压缩 60 秒等待）——矿工经历整秒 tick 后计数停在 59、无中毒；切 NONE 后下一整秒计数到 60、获得中毒（对照成立）。

**验证**
- `gradlew build` 通过；`SC_SERVERTEST=1 gradlew runServer` 全套服务端自测 **487 PASS / 0 FAIL**（含本次 6 条新断言：3 映射 + 低血糖行为对照 + 矿工尘肺免疫 + NONE 尘肺对照）。
- 脆骨症摔落补伤/攻击反伤两处为映射级断言覆盖，行为效果留待实机观察。

## 2026-10-15 ｜ 大厅模式改为世界类型显式启用 + 服务端开关 + 信雅互联兼容

**需求**
1. 原先任何新地图创建后都会自动进入大厅并发放挑战书，容易与其他模组冲突。
2. 客户端单人「创建世界 → 世界类型」新增「sc大厅」，仅该类型创建的地图进入大厅、发挑战书；并设为创建界面的默认选中类型。
3. 专用服务器增加独立大厅模式开关配置文件，默认开启。
4. 兼容信雅互联（Sinytra Connector）。

**改动文件**
- 新增 `src/main/resources/data/survival-contract/worldgen/world_preset/sc_lobby.json`：sc大厅世界类型（地形参数与原版默认一致）。
- 新增 `src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json`：把 sc大厅加入世界类型循环列表（append，不替换原版）。
- 新增 `dimension/LobbyMode.java`：大厅模式总开关。专用服务器读 `config/survival-contract/lobby-mode.json`（首次生成、默认开启）；单人读存档标记 `survival-contract/lobby-world.marker`；自测环境强制开启。
- 新增客户端 mixin：`CreateWorldScreenMixin`（两处注入：`@ModifyArg` 把 openFresh 路径的初始预设由 minecraft:normal 替换为 sc大厅，仅标题界面「创建新世界」生效，演示世界/重建世界不受影响；`@Redirect` 在建档落盘、集成服务器启动前写存档标记）与 `CreateWorldScreenAccessor`。均 `require=0`，信雅互联下界面被改造时注入失败不致命。
- 新增 `client/CreateWorldUiTest.java`：SC_UITEST=1 启用的世界创建 UI 端到端自动化（标题界面建两个世界并断言维度/挑战书/marker），与既有 SC_SELFTEST 并存。
- 改 `dimension/LobbyDimensionSetup.java`：SERVER_STARTING 先判定大厅模式，关闭时不注入大厅维度；SERVER_STARTED 关闭时不绑定大厅维度。
- 改 `dimension/LobbyManager.java`：关闭时 `onServerStart` 不回退主世界；`startGame` 守卫拒绝。
- 改 `MainEventListener.java`：JOIN / AFTER_RESPAWN / LOBBY tick（buff、虚空保护、挑战书防丢）全部在大厅模式关闭时短路。
- 改 `SurvivalContract.java`：自测强制开关、关服复位、会话存档恢复/落盘门控、信雅互联环境检测日志。
- 新增 `src/main/resources/META-INF/neoforge.mods.toml`：信雅互联官方 `connector:placeholder=true` 占位元数据（Fabric 忽略；Connector 按 Fabric 模组加载；纯 NeoForge 缺依赖时明确报错）。
- 三份语言文件新增 `generator.survival-contract.sc_lobby`（zh_cn: sc大厅 / zh_tw: sc大廳 / en_us: SC Lobby）。

**验证**
- `gradlew build` 通过。
- 客户端实测（26.2 + 实机同款 Xaero）：
  - 无标记旧存档快速进入 → 日志「大厅模式（单人存档）：关闭」，不传送、不发书、不注入；
  - 带 marker 存档 → 「开启」，注入大厅维度、传送到 survival-contract:lobby；
  - SC_UITEST 全自动建图：创建界面默认世界类型=「sc大厅」；sc大厅建图 → 大厅维度 + 背包有挑战书 + marker 落盘（VERIFIED 1/2）；切回原版默认建图 → 留在 minecraft:overworld、无书、无 marker（VERIFIED 2/2）。
- 专用服务器实测：首次启动自动生成 config/survival-contract/lobby-mode.json（enabled:true + 中文注释）并开启大厅、注入维度；改为 enabled:false 重启后日志「大厅模式（专用服务器）：关闭」、不注入。
- 备注：Gson 配置写入关闭 HTML 转义（注释里的等号不再变 \u003d）。

## 2026-10-15 ｜ Xaero 小地图/世界地图回大厅清扫修复（三次失败根因）

**需求**：每局结束回大厅后自动删除竞技场维度的 Xaero 地图与记录点，下一局从零记录；大厅/原版维度数据保留。

**根因**：结束序列实际为 `PLAYING → FINISHED（5 秒结算）→ LOBBY`，旧触发只认 `PLAYING → LOBBY` 直跳，回大厅清扫从未注册（实机日志 7 次清扫全发生在进 arena 同一秒）。

**改动文件**
- `client/ClientState.java`：进入 LOBBY 即注册回大厅清扫（不挑上一阶段），进入 PLAYING 立即兜底清扫。
- `client/XaeroWaypointCleaner.java`：重写为绝对 tick 调度 + 5s/20s/60s/120s/240s 多轮延迟；断连 3 秒兜底；每轮必打日志；开局清扫作废遗留轮次（防快速重开局误删新局）。
- `client/ClientSelfTest.java`：新增相位驱动的 Xaero 端到端断言。

**验证**：开发环境装入 Xaero minimap 26.5.1 + world-map 1.46.1，日志 `BEFORE=1 → 回大厅命中 1 删除 1（xaero\world-map\<世界>\survival-contract$arena）→ FINAL=0，lobby 保留=1，XAERO SWEEP VERIFIED`。
