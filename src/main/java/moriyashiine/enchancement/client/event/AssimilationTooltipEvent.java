/*
 * Copyright (c) MoriyaShiine. All Rights Reserved.
 */
package moriyashiine.enchancement.client.event;

import moriyashiine.enchancement.common.Enchancement;
import moriyashiine.enchancement.common.init.ModEnchantments;
import moriyashiine.enchancement.common.tag.ModItemTags;
import moriyashiine.enchancement.common.util.EnchancementUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.TooltipType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class AssimilationTooltipEvent implements ItemTooltipCallback {
	@Override
	public void getTooltip(ItemStack stack, Item.TooltipContext tooltipContext, TooltipType tooltipType, List<Text> lines) {
		if (MinecraftClient.getInstance().cameraEntity instanceof PlayerEntity player && stack.isIn(ModItemTags.CANNOT_ASSIMILATE) && !stack.equals(player.getOffHandStack()) && EnchancementUtil.hasEnchantment(ModEnchantments.ASSIMILATION, player)) {
			lines.add(1, Text.translatable("tooltip." + Enchancement.MOD_ID + ".cannot_assimilate").formatted(Formatting.RED));
		}
	}
}
