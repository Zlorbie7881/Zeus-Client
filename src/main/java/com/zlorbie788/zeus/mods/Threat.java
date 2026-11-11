package com.zlorbie788.zeus.mods;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Threat {
    private Threat() {}

    public record Input(int armorRank, String main, String off, double dist, double closing, boolean aimedAtMe, float health, float maxHealth,
                        boolean invisible, boolean elytra) {}

    public record Result(int score, String level, List<String> reasons) {}

    public static final String[] LEVELS = {"Low", "Medium", "High", "Danger"};

    public static int weaponTier(String id) {
        String n = id == null ? "" : id.toLowerCase(Locale.ROOT);
        if (n.equals("mace")) return 7;
        if (n.equals("trident")) return 5;
        if (!n.endsWith("_sword") && !n.endsWith("_axe")) return 0;
        int t = n.startsWith("netherite_") ? 6 : n.startsWith("diamond_") ? 5 : n.startsWith("iron_") ? 4 : n.startsWith("stone_") ? 3 : n.startsWith("golden_") ? 2 : 1;
        return n.endsWith("_axe") ? Math.max(1, t - 1) : t;
    }

    public static boolean explosive(String id) {
        return id != null && (id.equals("end_crystal") || id.equals("respawn_anchor") || id.equals("tnt") || id.equals("tnt_minecart"));
    }

    public static boolean ranged(String id) {
        return id != null && (id.equals("bow") || id.equals("crossbow"));
    }

    public static String level(int score) {
        return score < 25 ? LEVELS[0] : score < 50 ? LEVELS[1] : score < 75 ? LEVELS[2] : LEVELS[3];
    }

    public static Result rate(Input in) {
        double s = 0;
        List<String> why = new ArrayList<>();
        double gear = Math.min(30, in.armorRank / 288.0 * 30);
        s += gear;
        if (in.armorRank >= 240) why.add("full netherite-class armor");
        else if (in.armorRank >= 170) why.add("strong armor");
        int wt = Math.max(weaponTier(in.main), weaponTier(in.off));
        s += wt * 2.5;
        if (wt >= 5) why.add("holding a " + (weaponTier(in.main) >= wt ? in.main : in.off).replace('_', ' '));
        if (explosive(in.main) || explosive(in.off)) {
            s += 14;
            why.add("holding explosives");
        } else if (ranged(in.main) || ranged(in.off)) {
            s += 8;
            why.add("has a bow");
        }
        if ("totem_of_undying".equals(in.off)) {
            s += 6;
            why.add("totem in offhand");
        }
        double d = in.dist;
        double prox = d <= 6 ? 24 : d <= 12 ? 18 : d <= 24 ? 10 : d <= 48 ? 4 : 0;
        s += prox;
        if (d <= 12) why.add("very close");
        if (in.closing > 1.0) {
            s += Math.min(16, in.closing * 1.6);
            why.add(String.format(Locale.ROOT, "closing in at %.0f b/s", in.closing));
        } else if (in.closing < -1.0) {
            s -= Math.min(4, -in.closing * 0.5);
        }
        if (in.aimedAtMe && d < 40) {
            s += 8;
            why.add("looking at you");
        }
        if (in.elytra) {
            s += 4;
            why.add("flying");
        }
        if (in.invisible) {
            s += 6;
            why.add("invisible");
        }
        if (in.maxHealth > 0 && in.health / in.maxHealth < 0.35f) {
            s -= 6;
            why.add("hurt");
        }
        int score = (int) Math.max(0, Math.min(100, Math.round(s)));
        return new Result(score, level(score), why);
    }
}
