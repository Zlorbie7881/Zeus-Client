package com.zlorbie788.zeus.core;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * Enemy armour, read straight out of what the server already sends. Equipment packets carry the full
 * item stack, damage component included, so another player's durability is not a guess: the server
 * re-sends the stack every time a piece loses a point, and this reads that copy.
 */
public final class Armor {
    private Armor() {}

    public static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** Remaining durability as a fraction of the piece's maximum, or -1 when nothing is worn there. */
    public static float left(ItemStack s) {
        if (s == null || s.isEmpty()) return -1f;
        int max = s.getMaxDamage();
        if (max <= 0) return 1f;
        int dmg = s.getDamageValue();
        return Math.max(0f, Math.min(1f, 1f - (float) dmg / max));
    }

    /** Points of durability left before the piece breaks, or -1 when nothing is worn there. */
    public static int remaining(ItemStack s) {
        if (s == null || s.isEmpty()) return -1;
        int max = s.getMaxDamage();
        if (max <= 0) return Integer.MAX_VALUE;
        return Math.max(0, max - s.getDamageValue());
    }

    public static String letter(EquipmentSlot s) {
        return switch (s) {
            case HEAD -> "H";
            case CHEST -> "C";
            case LEGS -> "L";
            default -> "B";
        };
    }

    public static String piece(EquipmentSlot s) {
        return switch (s) {
            case HEAD -> "Helmet";
            case CHEST -> "Chestplate";
            case LEGS -> "Leggings";
            default -> "Boots";
        };
    }

    /** The worn piece closest to breaking, or null when the target wears no armour. */
    public static EquipmentSlot weakest(LivingEntity e) {
        EquipmentSlot worst = null;
        float f = 2f;
        for (EquipmentSlot s : SLOTS) {
            float v = left(e.getItemBySlot(s));
            if (v >= 0 && v < f) {
                f = v;
                worst = s;
            }
        }
        return worst;
    }

    /**
     * The hotbar slot that hits for the least damage. This is what drains armour fastest: a landed hit
     * takes {@code max(1, damage / 4)} durability off every worn piece, so twenty soft hits cost twenty
     * points per piece while twenty full-swing sword hits also cost twenty, for five times the health.
     * Soft and fast beats hard and slow when the goal is the armour and not the player.
     */
    public static int softestSlot() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1;
        int hand = -1;
        int block = -1;
        int weak = -1;
        double weakDmg = Double.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.isEmpty()) {
                if (hand < 0) hand = i;
                continue;
            }
            // Blocks, food and buckets hit for fist damage and have no durability, so they are the
            // ideal armour breaker: nothing of yours wears out while their armour does.
            if (!s.isDamageableItem()) {
                if (block < 0) block = i;
                continue;
            }
            double[] bonus = {0};
            s.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
                if (attr.value() == Attributes.ATTACK_DAMAGE.value()) bonus[0] += mod.amount();
            });
            double dmg = 1.0 + bonus[0];
            if (dmg < weakDmg) {
                weakDmg = dmg;
                weak = i;
            }
        }
        if (hand >= 0) return hand;
        if (block >= 0) return block;
        return weak;
    }
}
