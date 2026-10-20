package com.survivalcontract.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.survivalcontract.game.GameStateManager;
import com.survivalcontract.SurvivalContract;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.Comparator;
import java.util.List;

/**
 * 生存契约自定义指令。
 * <ul>
 *   <li>{@code /sc tp [玩家]} —— 非作弊指令（所有玩家可用）：传送到同队队友身边。
 *       不带参数传送到最近的队友；带参数使用原版 {@code EntityArgument}（与 /tp 一致，
 *       输入时弹出在线玩家名选择框），且必须指定同队玩家。</li>
 *   <li>{@code /sc endgame} —— 管理员指令（需 OP）：立即强制结束当前游戏。</li>
 *   <li>{@code /sc addscore <player> <score>} —— 管理员作弊指令（需 OP）：给指定玩家加分；
 *       常规模式下无效（拒绝执行），仅特殊玩法（神的谎言/走私犯与检察官/文明时代）生效。</li>
 *   <li>{@code /sc spectate} —— 所有人可用：大厅观战模式开关。</li>
 *   <li>{@code /sc style [玩法键]} —— 管理员指令（需 GameMaster 权限）：**在大厅或对局进行中**切换玩法
 *       （NORMAL/GODS_LIES/STOWAWAY/CIVILIZATION + 已注册的附属玩法 id，如 {@code hell-kitchen}）；
 *       不带参数列出可选玩法。对局中切到附属玩法会立即初始化（传送固定地图 + 生成套餐）。</li>
 * </ul>
 */
