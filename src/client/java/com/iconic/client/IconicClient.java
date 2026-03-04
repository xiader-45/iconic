package com.iconic.client;

import com.iconic.Iconic;
import com.iconic.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class IconicClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Здесь мы регистрируем всё, что связано ТОЛЬКО с визуалом игрока
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (stack.isOf(ModItems.CHALK)) {
                lines.add(Text.translatable("item.iconic.chalk.tooltip").formatted(Formatting.GRAY));
            }
        });

        Iconic.LOGGER.info("Iconic Mod: Client-side initialized.");
    }
}