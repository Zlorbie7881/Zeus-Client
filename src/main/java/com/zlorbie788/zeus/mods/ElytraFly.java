package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Elytra flight with a choice of how it holds itself up.
 *
 * <p>Glide keeps the height you already have instead of sinking, Pitch 40 points the wings at the
 * angle that glides fastest and keeps the speed there, Boost spends the jump key as a free rocket,
 * and Sustain kills the slow drag that normally bleeds speed until you throw a real firework.
 */
public final class ElytraFly extends Module {
    private final Setting.Choice mode = add(new Setting.Choice("Mode",
            "Glide: hold your height. Pitch 40: the fastest glide angle, held. Boost: hold jump for a free push. Sustain: no drag, so you keep the speed you have.",
            0, "Glide", "Pitch 40", "Boost", "Sustain"));
    private final Setting.Num speed = add(new Setting.Num("Speed", "How hard Pitch 40 and Boost push.", 1.8, 0.6, 6.0, 0.1, "x"));
    private final Setting.Num pitch = add(new Setting.Num("Pitch", "The angle Pitch 40 holds. Around 40 degrees down glides fastest.", 40, 10, 80, 5, "°"))
            .showIf(() -> this.mode.index() == 1);
    private final Setting.Num sustain = add(new Setting.Num("Kept speed", "How much of your speed Sustain keeps each tick. 100% means none is lost.", 100, 85, 100, 1, "%"))
            .showIf(() -> this.mode.index() == 3);

    public ElytraFly() {
        super("elytrafly", "Elytra Fly", "Fly with an elytra without fireworks, in four different ways.", Category.MOVEMENT);
        alias("elytra", "rocketless", "elytra boost");
    }

    @Override
    protected void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.screen != null || !p.isFallFlying() || p.isPassenger()) return;
        Vec3 d = p.getDeltaMovement();
        switch (mode.index()) {
            case 1 -> {
                p.setXRot(pitch.f());
                Vec3 look = p.getLookAngle();
                double sp = 0.62 * speed.get();
                p.setDeltaMovement(look.x * sp, look.y * sp - 0.012, look.z * sp);
                p.fallDistance = 0f;
            }
            case 2 -> {
                if (mc.options.keyJump.isDown()) {
                    Vec3 look = p.getLookAngle();
                    double sp = 0.055 * speed.get();
                    p.setDeltaMovement(
                            Mth.clamp(d.x + look.x * sp, -3.0, 3.0),
                            Mth.clamp(d.y + look.y * sp * 0.55 + 0.008, -1.5, 2.0),
                            Mth.clamp(d.z + look.z * sp, -3.0, 3.0));
                }
                p.fallDistance = 0f;
            }
            case 3 -> {
                float keep = sustain.f() / 100f;
                p.setDeltaMovement(d.x * keep, Math.max(d.y, -0.32) + 0.014, d.z * keep);
                p.fallDistance = 0f;
            }
            default -> p.setDeltaMovement(d.x, Math.max(d.y, -0.06) * 0.35, d.z);
        }
    }

    @Override
    public String info() {
        return mode.value();
    }
}
