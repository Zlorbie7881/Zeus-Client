package com.zlorbie788.zeus.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Daily quests for Zeus Crates: three small goals a day, picked from a pool, each paying a few tokens, with a bonus
 * for finishing all three. Progress is counted on the client and saved inside crates.dat, so it is signed too.
 */
public final class Quests {
    private Quests() {}

    public enum Kind {
        WALK("Walk %d blocks", 600, 1600, 100, 3),
        MINE("Break %d blocks", 64, 192, 16, 3),
        HIT("Hit mobs %d times", 30, 90, 10, 2),
        PLAY("Play for %d minutes", 20, 45, 5, 2),
        JUMP("Jump %d times", 60, 160, 10, 2),
        SPRINT("Sprint %d blocks", 300, 800, 50, 3);

        final String text;
        final int min, max, step;
        public final int reward;

        Kind(String text, int min, int max, int step, int reward) {
            this.text = text;
            this.min = min;
            this.max = max;
            this.step = step;
            this.reward = reward;
        }
    }

    public static final int PER_DAY = 3;
    public static final int BONUS = 3;

    public record Quest(Kind kind, int target, int progress, boolean done) {
        public String text() {
            return String.format(kind.text, target);
        }

        public float fraction() {
            return Math.min(1f, progress / (float) target);
        }
    }

    private static long day = -1;
    private static long salt;
    private static final Kind[] kinds = new Kind[PER_DAY];
    private static final int[] targets = new int[PER_DAY];
    private static final double[] progress = new double[PER_DAY];
    private static final boolean[] done = new boolean[PER_DAY];
    private static boolean bonusPaid;
    // movement tracking
    private static double lx = Double.NaN, lz;
    private static boolean wasOnGround = true;

    private static long today() {
        return java.time.LocalDate.now().toEpochDay();
    }

    /** Picks today's quests if the day changed (the same three all day, different for every install). */
    private static void roll() {
        long d = today();
        if (d == day) return;
        if (salt == 0) salt = new java.security.SecureRandom().nextLong() | 1;
        day = d;
        Random r = new Random(d * 0x9E3779B97F4A7C15L ^ salt);
        List<Kind> pool = new ArrayList<>(List.of(Kind.values()));
        for (int i = 0; i < PER_DAY; i++) {
            Kind k = pool.remove(r.nextInt(pool.size()));
            kinds[i] = k;
            int steps = (k.max - k.min) / k.step;
            targets[i] = k.min + r.nextInt(steps + 1) * k.step;
            progress[i] = 0;
            done[i] = false;
        }
        bonusPaid = false;
        Crates.markDirty();
    }

    public static List<Quest> list() {
        Crates.load();
        roll();
        List<Quest> l = new ArrayList<>();
        for (int i = 0; i < PER_DAY; i++) l.add(new Quest(kinds[i], targets[i], (int) Math.floor(progress[i]), done[i]));
        return l;
    }

    public static boolean allDone() {
        for (boolean b : done) if (!b) return false;
        return true;
    }

    private static void add(Kind kind, double amount) {
        if (amount <= 0) return;
        Crates.load();
        roll();
        for (int i = 0; i < PER_DAY; i++) {
            if (kinds[i] != kind || done[i]) continue;
            progress[i] += amount;
            if (progress[i] >= targets[i]) {
                progress[i] = targets[i];
                done[i] = true;
                Crates.addTokens(kind.reward);
                Zeus.toast("Daily quest done: " + String.format(kind.text, targets[i]) + "  +" + kind.reward + " tokens", true);
                if (allDone() && !bonusPaid) {
                    bonusPaid = true;
                    Crates.addTokens(BONUS);
                    Zeus.toast("All daily quests done!  +" + BONUS + " bonus tokens", true);
                }
                Crates.save(true);
            }
            Crates.markDirty();
        }
    }

    // ------------------------------------------------------------------ events
    /** Called every client tick while the player is active (Crates decides that). */
    static void tick(Minecraft mc, long dtMs) {
        var p = mc.player;
        if (p == null) return;
        add(Kind.PLAY, dtMs / 60_000.0);
        double x = p.getX(), z = p.getZ();
        if (!Double.isNaN(lx)) {
            double d = Math.hypot(x - lx, z - lz);
            if (d < 1.2 && !p.isPassenger() && !p.getAbilities().flying) {       // no teleports, rides or flying
                add(Kind.WALK, d);
                if (p.isSprinting()) add(Kind.SPRINT, d);
            }
        }
        lx = x;
        lz = z;
        boolean ground = p.onGround();
        if (wasOnGround && !ground && p.getDeltaMovement().y > 0.2 && !p.isInWater()) add(Kind.JUMP, 1);
        wasOnGround = ground;
    }

    public static void blockBroken() {
        add(Kind.MINE, 1);
    }

    public static void hit(net.minecraft.world.entity.Entity target) {
        if (target instanceof net.minecraft.world.entity.Mob) add(Kind.HIT, 1);
    }

    // ------------------------------------------------------------------ storage (inside crates.dat)
    static void write(JsonObject d) {
        JsonObject q = new JsonObject();
        q.addProperty("day", day);
        q.addProperty("salt", salt);
        q.addProperty("bonus", bonusPaid);
        JsonArray a = new JsonArray();
        for (int i = 0; i < PER_DAY; i++) {
            if (kinds[i] == null) continue;
            JsonObject o = new JsonObject();
            o.addProperty("k", kinds[i].name());
            o.addProperty("t", targets[i]);
            o.addProperty("p", progress[i]);
            o.addProperty("d", done[i]);
            a.add(o);
        }
        q.add("list", a);
        d.add("quests", q);
    }

    static void read(JsonObject d) {
        day = -1;
        bonusPaid = false;
        if (!d.has("quests")) return;
        try {
            JsonObject q = d.getAsJsonObject("quests");
            salt = q.get("salt").getAsLong();
            JsonArray a = q.getAsJsonArray("list");
            if (a.size() != PER_DAY) return;
            for (int i = 0; i < PER_DAY; i++) {
                JsonObject o = a.get(i).getAsJsonObject();
                kinds[i] = Kind.valueOf(o.get("k").getAsString());
                targets[i] = o.get("t").getAsInt();
                progress[i] = o.get("p").getAsDouble();
                done[i] = o.get("d").getAsBoolean();
            }
            bonusPaid = q.get("bonus").getAsBoolean();
            day = q.get("day").getAsLong();
        } catch (RuntimeException e) {
            day = -1;
        }
    }

    static void reset() {
        day = -1;
        salt = 0;
        bonusPaid = false;
    }
}
