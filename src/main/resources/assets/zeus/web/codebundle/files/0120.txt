package com.zlorbie788.zeus.mods;

import java.util.ArrayList;
import java.util.List;

public final class BaseBrain {
    private BaseBrain() {}

    public static final int R_BED = 1;
    public static final int R_DOOR = 2;
    public static final int R_HOPPER = 3;
    public static final int R_CONTAINER = 4;
    public static final int R_LIGHT = 5;
    public static final int R_VALUABLE = 6;
    public static final int R_WORK = 7;
    public static final int R_FIRE = 8;
    public static final int R_PORTAL = 9;
    public static final int ROLES = 10;

    public static final class Features {
        public double total;
        public double tell;
        public int strong;
        public double ents;
        public double[] groups = new double[6];
        public int[] roles = new int[ROLES];
        public double underground;

        public int structure = -1;
        public double structPoints;
        public int items;

        public int foot;
        public int pearls;
        public int named;
        public int villagers;
        public int herd;

        public double activity;
    }

    public static final class Verdict {
        public int confidence;
        public String type = "Nothing";
        public final List<String> reasons = new ArrayList<>();
        public double evidence;

        public String why() {
            return reasons.isEmpty() ? "no clear signs" : String.join(", ", reasons.subList(0, Math.min(3, reasons.size())));
        }
    }

    private static int kinds(double[] g) {
        int k = 0;
        for (double v : g) if (v >= 3) k++;
        return k;
    }

    public static Verdict judge(Features f, boolean hub, boolean nether) {
        Verdict v = new Verdict();
        double e = 0;
        int containers = f.roles[R_CONTAINER];
        int beds = f.roles[R_BED];
        int hoppers = f.roles[R_HOPPER];
        int lights = f.roles[R_LIGHT];
        int work = f.roles[R_WORK];
        int valuable = f.roles[R_VALUABLE];

        double tellPts = Math.min(3.0, f.tell / 40.0);
        if (tellPts > 0.2) {
            e += tellPts;
            v.reasons.add("blocks only players place");
        }
        boolean outside = f.structure < 0;
        int domestic = 0;
        int clues = 0;
        if (outside) {
            if (beds >= 1) {
                e += 1.6 + Math.min(0.6, (beds - 1) * 0.3);
                domestic++;
                v.reasons.add(beds == 1 ? "a bed" : beds + " beds");
            }
            if (containers >= 1) {
                e += containers >= 3 ? 2.0 : containers == 2 ? 1.4 : 0.8;
                if (containers >= 5) e += 0.7;
                domestic++;
                v.reasons.add(containers + (containers == 1 ? " storage block" : " storage blocks"));
            }
            if (work >= 1) {
                e += work >= 2 ? 1.4 : 0.8;
                domestic++;
                v.reasons.add(work == 1 ? "a workstation" : work + " workstations");
            }
            if (domestic >= 3) {
                e += 1.6;
                v.reasons.add("bed, storage and workstations together");
            } else if (domestic == 2) {
                e += 0.8;
            }
        } else {
            if (beds >= 1 && containers >= 2) {
                e += 2.2;
                domestic = 2;
                v.reasons.add(containers + " storage + " + beds + (beds == 1 ? " bed" : " beds"));
            } else if (containers >= 5) {
                e += 1.5;
                v.reasons.add(containers + " storage blocks");
            } else if (containers >= 2) {
                e += 0.7;
                v.reasons.add(containers + " storage blocks");
            }
            if (containers >= 2 && work >= 1 && beds == 0) {
                e += 0.9;
                v.reasons.add("storage next to workstations");
            }
        }
        if (f.pearls >= 1) {
            e += 2.2;
            clues++;
            v.reasons.add("ender pearls parked (stasis chamber)");
        }
        if (f.named >= 1) {
            e += f.named >= 3 ? 1.6 : 1.0;
            clues++;
            v.reasons.add("name-tagged mobs");
        }
        if (f.villagers >= 2 && outside) {
            e += 1.4;
            clues++;
            v.reasons.add("villagers outside a village");
        }
        if (f.herd >= 9) {
            e += 1.2;
            clues++;
            v.reasons.add("an animal pen");
        }
        if (outside && f.roles[R_PORTAL] >= 1) {
            e += 1.1;
            clues++;
            v.reasons.add("a lit Nether portal");
        }
        if (f.activity >= 3 && e >= 0.8 && outside) {
            e += Math.min(1.3, 0.22 * f.activity);
            clues++;
            v.reasons.add("someone is working here right now");
        }
        if (outside && f.foot >= 4) {
            double fp = f.foot >= 13 ? 3.4 : f.foot >= 9 ? 2.7 : f.foot >= 7 ? 2.1 : f.foot >= 6 ? 1.6 : 0.8;
            e += fp;
            if (f.foot >= 6) domestic++;
            v.reasons.add(f.foot + " kinds of player-made blocks");
        }
        if (hoppers >= 3 || f.groups[2] >= 40) {
            e += 1.4;
            v.reasons.add("redstone machine");
        }
        if (valuable >= 2) {
            e += 1.0;
            v.reasons.add("blocks of valuables");
        }
        if (lights >= 4 && f.underground > 0.6) {
            e += 1.2;
            v.reasons.add("lit rooms underground");
        } else if (lights >= 8) {
            e += 0.5;
        }
        int k = kinds(f.groups);
        if (k >= 3) {
            e += 0.8;
            v.reasons.add("mixes " + k + " kinds of blocks");
        }
        if (f.ents >= 14) {
            e += 0.8;
            v.reasons.add("item frames or stands");
        }
        if (f.structure >= 0) {
            double pen = 3.0 - Math.min(2.6, f.tell / 45.0);
            if (nether) pen += 0.5;
            e -= Math.max(0, pen);
            if (pen > 0.8) v.reasons.add("inside a natural structure");
        }
        if (hub) {
            e -= 1.2;
            v.reasons.add("area full of builds (spawn or hub)");
        }
        if (f.strong < 2 && domestic == 0 && clues == 0 && f.tell < 20) e = Math.min(e, 1.2);
        v.evidence = e;
        v.confidence = (int) Math.min(99, Math.round(100.0 / (1.0 + Math.exp(-(e - 2.3) * 1.35))));

        if (f.structure >= 0 && v.confidence < 50) v.type = "Natural structure";
        else if ((hoppers >= 3 || f.groups[2] >= 40) && containers < 5 && beds == 0) v.type = "Farm / machine";
        else if (beds >= 1 && (containers >= 2 || work >= 1)) v.type = "Base";
        else if (outside && domestic >= 2 && v.confidence >= 50) v.type = "Camp / small base";
        else if (containers >= 3 && beds == 0) v.type = work >= 1 ? "Base" : "Storage room";
        else if (lights >= 4 && f.underground > 0.6 && containers < 2) v.type = "Tunnels / mine";
        else if (f.groups[5] > f.groups[0] + f.groups[1] + f.groups[2]) v.type = "Build";
        else v.type = v.confidence >= 50 ? "Base" : "Unclear";
        return v;
    }

