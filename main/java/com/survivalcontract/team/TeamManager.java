package com.survivalcontract.team;

import com.survivalcontract.SurvivalContract;
import com.survivalcontract.game.GameStateManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

import java.util.*;

/**
 * Manages team assignments for RED/BLUE mode.
 */
public class TeamManager {
	public static final TeamManager INSTANCE = new TeamManager();

	private final List<UUID> redTeam = new ArrayList<>();
	private final List<UUID> blueTeam = new ArrayList<>();
	private MinecraftServer server;

	private TeamManager() {}
	public static TeamManager getInstance() { return INSTANCE; }

	public void onServerStart(MinecraftServer server) {
		this.server = server;
		redTeam.clear();
		blueTeam.clear();
	}
	public void onServerStop(MinecraftServer server) {
		this.server = null;
	}

	public void randomizeTeams(MinecraftServer server) {
		redTeam.clear();
		blueTeam.clear();
		List<UUID> allPlayers = new ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (GameStateManager.getInstance().isLobbySpectator(p.getUUID())) continue; // 观战者不参与分队
			allPlayers.add(p.getUUID());
		}
		Collections.shuffle(allPlayers);
		int halfSize = (allPlayers.size() + 1) / 2;
		for (int i = 0; i < allPlayers.size(); i++) {
			if (i < halfSize) {
				redTeam.add(allPlayers.get(i));
				GameStateManager.getInstance().setPlayerTeam(allPlayers.get(i), GameStateManager.Team.RED);
			} else {
				blueTeam.add(allPlayers.get(i));
				GameStateManager.getInstance().setPlayerTeam(allPlayers.get(i), GameStateManager.Team.BLUE);
			}
		}
		applyTeamColors(server);
		announceTeams(server);
	}

	/**
	 * 只补齐尚未分配队伍（Team.NONE）的玩家：加入人数较少的一边（随机打破平衡），
	 * 然后刷新计分板染色。开始游戏时对后来加入的玩家调用。
	 */
	public void assignMissing(MinecraftServer server) {
		List<ServerPlayer> missing = new ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (GameStateManager.getInstance().isLobbySpectator(p.getUUID())) continue; // 观战者不参与分队
			if (GameStateManager.getInstance().getPlayerTeam(p.getUUID()) == GameStateManager.Team.NONE) {
				missing.add(p);
			}
		}
		if (missing.isEmpty()) return;
		Collections.shuffle(missing);
		for (ServerPlayer p : missing) {
			GameStateManager.Team team;
			if (redTeam.size() <= blueTeam.size()) {
				team = GameStateManager.Team.RED;
				redTeam.add(p.getUUID());
			} else {
				team = GameStateManager.Team.BLUE;
				blueTeam.add(p.getUUID());
			}
			GameStateManager.getInstance().setPlayerTeam(p.getUUID(), team);
		}
		applyTeamColors(server);
		announceTeams(server);
	}

	/** 大厅观战：把玩家移出红蓝队（内部列表 + 原版计分板染色），队别归 NONE */
	public void removePlayer(MinecraftServer srv, UUID playerId) {
		redTeam.remove(playerId);
		blueTeam.remove(playerId);
		GameStateManager.getInstance().setPlayerTeam(playerId, GameStateManager.Team.NONE);
		try {
			Scoreboard scoreboard = srv.getScoreboard();
			ServerPlayer p = srv.getPlayerList().getPlayer(playerId);
			if (p != null) {
				PlayerTeam cur = scoreboard.getPlayersTeam(p.getScoreboardName());
				if (cur != null) scoreboard.removePlayerFromTeam(p.getScoreboardName(), cur);
			}
		} catch (Exception ignored) {}
	}

	/** 给每个玩家发队伍分配提示 + 提示音 */
	private void announceTeams(MinecraftServer server) {
		GameStateManager gsm = GameStateManager.getInstance();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			GameStateManager.Team team = gsm.getPlayerTeam(p.getUUID());
			if (team == GameStateManager.Team.RED) {
				p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("survival-contract.msg.team_assigned_red"));
				p.playSound(net.minecraft.sounds.SoundEvents.BELL_BLOCK, 1.0f, 0.8f);
			} else if (team == GameStateManager.Team.BLUE) {
				p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("survival-contract.msg.team_assigned_blue"));
				p.playSound(net.minecraft.sounds.SoundEvents.BELL_BLOCK, 1.0f, 1.2f);
			}
		}
	}

	public void applyTeamColors(MinecraftServer server) {
		GameStateManager gsm = GameStateManager.getInstance();
		// 队伍友伤按模式设置（第五十四批）：文明模式队内友伤关闭，其他模式保持原版默认（允许）
		boolean allowFF = !gsm.isCivilization();
		Scoreboard scoreboard = server.getScoreboard();
		PlayerTeam redSB = scoreboard.addPlayerTeam("survival_red");
		redSB.setDisplayName(net.minecraft.network.chat.Component.translatable("survival-contract.gui.team.red_plain"));
		redSB.setColor(Optional.of(TeamColor.RED));
		redSB.setAllowFriendlyFire(allowFF);

		PlayerTeam blueSB = scoreboard.addPlayerTeam("survival_blue");
		blueSB.setDisplayName(net.minecraft.network.chat.Component.translatable("survival-contract.gui.team.blue_plain"));
		blueSB.setColor(Optional.of(TeamColor.BLUE));
		blueSB.setAllowFriendlyFire(allowFF);

		for (UUID playerId : redTeam) {
			ServerPlayer p = server.getPlayerList().getPlayer(playerId);
			if (p != null) scoreboard.addPlayerToTeam(p.getScoreboardName(), redSB);
		}
		for (UUID playerId : blueTeam) {
			ServerPlayer p = server.getPlayerList().getPlayer(playerId);
			if (p != null) scoreboard.addPlayerToTeam(p.getScoreboardName(), blueSB);
		}
	}

	public void clearTeamColors(MinecraftServer server) {
		Scoreboard scoreboard = server.getScoreboard();
		PlayerTeam redTeamObj = scoreboard.getPlayerTeam("survival_red");
		PlayerTeam blueTeamObj = scoreboard.getPlayerTeam("survival_blue");
		for (UUID playerId : redTeam) {
			ServerPlayer p = server.getPlayerList().getPlayer(playerId);
			if (p == null || redTeamObj == null) continue;
			// 26.2：对不在该队的玩家调用 removePlayerFromTeam 会抛 IllegalStateException
			// （玩家可能已掉线重连/被 vanilla 清除队伍），必须先校验当前所属队伍
			var current = scoreboard.getPlayersTeam(p.getScoreboardName());
			if (redTeamObj.equals(current)) scoreboard.removePlayerFromTeam(p.getScoreboardName(), redTeamObj);
		}
		for (UUID playerId : blueTeam) {
			ServerPlayer p = server.getPlayerList().getPlayer(playerId);
			if (p == null || blueTeamObj == null) continue;
			var current = scoreboard.getPlayersTeam(p.getScoreboardName());
			if (blueTeamObj.equals(current)) scoreboard.removePlayerFromTeam(p.getScoreboardName(), blueTeamObj);
		}
		redTeam.clear();
		blueTeam.clear();
	}

	/** 彻底清空队伍：移除计分板染色 + 清空 GameStateManager 队伍分配（切回单人模式 / 游戏重置时调用） */
	public void clearTeams(MinecraftServer server) {
		clearTeamColors(server);
		GameStateManager.getInstance().resetPlayerTeams();
	}

	public List<UUID> getRedTeam() { return new ArrayList<>(redTeam); }
	public List<UUID> getBlueTeam() { return new ArrayList<>(blueTeam); }
	public boolean isInRedTeam(UUID p) { return redTeam.contains(p); }
	public boolean isInBlueTeam(UUID p) { return blueTeam.contains(p); }

	public int getTeamScore(GameStateManager.Team team) {
		int total = 0;
		List<UUID> members = (team == GameStateManager.Team.RED) ? redTeam : blueTeam;
		for (UUID id : members) total += GameStateManager.getInstance().getScore(id);
		return total;
	}

	public Map.Entry<net.minecraft.core.BlockPos, net.minecraft.core.BlockPos> calculateTeamSpawns() {
		net.minecraft.core.BlockPos center = new net.minecraft.core.BlockPos(0, 64, 0);
		// 两队重生点间隔 15 区块（240 格）：z+120 / z-120
		return new AbstractMap.SimpleEntry<>(
			new net.minecraft.core.BlockPos(center.getX(), center.getY(), center.getZ() + 120),
			new net.minecraft.core.BlockPos(center.getX(), center.getY(), center.getZ() - 120)
		);
	}
}
