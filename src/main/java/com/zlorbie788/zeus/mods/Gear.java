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
        private final Setting.Num range = add(new Setting.Num("Range", "How far away a target is picked.", 40, 10, 100, 5, " blocks"));
        private final Setting.Bool lead = add(new Setting.Bool("Lead moving targets", "Aim ahead of a target that moves.", true));

        public BowAimbot() {
            super("bowaimbot", "Bow Aimbot", "Aims your bow at the nearest target while you draw it.", Category.COMBAT);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.screen != null || !mc.player.isUsingItem()) return;
            if (!mc.player.getUseItem().is(Items.BOW)) return;
            LivingEntity t = Targets.nearest(mc, who, range.get());
            if (t == null || !mc.player.hasLineOfSight(t)) return;
            float f = mc.player.getTicksUsingItem() / 20f;
            f = (f * f + f * 2f) / 3f;
            if (f < 0.1f) return;
            double speed = Math.min(1f, f) * 3.0;
            Vec3 eye = mc.player.getEyePosition();
            Vec3 aim = t.getBoundingBox().getCenter();
            if (lead.on()) {
                double flight = eye.distanceTo(aim) / speed;
                Vec3 v = t.getDeltaMovement();
                aim = aim.add(v.x * flight, 0, v.z * flight);
            }
            double dx = aim.x - eye.x;
            double dz = aim.z - eye.z;
            double dy = aim.y - eye.y;
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            double bestErr = Double.MAX_VALUE;
            float bestPitch = mc.player.getXRot();
            double horiz = Math.sqrt(dx * dx + dz * dz);
            for (float pitch = -80f; pitch <= 60f; pitch += 0.5f) {
                double vy = -Math.sin(Math.toRadians(pitch)) * speed;
                double vh = Math.cos(Math.toRadians(pitch)) * speed;
                double x = 0;
                double y = 0;
                double err = Double.MAX_VALUE;
                for (int i = 0; i < 120; i++) {
                    x += vh;
                    y += vy;
                    vh *= 0.99;
                    vy = vy * 0.99 - 0.05;
                    double e = Math.abs(x - horiz) + Math.abs(y - dy);
                    if (e < err) err = e;
                    if (x > horiz + 2) break;
                }
                if (err < bestErr) {
                    bestErr = err;
                    bestPitch = pitch;
                }
            }
            mc.player.setYRot(yaw);
            mc.player.setXRot(bestPitch);
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
}
