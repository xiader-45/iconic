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

/**
 * Registry class for all custom items in the Iconic mod.
 */
public class ModItems {

    public static final RegistryKey<Item> CHALK_KEY = RegistryKey.of(
            RegistryKeys.ITEM, Identifier.of(Iconic.MOD_ID, "chalk")
    );

    public static final Item CHALK = new ChalkItem(
            new Item.Settings().registryKey(CHALK_KEY).maxDamage(128)
    );

    public static final RegistryKey<Item> FRAME_KEY = RegistryKey.of(
            RegistryKeys.ITEM, Identifier.of(Iconic.MOD_ID, "frame")
    );
    public static final Item FRAME = new FrameItem(new Item.Settings().registryKey(FRAME_KEY));

    public static void registerModItems() {
        Registry.register(Registries.ITEM, CHALK_KEY, CHALK);
        Registry.register(Registries.ITEM, FRAME_KEY, FRAME);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(content -> {
            content.addAfter(Items.SHEARS, CHALK);
        });

        Iconic.LOGGER.info("Iconic Mod: Items registered successfully.");
    }
}