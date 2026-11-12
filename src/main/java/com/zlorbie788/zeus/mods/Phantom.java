package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

/**
 * The human layer. Every assisted module that aims or swings can ask Phantom for a reaction
 * window, a little aim wobble and the odd deliberate miss, so nothing looks like a machine.
 */
public final class Phantom extends Module {
    private static final Random RNG = new Random();

    private final Setting.Num reactLow = add(new Setting.Num("Reaction from",
            "Fastest time an assisted action may take to start, like a quick human.", 55, 0, 400, 5, " ms"));
    private final Setting.Num reactHigh = add(new Setting.Num("Reaction to",
            "Slowest time an assisted action may take to start, like a distracted human.", 190, 0, 500, 5, " ms"));
    private final Setting.Num wobble = add(new Setting.Num("Aim wobble",
            "Degrees of random offset added either side of every assisted aim. 0 is a machine.", 0.7, 0, 5, 0.1, "°"));
    private final Setting.Num miss = add(new Setting.Num("Miss chance",
            "How often an assisted hit is deliberately skipped, so your accuracy is never perfect.", 3, 0, 40, 1, " %"));
    private final Setting.Bool drift = add(new Setting.Bool("Idle drift",
            "Slowly wanders the aim a fraction of a degree while a target is held, instead of freezing on it.", true));
    private final Setting.Info state = add(new Setting.Info("Ghost", this::lines));

    public Phantom() {
        super("phantom", "Phantom",
                "Human layer for every assisted aim and clutch: reaction windows, aim wobble, the odd miss and a drifting crosshair. Leave it on and nothing you switch on looks like a machine.",
                Category.UTILITY);
        alias("ghostmode", "human", "humanizer");
        light();
        anywhere();
        onByDefault();
    }

    public static Phantom get() {
        return Zeus.get(Phantom.class);
    }

    private static Phantom live() {
        Phantom p = get();
        return p != null && p.enabled() ? p : null;
    }

    public static boolean active() {
        return live() != null;
    }

    /** A randomised reaction time in milliseconds, or 0 when Phantom is off. */
    public static long reaction() {
        Phantom p = live();
        if (p == null) return 0L;
        double lo = Math.min(p.reactLow.get(), p.reactHigh.get());
        double hi = Math.max(p.reactLow.get(), p.reactHigh.get());
        if (hi <= lo) return Math.round(lo);
        return Math.round(lo + RNG.nextDouble() * (hi - lo));
    }

    /** True when this assisted hit should be skipped on purpose. */
    public static boolean miss() {
        Phantom p = live();
        return p != null && p.miss.get() > 0 && RNG.nextDouble() * 100.0 < p.miss.get();
    }

    /** Random degrees either side of zero, for use on a yaw or a pitch. */
    public static float wobble() {
        Phantom p = live();
        if (p == null || p.wobble.get() <= 0) return 0f;
        double amounts = p.wobble.get();
        return (float) ((RNG.nextDouble() * 2.0 - 1.0) * amounts);
    }

    /** A slow wander added to a held aim, in degrees. */
    public static float drift() {
        Phantom p = live();
        if (p == null || !p.drift.on()) return 0f;
        double t = (System.currentTimeMillis() % 2600L) / 2600.0;
        return (float) (Math.sin(t * Math.PI * 2.0) * 0.18 + (RNG.nextDouble() - 0.5) * 0.08);
    }

    /** Points the player at a point with human noise on top. */
    public static void aim(Player player, Vec3 target) {
        if (player == null || target == null) return;
        double dx = target.x - player.getX();
        double dy = target.y - player.getEyeY();
        double dz = target.z - player.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)) + wobble() + drift();
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, flat)) + wobble() * 0.5f;
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(Math.max(-90f, Math.min(90f, pitch)));
    }

    /** Looks straight down, for buckets, pearls and wind charges. */
    public static void aimDown(Player player) {
        if (player == null) return;
        player.setXRot(Math.max(-90f, Math.min(90f, 88f + wobble())));
    }

    private List<String> lines() {
        if (!enabled()) return List.of("Off - assisted aim is exact and instant");
        double lo = Math.min(reactLow.get(), reactHigh.get());
        double hi = Math.max(reactLow.get(), reactHigh.get());
        return List.of(
                "Reaction " + Math.round(lo) + "-" + Math.round(hi) + " ms",
                "Aim wobble ±" + String.format(java.util.Locale.ROOT, "%.1f", wobble.get()) + "°"
                        + (drift.on() ? " + drift" : ""),
                "Deliberate misses " + miss.i() + "%");
    }
}
