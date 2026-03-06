package com.iconic.client;

import com.iconic.Iconic;
import com.iconic.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Client-side entry point for the Iconic mod.
 * Handles item tooltips and other client-only logic.
 */
public class IconicClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (stack.isOf(ModItems.CHALK)) {
                lines.add(Text.translatable("item.iconic.chalk.tooltip.1").formatted(Formatting.GRAY));
                lines.add(Text.translatable("item.iconic.chalk.tooltip.2").formatted(Formatting.GRAY));
                lines.add(Text.translatable("item.iconic.chalk.tooltip.3").formatted(Formatting.GRAY));
            }
        });

        Iconic.LOGGER.info("Iconic Mod: Client-side initialized.");
    }
}