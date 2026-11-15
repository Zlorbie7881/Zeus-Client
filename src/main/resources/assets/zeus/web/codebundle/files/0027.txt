package com.zlorbie788.zeus.core;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class Dmg {
    private Dmg() {}

    private static float exposure(Minecraft mc, Vec3 center, Entity e) {
        AABB bb = e.getBoundingBox();
        double cx = (bb.minX + bb.maxX) / 2;
        double cz = (bb.minZ + bb.maxZ) / 2;
        double[] ys = {bb.minY + 0.1, (bb.minY + bb.maxY) / 2, bb.maxY - 0.1};
        int seen = 0;
        for (double y : ys) {
            Vec3 p = new Vec3(cx, y, cz);
            HitResult r = mc.level.clip(new ClipContext(center, p, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
            if (r.getType() == HitResult.Type.MISS) seen++;
        }
        return seen / 3f;
    }

    public static float explosion(Minecraft mc, Vec3 center, Entity e, float power) {
        double radius = power * 2.0;
        double dist = Math.sqrt(e.distanceToSqr(center.x, center.y, center.z)) / radius;
        if (dist > 1.0) return 0f;
        double impact = (1.0 - dist) * exposure(mc, center, e);
        double raw = (impact * impact + impact) / 2.0 * 7.0 * radius + 1.0;
        float dmg = (float) Math.floor(raw);
        if (e instanceof LivingEntity le) {
            double armor = Math.min(20.0, le.getArmorValue());
            dmg *= (float) (1.0 - armor / 25.0);
        }
        return Math.max(0f, dmg);
    }
}