    public static void boost(Verdict v, double neighbourEvidence) {
        if (v.evidence < 0.6 || v.type.equals("Natural structure")) return;
        double extra = Math.min(1.2, 0.3 * neighbourEvidence);
        if (extra < 0.1) return;
        v.evidence += extra;
        v.confidence = (int) Math.min(99, Math.round(100.0 / (1.0 + Math.exp(-(v.evidence - 2.3) * 1.35))));
        v.reasons.add("the chunks around it look built too");
    }

    public static int cutoff(int sensitivity, double crowding) {
        int base = switch (sensitivity) {
            case 0 -> 82;
            case 1 -> 64;
            case 2 -> 46;
            default -> 30;
        };
        double extra = Math.max(0, Math.min(12, (crowding - 0.08) * 60));
        return (int) Math.round(base + extra);
    }

    public static Verdict merge(List<Verdict> parts) {
        Verdict out = new Verdict();
        if (parts.isEmpty()) return out;
        Verdict best = parts.get(0);
        double sum = 0;
        for (Verdict p : parts) {
            if (p.confidence > best.confidence) best = p;
            sum += p.confidence;
        }
        double bonus = Math.min(8, (parts.size() - 1) * 2.5);
        out.confidence = (int) Math.min(99, Math.round(Math.max(best.confidence, sum / parts.size()) + bonus));
        out.type = best.type;
        out.reasons.addAll(best.reasons);
        out.evidence = best.evidence;
        return out;
    }

    public static final class Baseline {
        public double crowding = 0.08;
        public long samples;

        public void add(double fractionFlagged) {
            double a = samples < 5 ? 0.5 : 0.1;
            crowding = crowding + (fractionFlagged - crowding) * a;
            samples++;
        }
    }
}
