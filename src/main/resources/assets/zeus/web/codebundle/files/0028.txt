package com.zlorbie788.zeus.core;

import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Env {
    private Env() {}

    public static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static boolean inWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null;
    }

    public static boolean local() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.getSingleplayerServer() != null;
    }

    public static net.minecraft.world.level.chunk.LevelChunk chunk(Minecraft mc, int cx, int cz) {
        if (mc.level == null || !mc.level.hasChunk(cx, cz)) return null;
        net.minecraft.world.level.chunk.LevelChunk c = mc.level.getChunk(cx, cz);
        return c == null || c instanceof net.minecraft.world.level.chunk.EmptyLevelChunk ? null : c;
    }

    public static java.util.List<net.minecraft.world.entity.Entity> entities(Minecraft mc, double range) {
        if (mc.level == null || mc.player == null) return java.util.List.of();
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        return mc.level.getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(x - range, y - range, z - range, x + range, y + range, z + range));
    }

    private record Kept(String attribute, double value, String op) {}

    private static final Map<String, Kept> ATTRIBUTES = new LinkedHashMap<>();
    private static final Map<String, Integer> EFFECTS = new LinkedHashMap<>();
    private static int ticks;

    private static net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr(String name) {
        return switch (name) {
            case "movement_speed" -> net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;
            case "jump_strength" -> net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH;
            case "step_height" -> net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT;
            case "gravity" -> net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY;
            case "block_break_speed" -> net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED;
            case "block_interaction_range" -> net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE;
            case "entity_interaction_range" -> net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE;
            case "scale" -> net.minecraft.world.entity.ai.attributes.Attributes.SCALE;
            default -> null;
        };
    }

    private static net.minecraft.resources.Identifier modId(String key) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath("zeus", key);
    }

    private static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation operation(String op) {
        return switch (op) {
            case "add_multiplied_base" -> net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total" -> net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE;
        };
    }

    private static void put(String key, Kept k) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var holder = attr(k.attribute());
        if (holder == null) return;
        var inst = mc.player.getAttribute(holder);
        if (inst == null) return;
        var id = modId(key);
        var old = inst.getModifier(id);
        if (old != null && Math.abs(old.amount() - k.value()) < 1e-9) return;
        inst.removeModifier(id);
        inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, k.value(), operation(k.op())));
    }

    public static void attribute(String attribute, String key, double value, String op) {
        Kept k = new Kept(attribute, value, op);
        ATTRIBUTES.put(key, k);
        put(key, k);
    }

    public static void attributeOff(String attribute, String key) {
        ATTRIBUTES.remove(key);
        Minecraft mc = Minecraft.getInstance();
        var holder = attr(attribute);
        if (holder == null || mc.player == null) return;
        var inst = mc.player.getAttribute(holder);
        if (inst != null) inst.removeModifier(modId(key));
    }

    private static net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> fx(String name) {
        return switch (name) {
            case "night_vision" -> net.minecraft.world.effect.MobEffects.NIGHT_VISION;
            default -> null;
        };
    }

    private static void giveEffect(String effect, int amplifier) {
        Minecraft mc = Minecraft.getInstance();
        var holder = fx(effect);
        if (holder == null || mc.player == null) return;
        mc.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(holder, 600, amplifier, true, false, false));
    }

    public static void effect(String effect, int amplifier) {
        EFFECTS.put(effect, amplifier);
        giveEffect(effect, amplifier);
    }

    public static void effectOff(String effect) {
        EFFECTS.remove(effect);
        Minecraft mc = Minecraft.getInstance();
        var holder = fx(effect);
        if (holder == null || mc.player == null) return;
        mc.player.removeEffect(holder);
    }

    public static void tick() {
        if (!inWorld()) return;
        ticks++;
        if (ticks % 4 == 0) {
            for (Map.Entry<String, Kept> e : ATTRIBUTES.entrySet()) put(e.getKey(), e.getValue());
        }
        if (ticks % 200 == 0) {
            for (Map.Entry<String, Integer> e : EFFECTS.entrySet()) giveEffect(e.getKey(), e.getValue());
        }
    }

    public static void reset() {
        ATTRIBUTES.clear();
        EFFECTS.clear();
        ticks = 0;
    }
}
