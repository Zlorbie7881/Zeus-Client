package com.zlorbie788.zeus.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class Names {
    private Names() {}

    public static String item(ItemStack s) {
        try {
            String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
            return id.substring(id.indexOf(':') + 1);
        } catch (RuntimeException e) {
            return "";
        }
    }

    public static String block(Block b) {
        try {
            String id = BuiltInRegistries.BLOCK.getKey(b).toString();
            return id.substring(id.indexOf(':') + 1);
        } catch (RuntimeException e) {
            return "";
        }
    }
}
