package com.zlorbie788.zeus.core;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

public final class Inv {
    private Inv() {}

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static Predicate<ItemStack> is(Item item) {
        return s -> s.is(item);
    }

    public static int selected() {
        return mc().player.getInventory().getSelectedSlot();
    }

    public static void select(int slot) {
        if (slot < 0 || slot > 8) return;
        mc().player.getInventory().setSelectedSlot(slot);
    }

    public static ItemStack get(int slot) {
        return mc().player.getInventory().getItem(slot);
    }

    public static int hotbar(Predicate<ItemStack> p) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = get(i);
            if (!s.isEmpty() && p.test(s)) return i;
        }
        return -1;
    }

    public static int main(Predicate<ItemStack> p) {
        for (int i = 9; i < 36; i++) {
            ItemStack s = get(i);
            if (!s.isEmpty() && p.test(s)) return i;
        }
        return -1;
    }

    public static boolean offhand(Predicate<ItemStack> p) {
        ItemStack s = mc().player.getOffhandItem();
        return !s.isEmpty() && p.test(s);
    }

    public static boolean holding(Predicate<ItemStack> p) {
        ItemStack s = mc().player.getMainHandItem();
        return !s.isEmpty() && p.test(s);
    }

    public static int menuSlot(int inv) {
        return inv < 9 ? 36 + inv : inv;
    }

    public static final int MENU_OFFHAND = 45;

    public static int armorMenuSlot(EquipmentSlot s) {
        return switch (s) {
            case HEAD -> 5;
            case CHEST -> 6;
            case LEGS -> 7;
            default -> 8;
        };
    }

    private static int clickTick = -1;
    private static int clickSlot = -1;
    private static int clickButton = -1;
    private static ClickType clickType;

    public static void click(int menuSlot, int button, ClickType type) {
        Minecraft mc = mc();
        if (mc.gameMode == null || mc.player == null) return;
        int tick = mc.player.tickCount;
        // Two identical clicks on the same slot inside one tick never come from a hand; they only
        // make the click stream look scripted, so the repeat is dropped and the module tries again.
        if (tick == clickTick && menuSlot == clickSlot && button == clickButton && type == clickType) return;
        clickTick = tick;
        clickSlot = menuSlot;
        clickButton = button;
        clickType = type;
        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, menuSlot, button, type, mc.player);
    }

    public static void toOffhand(int invSlot) {
        click(menuSlot(invSlot), 40, ClickType.SWAP);
    }

    public static boolean with(int slot, Runnable action) {
        if (slot < 0 || slot > 8) return false;
        int prev = selected();
        if (prev != slot) select(slot);
        try {
            action.run();
        } finally {
            if (prev != slot) select(prev);
        }
        return true;
    }

    public static InteractionHand handFor(Predicate<ItemStack> p) {
        return offhand(p) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }
}
