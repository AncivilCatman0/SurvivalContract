package com.survivalcontract.client.mixin;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 暴露 CreateWorldScreen 的私有 uiState（判断当前选中的世界类型用） */
@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenAccessor {
	@Accessor("uiState")
	WorldCreationUiState survivalcontract$getUiState();
}
