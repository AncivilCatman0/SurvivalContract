package com.survivalcontract.api;

/**
 * 附属玩法对挑战书 UI 的抑制开关（哪一块隐藏/锁定）。
 * 全部 false = 与内置玩法一致的完整菜单。
 */
public record StyleUi(boolean suppressRuleCards,
                      boolean lockProfession,
                      boolean disableTeamChest,
                      boolean noFinalGoal,
                      boolean noTasks) {

	/** 无任何抑制（默认） */
	public static final StyleUi NONE = new StyleUi(false, false, false, false, false);
}
