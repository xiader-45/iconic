package com.iconic;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DyeColor;

/**
 * Single frame item - color is stored in DYED_COLOR component, tint applied via model.
 * Use a grayscale texture; the dye color tints it.
 */
public class FrameItem extends Item {

    public FrameItem(Settings settings) {
        super(settings);
    }

    public static ItemStack createStack(DyeColor color) {
        ItemStack stack = new ItemStack(ModItems.FRAME);
        stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(color.getSignColor()));
        return stack;
    }

    /**
     * Retrieves the dye color from an item stack.
     * @return the DyeColor or null if not dyed.
     */
    public static DyeColor getColor(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(ModItems.FRAME)) return null;
        
        DyedColorComponent component = stack.get(DataComponentTypes.DYED_COLOR);
        if (component == null) return null;
        
        int rgb = component.rgb();
        for (DyeColor color : DyeColor.values()) {
            if (color.getSignColor() == rgb) return color;
        }
        return null;
    }

    public static boolean hasSameColor(ItemStack stack, DyeColor color) {
        DyeColor stackColor = getColor(stack);
        return stackColor != null && stackColor == color;
    }
}
