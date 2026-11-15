package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Names;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Random;
import java.util.function.Predicate;

public final class Gear {
    private Gear() {}

    private static int tier(String n) {
        if (n.startsWith("netherite_")) return 6;
        if (n.startsWith("diamond_")) return 5;
        if (n.startsWith("iron_")) return 4;
        if (n.startsWith("chainmail_")) return 3;
        if (n.startsWith("golden_")) return 2;
        if (n.startsWith("turtle_")) return 3;
        if (n.startsWith("leather_")) return 1;
        return 0;
    }

    static EquipmentSlot armorSlot(ItemStack s) {
        String n = Names.item(s);
        if (n.endsWith("_helmet")) return EquipmentSlot.HEAD;
        if (n.endsWith("_chestplate")) return EquipmentSlot.CHEST;
        if (n.endsWith("_leggings")) return EquipmentSlot.LEGS;
        if (n.endsWith("_boots")) return EquipmentSlot.FEET;
        return null;
    }

    static int armorRank(ItemStack s) {
        if (s.isEmpty()) return 0;
        int t = tier(Names.item(s));
        if (t == 0) return 0;
        int dmg = s.isDamageableItem() ? Math.round(10f * s.getDamageValue() / Math.max(1, s.getMaxDamage())) : 0;
        return t * 12 - dmg;
    }

    static void equipFrom(int invSlot, EquipmentSlot slot) {
        int from = Inv.menuSlot(invSlot);
        int to = Inv.armorMenuSlot(slot);
        Inv.click(from, 0, ClickType.PICKUP);
        Inv.click(to, 0, ClickType.PICKUP);
        Inv.click(from, 0, ClickType.PICKUP);
    }

    public static final class AutoTotem extends Module {
        private final Setting.Choice item = add(new Setting.Choice("Offhand item", "What sits in your offhand when your health is fine.", 0, "Totem", "Golden apple", "End crystal", "Shield"));
        private final Setting.Num health = add(new Setting.Num("Totem below", "When your health (hearts x 2) falls to this, the totem goes in whatever the setting above says.", 10, 0, 20, 1, ""));
        private final Setting.Num delay = add(new Setting.Num("Delay", "Ticks between two swaps.", 2, 0, 20, 1, " ticks"));
        private int cd;

        public AutoTotem() {
            super("autototem", "Auto Totem", "Keeps a totem of undying (or another item) in your offhand.", Category.COMBAT);
        }

