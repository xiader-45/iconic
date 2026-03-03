package com.iconic;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ModItems {
    // Создаем ключ заранее (обязательно для 1.21.1)
    public static final RegistryKey<Item> CHALK_KEY = RegistryKey.of(
            RegistryKeys.ITEM,
            Identifier.of(Iconic.MOD_ID, "chalk")
    );

    // Создаем предмет с привязкой к ключу и прочностью 64 использования
    public static final Item CHALK = new ChalkItem(
            new Item.Settings().registryKey(CHALK_KEY).maxDamage(128)
    );

    public static void registerModItems() {
        Registry.register(Registries.ITEM, CHALK_KEY, CHALK);
        Iconic.LOGGER.info("Iconic Mod: Chalk registered successfully.");
    }
}