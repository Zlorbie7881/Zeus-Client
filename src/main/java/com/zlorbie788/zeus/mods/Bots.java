package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Names;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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

public final class Bots {
    private Bots() {}

    private static final Random RNG = new Random();

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
        protected final Setting.Num reaction = add(new Setting.Num("Reaction time", "How long a fresh target is left alone before the bot swings, so it never answers instantly.", 0.14, 0, 0.7, 0.02, " s"));
        protected final Setting.Num aimDrift = add(new Setting.Num("Aim drift", "Hand tremor added to the aim, in degrees. 0 is a machine and looks like one.", 0.9, 0, 3, 0.1, " deg"));
        protected final Setting.Num hesitation = add(new Setting.Num("Swing hesitation", "Chance that a swing waits one more tick, the way a hand sometimes does.", 7, 0, 40, 1, " %"));
        protected final Setting.Bool pathing = add(new Setting.Bool("Route planning", "Reads the ground a step ahead: steps up one-block ledges, hops small gaps and avoids lava instead of walking into them.", true));
        protected final Setting.Num retreat = add(new Setting.Num("Retreat below health", "Backs away from the fight when your health drops under this, so it can eat and come back. 0 never retreats.", 8, 0, 20, 1, " hp"));
        protected final Setting.Num rejoin = add(new Setting.Num("Rejoin at health", "Health that has to come back before it pushes in again.", 14, 2, 20, 1, " hp"));

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
        protected long reactAt;
        protected int reactTarget = -1;
        private boolean backingOff;
        private int jumpCd;
        private int wallTicks;
        protected float driftYaw;
        protected float driftPitch;
        protected float aimYawSpeed;
        protected float aimPitchSpeed;
        private int driftTicks;

        Bot(String id, String name, String desc) {
            super(id, name, desc, Category.COMBAT);
        }

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

        protected void humanize(Minecraft mc) {
            if (target == null) {
                reactTarget = -1;
                return;
            }
            if (target.getId() != reactTarget) {
                reactTarget = target.getId();
                reactAt = System.currentTimeMillis() + Math.round(reaction.get() * 1000.0);
            }
            if (jumpCd > 0) jumpCd--;
            if (--driftTicks > 0) return;
            driftTicks = 5 + RNG.nextInt(12);
            driftYaw = (RNG.nextFloat() - 0.5f) * 2f * aimDrift.f();
            driftPitch = (RNG.nextFloat() - 0.5f) * 2f * aimDrift.f();
        }

        protected boolean canSwing() {
            if (target == null || System.currentTimeMillis() < reactAt) return false;
            return hesitation.f() <= 0 || RNG.nextFloat() * 100f >= hesitation.f();
        }

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

        protected void steer(Minecraft mc, Vec3 point, boolean sprint) {
            float want = com.zlorbie788.zeus.core.Aim.yawTo(mc.player.getEyePosition(), point);
            float diff = wrap(want - mc.player.getYRot());
            float abs = Math.abs(diff);
            boolean fwd = abs < 62f;
            boolean back = abs > 118f;
            boolean left = diff > 18f && diff < 162f;
            boolean right = diff < -18f && diff > -162f;
            keys(mc, fwd, back, left, right, jumpWanted(mc, point), sprint);
        }

        protected boolean jumpWanted(Minecraft mc, Vec3 toward) {
            if (!pathing.on() || jumpCd > 0 || !mc.player.onGround() || mc.level == null) return false;
            Vec3 flat = toward.subtract(mc.player.position());
            flat = new Vec3(flat.x, 0, flat.z);
            if (flat.lengthSqr() < 0.04) return false;
            flat = flat.normalize().scale(0.85);
            double px = mc.player.getX() + flat.x;
            double pz = mc.player.getZ() + flat.z;
            BlockPos legs = BlockPos.containing(px, mc.player.getY(), pz);
            BlockPos head = BlockPos.containing(px, mc.player.getY() + 1, pz);
            BlockPos floor = BlockPos.containing(px, mc.player.getY() - 1, pz);
            boolean legsBlocked = blocked(mc, legs);
            boolean headBlocked = blocked(mc, head);
            boolean floorClear = !blocked(mc, floor);
            if (lava(mc, floor) || lava(mc, legs)) return false;
            if (legsBlocked && !headBlocked && (floorClear || mc.player.horizontalCollision)) {
                jumpCd = 5;
                return true;
            }
            if (floorClear && headBlocked && !legsBlocked) {
                jumpCd = 4;
                return true;
            }
            return false;
        }

