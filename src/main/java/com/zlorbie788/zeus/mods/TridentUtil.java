package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class TridentUtil extends Module {
    private final Setting.Bool onUse = add(new Setting.Bool("Draw it when I use", "Pressing use with anything else in your hand takes the trident out first.", true));
    private final Setting.Bool giveBack = add(new Setting.Bool("Give my item back", "Return to the slot you were holding once the riptide is done.", true));
    private final Setting.Bool escape = add(new Setting.Bool("Riptide on the way down", "While falling, holding a riptide upward if the weather allows it.", false));
    private final Setting.Num fallAfter = add(new Setting.Num("Fall distance", "How far you have to fall before the riptide escape is used.", 3, 1, 20, 1, " blocks")).showIf(() -> this.escape.on());
    private final Setting.Bool warn = add(new Setting.Bool("Tell me when it will not work", "A toast when a riptide is impossible here, so you know the server refused instead of guessing.", true));
    private final Setting.Info state = add(new Setting.Info("Now", this::state));

    private int previous = -1;
    private int restoreIn;
    private int holding;
    private boolean told;
    private boolean useDown;

    public TridentUtil() {
        super("tridentutil", "Trident Util", "Takes the trident out, holds the riptide long enough, and gives your sword back.", Category.COMBAT);
        light();
    }

    @Override
    protected void onEnable() {
        previous = -1;
        restoreIn = 0;
        holding = 0;
        told = false;
        useDown = false;
    }

    @Override
    protected void onDisable() {
        stopHolding(Minecraft.getInstance());
        if (giveBack.on() && previous >= 0) Inv.select(previous);
        previous = -1;
        restoreIn = 0;
    }

    private static boolean possible(Minecraft mc) {
        return mc.player != null && (mc.player.isInWaterOrRain() || mc.player.isInWater());
    }

    @Override
    protected void onTick(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) return;
        Inventory inv = mc.player.getInventory();
        if (holding > 0) {
            holding--;
            if (holding == 0) stopHolding(mc);
        }
        if (restoreIn > 0 && --restoreIn == 0 && giveBack.on() && previous >= 0) {
            Inv.select(previous);
            previous = -1;
        }
        boolean useEdge = mc.options.keyUse.isDown() && !useDown;
        useDown = mc.options.keyUse.isDown();

        int trident = tridentSlot(inv);
        boolean holdingTrident = mc.player.getMainHandItem().is(Items.TRIDENT);

        if (onUse.on() && useEdge && !holdingTrident && trident >= 0) {
            if (previous < 0) previous = inv.getSelectedSlot();
            Inv.select(trident);
            restoreIn = 0;
        }
        if (holdingTrident && useEdge && holding == 0 && !mc.player.isUsingItem()) {
            if (possible(mc)) {
                startHolding(mc);
            } else if (warn.on() && !told) {
                told = true;
                Zeus.toast("Riptide needs water or rain; the server will refuse it here", false);
            }
        }
        if (!useDown) told = false;

        if (escape.on() && possible(mc) && mc.player.fallDistance > fallAfter.get() && trident >= 0) {
            if (!holdingTrident && useEdge) {
                if (previous < 0) previous = inv.getSelectedSlot();
                Inv.select(trident);
            }
            if (holding == 0 && useEdge) startHolding(mc);
            restoreIn = Math.max(restoreIn, 14);
        }
    }

    private void startHolding(Minecraft mc) {
        if (holding > 0 || !mc.player.isInWaterOrRain()) return;
        holding = 12;
        mc.options.keyUse.setDown(true);
    }

    private void stopHolding(Minecraft mc) {
        if (mc == null) return;
        holding = 0;
        if (mc.player.isInWaterOrRain()) mc.options.keyUse.setDown(false);
    }

    private static int tridentSlot(Inventory inv) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(Items.TRIDENT)) return i;
        }
        return -1;
    }

    private java.util.List<String> state() {
        Minecraft mc = Minecraft.getInstance();
        java.util.List<String> out = new java.util.ArrayList<>();
        if (mc.player == null) return out;
        int slot = tridentSlot(mc.player.getInventory());
        out.add(slot < 0 ? "No trident in the hotbar" : "Trident in slot " + (slot + 1));
        out.add(possible(mc) ? "Riptide works here (water or rain)" : "Riptide is refused here: not in water and not raining");
        if (holding > 0) out.add("Holding a riptide, " + holding + " ticks left");
        return out;
    }
}
