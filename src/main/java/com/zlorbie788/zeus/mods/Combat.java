package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Aim;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Hits;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Names;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class Combat {
    private Combat() {}

    public static final class AttributeSwap extends Module {
        private final Setting.Choice mode = add(new Setting.Choice("Mode", "Smart finds a sword and mace in your hotbar. Simple alternates two selected slots.", 0, "Smart sword / mace", "Simple slots"));
        private final Setting.Num first = add(new Setting.Num("First hotbar slot", "First slot for Simple mode (1-9).", 1, 1, 9, 1, "" )).showIf(() -> mode.index() == 1);
        private final Setting.Num second = add(new Setting.Num("Second hotbar slot", "Second slot for Simple mode (1-9).", 2, 1, 9, 1, "" )).showIf(() -> mode.index() == 1);
        private int lastSmart = -1;
        public AttributeSwap() { super("attributeswap", "Attribute Swap", "Quickly alternates your hotbar between a sword and mace, or two chosen slots. Bind a key in Controls.", Category.COMBAT); }
        public void swap(Minecraft mc) {
            if (!enabled() || mc.player == null || mc.level == null || mc.screen != null) return;
            var inv = mc.player.getInventory();
            int a, b;
            if (mode.index() == 0) {
                a = -1; b = -1;
                for (int i = 0; i < 9; i++) {
                    var st = inv.getItem(i);
                    if (st.is(net.minecraft.world.item.Items.MACE)) b = i;
                    else if (Names.item(st).endsWith("_sword")) a = i;
                }
                if (a < 0 || b < 0) { com.zlorbie788.zeus.core.Zeus.toast("Need a sword and mace in your hotbar", false); return; }
                int current = inv.getSelectedSlot();
                inv.setSelectedSlot(current == a ? b : current == b ? a : (lastSmart == a ? b : a));
                lastSmart = inv.getSelectedSlot();
            } else {
                a = (int) first.get() - 1; b = (int) second.get() - 1;
                if (a == b) { com.zlorbie788.zeus.core.Zeus.toast("Choose two different hotbar slots", false); return; }
                if (inv.getItem(a).isEmpty() || inv.getItem(b).isEmpty()) {
                    com.zlorbie788.zeus.core.Zeus.toast("Both Simple slots need an item", false);
                    return;
                }
                inv.setSelectedSlot(inv.getSelectedSlot() == a ? b : a);
            }
        }
    }

    public static final class KillAura extends Module {
        private final Setting.Num range = add(new Setting.Num("Range", "How far away a target can be.", 4.2, 2.5, 6.0, 0.1, " blocks"));
        private final Setting.Bool hostiles = add(new Setting.Bool("Monsters", "Attack hostile mobs.", true));
        private final Setting.Bool animals = add(new Setting.Bool("Animals and villagers", "Also attack passive mobs. Tamed pets are always spared.", false));
        private final Setting.Bool players = add(new Setting.Bool("Players", "Also attack other players. Friends are always spared.", false));
        private final Setting.Choice priority = add(new Setting.Choice("Pick the target by", "Closest: the nearest one. Lowest health: the one that is closest to dying, which finishes fights fastest. Biggest threat: weighs distance, health and line of sight, so the one actually hurting you comes first.", 0, "Closest", "Lowest health", "Biggest threat"));
        private final Setting.Choice mode = add(new Setting.Choice("Attack mode", "Spam: hits again and again as fast as you set, ignoring the attack bar. Crit: only hits while falling, so every hit is a critical hit. Cooldown: one full-power hit each time the bar is full.", 2, "Spam", "Crit", "Cooldown"));
        private final Setting.Choice style = add(new Setting.Choice("Aura style", "Rage: attacks as fast as the settings allow. Legit: turns the view like a player, keeps reach and view inside vanilla limits, waits for a full swing and pauses briefly before the first hit.", 0, "Rage", "Legit"));
        private final Setting.Num rate = add(new Setting.Num("Hits per second", "How fast Spam mode hits.", 10, 1, 20, 1, "")).showIf(() -> this.mode.index() == 0);
        private final Setting.Bool jump = add(new Setting.Bool("Jump for crits", "Crit mode jumps by itself when it has a target and the bar is full, then hits on the way down. Off: you jump yourself.", true)).showIf(() -> this.mode.index() == 1);
        private final Setting.Bool full = add(new Setting.Bool("Wait for full swing", "Only hit when the attack bar is full, so every hit does full damage.", true)).showIf(() -> this.mode.index() != 0);
        private long nextHit;
        private final Setting.Bool los = add(new Setting.Bool("Line of sight only", "Skip targets that are behind walls.", true));
        private final Setting.Bool swing = add(new Setting.Bool("Swing arm", "Show the arm swinging on every hit.", true));
        private final Setting.Bool silent = add(new Setting.Bool("Silent rotation", "Send a look packet at the target right before every hit, so the server sees you facing what you hit even while your camera looks somewhere else.", true));
        private final Setting.Bool confirm = add(new Setting.Bool("Confirm hits", "Drop a target whose last few swings got no answer from the server, and show how many hits are landing.", true));
        private final Setting.Num fov = add(new Setting.Num("Field of view", "Only attack targets inside this cone around your crosshair, so the aura never turns you round to hit something behind you. 360 ignores it.", 150, 30, 360, 10, "\u00b0"));
        private final Setting.Bool humanAim = add(new Setting.Bool("Humanised aim", "Turn the server's copy of your view toward the target at a human speed instead of snapping to it, and hit only once it is on target.", true));
        private final Setting.Num turn = add(new Setting.Num("Turn speed", "The fastest the server-side view turns, in degrees per second.", 420, 180, 1080, 20, "\u00b0/s")).showIf(humanAim::on);
        private final Setting.Bool timing = add(new Setting.Bool("Randomise timing", "Vary the time between hits like a hand instead of a metronome.", true));
        private final Setting.Bool pauseUse = add(new Setting.Bool("Pause while using an item", "Do not attack while you eat or draw a bow.", true));
        private float aimYaw = Float.NaN;
        private float aimPitch;
        private long reactAt;
        private int lastTargetId = -1;
        private String target;

        public KillAura() {
            super("killaura", "Kill Aura", "Attacks the nearest monster in range for you.", Category.COMBAT);
        }

        @Override
        protected void onTick(Minecraft mc) {
            target = null;
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
            boolean legit = style.index() == 1;
            int m = legit ? 2 : mode.index();
            var me = mc.player;
            if ((legit || pauseUse.on()) && me.isUsingItem()) {
                aimYaw = Float.NaN;
                return;
            }
            if (m == 0 && System.currentTimeMillis() < nextHit) return;
            if (legit && me.getAttackStrengthScale(0f) < 0.99f) return;
            if (m != 0 && full.on() && me.getAttackStrengthScale(0f) < 0.95f) return;
            double reach = legit ? Math.min(range.get(), 3.1) : range.get();
            double fovDeg = legit ? Math.min(fov.get(), 100.0) : fov.get();
            double r2 = reach * reach;
            Vec3 look = me.getLookAngle();
            double cone = Math.cos(Math.toRadians(fovDeg / 2.0));
            Entity best = null;
            double bestD = Double.MAX_VALUE;
            for (Entity e : Env.entities(mc, reach + 1.0)) {
                if (!(e instanceof LivingEntity le) || e == mc.player || e.getType() == EntityType.ARMOR_STAND || !le.isAlive()) continue;
                if (e instanceof Player) {
                    if (!players.on() || Friends.protects(e) || e.isSpectator()) continue;
                } else {
                    if (e instanceof TamableAnimal t && t.isTame()) continue;
                    boolean hostile = e instanceof Enemy;
                    if (hostile ? !hostiles.on() : !animals.on()) continue;
                }
                double dist = mc.player.distanceToSqr(e);
                if (dist > r2) continue;
                if (fovDeg < 359.0) {
                    Vec3 to = e.getBoundingBox().getCenter().subtract(me.getEyePosition());
                    if (to.lengthSqr() > 1e-6 && to.normalize().dot(look) < cone) continue;
                }
                double d = switch (priority.index()) {
                    case 1 -> le.getHealth() + le.getAbsorptionAmount() + dist * 0.001;
                    case 2 -> threat(le, dist, me);
                    default -> dist;
                };
                if (d >= bestD) continue;
                if (los.on() && !mc.player.hasLineOfSight(e)) continue;
                best = e;
                bestD = d;
            }
            if (best == null) {
                aimYaw = Float.NaN;
                lastTargetId = -1;
                return;
            }
            target = best.getName().getString();
            if (m == 1) {
                boolean falling = !me.onGround() && me.fallDistance > 0.0f && me.getDeltaMovement().y < 0 && !me.isInWater() && !me.onClimbable() && !me.isPassenger() && !me.isSprinting();
                if (!falling) {
                    if (jump.on() && me.onGround() && !me.isInWater() && !me.onClimbable() && !me.isPassenger()) {
                        var v = me.getDeltaMovement();
                        me.setDeltaMovement(v.x, 0.42, v.z);
                    }
                    return;
                }
            }
            if (Ghost.skipSwing()) return;
            if (confirm.on() && Hits.cold(best.getId())) {
                target = null;
                return;
            }
            Vec3 lead = best.getDeltaMovement();
            double leadScale = 0.35 + Math.min(0.20, lead.length() * 0.05);
            Vec3 aimAt = best.position().add(lead.scale(leadScale)).add(0, best.getBbHeight() * 0.55, 0);
            if (legit || humanAim.on()) {
                if (!humanAim(me, aimAt)) return;
            } else if (silent.on()) {
                Aim.look(aimAt);
            }
            if (legit) {
                if (best.getId() != lastTargetId) {
                    lastTargetId = best.getId();
                    reactAt = System.currentTimeMillis() + 130 + ThreadLocalRandom.current().nextLong(130);
                }
                if (System.currentTimeMillis() < reactAt) return;
            }
            if (m == 0) {
                double interval = 1000.0 / rate.get();
                if (timing.on()) interval *= ThreadLocalRandom.current().nextDouble(0.88, 1.12);
                nextHit = System.currentTimeMillis() + (long) interval;
            }
            if (!HitSelect.allow(best)) return;
            if (Phantom.miss()) return;
            if (confirm.on()) Hits.attempt(best);
            mc.gameMode.attack(mc.player, best);
            if (swing.on() || legit) mc.player.swing(InteractionHand.MAIN_HAND);
        }

        private static double threat(LivingEntity le, double dist, Player me) {
            double score = dist * 0.8 + (le.getHealth() + le.getAbsorptionAmount()) * 0.35;
            if (!le.hasLineOfSight(me)) score += 6.0;
            if (le instanceof Player p && p.getLastHurtByMob() == me) score -= 2.0;
            if (le.isBlocking()) score -= 0.5;
            return score;
        }

        private boolean humanAim(Player me, Vec3 point) {
            if (Float.isNaN(aimYaw)) {
                aimYaw = me.getYRot();
                aimPitch = me.getXRot();
            }
            Vec3 eye = me.getEyePosition();
            float wantYaw = Aim.yawTo(eye, point);
            float wantPitch = Math.max(-90f, Math.min(90f, Aim.pitchTo(eye, point)));
            float dy = AimAssist.wrap(wantYaw - aimYaw);
            float dp = wantPitch - aimPitch;
            float off = Math.max(Math.abs(dy), Math.abs(dp));
            if (off > 0.05f) {
                double turnRate = style.index() == 1 ? Math.min(turn.get(), 260.0) : turn.get();
                float step = (float) Math.min(off, turnRate / 20.0 * ThreadLocalRandom.current().nextDouble(0.88, 1.12));
                aimYaw = AimAssist.wrap(aimYaw + dy / off * step);
                aimPitch = Math.max(-90f, Math.min(90f, aimPitch + dp / off * step));
                Aim.sendRotation(aimYaw, aimPitch);
            }
            return off <= (style.index() == 1 ? 2.5f : 4.0f);
        }

        @Override
        protected void onDisable() {
            aimYaw = Float.NaN;
            lastTargetId = -1;
            target = null;
        }

        @Override
        protected void cleanSlate() {
            aimYaw = Float.NaN;
            lastTargetId = -1;
        }

        @Override
        public String info() {
            if (target == null) return null;
            String landed = confirm.on() ? Hits.text() : "";
            return landed.isEmpty() ? target : target + " " + landed;
        }
    }

    public static final class AimAssist extends Module {
        private final Targets.Pick who = Targets.pick(this::add, true, true, false);
        private final Setting.Choice when = add(new Setting.Choice("Works", "Holding attack: only while the attack button is held down. Always: whenever something is near your crosshair.", 0, "Holding attack", "Always"));
        private final Setting.Choice mode = add(new Setting.Choice("Mode", "Smooth: turns your real view, so your screen follows the target. Silent: only the server's copy of your view turns, so the hit is accepted and your screen stays where you put it.", 0, "Smooth", "Silent"));
        private final Setting.Num range = add(new Setting.Num("Range", "How far away a target can be.", 4.5, 2.0, 8.0, 0.1, " blocks"));
        private final Setting.Num fov = add(new Setting.Num("Field of view", "Only targets this close to where you look (the whole cone, in degrees) are helped. Small values only nudge you toward what you already aim at.", 70, 10, 180, 5, "\u00b0"));
        private final Setting.Num strength = add(new Setting.Num("Strength", "How much of the angle that is left is covered every tick. Lower values create a slower, more natural correction.", 18, 3, 60, 1, "%"));
        private final Setting.Num speed = add(new Setting.Num("Top speed", "The fastest it will ever turn you, in degrees per second. Keeps big corrections smooth.", 260, 40, 720, 10, "\u00b0/s"));
        private final Setting.Num vertical = add(new Setting.Num("Up and down", "How strongly it helps up and down compared to left and right. 0: sideways only.", 60, 0, 100, 5, "%"));
        private final Setting.Choice point = add(new Setting.Choice("Aim at", "Nearest part: the spot of the target's body closest to your crosshair, so it moves you as little as possible. Chest: the middle of the body. Head: the eyes.", 0, "Nearest part", "Chest", "Head"));
        private final Setting.Bool lead = add(new Setting.Bool("Lead moving targets", "Aims a little ahead of a target that is running, using its real velocity.", true));
        private final Setting.Num leadAmount = add(new Setting.Num("Lead amount", "How many ticks of the target's movement to aim ahead by.", 1.2, 0.0, 3.0, 0.1, " ticks")).showIf(lead::on);
        private final Setting.Bool los = add(new Setting.Bool("Line of sight only", "Ignore targets behind walls.", true));
        private final Setting.Bool weapon = add(new Setting.Bool("Only with a weapon", "Only helps while you hold a sword, axe, mace or trident.", false));
        private final Setting.Bool sticky = add(new Setting.Bool("Keep the same target", "Stays with one target while it is in range and view, instead of jumping between several.", true));
        private final Setting.Bool yieldMouse = add(new Setting.Bool("Give way to the mouse", "While you turn the mouse yourself, the assist backs off, so it never fights you.", true));
        private final Setting.Num dead = add(new Setting.Num("Dead zone", "Inside this angle (degrees) it does nothing, so a target you already aim at is not shaken.", 0.6, 0.0, 3.0, 0.1, "\u00b0"));
        private final Setting.Bool wobble = add(new Setting.Bool("Human wobble", "A tiny, slow drift in the pull, so the movement is not perfectly even.", true));
        private final Setting.Bool menus = add(new Setting.Bool("Pause in menus", "No assist while a screen is open.", true));

        private LivingEntity locked;
        private String name;
        private float lastYaw = Float.NaN;
        private float lastPitch;
        private float give = 1f;
        private long tick;

        public AimAssist() {
            super("aimassist", "Aim Assist", "Eases your aim onto the target nearest your crosshair, smoothly and without a snap.", Category.COMBAT);
        }

        @Override
        protected void onDisable() {
            locked = null;
            name = null;
            lastYaw = Float.NaN;
        }

        @Override
        public String info() {
            return name;
        }

        private static boolean isWeapon(net.minecraft.world.item.ItemStack st) {
            String n = Names.item(st);
            return n.endsWith("_sword") || n.endsWith("_axe") || n.equals("mace") || n.equals("trident") || n.endsWith(":mace") || n.endsWith(":trident");
        }

        static float wrap(float a) {
            a %= 360f;
            if (a >= 180f) a -= 360f;
            if (a < -180f) a += 360f;
            return a;
        }

        private Vec3 aimPoint(Vec3 eye, Vec3 look, LivingEntity e) {
            AABB b = e.getBoundingBox();
            Vec3 p;
            switch (point.index()) {
                case 1 -> p = new Vec3((b.minX + b.maxX) / 2, b.minY + (b.maxY - b.minY) * 0.62, (b.minZ + b.maxZ) / 2);
                case 2 -> p = e.getEyePosition();
                default -> {
                    Vec3 c = new Vec3((b.minX + b.maxX) / 2, (b.minY + b.maxY) / 2, (b.minZ + b.maxZ) / 2);
                    double t = Math.max(0.0, c.subtract(eye).dot(look));
                    Vec3 on = eye.add(look.scale(t));
                    p = new Vec3(Math.max(b.minX, Math.min(b.maxX, on.x)), Math.max(b.minY, Math.min(b.maxY, on.y)), Math.max(b.minZ, Math.min(b.maxZ, on.z)));
                }
            }
            if (lead.on() && leadAmount.get() > 0.01) {
                Vec3 v = e.getDeltaMovement();
                double t = leadAmount.get();
                p = p.add(v.x * t, Math.max(0.0, v.y) * t * 0.5, v.z * t);
            }
            return p;
        }

        private static double threat(Minecraft mc, LivingEntity e) {
            var me = mc.player;
            if (me == null) return 0.0;
            double score = 0.0;
            if (e.getLastHurtByMob() == me) score += 1.0;
            if (e instanceof net.minecraft.world.entity.monster.Enemy) score += 0.4;
            double d2 = me.distanceToSqr(e);
            if (d2 < 9.0) score += 0.5;
            return score;
        }

        private boolean stillGood(Minecraft mc, LivingEntity e, Vec3 eye, Vec3 look, double cone, double r) {
            if (!e.isAlive() || e.getHealth() <= 0f) return false;
            if (mc.player == null || mc.player.distanceTo(e) > r + 0.5) return false;
            if (los.on() && !mc.player.hasLineOfSight(e)) return false;
            Vec3 d = aimPoint(eye, look, e).subtract(eye);
            return d.lengthSqr() > 1e-4 && d.normalize().dot(look) >= cone - 0.08;
        }

        private LivingEntity pick(Minecraft mc) {
            var me = mc.player;
            if (me == null) return null;
            Vec3 eye = me.getEyePosition();
            Vec3 look = me.getLookAngle();
            double cone = Math.cos(Math.toRadians(fov.get() / 2.0));
            double r = range.get();
            if (locked != null && sticky.on()) {
                if (stillGood(mc, locked, eye, look, cone, r)) return locked;
            }
            locked = null;
            double bestScore = -2.0;
            LivingEntity best = null;
            for (Entity e : Env.entities(mc, r + 1.0)) {
                if (!Targets.accepts(mc, who, e)) continue;
                LivingEntity le = (LivingEntity) e;
                double dist = me.distanceTo(le);
                if (dist > r) continue;
                if (los.on() && !me.hasLineOfSight(le)) continue;
                Vec3 d = aimPoint(eye, look, le).subtract(eye);
                if (d.lengthSqr() < 1e-4) continue;
                double dot = d.normalize().dot(look);
                if (dot < cone) continue;
                double score = dot - dist * 0.015 + threat(mc, le) * 0.02;
                if (score <= bestScore) continue;
                bestScore = score;
                best = le;
            }
            locked = best;
            return best;
        }

        @Override
        protected void onTick(Minecraft mc) {
            tick++;
            var me = mc.player;
            if (me == null || mc.level == null || (menus.on() && mc.screen != null) || me.isSpectator()) {
                clearAll();
                return;
            }
            float yaw = me.getYRot();
            float pitch = me.getXRot();
            if (yieldMouse.on() && !Float.isNaN(lastYaw)) {
                float moved = Math.abs(wrap(yaw - lastYaw)) + Math.abs(pitch - lastPitch);
                float want = moved > 1.2f ? Math.max(0.25f, 1f - moved / 14f) : 1f;
                give += (want - give) * (want < give ? 0.7f : 0.15f);
            } else {
                give = 1f;
            }
            boolean wanted = when.index() == 1 || mc.options.keyAttack.isDown();
            if (!wanted || (weapon.on() && !isWeapon(me.getMainHandItem()))) {
                locked = null;
                name = null;
                lastYaw = yaw;
                lastPitch = pitch;
                return;
            }
            LivingEntity target = pick(mc);
            if (target == null) {
                name = null;
                lastYaw = yaw;
                lastPitch = pitch;
                return;
            }
            name = target.getName().getString();
            Vec3 eye = me.getEyePosition();
            Vec3 aim = aimPoint(eye, me.getLookAngle(), target);
            float dYaw = wrap(Aim.yawTo(eye, aim) - yaw);
            float dPitch = Math.max(-90f, Math.min(90f, Aim.pitchTo(eye, aim))) - pitch;
            float dz = (float) dead.get();
            if (Math.abs(dYaw) <= dz && Math.abs(dPitch) <= dz) {
                lastYaw = yaw;
                lastPitch = pitch;
                return;
            }
            double rate = Math.max(0.03, strength.get() / 100.0);
            float k = (float) (1.0 - Math.exp(-rate * 2.4)) * give;
            float off = Math.max(Math.abs(dYaw), Math.abs(dPitch));
            k *= 0.55f + 0.45f * Math.min(1f, off / 12f);
            if (wobble.on()) k *= 0.9f + 0.2f * (float) (0.5 + 0.5 * Math.sin(tick * 0.31 + 1.7) * Math.sin(tick * 0.113));
            float maxStep = (float) (speed.get() / 20.0) * give;
            float sy = Math.abs(dYaw) <= dz ? 0f : clamp(dYaw * k, maxStep);
            float sp = Math.abs(dPitch) <= dz ? 0f : clamp(dPitch * k * (float) (vertical.get() / 100.0), maxStep * 0.7f);
            sy = Math.signum(sy) * Math.min(Math.abs(sy), Math.abs(dYaw));
            sp = Math.signum(sp) * Math.min(Math.abs(sp), Math.abs(dPitch));
            if (sy == 0f && sp == 0f) {
                lastYaw = yaw;
                lastPitch = pitch;
                return;
            }
            float ny = yaw + sy + Phantom.wobble() * 0.6f + Phantom.drift();
            float np = Math.max(-90f, Math.min(90f, pitch + sp + Phantom.wobble() * 0.35f));
            if (mode.index() == 1) {
                Aim.sendRotation(ny, np);
                lastYaw = yaw;
                lastPitch = pitch;
            } else {
                me.setYRot(ny);
                me.setXRot(np);
                lastYaw = ny;
                lastPitch = np;
            }
        }

        private void clearAll() {
            locked = null;
            name = null;
            lastYaw = Float.NaN;
        }

        private static float clamp(float v, float max) {
            return Math.max(-max, Math.min(max, v));
        }
    }

    public static final class AutoCombo extends Module {
        private final Targets.Pick who = Targets.pick(this::add, true, true, false);
        private final Setting.Num reach = add(new Setting.Num("Reach", "How far the target may be. Normal hits reach 3 blocks.", 3.0, 2.5, 6.0, 0.1, " blocks"));
        private final Setting.Num charge = add(new Setting.Num("Minimum attack charge", "The attack bar must be at least this full. 1 is a full-damage hit every time.", 1.0, 0.8, 1.0, 0.01, ""));
        private final Setting.Choice crits = add(new Setting.Choice("Critical hits", "Off: hit as soon as the bar is full. Wait for the fall: hit only while falling, so the hit is a critical one. Jump when ready: jumps by itself, then hits on the way down.", 0, "Off", "Wait for the fall", "Jump when ready"));
        private final Setting.Bool aim = add(new Setting.Bool("Aim at target", "When a target is in reach but not under the crosshair, tell the server you look at it and hit it anyway (your own view does not move).", false));
        private final Setting.Bool shield = add(new Setting.Bool("Break shields", "Use an axe from your hotbar on a target that is blocking with a shield.", true));
        private final Setting.Bool los = add(new Setting.Bool("Line of sight only", "Skip targets behind walls.", true));
        private final Setting.Bool pauseUse = add(new Setting.Bool("Pause while using an item", "Do not hit while you eat or draw a bow. Turn it off together with Multi Use.", true));
        private final Setting.Bool swing = add(new Setting.Bool("Swing arm", "Show the arm swinging.", true));
        private String target;

        public AutoCombo() {
            super("autocombo", "Auto Combo", "Hits what you look at the moment a hit does full damage, every time.", Category.COMBAT);
        }

        @Override
        public String info() {
            return target;
        }

        private static double reachTo(Vec3 eye, Entity e) {
            AABB b = e.getBoundingBox();
            double x = Math.max(b.minX, Math.min(eye.x, b.maxX));
            double y = Math.max(b.minY, Math.min(eye.y, b.maxY));
            double z = Math.max(b.minZ, Math.min(eye.z, b.maxZ));
            return eye.distanceTo(new Vec3(x, y, z));
        }

        @Override
        protected void onTick(Minecraft mc) {
            target = null;
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
            var me = mc.player;
            if (pauseUse.on() && me.isUsingItem()) return;
            Vec3 eye = me.getEyePosition();
            Entity t = null;
            if (mc.hitResult instanceof EntityHitResult ehr && Targets.accepts(mc, who, ehr.getEntity()) && reachTo(eye, ehr.getEntity()) <= reach.get()) {
                t = ehr.getEntity();
            } else if (aim.on()) {
                LivingEntity near = Targets.nearest(mc, who, reach.get() + 1.0);
                if (near != null && reachTo(eye, near) <= reach.get() && (!los.on() || me.hasLineOfSight(near))) t = near;
            }
            if (t == null) return;
            target = t.getName().getString();
            if (me.getAttackStrengthScale(0f) < charge.get()) return;
            if (crits.index() > 0) {
                boolean falling = !me.onGround() && me.fallDistance > 0.0f && me.getDeltaMovement().y < 0 && !me.isInWater() && !me.onClimbable() && !me.isPassenger() && !me.isSprinting();
                if (!falling) {
                    if (crits.index() == 2 && me.onGround() && !me.isInWater() && !me.onClimbable() && !me.isPassenger()) {
                        var v = me.getDeltaMovement();
                        me.setDeltaMovement(v.x, 0.42, v.z);
                    }
                    return;
                }
            }
            final Entity hit = t;
            if (aim.on() && !(mc.hitResult instanceof EntityHitResult e2 && e2.getEntity() == hit)) Aim.look(hit.position().add(0, hit.getBbHeight() * 0.6, 0));
            Runnable strike = () -> {
                if (Phantom.miss()) return;
                Hits.attempt(hit);
                mc.gameMode.attack(mc.player, hit);
                if (swing.on()) mc.player.swing(InteractionHand.MAIN_HAND);
            };
            if (shield.on() && hit instanceof LivingEntity le && le.isBlocking()) {
                int axe = Inv.hotbar(s -> Names.item(s).endsWith("_axe"));
                if (axe >= 0 && axe != Inv.selected()) {
                    Inv.with(axe, strike);
                    return;
                }
            }
            strike.run();
        }
    }

    public static final class AutoClicker extends Module {
        private final Setting.Choice mode = add(new Setting.Choice("Button", "Which button to click.", 0, "Left (attack)", "Right (use / place)", "Both"));
        private final Setting.Num cps = add(new Setting.Num("Clicks per second", "How fast to click.", 10, 1, 20, 1, ""));
        private final Setting.Bool jitter = add(new Setting.Bool("Randomise timing", "Vary the time between clicks a little, like a hand.", true));
        private final Setting.Bool human = add(new Setting.Bool("Human pauses", "Take a short pause every few seconds, so the clicking is not perfectly continuous.", true));
        private final Setting.Bool hold = add(new Setting.Bool("Only while the button is held", "Off: it clicks all the time.", true));
        private final Random rng = new Random();
        private long nextLeft;
        private long nextRight;
        private long nextBurst;
        private BlockPos breakingBlock;

        public AutoClicker() {
            super("autoclicker", "Auto Clicker", "Left and right clicks, as fast as you like.", Category.COMBAT);
        }

        private long delay() {
            double d = 1000.0 / cps.get();
            if (jitter.on()) d *= 1.0 + Math.max(-0.18, Math.min(0.18, rng.nextGaussian() * 0.07));
            if (human.on()) {
                long now = System.currentTimeMillis();
                if (nextBurst == 0) nextBurst = now + 1800 + rng.nextInt(2600);
                if (now >= nextBurst) {
                    nextBurst = now + 2200 + rng.nextInt(4200);
                    d += 60 + rng.nextInt(140);
                }
            }
            return (long) d;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.gameMode == null || mc.screen != null) { breakingBlock = null; return; }
            long now = System.currentTimeMillis();
            boolean leftHeld = !hold.on() || mc.options.keyAttack.isDown();
            if (!leftHeld) breakingBlock = null;
            if ((mode.index() == 0 || mode.index() == 2) && now >= nextLeft && leftHeld) {
                nextLeft = now + delay();
                HitResult hit = mc.hitResult;
                if (hit instanceof EntityHitResult ehr) {
                    Hits.attempt(ehr.getEntity());
                    mc.gameMode.attack(mc.player, ehr.getEntity());
                    mc.player.swing(InteractionHand.MAIN_HAND);
                } else if (hit instanceof BlockHitResult bhr && bhr.getType() != HitResult.Type.MISS) {
                    BlockPos pos = bhr.getBlockPos();
                    if (pos.equals(breakingBlock)) mc.gameMode.continueDestroyBlock(pos, bhr.getDirection());
                    else { mc.gameMode.startDestroyBlock(pos, bhr.getDirection()); breakingBlock = pos; }
                    mc.player.swing(InteractionHand.MAIN_HAND);
                } else {
                    breakingBlock = null;
                }
            }
            if ((mode.index() == 1 || mode.index() == 2) && now >= nextRight && (!hold.on() || mc.options.keyUse.isDown())) {
                nextRight = now + delay();
                HitResult hit = mc.hitResult;
                if (hit instanceof BlockHitResult bhr && bhr.getType() != HitResult.Type.MISS) {
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
                } else {
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                }
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
        }

        @Override
        public String info() {
            return Math.round(cps.get()) + " cps";
        }
    }

    public static final class Reach extends Module {
        private final Setting.Num block = add(new Setting.Num("Block reach", "How far you can break and place blocks. Normal is 4.5. Servers usually refuse anything past about 5.5.", 5.5, 4.5, 14, 0.5, " blocks"));
        private final Setting.Num entity = add(new Setting.Num("Attack reach", "How far you can hit mobs. Normal is 3. Servers usually refuse anything past about 4.", 4, 3, 12, 0.5, " blocks"));
        private final Setting.Bool safe = add(new Setting.Bool("Server-safe", "Cap the bonus at what ordinary server reach checks accept, so the module does not stand out. Off: use the full sliders, which most servers refuse.", true));

        public Reach() {
            super("reach", "Reach", "Break, place and hit from further away.", Category.COMBAT);
            block.onChange(this::apply);
            entity.onChange(this::apply);
            safe.onChange(this::apply);
        }

        private double blockValue() {
            return safe.on() ? Math.min(block.get(), 5.25) : block.get();
        }

        private double entityValue() {
            return safe.on() ? Math.min(entity.get(), 3.35) : entity.get();
        }

        private void apply() {
            if (!enabled()) return;
            Env.attribute("block_interaction_range", "reach_block", blockValue() - 4.5, "add_value");
            Env.attribute("entity_interaction_range", "reach_entity", entityValue() - 3.0, "add_value");
        }

        @Override
        protected void onEnable() {
            apply();
        }

        @Override
        protected void onDisable() {
            Env.attributeOff("block_interaction_range", "reach_block");
            Env.attributeOff("entity_interaction_range", "reach_entity");
        }

        @Override
        protected void cleanSlate() {
            onDisable();
        }

        @Override
        public String info() {
            return block.text();
        }
    }

    public static final class HitSelect extends Module {
        private final Setting.Choice mode = add(new Setting.Choice("Only hit when", "Always: no filtering. Kill: a hit could finish the target. Low health: the target is under the health below. Crit: only while falling, so the hit is a critical.", 0, "Always", "Kill", "Low health", "Crit"));
        private final Setting.Num low = add(new Setting.Num("Low health under", "The health a target has to be under for Low health mode.", 8, 1, 20, 1, " hp")).showIf(() -> mode.index() == 2);
        private final Setting.Num chance = add(new Setting.Num("Chance", "How often a qualifying hit is let through.", 100, 10, 100, 5, "%"));
        private final Setting.Num gap = add(new Setting.Num("Minimum gap", "Never hit more often than this.", 0, 0, 400, 10, " ms"));
        private long lastHit;

        public HitSelect() {
            super("hitselect", "Hit Select", "Picks the moment a hit is worth throwing: only for a kill, only on a low target, or only while falling for a critical. Kill Aura and Trigger Bot both obey it.", Category.COMBAT);
        }

        public static boolean allow(Entity target) {
            Module m = com.zlorbie788.zeus.core.Zeus.get("hitselect");
            if (!(m instanceof HitSelect h) || !h.enabled()) return true;
            return h.permits(target);
        }

        private boolean permits(Entity target) {
            long now = System.currentTimeMillis();
            if (now - lastHit < gap.get()) return false;
            if (ThreadLocalRandom.current().nextDouble(100.0) >= chance.get()) return false;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return true;
            boolean ok = switch (mode.index()) {
                case 1 -> target instanceof LivingEntity le && le.getHealth() + le.getAbsorptionAmount() <= swingDamage(mc);
                case 2 -> target instanceof LivingEntity le && le.getHealth() + le.getAbsorptionAmount() <= low.get();
                case 3 -> !mc.player.onGround() && mc.player.getDeltaMovement().y < 0;
                default -> true;
            };
            if (ok) lastHit = now;
            return ok;
        }

        private static double swingDamage(Minecraft mc) {
            String item = Names.item(mc.player.getMainHandItem());
            if (item.endsWith("_sword")) return 7.0;
            if (item.endsWith("_axe")) return 8.0;
            if (item.equals("mace")) return 7.0;
            return 6.0;
        }
    }

    public static final class TriggerBot extends Module {
        private final Setting.Num min = add(new Setting.Num("Shortest delay", "Least time between attacks.", 40, 0, 400, 5, " ms"));
        private final Setting.Num max = add(new Setting.Num("Longest delay", "Most time between attacks.", 110, 0, 500, 5, " ms"));
        private final Setting.Bool players = add(new Setting.Bool("Players", "Attack players.", true));
        private final Setting.Bool mobs = add(new Setting.Bool("Monsters", "Attack hostile mobs.", true));
        private final Setting.Bool hold = add(new Setting.Bool("Only while holding attack", "Off: it swings whenever something is under the crosshair.", false));
        private long next;

        public TriggerBot() {
            super("triggerbot", "Trigger Bot", "Attacks whatever your crosshair is on, the moment you look at it.", Category.COMBAT);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
            if (hold.on() && !mc.options.keyAttack.isDown()) return;
            long now = System.currentTimeMillis();
            if (now < next) return;
            if (!(mc.hitResult instanceof EntityHitResult ehr)) return;
            Entity e = ehr.getEntity();
            if (!(e instanceof LivingEntity le) || !le.isAlive() || e == mc.player) return;
            if (e instanceof Player) {
                if (!players.on() || Friends.protects(e) || e.isSpectator()) return;
            } else if (!(e instanceof Enemy) || !mobs.on()) return;
            if (mc.player.getAttackStrengthScale(0f) < 0.95f) return;
            if (!HitSelect.allow(e)) return;
            long a = (long) min.get(), b = (long) Math.max(min.get(), max.get());
            next = now + a + (b > a ? ThreadLocalRandom.current().nextLong(b - a) : 0);
            if (Phantom.miss()) return;
            Hits.attempt(e);
            mc.gameMode.attack(mc.player, e);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    public static final class AntiKnockback extends Module {
        private static volatile boolean active;

        private final Setting.Num horizontal = add(new Setting.Num("Horizontal",
                "How much of the sideways push is removed. 100 is none of it, 70 keeps a little so it never looks frozen.", 100, 0, 100, 5, "%"));
        private final Setting.Num vertical = add(new Setting.Num("Vertical",
                "How much of the upward launch is removed.", 100, 0, 100, 5, "%"));
        private final Setting.Num nudge = add(new Setting.Num("Leave tiny nudges",
                "Motions slower than this (blocks per tick) are passed through untouched.", 0.04, 0, 0.3, 0.01, ""));
        private final Setting.Num chance = add(new Setting.Num("Chance",
                "How often the filter applies at all. Below 100 it only wins some of the pushes.", 100, 10, 100, 5, "%"));
        private final Setting.Bool ground = add(new Setting.Bool("Only on the ground",
                "Leaves knockback that hits you mid-air alone, which is the easiest kind to notice.", false));

        public AntiKnockback() {
            super("antiknockback", "Anti Knockback",
                    "Scales the push the server sends you instead of deleting it, so you keep position without looking like you ignored the hit.",
                    Category.COMBAT);
        }

        public static double[] factor(net.minecraft.world.phys.Vec3 motion) {
            AntiKnockback self = com.zlorbie788.zeus.core.Zeus.get(AntiKnockback.class);
            if (self == null || !self.enabled()) return null;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return null;
            if (self.ground.on() && !mc.player.onGround()) return null;
            double len = Math.sqrt(motion.x * motion.x + motion.y * motion.y + motion.z * motion.z);
            if (len <= 0.0001 || len < self.nudge.get()) return null;
            if (self.chance.get() < 100 && java.util.concurrent.ThreadLocalRandom.current().nextDouble(100.0) >= self.chance.get()) return null;
            double h = 1.0 - self.horizontal.get() / 100.0;
            double v = 1.0 - self.vertical.get() / 100.0;
            return new double[] {h, v};
        }

        @Override
        protected void onEnable() {
            active = true;
        }

        @Override
        protected void onDisable() {
            active = false;
        }
    }

    public static final class AntiMiss extends Module {
        private final Setting.Bool players = add(new Setting.Bool("Players", "Also cancel swings aimed at players.", true));
        private final Setting.Bool mobs = add(new Setting.Bool("Mobs", "Also cancel swings aimed at mobs.", true));

        public AntiMiss() {
            super("antimiss", "Anti Miss", "Cancels a swing that would hit nothing, so a wasted click never eats your hit.", Category.COMBAT);
        }

        public static boolean blocks(Entity target) {
            AntiMiss self = com.zlorbie788.zeus.core.Zeus.get(AntiMiss.class);
            if (self == null || !self.enabled()) return false;
            if (target == null) return true;
            if (!(target instanceof LivingEntity)) return true;
            if (target instanceof Player) return !self.players.on();
            return !self.mobs.on();
        }
    }

    public static final class BreachSwap extends Module {
        private final Setting.Num back = add(new Setting.Num("Swap back after", "Ticks before the slot you came from returns. 0 keeps the mace in hand.", 4, 0, 20, 1, " ticks"));
        private int previous = -1;
        private int wait;

        public BreachSwap() {
            super("breachswap", "Breach Swap", "Swaps to a Breach mace for the swing itself, so your shield stays up until the hit actually matters.", Category.COMBAT);
        }

        public static void beforeAttack() {
            BreachSwap self = com.zlorbie788.zeus.core.Zeus.get(BreachSwap.class);
            if (self != null) self.arm();
        }

        private void arm() {
            Minecraft mc = Minecraft.getInstance();
            if (!enabled() || mc.player == null || mc.level == null) return;
            var inventory = mc.player.getInventory();
            int slot = find(inventory);
            if (slot < 0 || slot == inventory.getSelectedSlot()) return;
            if (wait <= 0) previous = inventory.getSelectedSlot();
            inventory.setSelectedSlot(slot);
            wait = back.i();
        }

        private int find(net.minecraft.world.entity.player.Inventory inventory) {
            for (int i = 0; i < 9; i++) {
                net.minecraft.world.item.ItemStack stack = inventory.getItem(i);
                if (!stack.is(net.minecraft.world.item.Items.MACE)) continue;
                for (net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> held : stack.getEnchantments().keySet()) {
                    if (held.is(net.minecraft.world.item.enchantment.Enchantments.BREACH)) return i;
                }
            }
            return -1;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null) {
                previous = -1;
                wait = 0;
                return;
            }
            if (wait <= 0) return;
            wait--;
            if (wait > 0) return;
            if (previous >= 0 && previous < 9) mc.player.getInventory().setSelectedSlot(previous);
            previous = -1;
        }
    }

    public static final class TotemHit extends Module {
        private final Setting.Bool axe = add(new Setting.Bool("Axes too", "Also accept an axe when no sword is in the hotbar.", false));

        public TotemHit() {
            super("totemhit", "Totem Hit", "Holding a totem no longer costs you the swing: the hit is made with a sword from your hotbar instead.", Category.COMBAT);
        }

        public static void beforeAttack() {
            TotemHit self = com.zlorbie788.zeus.core.Zeus.get(TotemHit.class);
            if (self == null || !self.enabled()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            var inventory = mc.player.getInventory();
            if (!inventory.getItem(inventory.getSelectedSlot()).is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) return;
            int slot = self.find(inventory);
            if (slot >= 0) inventory.setSelectedSlot(slot);
        }

        private int find(net.minecraft.world.entity.player.Inventory inventory) {
            for (int i = 0; i < 9; i++) {
                net.minecraft.world.item.ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty()) continue;
                String name = com.zlorbie788.zeus.core.Names.item(stack);
                if (name.endsWith("_sword")) return i;
                if (axe.on() && name.endsWith("_axe")) return i;
            }
            return -1;
        }
    }

    public static final class HitFx extends Module {
        private final Setting.Num sparks = add(new Setting.Num("Sparks", "How many particles each hit throws off.", 10, 1, 40, 1, ""));
        private final Setting.Num spread = add(new Setting.Num("Spread", "How wide the burst is around the target.", 0.5, 0.1, 2.0, 0.1, " blocks"));
        private final Setting.Bool crit = add(new Setting.Bool("Critical sparks", "Use the brighter critical-hit spark instead of the plain one.", true));

        public HitFx() {
            super("hitfx", "Hit FX", "Throws a burst of sparks off whatever you hit, so a landed swing reads instantly.", Category.RENDER);
        }

        public static void burst(Entity target) {
            HitFx self = com.zlorbie788.zeus.core.Zeus.get(HitFx.class);
            if (self == null || !self.enabled() || target == null) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            java.util.Random random = new java.util.Random();
            var particle = self.crit.on() ? net.minecraft.core.particles.ParticleTypes.CRIT : net.minecraft.core.particles.ParticleTypes.SMOKE;
            double spread = self.spread.get();
            int count = self.sparks.i();
            double cx = target.getX();
            double cy = target.getY() + target.getBbHeight() * 0.5;
            double cz = target.getZ();
            for (int i = 0; i < count; i++) {
                double x = cx + (random.nextDouble() - 0.5) * spread;
                double y = cy + (random.nextDouble() - 0.5) * spread;
                double z = cz + (random.nextDouble() - 0.5) * spread;
                mc.level.addParticle(particle, x, y, z, (random.nextDouble() - 0.5) * 0.4, random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.4);
            }
        }
    }
}