        protected static boolean blocked(Minecraft mc, BlockPos pos) {
            if (mc.level == null) return false;
            return !mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty();
        }

        private static boolean lava(Minecraft mc, BlockPos pos) {
            if (mc.level == null) return false;
            return mc.level.getBlockState(pos).getFluidState().getType() == net.minecraft.world.level.material.Fluids.LAVA;
        }

        protected boolean backingOff(Minecraft mc) {
            if (retreat.f() <= 0) {
                backingOff = false;
                return false;
            }
            float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            if (!backingOff && health <= retreat.f()) backingOff = true;
            else if (backingOff && health >= Math.max(retreat.f() + 1, rejoin.f())) backingOff = false;
            return backingOff;
        }

        protected void unstuck(Minecraft mc) {
            if (!pathing.on()) return;
            if (mc.player.horizontalCollision) {
                if (++wallTicks > 6) {
                    wallTicks = 0;
                    strafeDir = -strafeDir;
                    strafeLeft = 8 + RNG.nextInt(10);
                }
            } else {
                wallTicks = 0;
            }
        }

        protected void disengage(Minecraft mc, Entity from, float turnSpeed) {
            Vec3 away = mc.player.position().subtract(from.position());
            if (away.lengthSqr() < 0.01) away = new Vec3(1, 0, 0);
            Vec3 point = mc.player.position().add(away.normalize().scale(7));
            face(mc, from.getBoundingBox().getCenter(), turnSpeed);
            steer(mc, point, true);
            if (mc.player.horizontalCollision) wallTicks++;
            if (wallTicks > 3) {
                wallTicks = 0;
                strafeDir = -strafeDir;
            }
        }

        protected void keys(Minecraft mc, boolean fwd, boolean back, boolean left, boolean right, boolean jump, boolean sprint) {
            mc.options.keyUp.setDown(fwd);
            mc.options.keyDown.setDown(back);
            mc.options.keyLeft.setDown(left);
            mc.options.keyRight.setDown(right);
            mc.options.keyJump.setDown(jump);
            mc.options.keySprint.setDown(sprint);
        }