public class ScCommands {

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				registerSc(dispatcher));
		SurvivalContract.LOGGER.info("SC commands registered (/sc tp, /sc endgame, /sc addscore, /sc spectate, /sc style)");
	}

	private static void registerSc(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> sc = net.minecraft.commands.Commands.literal("sc")
				// /sc tp [player] —— 所有人可用；player 用原版 EntityArgument（玩家名自动补全选择框）
				.then(net.minecraft.commands.Commands.literal("tp")
						.executes(ctx -> teleportToNearestTeammate(ctx))
						.then(net.minecraft.commands.Commands.argument("player", EntityArgument.player())
								.executes(ctx -> teleportToPlayer(ctx,
										EntityArgument.getPlayer(ctx, "player")))))
				// /sc endgame —— 仅管理员
				.then(net.minecraft.commands.Commands.literal("endgame")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
						.executes(ScCommands::forceEndGame))
				// /sc addscore <player> <score> —— 仅管理员的作弊指令：给指定玩家加分（常规模式下无效）
				.then(net.minecraft.commands.Commands.literal("addscore")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
						.then(net.minecraft.commands.Commands.argument("player", EntityArgument.player())
								.then(net.minecraft.commands.Commands.argument("score",
												com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
										.executes(ctx -> cheatAddScore(ctx,
												EntityArgument.getPlayer(ctx, "player"),
												com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "score"))))))
				// /sc spectate —— 所有人可用：大厅观战模式开关（分数清零、不参与分队与投票）
				.then(net.minecraft.commands.Commands.literal("spectate")
						.executes(ScCommands::toggleSpectate))
				// /sc style [玩法键] —— 仅管理员：大厅或**对局进行中**切换玩法（含附属玩法 id）
				.then(net.minecraft.commands.Commands.literal("style")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
						.executes(ScCommands::listPlayStyles)
						.then(net.minecraft.commands.Commands.argument("key",
										com.mojang.brigadier.arguments.StringArgumentType.word())
								.suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider
										.suggest(playStyleKeys(), builder))
								.executes(ctx -> switchPlayStyle(ctx,
										com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "key")))));
		dispatcher.register(sc);
	}

	/** 传送到指定的同队玩家（目标由原版 EntityArgument 解析，支持玩家名选择框与 @ 选择器）。 */
	private static int teleportToPlayer(CommandContext<CommandSourceStack> ctx, ServerPlayer target)
			throws CommandSyntaxException {
		ServerPlayer source = ctx.getSource().getPlayer();
		if (source == null) {
			ctx.getSource().sendFailure(Component.translatable("survival-contract.msg.player_only"));
			return 0;
		}
		GameStateManager gsm = GameStateManager.getInstance();
		if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) {
			source.sendSystemMessage(Component.translatable("survival-contract.msg.tp_playing_only"));
			return 0;
		}
		if (target == source) {
			source.sendSystemMessage(Component.translatable("survival-contract.msg.tp_self"));
			return 0;
		}
		if (gsm.getPlayerTeam(target.getUUID()) != gsm.getPlayerTeam(source.getUUID())) {
			source.sendSystemMessage(Component.translatable("survival-contract.msg.tp_not_teammate",
					target.getName().getString()));
			return 0;
		}
		doTeleport(source, target);
		return 1;
	}

	/** 无参数：传送到最近的队友（NONE 队伍也算同队，即个人模式下可传送到任意玩家）。 */
	private static int teleportToNearestTeammate(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer source = ctx.getSource().getPlayer();
		if (source == null) {
			ctx.getSource().sendFailure(Component.translatable("survival-contract.msg.player_only"));
			return 0;
		}
		GameStateManager gsm = GameStateManager.getInstance();
		if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) {
			source.sendSystemMessage(Component.translatable("survival-contract.msg.tp_playing_only"));
			return 0;
		}

		List<ServerPlayer> all = ctx.getSource().getServer().getPlayerList().getPlayers();
		var myTeam = gsm.getPlayerTeam(source.getUUID());

		// 无参数：最近的队友（NONE 队伍也算同队，即个人模式下可传送到任意玩家）
		ServerPlayer nearest = all.stream()
				.filter(p -> p != source)
				.filter(p -> gsm.getPlayerTeam(p.getUUID()) == myTeam)
				.min(Comparator.comparingDouble(p -> p.distanceToSqr(source)))
				.orElse(null);
		if (nearest == null) {
			source.sendSystemMessage(Component.translatable("survival-contract.msg.tp_no_teammate"));
			return 0;
		}
		doTeleport(source, nearest);
		return 1;
	}

	private static void doTeleport(ServerPlayer source, ServerPlayer target) {
		ServerLevel targetLevel = (ServerLevel) target.level();
		boolean ok = source.teleportTo(targetLevel,
				target.getX(), target.getY(), target.getZ(),
				java.util.Set.of(), target.getYRot(), target.getXRot(), false);
		if (ok) {
			source.sendSystemMessage(Component.translatable("survival-contract.msg.tp_done",
					target.getName().getString()));
			target.sendSystemMessage(Component.translatable("survival-contract.msg.tp_arrived",
					source.getName().getString()));
		} else {
			source.sendSystemMessage(Component.translatable("survival-contract.msg.tp_failed"));
		}
	}

	/** /sc spectate：大厅观战模式开关。开启：进入旁观者、分数清零、退出分队且不参与投票；再次输入解除。 */
	private static int toggleSpectate(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer source = ctx.getSource().getPlayer();
		if (source == null) {
			ctx.getSource().sendFailure(Component.translatable("survival-contract.msg.player_only"));
			return 0;
		}
		GameStateManager gsm = GameStateManager.getInstance();
		if (gsm.getCurrentPhase() != GameStateManager.GamePhase.LOBBY) {
			source.sendSystemMessage(Component.translatable("survival-contract.msg.spectate_lobby_only"));
			return 0;
		}
		MinecraftServer srv = ctx.getSource().getServer();
		java.util.UUID id = source.getUUID();
		if (gsm.isLobbySpectator(id)) {
			// 解除观战：回大厅生存（冒险）模式，团队模式立即重新分队
			gsm.removeLobbySpectator(id);
			source.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
			if (gsm.isTeamMode()) {
				com.survivalcontract.team.TeamManager.getInstance().assignMissing(srv);
				com.survivalcontract.team.TeamManager.getInstance().applyTeamColors(srv);
			}
			for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
				p.sendSystemMessage(Component.translatable("survival-contract.msg.spectate_left",
						source.getName().getString()));
			}
		} else {
			// 进入观战：分数清零 + 移出红蓝队 + 旁观者模式
			gsm.addLobbySpectator(id);
			gsm.setScore(id, 0);
			com.survivalcontract.team.TeamManager.getInstance().removePlayer(srv, id);
			source.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
			for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
				if (p == source) {
					p.sendSystemMessage(Component.translatable("survival-contract.msg.spectate_joined_self"));
				} else {
					p.sendSystemMessage(Component.translatable("survival-contract.msg.spectate_joined",
							source.getName().getString()));
				}
			}
			com.survivalcontract.network.NetworkRegistry.broadcastScores(srv); // 计分板立即刷为 0
		}
		return 1;
	}

	/** /sc addscore 入口：解析执行者后走核心逻辑。 */
	private static int cheatAddScore(CommandContext<CommandSourceStack> ctx, ServerPlayer target, int amount) {
		addScoreCore(ctx.getSource().getPlayer(), target, amount, ctx.getSource().getServer());
		return 1;
	}

	/**
	 * 作弊加分核心逻辑（public 供自测直接调用）：
	 * 常规模式（PlayStyle.NORMAL）下无效——拒绝执行、分数不变；
	 * 特殊玩法（神的谎言/走私犯与检察官/文明时代）且执行者具备管理员权限（由指令 .requires 保证）时生效。
	 *
	 * @param executor 执行者（控制台可为 null）；target 目标玩家；amount 加分数（≥1）；srv 用于广播
	 * @return 1=已加分 0=被拒绝
	 */
	public static int addScoreCore(ServerPlayer executor, ServerPlayer target, int amount, MinecraftServer srv) {
		GameStateManager gsm = GameStateManager.getInstance();
		if (gsm.getPlayStyle() == GameStateManager.PlayStyle.NORMAL) {
			Component deny = Component.translatable("survival-contract.msg.cheat_normal_invalid");
			if (executor != null) executor.sendSystemMessage(deny);
			else if (srv != null) srv.getPlayerList().broadcastSystemMessage(deny, false);
			return 0;
		}
		if (target == null) {
			if (executor != null) executor.sendSystemMessage(Component.translatable("survival-contract.msg.target_offline"));
			return 0;
		}
		gsm.addScore(target.getUUID(), amount);
		Component msg = Component.translatable("survival-contract.msg.cheat_add_score",
				target.getName().getString(), amount, gsm.getScore(target.getUUID()));
		if (srv != null) {
			srv.getPlayerList().broadcastSystemMessage(msg, false);
			com.survivalcontract.network.NetworkRegistry.broadcastScores(srv); // 计分板立即刷新
		}
		SurvivalContract.LOGGER.info("[Cheat] {} added {} score to {} via /sc addscore (style={})",
				executor == null ? "console" : executor.getName().getString(),
				amount, target.getName().getString(), gsm.getPlayStyle());
		return 1;
	}

	/** 可选玩法键：内置 4 项 + 已注册的附属玩法 id（自动补全与列表共用） */
	private static List<String> playStyleKeys() {
		List<String> keys = new java.util.ArrayList<>(
				List.of("NORMAL", "GODS_LIES", "STOWAWAY", "CIVILIZATION"));
		for (var p : com.survivalcontract.api.PlayStyleRegistry.all()) keys.add(p.id());
		return keys;
	}

	/** /sc style（无参）：列出可选玩法键 */
	private static int listPlayStyles(CommandContext<CommandSourceStack> ctx) {
		String list = String.join("§7 / §f", playStyleKeys());
		ctx.getSource().sendSuccess(() -> Component.translatable("survival-contract.msg.style_list", list), false);
		return 1;
	}

	/**
	 * /sc style &lt;玩法键&gt;：管理员在大厅或**对局进行中**切换玩法。
	 * 对局进行中切到附属玩法会立即调用其 onGameStart（传送固定地图出生点 + 生成套餐）。
	 */
	private static int switchPlayStyle(CommandContext<CommandSourceStack> ctx, String key) {
		if (!com.survivalcontract.network.NetworkRegistry.isPlayStyleSwitchPhase()) {
			ctx.getSource().sendFailure(Component.translatable("survival-contract.msg.style_phase_invalid"));
			return 0;
		}
		MinecraftServer srv = ctx.getSource().getServer();
		ServerPlayer executor = ctx.getSource().getPlayer();
		if (!com.survivalcontract.network.NetworkRegistry.applyPlayStyle(srv, executor, key)) {
			ctx.getSource().sendFailure(Component.translatable("survival-contract.msg.style_switch_failed", key));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("survival-contract.msg.style_switched", key), true);
		SurvivalContract.LOGGER.info("Play style switched to '{}' via /sc style", key);
		return 1;
	}

	/** /sc endgame：管理员强制结束游戏。 */
	private static int forceEndGame(CommandContext<CommandSourceStack> ctx) {
		GameStateManager gsm = GameStateManager.getInstance();
		if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) {
			ctx.getSource().sendFailure(Component.translatable("survival-contract.msg.no_active_game"));
			return 0;
		}
		var goal = gsm.getCurrentFinalGoal();
		gsm.endGame(goal); // 神的谎言/走私犯与检察官无最终目标 → goal 为 null（文案/结算全链路已做空保护）
		ctx.getSource().sendSuccess(() -> Component.translatable("survival-contract.msg.endgame_forced",
				goal == null ? Component.translatable("survival-contract.gui.goal.none")
						: Component.translatable("survival-contract.gui.goal." + goal.name())), true);
		SurvivalContract.LOGGER.info("Game force-ended via /sc endgame");
		return 1;
	}
}
