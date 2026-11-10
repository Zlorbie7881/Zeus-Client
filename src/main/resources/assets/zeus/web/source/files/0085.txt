package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Names;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/**
 * The Sword PvP Bot and the Crystal PvP Bot. A bot walks, turns and fights for you against the nearest player: it closes in,
 * strafes, times its hits (critical hits for the sword), keeps out of reach while its swing recharges, eats golden apples,
 * mends its armour with experience bottles when nobody is close, refills its hotbar from the inventory and throws an ender
 * pearl to close a gap. The Crystal Bot hands the explosions to Crystal Aura and Anchor Aura and keeps the fight at the right
 * distance for them.
 *
 * <p>Both bots lean on the modules that already exist in Zeus (Auto Totem, Auto Armor, Auto Eat, Exp Thrower, Auto Replenish,
 * Crystal Aura, Anchor Aura): they switch the ones they need on while they run and back off again when they stop. They never
 * target anyone on your friends list. Using a fighting bot on a server that does not allow it is your decision and your risk.</p>
 */
public final class Bots {
    private Bots() {}

    private static final Random RNG = new Random();

    /** Everything the two bots share. */
    abstract static class Bot extends Module {
        protected final Setting.Num range = add(new Setting.Num("Target range", "How far away a player is picked as the enemy.", 28, 8, 64, 2, " blocks"));
        protected final Setting.Bool strafe = add(new Setting.Bool("Strafe", "Moves sideways while fighting, switching direction at random.", true));
        protected final Setting.Bool helpers = add(new Setting.Bool("Run the helper modules", "Switches Auto Totem, Auto Armor and Auto Eat on while the bot runs (and off again after, if it was the one that switched them on).", true));
        protected final Setting.Bool pearls = add(new Setting.Bool("Throw pearls to close a gap", "Throws an ender pearl at the enemy when they are far away and you are not close to catching up.", true));
        protected final Setting.Num pearlGap = add(new Setting.Num("Pearl when further than", "The distance at which a pearl is thrown.", 18, 10, 40, 1, " blocks")).showIf(() -> pearls.on());
        protected final Setting.Bool mend = add(new Setting.Bool("Mend armour", "Throws experience bottles at your feet when no enemy is near and your armour is worn down.", true));
        protected final Setting.Num mendBelow = add(new Setting.Num("Mend below", "Armour durability, in percent, that makes it mend.", 60, 10, 95, 5, " %")).showIf(() -> mend.on());
        protected final Setting.Bool refill = add(new Setting.Bool("Refill the hotbar", "Moves golden apples, pearls, experience bottles and (for the Crystal Bot) crystals and obsidian from your inventory into free hotbar slots when they run out.", true));
        protected final Setting.Bool stopOnScreen = add(new Setting.Bool("Stop when I open a menu", "Lets go of every key while a menu or chat is open.", true));

        protected Player target;
        protected int strafeDir = 1;
        protected int strafeLeft;
        protected int pearlCd;
        protected int pearlAim;
        protected int refillCd;
        protected int tickNo;
        protected long warned;
        private final List<Module> started = new ArrayList<>();
        private boolean mending;

        Bot(String id, String name, String desc) {
            super(id, name, desc, Category.COMBAT);
        }

        /** The ids of the helper modules this bot wants. */
        protected abstract String[] helperIds();

        @Override
        protected void onEnable() {
            target = null;
            pearlCd = pearlAim = refillCd = tickNo = 0;
            mending = false;
            strafeLeft = 0;
            if (helpers.on()) {
                for (String id : helperIds()) {
                    Module m = Zeus.get(id);
                    if (m != null && !m.enabled()) {
                        m.setEnabled(true);
                        started.add(m);
                    }
                }
            }
        }

        @Override
        protected void onDisable() {
            releaseKeys();
            setMend(false);
            for (Module m : started) if (m.enabled()) m.setEnabled(false);
            started.clear();
            target = null;
        }

        @Override
        public String info() {
            return target == null ? null : target.getName().getString();
        }

        // ---------------------------------------------------------------- keys and view