        protected float face(Minecraft mc, Vec3 point, float step) {
            Vec3 eye = mc.player.getEyePosition();
            float wantYaw = com.zlorbie788.zeus.core.Aim.yawTo(eye, point) + driftYaw;
            float wantPitch = Math.max(-89f, Math.min(89f, com.zlorbie788.zeus.core.Aim.pitchTo(eye, point) + driftPitch));
            float dy = wrap(wantYaw - mc.player.getYRot());
            float dp = wantPitch - mc.player.getXRot();
            if (Math.abs(dy) < 0.35f) dy = 0f;
            if (Math.abs(dp) < 0.25f) dp = 0f;
            float pullY = Math.max(-1f, Math.min(1f, dy / 22f)) * step * 0.62f;
            float pullP = Math.max(-1f, Math.min(1f, dp / 18f)) * step * 0.45f;
            aimYawSpeed = aimYawSpeed * 0.58f + pullY;
            aimPitchSpeed = aimPitchSpeed * 0.50f + pullP;
            float cy = Math.max(-step, Math.min(step, aimYawSpeed));
            float cp = Math.max(-step * 0.7f, Math.min(step * 0.7f, aimPitchSpeed));
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

        protected Player pick(Minecraft mc) {
            if (target != null && target.isAlive() && !target.isSpectator() && mc.player.distanceTo(target) <= range.get() * 1.25 && !Friends.protects(target)) return target;
            Player best = null;
            double bestScore = range.get() * range.get();
            for (Entity e : Env.entities(mc, range.get())) {
                if (!(e instanceof Player p) || p == mc.player || !p.isAlive() || p.isSpectator() || Friends.protects(p)) continue;
                double score = mc.player.distanceToSqr(p);
                if (p.getHealth() < 8f) score *= 0.82;
                if (weapon(p.getMainHandItem())) score *= 0.88;
                if (p.isBlocking()) score *= 1.12;
                if (p.isInvisible()) score *= 1.25;
                if (score < bestScore) {
                    best = p;
                    bestScore = score;
                }
            }
            return best;
        }

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
                strafeLeft = 12 + RNG.nextInt(30);
                if (RNG.nextInt(100) < 14) strafeLeft = 4 + RNG.nextInt(6);
            }
        }

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

        protected boolean mending(Minecraft mc, double dist) {
            boolean want = mend.on() && dist > 16 && armourPercent(mc) < mendBelow.get()
                    && (Inv.hotbar(Inv.is(Items.EXPERIENCE_BOTTLE)) >= 0 || Inv.offhand(Inv.is(Items.EXPERIENCE_BOTTLE)));
            setMend(want);
            if (want) releaseKeys();
            return want;
        }

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
                return;
            }
        }

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
            humanize(mc);
            refillHotbar(mc, List.of(Bot::gapple, Inv.is(Items.ENDER_PEARL), Inv.is(Items.EXPERIENCE_BOTTLE)));
            if (target == null) {
                releaseKeys();
                mending(mc, 99);
                return;
            }
            double dist = reachTo(mc, target);
            if (mending(mc, mc.player.distanceTo(target))) return;
            if (pearl(mc, mc.player.distanceTo(target))) return;
            if (backingOff(mc)) {
                disengage(mc, target, turn.f());
                return;
            }

            if (mc.player.isUsingItem()) {
                face(mc, target.getBoundingBox().getCenter(), turn.f());
                strafeTick();
                keys(mc, false, dist < 6, strafe.on() && strafeDir < 0, strafe.on() && strafeDir > 0, false, false);
                return;
            }

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
                if (dist < reach.get() + 0.7) keys(mc, false, true, sl, sr, false, false);
                else if (dist > reach.get() + 2.2) {
                    unstuck(mc);
                    keys(mc, true, false, sl, sr, jumpWanted(mc, chest), true);
                }
                else keys(mc, false, false, sl, sr, false, false);
                return;
            }
            if (!inReach) {
                unstuck(mc);
                keys(mc, true, false, strafe.on() && strafeDir < 0, strafe.on() && strafeDir > 0, jumpWanted(mc, chest), dist > 4.5);
                return;
            }
            if (!facing) {
                keys(mc, false, false, sl, sr, false, false);
                return;
            }
            if (crits.on()) {
                if (!airborne) {
                    keys(mc, true, false, sl, sr, true, false);
                    return;
                }
                if (mc.player.getDeltaMovement().y < 0 && mc.player.fallDistance > 0.05f) {
                    if (mc.player.isSprinting()) {
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
            if (!canSwing()) {
                hitCd = 1;
                return;
            }
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
            hitCd = 3 + RNG.nextInt(3);
            if (wtap.on()) tapTicks = 1 + RNG.nextInt(2);
        }
    }

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
            humanize(mc);
            refillHotbar(mc, List.of(Bot::gapple, Inv.is(Items.ENDER_PEARL), Inv.is(Items.EXPERIENCE_BOTTLE), Inv.is(Items.END_CRYSTAL), Inv.is(Items.OBSIDIAN)));
            if (target == null) {
                releaseKeys();
                mending(mc, 99);
                return;
            }
            double dist = reachTo(mc, target);
            if (mending(mc, mc.player.distanceTo(target))) return;
            if (pearl(mc, mc.player.distanceTo(target))) return;
            if (backingOff(mc)) {
                disengage(mc, target, turn.f());
                return;
            }
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
            if (dist > far.get()) {
                unstuck(mc);
                keys(mc, true, false, sl, sr, jumpWanted(mc, chest), dist > far.get() + 3);
            }
            else if (dist < near.get()) keys(mc, false, true, sl, sr, false, false);
            else keys(mc, false, false, sl, sr, false, false);
        }
    }
}