        private Predicate<ItemStack> wanted(Minecraft mc) {
            float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            if (hp <= health.get() || item.index() == 0) return Inv.is(Items.TOTEM_OF_UNDYING);
            return switch (item.index()) {
                case 1 -> s -> s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE);
                case 2 -> Inv.is(Items.END_CRYSTAL);
                default -> Inv.is(Items.SHIELD);
            };
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.gameMode == null || mc.screen != null) return;
            if (cd > 0) {
                cd--;
                return;
            }
            Predicate<ItemStack> want = wanted(mc);
            if (Inv.offhand(want)) return;
            int slot = Inv.main(want);
            if (slot < 0) slot = Inv.hotbar(want);
            if (slot < 0) return;
            Inv.toOffhand(slot);
            cd = delay.i();
        }

        @Override
        public String info() {
            return null;
        }
    }

    public static final class AutoArmor extends Module {
        private final Setting.Num delay = add(new Setting.Num("Delay", "Ticks between two swaps.", 3, 0, 20, 1, " ticks"));
        private final Setting.Bool elytra = add(new Setting.Bool("Keep elytra", "Do not replace an elytra with a chestplate.", true));
        private int cd;

        public AutoArmor() {
            super("autoarmor", "Auto Armor", "Puts on the best armor in your inventory, and swaps to better pieces.", Category.COMBAT);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.gameMode == null || mc.screen != null) return;
            if (cd > 0) {
                cd--;
                return;
            }
            for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack cur = mc.player.getItemBySlot(slot);
                if (slot == EquipmentSlot.CHEST && elytra.on() && cur.is(Items.ELYTRA)) continue;
                int curRank = armorRank(cur);
                int bestSlot = -1;
                int bestRank = curRank;
                for (int i = 0; i < 36; i++) {
                    ItemStack s = Inv.get(i);
                    if (s.isEmpty() || armorSlot(s) != slot) continue;
                    int r = armorRank(s);
                    if (r > bestRank) {
                        bestRank = r;
                        bestSlot = i;
                    }
                }
                if (bestSlot >= 0) {
                    equipFrom(bestSlot, slot);
                    cd = delay.i();
                    return;
                }
            }
        }
    }

    public static final class ChestSwap extends Module {
        public ChestSwap() {
            super("chestswap", "Chest Swap", "Swaps your chestplate and elytra, once, when you switch it on. Put it on a key.", Category.PLAYER);
            oneShot();
        }

        @Override
        protected void onEnable() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.gameMode == null) return;
            boolean wearingElytra = mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
            int slot = -1;
            for (int i = 0; i < 36 && slot < 0; i++) {
                ItemStack s = Inv.get(i);
                if (s.isEmpty()) continue;
                if (wearingElytra ? armorSlot(s) == EquipmentSlot.CHEST : s.is(Items.ELYTRA)) slot = i;
            }
            if (slot < 0) {
                Zeus.toast(wearingElytra ? "Chest Swap: no chestplate in your inventory" : "Chest Swap: no elytra in your inventory", false);
                return;
            }
            equipFrom(slot, EquipmentSlot.CHEST);
        }
    }

    public static final class AutoTool extends Module {
        private final Setting.Bool restore = add(new Setting.Bool("Switch back", "Go back to the slot you had when you stop mining.", true));
        private int previous = -1;

        public AutoTool() {
            super("autotool", "Auto Tool", "Switches to the best tool in your hotbar for the block you mine.", Category.PLAYER);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null) return;
            boolean mining = mc.options.keyAttack.isDown() && mc.screen == null && mc.hitResult instanceof BlockHitResult bhr && bhr.getType() != HitResult.Type.MISS;
            if (!mining) {
                if (previous >= 0 && restore.on()) Inv.select(previous);
                previous = -1;
                return;
            }
            BlockHitResult bhr = (BlockHitResult) mc.hitResult;
            var state = mc.level.getBlockState(bhr.getBlockPos());
            int bestSlot = Inv.selected();
            float best = Inv.get(bestSlot).getDestroySpeed(state);
            for (int i = 0; i < 9; i++) {
                float sp = Inv.get(i).getDestroySpeed(state);
                if (sp > best + 0.01f) {
                    best = sp;
                    bestSlot = i;
                }
            }
            if (bestSlot != Inv.selected()) {
                if (previous < 0) previous = Inv.selected();
                Inv.select(bestSlot);
            }
        }
    }

    public static final class AutoWeapon extends Module {
        private final Setting.Choice prefer = add(new Setting.Choice("Prefer", "Swords are faster, axes hit harder.", 0, "Sword", "Axe"));
        private final Setting.Bool restore = add(new Setting.Bool("Switch back", "Go back to the slot you had afterwards.", true));
        private int previous = -1;

        public AutoWeapon() {
            super("autoweapon", "Auto Weapon", "Switches to the best sword or axe in your hotbar when you attack.", Category.COMBAT);
        }

        private int score(ItemStack s) {
            String n = Names.item(s);
            boolean sword = n.endsWith("_sword");
            boolean axe = n.endsWith("_axe");
            if (!sword && !axe) return 0;
            int t = tier(n);
            if (n.startsWith("wooden_")) t = 1;
            if (n.startsWith("stone_")) t = 2;
            return t * 10 + ((sword == (prefer.index() == 0)) ? 5 : 0);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null) return;
            boolean fighting = mc.options.keyAttack.isDown() && mc.screen == null && mc.hitResult instanceof EntityHitResult;
            if (!fighting) {
                if (previous >= 0 && restore.on()) Inv.select(previous);
                previous = -1;
                return;
            }
            int best = Inv.selected();
            int bestScore = score(Inv.get(best));
            for (int i = 0; i < 9; i++) {
                int sc = score(Inv.get(i));
                if (sc > bestScore) {
                    bestScore = sc;
                    best = i;
                }
            }
            if (best != Inv.selected()) {
                if (previous < 0) previous = Inv.selected();
                Inv.select(best);
            }
        }
    }

    private static boolean goodFood(ItemStack s) {
        if (!s.has(DataComponents.FOOD)) return false;
        String n = Names.item(s);
        for (String bad : new String[] {"rotten_flesh", "spider_eye", "poisonous_potato", "pufferfish", "chorus_fruit", "suspicious_stew", "raw_chicken", "chicken"}) {
            if (n.equals(bad) || n.equals("raw_" + bad)) return false;
        }
        return !n.equals("chicken");
    }

    public static final class AutoEat extends Module {
        private final Setting.Num hunger = add(new Setting.Num("Eat when food is below", "Food level (of 20) that makes you eat.", 14, 1, 19, 1, ""));
        private final Setting.Bool gapples = add(new Setting.Bool("Golden apples when hurt", "Eat a golden apple when your health is low.", true));
        private final Setting.Num hurt = add(new Setting.Num("Hurt below", "Health (hearts x 2) that counts as low.", 10, 2, 19, 1, "")).showIf(() -> gapples.on());
        private int eatingSlot = -1;
        private int previous = -1;
        private int ticks;

        public AutoEat() {
            super("autoeat", "Auto Eat", "Eats for you: food when hungry, golden apples when hurt.", Category.PLAYER);
        }

        @Override
        protected void onDisable() {
            stop();
        }

        private void stop() {
            Minecraft mc = Minecraft.getInstance();
            if (eatingSlot >= 0) {
                mc.options.keyUse.setDown(false);
                if (previous >= 0) Inv.select(previous);
            }
            eatingSlot = -1;
            previous = -1;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null || mc.screen != null) {
                if (eatingSlot >= 0) stop();
                return;
            }
            if (eatingSlot >= 0) {
                ticks++;
                mc.options.keyUse.setDown(true);
                boolean full = mc.player.getFoodData().getFoodLevel() >= 20;
                if ((!mc.player.isUsingItem() && ticks > 4) || ticks > 80 || (full && ticks > 2 && !Inv.get(eatingSlot).is(Items.GOLDEN_APPLE))) stop();
                return;
            }
            if (mc.player.isUsingItem()) return;
            float hp = mc.player.getHealth();
            int slot = -1;
            if (gapples.on() && hp <= hurt.get()) {
                slot = Inv.hotbar(s -> s.is(Items.ENCHANTED_GOLDEN_APPLE));
                if (slot < 0) slot = Inv.hotbar(s -> s.is(Items.GOLDEN_APPLE));
            }
            if (slot < 0 && mc.player.getFoodData().getFoodLevel() <= hunger.i()) {
                int bestNutrition = -1;
                for (int i = 0; i < 9; i++) {
                    ItemStack s = Inv.get(i);
                    if (s.isEmpty() || !goodFood(s) || s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE)) continue;
                    var food = s.get(DataComponents.FOOD);
                    int n = food == null ? 0 : food.nutrition();
                    if (n > bestNutrition) {
                        bestNutrition = n;
                        slot = i;
                    }
                }
            }
            if (slot < 0) return;
            previous = Inv.selected();
            Inv.select(slot);
            eatingSlot = slot;
            ticks = 0;
            mc.options.keyUse.setDown(true);
        }
    }

    public static final class AutoRespawn extends Module {
        public AutoRespawn() {
            super("autorespawn", "Auto Respawn", "Respawns at once when you die.", Category.PLAYER);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.screen instanceof DeathScreen && mc.player != null) {
                mc.player.respawn();
                mc.setScreen(null);
            }
        }
    }

    public static final class ExpThrower extends Module {
        private final Setting.Num delay = add(new Setting.Num("Delay", "Ticks between two bottles.", 2, 0, 20, 1, " ticks"));
        private final Setting.Bool onlyDamaged = add(new Setting.Bool("Only when armor is damaged", "Stops when nothing you wear needs mending.", true));
        private int cd;
        private int aimed;
        private float oldPitch;

        public ExpThrower() {
            super("expthrower", "Exp Thrower", "Throws experience bottles at your feet to mend your gear.", Category.PLAYER);
        }

        @Override
        protected void onEnable() {
            aimed = 0;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) oldPitch = mc.player.getXRot();
        }

        @Override
        protected void onDisable() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && aimed > 0) mc.player.setXRot(oldPitch);
        }

        private boolean needsMending(Minecraft mc) {
            for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
                if (mc.player.getItemBySlot(s).isDamaged()) return true;
            }
            return false;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.gameMode == null || mc.screen != null) return;
            if (onlyDamaged.on() && !needsMending(mc)) {
                aimed = 0;
                return;
            }
            int slot = Inv.hotbar(Inv.is(Items.EXPERIENCE_BOTTLE));
            if (slot < 0 && !Inv.offhand(Inv.is(Items.EXPERIENCE_BOTTLE))) return;
            mc.player.setXRot(90f);
            aimed++;
            if (aimed < 3) return;
            if (cd > 0) {
                cd--;
                return;
            }
            if (slot >= 0) {
                Inv.with(slot, () -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
            } else {
                mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
            }
            cd = delay.i();
        }
    }

    public static final class AutoFish extends Module {
        private final Setting.Num recast = add(new Setting.Num("Recast delay", "Ticks to wait before casting again.", 15, 5, 60, 1, " ticks"));
        private int castTicks;
        private int wait;
        private boolean casting;

        public AutoFish() {
            super("autofish", "Auto Fish", "Reels in the moment a fish bites and casts again.", Category.PLAYER);
        }

        @Override
        protected void onEnable() {
            castTicks = 0;
            wait = 0;
            casting = false;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.gameMode == null || mc.level == null || mc.screen != null) return;
            InteractionHand hand = mc.player.getMainHandItem().is(Items.FISHING_ROD) ? InteractionHand.MAIN_HAND
                    : mc.player.getOffhandItem().is(Items.FISHING_ROD) ? InteractionHand.OFF_HAND : null;
            if (hand == null) return;
            if (wait > 0) {
                wait--;
                if (wait == 0 && !casting) {
                    mc.gameMode.useItem(mc.player, hand);
                    casting = true;
                    castTicks = 0;
                }
                return;
            }
            Entity bobber = null;
            double bestD = 40 * 40;
            for (Entity e : com.zlorbie788.zeus.core.Env.entities(mc, 40)) {
                if (e.getType() != EntityType.FISHING_BOBBER) continue;
                double d = mc.player.distanceToSqr(e);
                if (d < bestD) {
                    bestD = d;
                    bobber = e;
                }
            }
            if (bobber == null) {
                if (!casting) {
                    mc.gameMode.useItem(mc.player, hand);
                    casting = true;
                    castTicks = 0;
                } else if (++castTicks > 100) {
                    casting = false;
                }
                return;
            }
            casting = true;
            castTicks++;
            if (castTicks > 30 && bobber.getDeltaMovement().y < -0.08) {
                mc.gameMode.useItem(mc.player, hand);
                casting = false;
                wait = recast.i();
            }
        }
    }

    public static final class AntiAfk extends Module {
        private final Random rng = new Random();
        private int next = 60;
        private int ticks;

        public AntiAfk() {
            super("antiafk", "Anti AFK", "Swings and turns a little now and then so you are never marked away.", Category.PLAYER);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.screen != null) return;
            if (++ticks < next) return;
            ticks = 0;
            next = 200 + rng.nextInt(400);
            mc.player.swing(InteractionHand.MAIN_HAND);
            mc.player.setYRot(mc.player.getYRot() + (rng.nextFloat() - 0.5f) * 30f);
        }
    }

    public static final class NoStatusEffects extends Module {
        public NoStatusEffects() {
            super("nostatus", "No Bad Effects", "Removes the blindness and darkness effects from your screen.", Category.PLAYER);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null) return;
            mc.player.removeEffect(MobEffects.BLINDNESS);
            mc.player.removeEffect(MobEffects.DARKNESS);
        }
    }

    public static final class BowSpam extends Module {
        private final Setting.Num charge = add(new Setting.Num("Charge", "Ticks to draw the bow before the arrow is released. 20 is a full draw.", 4, 3, 20, 1, " ticks"));

        public BowSpam() {
            super("bowspam", "Bow Spam", "Releases the bow early again and again while you hold the button.", Category.COMBAT);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.gameMode == null || mc.screen != null) return;
            if (!mc.options.keyUse.isDown() || !mc.player.isUsingItem()) return;
            if (!mc.player.getMainHandItem().is(Items.BOW) && !mc.player.getOffhandItem().is(Items.BOW)) return;
            if (mc.player.getTicksUsingItem() >= charge.i()) {
                mc.gameMode.releaseUsingItem(mc.player);
                mc.player.startUsingItem(mc.player.getMainHandItem().is(Items.BOW) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            }
        }
    }

    public static final class BowAimbot extends Module {
        private final Targets.Pick who = Targets.pick(this::add, true, true, false);
        private final Setting.Num range = add(new Setting.Num("Range", "How far away a target is picked.", 48, 10, 120, 5, " blocks"));
        private final Setting.Choice pick = add(new Setting.Choice("Pick",
                "Nearest target, the one closest to your crosshair, or the one with the least health.", 0,
                "Nearest", "Closest to crosshair", "Lowest health"));
        private final Setting.Bool lead = add(new Setting.Bool("Lead moving targets",
                "Solves for where the target will be when the arrow arrives, not where it is now.", true));
        private final Setting.Bool walls = add(new Setting.Bool("Ignore walls",
                "Keep aiming at a target you cannot see. Off is honest and keeps you from shooting stone.", false));
        private final Setting.Choice aimAt = add(new Setting.Choice("Aim at", "Where on the body to hit.", 1,
                "Body centre", "Upper body", "Head"));
        private final Setting.Num smooth = add(new Setting.Num("Turn speed",
                "Degrees per tick the view may turn towards the solution. 0 snaps straight onto it.", 26, 0, 90, 1, "°"));
        private final Setting.Bool fire = add(new Setting.Bool("Fire when ready",
                "Releases the arrow itself once the solution is on target and the bow is fully drawn.", false));
        private final Setting.Bool human = add(new Setting.Bool("Human aim",
                "Adds the Phantom wobble to the final aim, so the solution is not pixel-perfect.", true));
        private final Setting.Info solved = add(new Setting.Info("Solution", this::lines));

        private volatile String note = "not drawing";

        public BowAimbot() {
            super("bowaimbot", "Bow Aimbot",
                    "Solves the real arrow arc for a moving target - charge, drag, gravity and travel time - and points the bow where the arrow will actually land.",
                    Category.COMBAT);
            alias("bowaim", "bow");
        }

        private java.util.List<String> lines() {
            return java.util.List.of(note);
        }

        private static final class Solve {
            float yaw;
            float pitch;
            double miss;
            int ticks;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null || mc.screen != null) {
                note = "not drawing";
                return;
            }
            if (!mc.player.isUsingItem() || !mc.player.getUseItem().is(Items.BOW)) {
                note = "not drawing";
                return;
            }
            float charge = Math.min(1f, mc.player.getTicksUsingItem() / 20f);
            double speed = Math.min(1f, (charge * charge + charge * 2f) / 3f) * 3.0;
            if (speed < 0.3) {
                note = "drawing";
                return;
            }
            LivingEntity target = pick(mc);
            if (target == null) {
                note = "no target";
                return;
            }
            Vec3 eye = mc.player.getEyePosition();
            Vec3 aim = aimPoint(target);
            double dist = eye.distanceTo(aim);
            if (dist > range.get()) {
                note = "target out of range";
                return;
            }
            Vec3 predicted = aim;
            Solve best = null;
            for (int round = 0; round < 3; round++) {
                Solve s = solve(eye, predicted, speed);
                if (s == null) break;
                best = s;
                if (!lead.on()) break;
                Vec3 motion = target.getDeltaMovement();
                double flight = s.ticks / 20.0;
                Vec3 next = aim.add(motion.x * s.ticks, motion.y * s.ticks - 0.5 * 0.08 * s.ticks * s.ticks, motion.z * s.ticks);
                predicted = next;
                if (next.distanceToSqr(aim) > range.get() * range.get() * 4.0) break;
                if (round > 0 && s.miss < 0.25) break;
            }
            if (best == null) {
                note = "no arc reaches it";
                return;
            }
            float wantYaw = best.yaw;
            float wantPitch = best.pitch;
            if (human.on() && Phantom.active()) {
                wantYaw += Phantom.wobble();
                wantPitch += Phantom.wobble() * 0.5f;
            }
            float dy = wrap(wantYaw - mc.player.getYRot());
            float dp = wantPitch - mc.player.getXRot();
            float step = smooth.f();
            if (step <= 0.01f) {
                mc.player.setYRot(wantYaw);
                mc.player.setYHeadRot(wantYaw);
                mc.player.setXRot(wantPitch);
            } else {
                float maxY = Math.min(Math.abs(dy), step);
                float maxP = Math.min(Math.abs(dp), step * 0.8f);
                mc.player.setYRot(mc.player.getYRot() + Math.signum(dy) * maxY);
                mc.player.setYHeadRot(mc.player.getYRot());
                mc.player.setXRot(Math.max(-90f, Math.min(90f, mc.player.getXRot() + Math.signum(dp) * maxP)));
                dy -= Math.signum(dy) * maxY;
                dp -= Math.signum(dp) * maxP;
            }
            boolean onTarget = Math.abs(dy) < 2.5f && Math.abs(dp) < 2.5f && best.miss < 0.45;
            note = String.format(java.util.Locale.ROOT, "%s · %.1f m · %s · %.0f%%", target.getName().getString(), dist,
                    onTarget ? "on target" : "turning", charge * 100f);
            if (fire.on() && onTarget && charge >= 0.98f) {
                mc.options.keyUse.setDown(false);
                note = "shot released at " + target.getName().getString();
            }
        }

        private LivingEntity pick(Minecraft mc) {
            Vec3 look = mc.player.getViewVector(1f);
            LivingEntity best = null;
            double bestScore = Double.MAX_VALUE;
            for (var e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(range.get()))) {
                if (!(e instanceof LivingEntity le) || !Targets.accepts(mc, who, le)) continue;
                if (!walls.on() && !mc.player.hasLineOfSight(le)) continue;
                double d = mc.player.distanceTo(le);
                if (d > range.get()) continue;
                double score = switch (pick.index()) {
                    case 1 -> {
                        Vec3 to = le.getBoundingBox().getCenter().subtract(mc.player.getEyePosition()).normalize();
                        yield 1.0 - to.dot(look);
                    }
                    case 2 -> le.getHealth() + le.getAbsorptionAmount() + d * 0.02;
                    default -> d;
                };
                if (score < bestScore) {
                    bestScore = score;
                    best = le;
                }
            }
            return best;
        }

        private Vec3 aimPoint(LivingEntity t) {
            var b = t.getBoundingBox();
            double h = b.maxY - b.minY;
            return switch (aimAt.index()) {
                case 1 -> new Vec3((b.minX + b.maxX) / 2, b.minY + h * 0.78, (b.minZ + b.maxZ) / 2);
                case 2 -> new Vec3((b.minX + b.maxX) / 2, b.maxY - 0.12, (b.minZ + b.maxZ) / 2);
                default -> b.getCenter();
            };
        }

        /** Finds the yaw and pitch whose arc passes closest to the target point, using the real arrow physics. */
        private Solve solve(Vec3 eye, Vec3 target, double speed) {
            double dx = target.x - eye.x;
            double dz = target.z - eye.z;
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            double ground = Math.sqrt(dx * dx + dz * dz);

            float bestPitch = Float.NaN;
            double bestMiss = Double.MAX_VALUE;
            int bestTicks = 0;
            for (float p = 45f; p >= -89f; p -= 3f) {
                double[] r = sim(eye, yaw, p, speed, target);
                if (r[0] < bestMiss) {
                    bestMiss = r[0];
                    bestPitch = p;
                    bestTicks = (int) r[1];
                }
            }
            if (Float.isNaN(bestPitch)) return null;
            for (float p = bestPitch - 3f; p <= bestPitch + 3f; p += 0.25f) {
                double[] r = sim(eye, yaw, p, speed, target);
                if (r[0] < bestMiss) {
                    bestMiss = r[0];
                    bestPitch = p;
                    bestTicks = (int) r[1];
                }
            }
            if (bestMiss > Math.max(3.0, ground * 0.06)) return null;
            Solve s = new Solve();
            s.yaw = yaw;
            s.pitch = bestPitch;
            s.miss = Math.sqrt(bestMiss);
            s.ticks = bestTicks;
            return s;
        }

        /** One arrow flight: returns {closest squared distance to the point, ticks at that point}. */
        private static double[] sim(Vec3 eye, float yawDeg, float pitchDeg, double speed, Vec3 point) {
            double yaw = Math.toRadians(yawDeg);
            double pitch = Math.toRadians(pitchDeg);
            double cp = Math.cos(pitch);
            double vx = -Math.sin(yaw) * cp * speed;
            double vy = -Math.sin(pitch) * speed;
            double vz = Math.cos(yaw) * cp * speed;
            double x = eye.x;
            double y = eye.y;
            double z = eye.z;
            double best = Double.MAX_VALUE;
            int bestTick = 0;
            for (int t = 0; t < 160; t++) {
                x += vx;
                y += vy;
                z += vz;
                double d = (x - point.x) * (x - point.x) + (y - point.y) * (y - point.y) + (z - point.z) * (z - point.z);
                if (d < best) {
                    best = d;
                    bestTick = t;
                }
                vx *= 0.99;
                vy = vy * 0.99 - 0.05;
                vz *= 0.99;
                if (y < point.y - 60 && vy < 0) break;
            }
            return new double[] {best, bestTick};
        }

        private static float wrap(float deg) {
            while (deg > 180f) deg -= 360f;
            while (deg < -180f) deg += 360f;
            return deg;
        }
    }

    public static final class Hitboxes extends Module {
        private static volatile Hitboxes self;
        private final Setting.Num size = add(new Setting.Num("Extra size", "How much bigger things are to hit, in blocks.", 0.3, 0.05, 1.5, 0.05, ""));
        private final Setting.Bool players = add(new Setting.Bool("Players", "Bigger hitboxes for players.", true));
        private final Setting.Bool mobs = add(new Setting.Bool("Mobs", "Bigger hitboxes for mobs.", true));

        public Hitboxes() {
            super("hitboxes", "Hitboxes", "Makes mobs and players easier to hit.", Category.COMBAT);
            self = this;
        }

        public static float extra(Entity e) {
            Hitboxes h = self;
            if (h == null || !h.enabled() || e == Minecraft.getInstance().player || !(e instanceof LivingEntity)) return 0f;
            boolean player = e instanceof net.minecraft.world.entity.player.Player;
            if (player ? !h.players.on() : !h.mobs.on()) return 0f;
            return h.size.f();
        }
    }

    public static final class ReBuffNotifier extends Module {
        private final Setting.Bool speed = add(new Setting.Bool("Speed", "Also watch Speed.", true));
        private final Setting.Bool strength = add(new Setting.Bool("Strength", "Also watch Strength.", true));
        private final Setting.Num warn = add(new Setting.Num("Warn before", "Seconds left before the warning plays. 0 warns only once the effect is gone.", 5, 0, 30, 1, "s"));
        private final Setting.Bool sound = add(new Setting.Bool("Sound", "Play a short pling with the warning.", true));

        private final boolean[] had = new boolean[2];
        private final boolean[] warned = new boolean[2];

        public ReBuffNotifier() {
            super("rebuff", "Re-Buff Notifier", "Warns you the moment Speed or Strength is about to run out, so a buff never lapses mid-fight.", Category.PLAYER);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null) {
                java.util.Arrays.fill(had, false);
                java.util.Arrays.fill(warned, false);
                return;
            }
            if (speed.on()) watch(mc, net.minecraft.world.effect.MobEffects.SPEED, "Speed", 0);
            else clear(0);
            if (strength.on()) watch(mc, net.minecraft.world.effect.MobEffects.STRENGTH, "Strength", 1);
            else clear(1);
        }

        private void clear(int slot) {
            had[slot] = false;
            warned[slot] = false;
        }

        private void watch(Minecraft mc, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, String label, int slot) {
            net.minecraft.world.effect.MobEffectInstance active = mc.player.getEffect(effect);
            if (active == null) {
                if (had[slot]) {
                    Zeus.note(label + " ran out", com.zlorbie788.zeus.core.Notify.Kind.WARN);
                    if (sound.on()) pling(mc, 0.9f);
                }
                clear(slot);
                return;
            }
            had[slot] = true;
            int limit = (int) Math.round(warn.get() * 20.0);
            if (limit <= 0 || warned[slot]) return;
            int left = active.getDuration();
            if (left > limit) return;
            warned[slot] = true;
            Zeus.note(label + " ends in " + Math.max(0, left / 20) + "s", com.zlorbie788.zeus.core.Notify.Kind.WARN);
            if (sound.on()) pling(mc, 1.25f);
        }

        private void pling(Minecraft mc, float pitch) {
            if (mc.getSoundManager() == null) return;
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), pitch));
        }
    }
}