        protected void releaseKeys() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options == null) return;
            mc.options.keyUp.setDown(false);
            mc.options.keyDown.setDown(false);
            mc.options.keyLeft.setDown(false);
            mc.options.keyRight.setDown(false);
            mc.options.keyJump.setDown(false);
            mc.options.keySprint.setDown(false);
        }

        protected void keys(Minecraft mc, boolean fwd, boolean back, boolean left, boolean right, boolean jump, boolean sprint) {
            mc.options.keyUp.setDown(fwd);
            mc.options.keyDown.setDown(back);
            mc.options.keyLeft.setDown(left);
            mc.options.keyRight.setDown(right);
            mc.options.keyJump.setDown(jump);
            mc.options.keySprint.setDown(sprint);
        }

        /** Turns your view toward a point by at most {@code step} degrees. Returns how many degrees are left to turn. */
        protected float face(Minecraft mc, Vec3 point, float step) {
            Vec3 eye = mc.player.getEyePosition();
            float wantYaw = com.zlorbie788.zeus.core.Aim.yawTo(eye, point);
            float wantPitch = Math.max(-89f, Math.min(89f, com.zlorbie788.zeus.core.Aim.pitchTo(eye, point)));
            float dy = wrap(wantYaw - mc.player.getYRot());
            float dp = wantPitch - mc.player.getXRot();
            float cy = Math.max(-step, Math.min(step, dy));
            float cp = Math.max(-step * 0.7f, Math.min(step * 0.7f, dp));
            mc.player.setYRot(mc.player.getYRot() + cy);
            mc.player.setXRot(mc.player.getXRot() + cp);
            return Math.abs(dy - cy) + Math.abs(dp - cp);
        }

        protected static float wrap(float deg) {
            deg %= 360f;
            if (deg >= 180f) deg -= 360f;
            if (deg < -180f) deg += 360f;
            return deg;
        }

        // ---------------------------------------------------------------- the enemy

        protected Player pick(Minecraft mc) {
            if (target != null && target.isAlive() && !target.isSpectator() && mc.player.distanceTo(target) <= range.get() * 1.25 && !Friends.protects(target)) return target;
            Player best = null;
            double bestD = range.get() * range.get();
            for (Entity e : Env.entities(mc, range.get())) {
                if (!(e instanceof Player p) || p == mc.player || !p.isAlive() || p.isSpectator() || Friends.protects(p)) continue;
                double d = mc.player.distanceToSqr(p);
                if (d < bestD) {
                    best = p;
                    bestD = d;
                }
            }
            return best;
        }

        /** The distance from your eyes to the nearest point of the enemy's body. */
        protected double reachTo(Minecraft mc, Entity e) {
            Vec3 eye = mc.player.getEyePosition();
            AABB b = e.getBoundingBox();
            double x = Math.max(b.minX, Math.min(eye.x, b.maxX));
            double y = Math.max(b.minY, Math.min(eye.y, b.maxY));
            double z = Math.max(b.minZ, Math.min(eye.z, b.maxZ));
            return eye.distanceTo(new Vec3(x, y, z));
        }

        protected void strafeTick() {
            if (--strafeLeft <= 0) {
                strafeDir = RNG.nextBoolean() ? 1 : -1;
                strafeLeft = 10 + RNG.nextInt(24);
            }
        }

        // ---------------------------------------------------------------- upkeep

        protected void setMend(boolean on) {
            Module m = Zeus.get("expthrower");
            if (m == null || on == mending) return;
            mending = on;
            if (on) m.setEnabled(true);
            else if (m.enabled()) m.setEnabled(false);
        }

        private double armourPercent(Minecraft mc) {
            double worst = 100;
            for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack st = mc.player.getItemBySlot(s);
                if (st.isEmpty() || !st.isDamageableItem() || st.getMaxDamage() <= 0) continue;
                worst = Math.min(worst, 100.0 * (st.getMaxDamage() - st.getDamageValue()) / st.getMaxDamage());
            }
            return worst;
        }

        /** Mends while nobody is close. Returns true when it is busy with that and the fight should wait. */
        protected boolean mending(Minecraft mc, double dist) {
            boolean want = mend.on() && dist > 16 && armourPercent(mc) < mendBelow.get()
                    && (Inv.hotbar(Inv.is(Items.EXPERIENCE_BOTTLE)) >= 0 || Inv.offhand(Inv.is(Items.EXPERIENCE_BOTTLE)));
            setMend(want);
            if (want) releaseKeys();
            return want;
        }

        /** Puts missing supplies into free hotbar slots from the main inventory. */
        protected void refillHotbar(Minecraft mc, List<Predicate<ItemStack>> wanted) {
            if (!refill.on() || (tickNo % 20) != 0 || mc.screen != null) return;
            for (Predicate<ItemStack> want : wanted) {
                if (Inv.hotbar(want) >= 0 || Inv.offhand(want)) continue;
                int from = Inv.main(want);
                if (from < 0) continue;
                int free = -1;
                for (int i = 8; i >= 0; i--) {
                    if (Inv.get(i).isEmpty()) {
                        free = i;
                        break;
                    }
                }
                if (free < 0) {
                    if (System.currentTimeMillis() - warned > 15000) {
                        warned = System.currentTimeMillis();
                        Zeus.toast(name + ": no free hotbar slot to refill into", false);
                    }
                    return;
                }
                Inv.click(Inv.menuSlot(from), free, ClickType.SWAP);
                return; // one swap a second is plenty
            }
        }

        /**
         * Throws an ender pearl at a far enemy. The view is turned to the throwing angle first and held a few ticks so the
         * server knows where you look. Returns true while it is busy with that.
         */
        protected boolean pearl(Minecraft mc, double dist) {
            if (pearlCd > 0) pearlCd--;
            if (!pearls.on() || mc.player.isUsingItem()) {
                pearlAim = 0;
                return false;
            }
            boolean can = pearlCd == 0 && dist > pearlGap.get() && dist < 60;
            int slot = Inv.hotbar(Inv.is(Items.ENDER_PEARL));
            boolean off = Inv.offhand(Inv.is(Items.ENDER_PEARL));
            if (!can || (slot < 0 && !off) || !mc.player.onGround()) {
                pearlAim = 0;
                return false;
            }
            Vec3 aim = pearlAim(mc, target);
            face(mc, aim, 60f);
            releaseKeys();
            if (++pearlAim < 4) return true;
            if (slot >= 0) Inv.with(slot, () -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
            else mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
            pearlAim = 0;
            pearlCd = 80;
            return true;
        }

        /** A point in the air to look at so that a pearl thrown there comes down on the enemy. */
        static Vec3 pearlAim(Minecraft mc, Entity e) {
            Vec3 eye = mc.player.getEyePosition();
            Vec3 to = e.position();
            double dx = to.x - eye.x;
            double dz = to.z - eye.z;
            double flat = Math.sqrt(dx * dx + dz * dz);
            double bestPitch = 20;
            double best = 1e9;
            for (double deg = -5; deg <= 55; deg += 1) {
                double rad = Math.toRadians(deg);
                double vx = Math.cos(rad) * 1.5;
                double vy = Math.sin(rad) * 1.5;
                double px = 0;
                double py = eye.y;
                for (int t = 0; t < 140; t++) {
                    px += vx * Math.pow(0.99, t);
                    py += vy;
                    vy = (vy - 0.03) * 0.99;
                    // vy above is the velocity after drag; the x speed decays by the same drag each tick
                    if (py <= to.y && vy < 0) break;
                }
                double miss = Math.abs(px - flat);
                if (miss < best) {
                    best = miss;
                    bestPitch = deg;
                }
            }
            double yaw = Math.atan2(dz, dx);
            double pr = Math.toRadians(bestPitch);
            double len = Math.max(1.0, flat);
            return new Vec3(eye.x + Math.cos(yaw) * Math.cos(pr) * len, eye.y + Math.sin(pr) * len, eye.z + Math.sin(yaw) * Math.cos(pr) * len);
        }

        protected static boolean weapon(ItemStack s) {
            String n = Names.item(s);
            return n.endsWith("_sword") || n.endsWith("_axe") || n.equals("mace");
        }

        protected static boolean sword(ItemStack s) {
            return Names.item(s).endsWith("_sword");
        }

        protected static boolean gapple(ItemStack s) {
            return s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE);
        }
    }

    // ================================================================== Sword PvP Bot

    /** Fights the nearest player with a sword: crits, strafing, keeping out of reach while the swing recharges, gapples, pearls. */
    public static final class SwordBot extends Bot {
        private final Setting.Bool crits = add(new Setting.Bool("Critical hits", "Jumps and hits on the way down, so every hit is a critical hit.", true));
        private final Setting.Bool wtap = add(new Setting.Bool("W-tap", "Lets go of forward for a moment after each hit so the next hit knocks back again.", true));
        private final Setting.Num reach = add(new Setting.Num("Hit reach", "How close the enemy must be before it swings.", 2.9, 2.0, 3.0, 0.1, " blocks"));
        private final Setting.Num charge = add(new Setting.Num("Swing charge", "How charged the swing must be before it hits (1 is full).", 0.92, 0.5, 1.0, 0.02, ""));
        private final Setting.Num turn = add(new Setting.Num("Turn speed", "The most degrees your view turns in a tick. Lower looks more human.", 28, 6, 90, 2, " deg"));
        private int tapTicks;
        private int hitCd;

        public SwordBot() {
            super("swordbot", "Sword PvP Bot", "Fights the nearest player with a sword: closes in, strafes, times critical hits, keeps out of reach while the swing charges, eats golden apples, mends, refills and pearls.");
            alias("sword bot", "pvp bot", "duel bot");
        }

        @Override
        protected String[] helperIds() {
            return new String[] {"autototem", "autoarmor", "autoeat"};
        }

        @Override
        protected void onTick(Minecraft mc) {
            tickNo++;
            if (mc.player == null || mc.level == null || mc.gameMode == null) return;
            if (mc.screen != null) {
                if (stopOnScreen.on()) releaseKeys();
                return;
            }
            if (hitCd > 0) hitCd--;
            target = pick(mc);
            refillHotbar(mc, List.of(Bot::gapple, Inv.is(Items.ENDER_PEARL), Inv.is(Items.EXPERIENCE_BOTTLE)));
            if (target == null) {
                releaseKeys();
                mending(mc, 99);
                return;
            }
            double dist = reachTo(mc, target);
            if (mending(mc, mc.player.distanceTo(target))) return;
            if (pearl(mc, mc.player.distanceTo(target))) return;

            // eating: stay out of the way and do not swing
            if (mc.player.isUsingItem()) {
                face(mc, target.getBoundingBox().getCenter(), turn.f());
                strafeTick();
                keys(mc, false, dist < 6, strafe.on() && strafeDir < 0, strafe.on() && strafeDir > 0, false, false);
                return;
            }

            // the weapon
            if (!weapon(mc.player.getMainHandItem())) {
                int slot = Inv.hotbar(Bot::sword);
                if (slot < 0) slot = Inv.hotbar(Bot::weapon);
                if (slot >= 0) Inv.select(slot);
            }

            Vec3 chest = target.position().add(0, target.getBbHeight() * 0.62, 0);
            float left = face(mc, chest, turn.f());
            Vec3 look = mc.player.getLookAngle();
            Vec3 dir = chest.subtract(mc.player.getEyePosition()).normalize();
            boolean facing = left < 14f || look.dot(dir) > 0.97;
            float strength = mc.player.getAttackStrengthScale(0f);
            boolean ready = strength >= charge.get();
            boolean inReach = dist <= reach.get();
            strafeTick();
            boolean sl = strafe.on() && strafeDir < 0;
            boolean sr = strafe.on() && strafeDir > 0;

            if (tapTicks > 0) {
                tapTicks--;
                keys(mc, false, false, sl, sr, false, false);
                return;
            }

            boolean airborne = !mc.player.onGround();
            if (!ready) {
                // recharging: stay just out of reach, circle
                if (dist < reach.get() + 0.7) keys(mc, false, true, sl, sr, false, false);
                else if (dist > reach.get() + 2.2) keys(mc, true, false, sl, sr, false, true);
                else keys(mc, false, false, sl, sr, false, false);
                return;
            }
            if (!inReach) {
                // charged and not there yet: run in
                keys(mc, true, false, sl, sr, mc.player.onGround() && mc.player.horizontalCollision, dist > 4.5);
                return;
            }
            if (!facing) {
                keys(mc, false, false, sl, sr, false, false);
                return;
            }
            if (crits.on()) {
                if (!airborne) {
                    // jump now; the hit comes on the way down
                    keys(mc, true, false, sl, sr, true, false);
                    return;
                }
                if (mc.player.getDeltaMovement().y < 0 && mc.player.fallDistance > 0.05f) {
                    if (mc.player.isSprinting()) {
                        // a sprinting hit cannot be a critical one: let the sprint end first
                        mc.player.setSprinting(false);
                        keys(mc, false, false, sl, sr, false, false);
                        return;
                    }
                    swing(mc);
                    return;
                }
                keys(mc, true, false, sl, sr, false, false);
                return;
            }
            swing(mc);
        }

        private void swing(Minecraft mc) {
            if (hitCd > 0 || target == null) return;
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
            hitCd = 4;
            if (wtap.on()) tapTicks = 2;
        }
    }

    // ================================================================== Crystal PvP Bot

    /** Fights with crystals and anchors: it steers Crystal Aura and Anchor Aura and keeps the fight at the right distance. */
    public static final class CrystalBot extends Bot {
        private final Setting.Choice blast = add(new Setting.Choice("Explosives", "Which explosive module the bot switches on. Anchors only work in the Overworld and the End.", 0, "Crystals", "Anchors", "Both"));
        private final Setting.Bool knockUp = add(new Setting.Bool("Knock them up", "Hits the enemy with your sword when they stand on the ground next to you, to lift them into the air where a crystal does more.", true));
        private final Setting.Bool guard = add(new Setting.Bool("Guard myself", "Switches Surround on while the bot runs (needs obsidian).", true));
        private final Setting.Num near = add(new Setting.Num("Closest distance", "The bot backs off when the enemy is closer than this.", 3.5, 2.0, 6.0, 0.5, " blocks"));
        private final Setting.Num far = add(new Setting.Num("Furthest distance", "The bot walks up to the enemy when they are further than this.", 6.5, 4.0, 10.0, 0.5, " blocks"));
        private final Setting.Num turn = add(new Setting.Num("Turn speed", "The most degrees your view turns in a tick.", 22, 6, 90, 2, " deg"));
        private int hitCd;

        public CrystalBot() {
            super("crystalbot", "Crystal PvP Bot", "Crystal-fights the nearest player: steers Crystal Aura and Anchor Aura, keeps the right distance, knocks them up, guards, eats, mends, refills and pearls.");
            alias("crystal bot", "cpvp bot", "crystal pvp");
        }

        @Override
        protected String[] helperIds() {
            List<String> ids = new ArrayList<>(List.of("autototem", "autoarmor", "autoeat", "autoreplenish"));
            if (blast.index() != 1) ids.add("crystalaura");
            if (blast.index() != 0) ids.add("anchoraura");
            if (guard.on()) ids.add("surround");
            return ids.toArray(new String[0]);
        }

        @Override
        protected void onTick(Minecraft mc) {
            tickNo++;
            if (mc.player == null || mc.level == null || mc.gameMode == null) return;
            if (mc.screen != null) {
                if (stopOnScreen.on()) releaseKeys();
                return;
            }
            if (hitCd > 0) hitCd--;
            target = pick(mc);
            refillHotbar(mc, List.of(Bot::gapple, Inv.is(Items.ENDER_PEARL), Inv.is(Items.EXPERIENCE_BOTTLE), Inv.is(Items.END_CRYSTAL), Inv.is(Items.OBSIDIAN)));
            if (target == null) {
                releaseKeys();
                mending(mc, 99);
                return;
            }
            double dist = reachTo(mc, target);
            if (mending(mc, mc.player.distanceTo(target))) return;
            if (pearl(mc, mc.player.distanceTo(target))) return;
            if (mc.player.isUsingItem()) {
                releaseKeys();
                return;
            }
            Vec3 chest = target.position().add(0, target.getBbHeight() * 0.5, 0);
            face(mc, chest, turn.f());
            strafeTick();
            boolean sl = strafe.on() && strafeDir < 0;
            boolean sr = strafe.on() && strafeDir > 0;

            if (knockUp.on() && hitCd == 0 && dist <= 3.0 && target.onGround() && mc.player.getAttackStrengthScale(0f) >= 0.95f) {
                int sword = Inv.hotbar(Bot::sword);
                if (sword >= 0) {
                    Inv.with(sword, () -> mc.gameMode.attack(mc.player, target));
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    hitCd = 12;
                }
            }
            if (dist > far.get()) keys(mc, true, false, sl, sr, mc.player.onGround() && mc.player.horizontalCollision, dist > far.get() + 3);
            else if (dist < near.get()) keys(mc, false, true, sl, sr, false, false);
            else keys(mc, false, false, sl, sr, false, false);
        }
    }
}
