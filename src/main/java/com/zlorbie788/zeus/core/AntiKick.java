package com.zlorbie788.zeus.core;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class AntiKick {
    private AntiKick() {}

    private static int ticks;

    static void tick(Minecraft mc, Module[] modules) {
        if (mc.player == null || mc.getSingleplayerServer() == null) {
            ticks = 0;
            return;
        }
        boolean need = false;
        for (Module m : modules) if (m.enabled() && m.isAirborne()) need = true;
        if (!need || mc.player.onGround() || mc.player.getAbilities().mayfly || mc.player.isInWater() || mc.player.onClimbable()) {
            ticks = 0;
            return;
        }
        if (++ticks % 25 == 0) {
            Vec3 v = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(v.x, Math.min(v.y, -0.12), v.z);
        }
    }
}
