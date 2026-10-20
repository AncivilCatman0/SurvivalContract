package com.survivalcontract.challenge;

/**
 * Represents a single challenge/constraint in Survival Contract.
 * Each challenge has:
 * - An ID for identification
 * - A display name
 * - A description
 * - A base score (different challenges give different initial scores!)
 * - A penalty score (deducted when violated)
 * - An icon (ItemStack/Identifier for the challenge book and HUD)
 * - A category (what kind of constraint it is)
 * - Whether it can be customized (e.g., damage percentages)
 */
public class Challenge {
	public enum Category {
		ITEM_RESTRICTION,    // Can't use XX item/block
		KILL_RESTRICTION,    // Can't kill XX mob
		DEATH_RESTRICTION,   // Can't be killed by XX
		RED_TEXT,            // 红字规则：选择时直接扣分（baseScore 为负），取消后恢复
		ACQUISITION_BLOCK,   // Can't obtain XX
		ATTRIBUTE_PENALTY,   // Player stats lowered (attack damage, armor, speed, etc.)
		MOVEMENT_PENALTY,    // Can't jump, oxygen reduced, etc.
		FOOD_PENALTY,        // Hunger/saturation reduced, can't eat certain foods
		PASSIVE_MOB_HOSTILE, // Neutral mobs become hostile
		FRIENDLY_MOB_COUNTER,// Friendly mobs fight back
		POTION_RESTRICTION,  // Can't use potions (scout profession bypasses)
		RULE                 // 游戏规则开关（PVP/夜视/保留物品栏等），不计分
	}

	private final String id;
	private final String displayName;
	private final String description;
	private final int baseScore;       // Score gained when selecting this challenge
	private final int penaltyScore;    // Score deducted when violating it
	private final Category category;
	private final String iconItemId;   // Item ID for icon (e.g., "minecraft:stick")
	private boolean enabled = false;

	// Customizable values (e.g., 50% damage reduction)
	private int customValue = 0;
	private boolean customizable = false;
	private String customValueLabel = "";

	// 无违规扣分规则（违反文本不显示、getPenaltyScore 恒 0）
	private boolean noViolation = false;

	// 规则板块（挑战书分组）：contract=与神合约（默认）、disease=久病成患、environment=环境恶化（全员共享）
	private String section = "contract";

	// 冲突键：描述本规则禁止的物品/方块/行为（如 "item:minecraft:cooked_beef"、"block:minecraft:furnace"、
	// "mine:minecraft:diamond_ore"、"craft:minecraft:chest"），专属任务按这些键过滤候选
	private java.util.Set<String> conflictKeys = java.util.Set.of();

	public Challenge(String id, String displayName, String description,
					 int baseScore, int penaltyScore, Category category,
					 String iconItemId) {
		this.id = id;
		this.displayName = displayName;
		this.description = description;
		this.baseScore = baseScore;
		this.penaltyScore = penaltyScore;
		this.category = category;
		this.iconItemId = iconItemId;
	}

	// Getters
	public String getId() { return id; }
	public String getDisplayName() { return displayName; }
	public String getDescription() { return description; }
	public int getBaseScore() { return baseScore; }
	public int getPenaltyScore() {
		return noViolation ? 0 : Math.max(1, baseScore - 1); // 违规扣分 = 加分 - 1（每玩家每规则一局仅扣一次）
	}

	/** 无违规扣分标记（如贫瘠铁矿：纯掉落概率规则，违反文本显示为空、扣分恒 0） */
	public boolean isNoViolation() { return noViolation; }
	public void setNoViolation(boolean v) { this.noViolation = v; }
	public Category getCategory() { return category; }
	public String getIconItemId() { return iconItemId; }
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }

	public boolean isCustomizable() { return customizable; }
	public void setCustomizable(boolean customizable) { this.customizable = customizable; }

	public int getCustomValue() { return customValue; }
	public void setCustomValue(int customValue) { this.customValue = customValue; }

	public String getCustomValueLabel() { return customValueLabel; }
	public void setCustomValueLabel(String label) { this.customValueLabel = label; }

	public java.util.Set<String> getConflictKeys() { return conflictKeys; }
	public void setConflictKeys(String... keys) { this.conflictKeys = java.util.Set.of(keys); }

	/** 规则板块 id（contract/disease/environment），未指定的默认"与神合约" */
	public String getSection() { return section; }
	public void setSection(String section) { this.section = section; }
}
