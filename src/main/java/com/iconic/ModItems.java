package com.iconic;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ModItems {

    public static final RegistryKey<Item> CHALK_KEY = RegistryKey.of(
            RegistryKeys.ITEM, Identifier.of(Iconic.MOD_ID, "chalk")
    );

    // Просто создаем предмет, логика подсказки теперь внутри класса ChalkItem
    public static final Item CHALK = new ChalkItem(
            new Item.Settings().registryKey(CHALK_KEY).maxDamage(128)
    );

    // --- РЕГИСТРИРУЕМ РАМКИ ---
    public static final Item WHITE_FRAME = registerFrame("white_frame");
    public static final Item ORANGE_FRAME = registerFrame("orange_frame");
    public static final Item MAGENTA_FRAME = registerFrame("magenta_frame");
    public static final Item LIGHT_BLUE_FRAME = registerFrame("light_blue_frame");
    public static final Item YELLOW_FRAME = registerFrame("yellow_frame");
    public static final Item LIME_FRAME = registerFrame("lime_frame");
    public static final Item PINK_FRAME = registerFrame("pink_frame");
    public static final Item GRAY_FRAME = registerFrame("gray_frame");
    public static final Item LIGHT_GRAY_FRAME = registerFrame("light_gray_frame");
    public static final Item CYAN_FRAME = registerFrame("cyan_frame");
    public static final Item PURPLE_FRAME = registerFrame("purple_frame");
    public static final Item BLUE_FRAME = registerFrame("blue_frame");
    public static final Item BROWN_FRAME = registerFrame("brown_frame");
    public static final Item GREEN_FRAME = registerFrame("green_frame");
    public static final Item RED_FRAME = registerFrame("red_frame");
    public static final Item BLACK_FRAME = registerFrame("black_frame");

    private static Item registerFrame(String name) {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Iconic.MOD_ID, name));
        return Registry.register(Registries.ITEM, key, new Item(new Item.Settings().registryKey(key)));
    }

    public static void registerModItems() {
        Registry.register(Registries.ITEM, CHALK_KEY, CHALK);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(content -> {
            content.addAfter(Items.SHEARS, CHALK);
        });

        Iconic.LOGGER.info("Iconic Mod: Items registered successfully.");
    }
}