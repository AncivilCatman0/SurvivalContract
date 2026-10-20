package com.survivalcontract.api;

import com.survivalcontract.SurvivalContract;
import com.survivalcontract.game.GameStateManager;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * 附属玩法注册表。
 * <p>
 * 由 {@link SurvivalContract#onInitialize()} 采集自定义入口点
 * {@code survival-contract:playstyle} 填充；主/客户端共用同一份静态列表
 * （自定义入口点在两端都会加载），因此客户端挑战书屏可直接读取。
 */
public final class PlayStyleRegistry {

	/** 自定义入口点名（附属模组在 fabric.mod.json 的 entrypoints 里使用该键） */
	public static final String ENTRYPOINT_KEY = "survival-contract:playstyle";

	private static final List<ScPlayStyleProvider> PROVIDERS = new ArrayList<>();

	private PlayStyleRegistry() {}

	/** 注册一个附属玩法（按 id 去重，重复注册忽略） */
	public static void register(ScPlayStyleProvider provider) {
		if (provider == null || provider.id() == null || provider.id().isBlank()) return;
		if (byId(provider.id()) != null) return;
		PROVIDERS.add(provider);
		SurvivalContract.LOGGER.info("Registered addon play style: {} ({})",
				provider.id(), provider.displayNameKey());
	}

	/** 采集全部自定义入口点（宿主 onInitialize 调用；失败不影响主流程） */
	public static void loadEntrypoints() {
		try {
			for (var container : FabricLoader.getInstance()
					.getEntrypointContainers(ENTRYPOINT_KEY, ScPlayStyleProvider.class)) {
				register(container.getEntrypoint());
			}
		} catch (Throwable t) {
			SurvivalContract.LOGGER.warn("Failed to load addon play style entrypoints", t);
		}
	}

	/** 全部已注册的附属玩法 */
	public static List<ScPlayStyleProvider> all() {
		return List.copyOf(PROVIDERS);
	}

	/** 按 id 查找（未注册返回 null） */
	public static ScPlayStyleProvider byId(String id) {
		if (id == null) return null;
		for (ScPlayStyleProvider p : PROVIDERS) {
			if (p.id().equalsIgnoreCase(id)) return p;
		}
		return null;
	}

	/** 当前对局选中的附属玩法（非附属玩法对局返回 null） */
	public static ScPlayStyleProvider selected() {
		GameStateManager gsm = GameStateManager.getInstance();
		return gsm.isAddon() ? byId(gsm.getAddonStyleId()) : null;
	}

	/** 清空注册表（仅供自测在用例结束后复位） */
	public static void clear() {
		PROVIDERS.clear();
	}
}
