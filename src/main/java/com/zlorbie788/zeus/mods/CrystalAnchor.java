package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Aim;
import com.zlorbie788.zeus.core.Build;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Dmg;
import com.zlorbie788.zeus.core.Hits;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public final class CrystalAnchor {
    private CrystalAnchor() {}

    private static final class ActionCoordinator {
        private static long nextAllowedNanos;
        private static String lastOwner = "";
        private static String preferredOwner = "crystal";

        private static synchronized boolean claim(String owner) {
            long now = System.nanoTime();
            if (now < nextAllowedNanos) return false;
            if (owner.equals(lastOwner) && !owner.equals(preferredOwner)) return false;
            lastOwner = owner;
            preferredOwner = owner.equals("crystal") ? "anchor" : "crystal";
            nextAllowedNanos = now + 60_000_000L + ThreadLocalRandom.current().nextLong(35_000_000L);
            return true;
        }
    }

    private static final Predicate<ItemStack> CRYSTAL = Inv.is(Items.END_CRYSTAL);
    private static final Predicate<ItemStack> OBSIDIAN = Inv.is(Items.OBSIDIAN);

    private static boolean base(BlockState s) {
        return s.is(Blocks.OBSIDIAN) || s.is(Blocks.BEDROCK);
    }

    private static int jitterTicks(int ticks) {
        return ticks <= 0 ? ticks : Math.max(1, ticks + ThreadLocalRandom.current().nextInt(3) - 1);
    }

    public static final class CrystalAura extends Module {
        private final Targets.Pick who = Targets.pick(this::add, true, true, false);
        private final Setting.Num targetRange = add(new Setting.Num("Target range", "How far away a target is picked.", 10, 4, 20, 1, " blocks"));
        private final Setting.Num placeRange = add(new Setting.Num("Place range", "How far you place crystals and obsidian.", 4.5, 2, 6, 0.1, " blocks"));
        private final Setting.Num breakRange = add(new Setting.Num("Break range", "How far you hit crystals.", 4.5, 2, 6, 0.1, " blocks"));
        private final Setting.Num placeDelay = add(new Setting.Num("Place delay", "Ticks between two crystals. 20 ticks are one second.", 2, 0, 20, 1, " ticks"));
        private final Setting.Num breakDelay = add(new Setting.Num("Break delay", "Ticks between two hits on crystals.", 1, 0, 20, 1, " ticks"));
        private final Setting.Num minDamage = add(new Setting.Num("Minimum damage", "A crystal is only used when it would hurt the target at least this much (2 = one heart).", 6, 1, 36, 1, ""));
        private final Setting.Num maxSelf = add(new Setting.Num("Maximum self damage", "A crystal is never used when it would hurt you more than this.", 6, 0, 36, 1, ""));
        private final Setting.Bool antiSuicide = add(new Setting.Bool("Anti suicide", "Never use a crystal that could kill you.", true));
        private final Setting.Bool obsidian = add(new Setting.Bool("Place obsidian", "Lays obsidian for the crystal when there is nothing to place it on.", true));
        private final Setting.Num obsidianDelay = add(new Setting.Num("Obsidian delay", "Ticks between two obsidian blocks.", 4, 0, 20, 1, " ticks")).showIf(() -> obsidian.on());
        private final Setting.Bool onlyUseful = add(new Setting.Bool("Break only useful crystals", "Only hit crystals that hurt the target. Off: hit every crystal in reach.", true));
        private final Setting.Bool pauseEat = add(new Setting.Bool("Pause while eating", "Do nothing while you use an item.", true));
        private final Setting.Bool los = add(new Setting.Bool("Line of sight only", "Only work on crystals and targets you can actually see.", true));
        private final Setting.Bool rotate = add(new Setting.Bool("Look at it", "Tells the server you look at the crystal or block before you hit or place it (your own view does not move). Some servers refuse hits at things you do not face.", true));
        private final Setting.Bool swing = add(new Setting.Bool("Swing arm", "Show the arm swinging.", true));

        private int placeCd;
        private int breakCd;
        private int obsCd;
        private long warned;
        private String info;

        public CrystalAura() {
            super("crystalaura", "Crystal Aura", "Places obsidian and end crystals next to a target and blows them up.", Category.COMBAT);
        }

        @Override
        protected void onDisable() {
            info = null;
        }

        @Override
        public String info() {
            return info;
        }

        private float selfDamage(Minecraft mc, Vec3 c) {
            return Dmg.explosion(mc, c, mc.player, 6f);
        }

        private boolean selfSafe(Minecraft mc, float self) {
            if (self > maxSelf.get()) return false;
            return !antiSuicide.on() || self < mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1f;
        }

        @Override
        protected void onTick(Minecraft mc) {
            info = null;
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
            if (pauseEat.on() && mc.player.isUsingItem()) return;
            if (placeCd > 0) placeCd--;
            if (breakCd > 0) breakCd--;
            if (obsCd > 0) obsCd--;
            LivingEntity target = Targets.nearest(mc, who, targetRange.get());
            if (target == null) return;
            if (los.on() && !mc.player.hasLineOfSight(target)) return;
            info = target.getName().getString();

            if (breakCd == 0) {
                Entity bestCrystal = null;
                float bestScore = -1e9f;
                for (Entity e : com.zlorbie788.zeus.core.Env.entities(mc, breakRange.get() + 1.0)) {
                    if (e.getType() != EntityType.END_CRYSTAL || !e.isAlive()) continue;
                    if (Hits.cold(e.getId())) continue;
                    if (mc.player.getEyePosition().distanceTo(e.position()) > breakRange.get()) continue;
                    if (los.on() && !mc.player.hasLineOfSight(e)) continue;
                    float dt = Dmg.explosion(mc, e.position(), target, 6f);
                    float ds = selfDamage(mc, e.position());
                    if (!selfSafe(mc, ds)) continue;
                    if (onlyUseful.on() && dt < minDamage.get()) continue;
                    float score = dt - ds;
                    if (score > bestScore) {
                        bestScore = score;
                        bestCrystal = e;
                    }
                }
                if (bestCrystal != null) {
                    if (!ActionCoordinator.claim("crystal")) return;
                    if (rotate.on()) Aim.look(bestCrystal.position().add(0, 0.5, 0));
                    Hits.attempt(bestCrystal);
                    mc.gameMode.attack(mc.player, bestCrystal);
                    if (swing.on()) mc.player.swing(InteractionHand.MAIN_HAND);
                    breakCd = jitterTicks(breakDelay.i());
                    return;
                }
            }

            BlockPos tp = target.blockPosition();
            if (placeCd == 0) {
                BlockPos bestBase = null;
                float bestScore = -1e9f;
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        for (int dy = -3; dy <= 1; dy++) {
                            BlockPos b = tp.offset(dx, dy, dz);
                            if (!base(Build.state(b))) continue;
                            BlockPos up = b.above();
                            if (!Build.air(up) || !Build.air(up.above())) continue;
                            if (Build.distTo(b) > placeRange.get() + 0.5) continue;
                            Vec3 c = new Vec3(b.getX() + 0.5, b.getY() + 1.0, b.getZ() + 0.5);
                            float dt = Dmg.explosion(mc, c, target, 6f);
                            if (dt < minDamage.get()) continue;
                            float ds = selfDamage(mc, c);
                            if (!selfSafe(mc, ds)) continue;
                            if (Build.blocked(up) || Build.blocked(up.above())) continue;
                            float score = dt - ds;
                            if (score > bestScore) {
                                bestScore = score;
                                bestBase = b;
                            }
                        }
                    }
                }
                if (bestBase != null) {
                    if (!ActionCoordinator.claim("crystal")) return;
                    if (rotate.on()) Aim.look(new Vec3(bestBase.getX() + 0.5, bestBase.getY() + 1.0, bestBase.getZ() + 0.5));
                    if (Build.useOn(Build.top(bestBase), CRYSTAL, swing.on())) {
                        placeCd = jitterTicks(placeDelay.i());
                        info = target.getName().getString() + " " + Math.round(bestScore);
                    } else if (System.currentTimeMillis() - warned > 5000) {
                        warned = System.currentTimeMillis();
                        Zeus.toast("Crystal Aura: no end crystals in your hotbar or offhand", false);
                    }
                    return;
                }
            }

            if (obsidian.on() && obsCd == 0 && (Inv.hotbar(OBSIDIAN) >= 0 || Inv.offhand(OBSIDIAN))) {
                BlockPos mine = mc.player.blockPosition();
                BlockPos bestSpot = null;
                float bestScore = -1e9f;
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        for (int dy = -1; dy <= 0; dy++) {
                            BlockPos b = tp.offset(dx, dy, dz);
                            if (b.equals(mine) || b.equals(mine.below()) || b.equals(tp) || b.equals(tp.below())) continue;
                            if (!Build.canPlace(b, Build.REACH) || Build.distTo(b) > placeRange.get()) continue;
                            BlockPos up = b.above();
                            if (!Build.air(up) || !Build.air(up.above()) || Build.blocked(up) || Build.blocked(up.above())) continue;
                            Vec3 c = new Vec3(b.getX() + 0.5, b.getY() + 1.0, b.getZ() + 0.5);
                            float dt = Dmg.explosion(mc, c, target, 6f);
                            if (dt < minDamage.get()) continue;
                            float ds = selfDamage(mc, c);
                            if (!selfSafe(mc, ds)) continue;
                            float score = dt - ds;
                            if (score > bestScore) {
                                bestScore = score;
                                bestSpot = b;
                            }
                        }
                    }
                }
                if (bestSpot != null) {
                    if (!ActionCoordinator.claim("crystal")) return;
                    if (rotate.on()) Aim.look(Vec3.atCenterOf(bestSpot));
                    if (Build.place(bestSpot, OBSIDIAN, swing.on(), Build.REACH)) obsCd = jitterTicks(obsidianDelay.i());
                }
            }
        }
    }

    public static final class AnchorAura extends Module {
        private final Targets.Pick who = Targets.pick(this::add, true, true, false);
        private final Setting.Num targetRange = add(new Setting.Num("Target range", "How far away a target is picked.", 8, 3, 16, 1, " blocks"));
        private final Setting.Num placeRange = add(new Setting.Num("Place range", "How far you place the anchor.", 4.5, 2, 6, 0.1, " blocks"));
        private final Setting.Num charges = add(new Setting.Num("Charges", "How many times the anchor is charged. Every extra charge uses a glowstone; one charge is already a full explosion.", 1, 1, 4, 1, ""));
        private final Setting.Num delay = add(new Setting.Num("Delay", "Ticks between steps (place, charge, explode).", 2, 0, 20, 1, " ticks"));
        private final Setting.Num minDamage = add(new Setting.Num("Minimum damage", "Only blow up an anchor when it hurts the target at least this much.", 6, 1, 36, 1, ""));
        private final Setting.Num maxSelf = add(new Setting.Num("Maximum self damage", "Never use an anchor that would hurt you more than this.", 6, 0, 36, 1, ""));
        private final Setting.Bool antiSuicide = add(new Setting.Bool("Anti suicide", "Never use an anchor that could kill you.", true));
        private final Setting.Bool rotate = add(new Setting.Bool("Look at it", "Tells the server you look at the anchor before you place, charge or set it off (your own view does not move).", true));
        private final Setting.Bool swing = add(new Setting.Bool("Swing arm", "Show the arm swinging.", true));
        private final Setting.Bool los = add(new Setting.Bool("Line of sight only", "Only work on targets and anchors you can actually see.", true));
        private final Setting.Bool pauseUse = add(new Setting.Bool("Pause while eating", "Do nothing while you use an item.", true));

        private BlockPos anchor;
        private long ticks;
        private long placedAt;
        private long chargedAt;
        private int charged;
        private int cd;
        private long warned;
        private String info;

        public AnchorAura() {
            super("anchoraura", "Anchor Aura", "Places, charges and blows up respawn anchors next to a target.", Category.COMBAT);
        }

        @Override
        protected void onEnable() {
            anchor = null;
            charged = 0;
            ticks = 0;
        }

        static int chargeOf(BlockState st) {
            try {
                for (net.minecraft.world.level.block.state.properties.Property<?> p : st.getProperties()) {
                    if (p.getName().equals("charges") && p instanceof net.minecraft.world.level.block.state.properties.IntegerProperty ip) return st.getValue(ip);
                }
            } catch (RuntimeException ignored) {
            }
            return -1;
        }

        @Override
        public String info() {
            return info;
        }

        private void warn(String text) {
            if (System.currentTimeMillis() - warned > 5000) {
                warned = System.currentTimeMillis();
                Zeus.toast(text, false);
            }
        }

        private boolean safe(Minecraft mc, Vec3 c) {
            float self = Dmg.explosion(mc, c, mc.player, 5f);
            if (self > maxSelf.get()) return false;
            return !antiSuicide.on() || self < mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1f;
        }

        private boolean isAnchor(BlockPos p) {
            return Build.state(p).is(Blocks.RESPAWN_ANCHOR);
        }

        @Override
        protected void onTick(Minecraft mc) {
            info = null;
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
            if (pauseUse.on() && mc.player.isUsingItem()) return;
            ticks++;
            if (cd > 0) {
                cd--;
                return;
            }
            if (mc.level.dimension().equals(Level.NETHER)) {
                warn("Anchor Aura: anchors do not explode in the Nether");
                return;
            }
            LivingEntity target = Targets.nearest(mc, who, targetRange.get());
            if (target == null) {
                anchor = null;
                return;
            }
            if (los.on() && !mc.player.hasLineOfSight(target)) {
                anchor = null;
                return;
            }
            info = target.getName().getString();
            if (anchor != null && !isAnchor(anchor)) {
                if (ticks - placedAt < 12) return;
                anchor = null;
                charged = 0;
            }
            if (anchor != null && charged > 0) {
                int real = chargeOf(Build.state(anchor));
                if (real >= 0 && ticks - chargedAt > 8) charged = real;
            }

            if (anchor == null) {
                BlockPos tp = target.blockPosition();
                BlockPos best = null;
                float bestScore = -1e9f;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dy = 0; dy <= 2; dy++) {
                            BlockPos p = tp.offset(dx, dy, dz);
                            if (p.equals(mc.player.blockPosition()) || p.equals(mc.player.blockPosition().above())) continue;
                            if (!Build.canPlace(p, Build.REACH) || Build.distTo(p) > placeRange.get()) continue;
                            Vec3 c = Vec3.atCenterOf(p);
                            float dt = Dmg.explosion(mc, c, target, 5f);
                            if (dt < minDamage.get() || !safe(mc, c)) continue;
                            float score = dt - Dmg.explosion(mc, c, mc.player, 5f);
                            if (score > bestScore) {
                                bestScore = score;
                                best = p;
                            }
                        }
                    }
                }
                if (best == null) return;
                if (!ActionCoordinator.claim("anchor")) return;
                if (rotate.on()) Aim.look(Vec3.atCenterOf(best));
                if (Build.place(best, Inv.is(Items.RESPAWN_ANCHOR), swing.on(), Build.REACH)) {
                    anchor = best;
                    placedAt = ticks;
                    charged = 0;
                    cd = jitterTicks(delay.i());
                } else {
                    warn("Anchor Aura: no respawn anchor in your hotbar");
                }
                return;
            }

            BlockHitResult click = new BlockHitResult(Vec3.atCenterOf(anchor).add(0, 0.5, 0), net.minecraft.core.Direction.UP, anchor, false);
            if (Build.distTo(anchor) > Build.REACH + 0.5) {
                anchor = null;
                return;
            }
            if (rotate.on()) Aim.look(Vec3.atCenterOf(anchor));
            if (charged < charges.i()) {
                if (!ActionCoordinator.claim("anchor")) return;
                if (Build.useOn(click, Inv.is(Items.GLOWSTONE), swing.on())) {
                    charged++;
                    chargedAt = ticks;
                    cd = Math.max(jitterTicks(delay.i()), 1);
                } else {
                    warn("Anchor Aura: no glowstone in your hotbar");
                }
                return;
            }
            int slot = Inv.hotbar(s -> !s.is(Items.GLOWSTONE));
            if (slot < 0) {
                for (int i = 0; i < 9; i++) if (!Inv.get(i).is(Items.GLOWSTONE)) slot = i;
            }
            if (slot < 0) {
                warn("Anchor Aura: free a hotbar slot that is not glowstone");
                return;
            }
            if (!ActionCoordinator.claim("anchor")) return;
            Inv.with(slot, () -> {
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, click);
                if (swing.on()) mc.player.swing(InteractionHand.MAIN_HAND);
            });
            charged = 0;
            anchor = null;
            cd = jitterTicks(delay.i());
        }
    }
}
