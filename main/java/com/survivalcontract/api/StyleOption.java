package com.survivalcontract.api;

import java.util.List;

/**
 * 附属玩法在大厅二级菜单提供的整数选项（循环切换按钮）。
 * 宿主 SC 只负责同步与渲染，含义完全由附属模组定义。
 *
 * @param key          选项唯一键（用于同步与读取，如 "groupsToWin"）
 * @param labelKey     本地化键（客户端按当前语言渲染，可带一个 %s 占位符）
 * @param labelFallback 语言文件缺失时的兜底文案
 * @param min          最小值
 * @param max          最大值
 * @param defaultValue 默认值
 * @param step         每次点击的增量
 * @param valueLabels  可选：每个取值对应的本地化键（下标 0 = min，依次递增）；
 *                     为空时按钮上直接显示数字（如「需要完成的套餐组数：3」），
 *                     非空时显示取值名（如「地图类型：街角」）
 */
public record StyleOption(String key, String labelKey, String labelFallback,
                          int min, int max, int defaultValue, int step,
                          List<String> valueLabels) {

	/** 不带取值名（按钮显示数字）的便捷构造 */
	public StyleOption(String key, String labelKey, String labelFallback,
	                   int min, int max, int defaultValue, int step) {
		this(key, labelKey, labelFallback, min, max, defaultValue, step, List.of());
	}

	/** 把 value 夹到 [min, max] 并对齐 step */
	public int clamp(int value) {
		int v = Math.max(min, Math.min(max, value));
		if (step > 1) v = min + Math.round((v - min) / (float) step) * step;
		return Math.max(min, Math.min(max, v));
	}

	/** 循环取下一个值（到达 max 后回落到 min） */
	public int next(int value) {
		int v = clamp(value) + Math.max(1, step);
		return v > max ? min : v;
	}

	/**
	 * 取值 value 对应的本地化键（客户端据此渲染取值名）。
	 *
	 * @return 取值名键；未提供取值名列表或下标越界时返回 null（调用方回退到数字显示）
	 */
	public String valueLabelKey(int value) {
		if (valueLabels == null || valueLabels.isEmpty()) return null;
		int idx = clamp(value) - min;
		if (idx < 0 || idx >= valueLabels.size()) return null;
		return valueLabels.get(idx);
	}
}
