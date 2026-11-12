package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Aim;
import com.zlorbie788.zeus.core.Build;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The cart family: minecart crossbow play, instant carts, keeping the carts and potions stocked,
 * and the anchor routine that keeps your own explosion survivable.
 */
public final class CartTech {
    private CartTech() {}

    private static boolean ready(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.gameMode != null && mc.screen == null;
    }

    private static boolean ridingCart(Minecraft mc) {
        Entity v = mc.player.getVehicle();
        return v != null && v.getType() == EntityType.MINECART;
    }

    private static boolean charged(ItemStack s) {
        return s.has(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES);
    }

    private static Player target(Minecraft mc, double range) {
        Player best = null;
        double bestD = range * range;
        for (Player other : mc.level.players()) {
            if (other == mc.player || !other.isAlive() || other.isSpectator()) continue;
            double d = mc.player.distanceToSqr(other);
            if (d < bestD && mc.player.hasLineOfSight(other)) {
                bestD = d;
                best = other;
            }
        }
        return best;
    }

    /** Solves the pitch that puts a crossbow bolt on the target, using the real arc. */
    private static float boltPitch(Minecraft mc, LivingEntity t) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 aim = t.getBoundingBox().getCenter();
        Vec3 v = t.getDeltaMovement();
        double flight = eye.distanceTo(aim) / 3.15;
        aim = aim.add(v.x * flight, v.y * flight - 0.5 * 0.05 * flight * flight, v.z * flight);
        float yaw = (float) (Math.toDegrees(Math.atan2(aim.x - eye.x, aim.z - eye.z)) - 90.0);
        float best = mc.player.getXRot();
        double bestMiss = Double.MAX_VALUE;
        for (float p = 40f; p >= -85f; p -= 2f) {
            double[] r = arc(eye, yaw, p, aim);
            if (r[0] < bestMiss) {
                bestMiss = r[0];
                best = p;
            }
        }
        return best;
    }

    private static float boltYaw(Minecraft mc, LivingEntity t) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 aim = t.getBoundingBox().getCenter();
        Vec3 v = t.getDeltaMovement();
        double flight = eye.distanceTo(aim) / 3.15;
        aim = aim.add(v.x * flight, v.y * flight - 0.5 * 0.05 * flight * flight, v.z * flight);
        return (float) (Math.toDegrees(Math.atan2(aim.x - eye.x, aim.z - eye.z)) - 90.0);
    }

    private static double[] arc(Vec3 eye, float yawDeg, float pitchDeg, Vec3 point) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double cp = Math.cos(pitch);
        double vx = -Math.sin(yaw) * cp * 3.15;
        double vy = -Math.sin(pitch) * 3.15;
        double vz = Math.cos(yaw) * cp * 3.15;
        double x = eye.x;
        double y = eye.y;
        double z = eye.z;
        double best = Double.MAX_VALUE;
        for (int t = 0; t < 140; t++) {
            x += vx;
            y += vy;
            z += vz;
            double d = (x - point.x) * (x - point.x) + (y - point.y) * (y - point.y) + (z - point.z) * (z - point.z);
            if (d < best) best = d;
            vx *= 0.99;
            vy = vy * 0.99 - 0.05;
            vz *= 0.99;
            if (y < point.y - 40 && vy < 0) break;
        }
        return new double[] {best};
    }

    private static boolean ensureHotbar(Minecraft mc, net.minecraft.world.item.Item item) {
        if (Inv.hotbar(Inv.is(item)) >= 0) return true;
        int from = Inv.main(Inv.is(item));
        if (from < 0) return false;
        for (int i = 0; i < 9; i++) {
            if (Inv.get(i).isEmpty()) {
                Inv.click(Inv.menuSlot(from), i, net.minecraft.world.inventory.ClickType.SWAP);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ Xbow Cart

    public static final class XbowCart extends Module {
        private final Setting.Num range = add(new Setting.Num("Range", "How far a target is picked.", 24, 4, 60, 1, " blocks"));
        private final Setting.Bool safe = add(new Setting.Bool("Safe mode",
                "Only places the cart on solid ground and never next to lava.", true));
        private final Setting.Bool fallback = add(new Setting.Bool("Safe fallback",
                "If the cart cannot be entered, keep shooting from where you stand instead of doing nothing.", true));
        private final Setting.Bool silentSwap = add(new Setting.Bool("Silent swap",
                "Changes the slot without the scroll wheel animation, so the crossbow appears already in hand.", true));
        private final Setting.Num wait = add(new Setting.Num("Retry delay", "Ticks before trying the cart again.", 12, 4, 60, 2, " ticks"));
        private final Setting.Info state = add(new Setting.Info("Cart", this::lines));

        private int cooldown;
        private int fireToggle;
        private volatile String note = "idle";

        public XbowCart() {
            super("xbowcart", "Xbow Cart",
                    "Places a minecart, rides it and shoots the crossbow at whoever is near, all in one motion. The cart shrugs off knockback while you shoot.",
                    Category.COMBAT);
            alias("cart", "xbow", "cartpvp");
        }

        private List<String> lines() {
            return List.of(note);
        }

        @Override
        protected void onDisable() {
            note = "off";
            Minecraft mc = Minecraft.getInstance();
            mc.options.keyUse.setDown(false);
            fireToggle = 0;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            if (fireToggle > 0) {
                fireToggle--;
                mc.options.keyUse.setDown(fireToggle == 1);
                return;
            }
            if (!ensureHotbar(mc, Items.CROSSBOW)) {
                note = "no crossbow";
                return;
            }
            int bow = Inv.hotbar(Inv.is(Items.CROSSBOW));
            ItemStack stack = Inv.get(bow);
            Player t = target(mc, range.get());
            if (ridingCart(mc)) {
                if (!charged(stack)) {
                    if (Inv.selected() != bow) {
                        int prev = Inv.selected();
                        Inv.select(bow);
                        if (!silentSwap.on()) note = "reloading";
                        else note = "reloading";
                    }
                    mc.options.keyUse.setDown(true);
                    note = "reloading in the cart";
                    return;
                }
                if (t == null) {
                    note = "in the cart, no target";
                    return;
                }
                if (Inv.selected() != bow) Inv.select(bow);
                float yaw = boltYaw(mc, t);
                float pitch = boltPitch(mc, t);
                Aim.sendRotation(yaw, pitch);
                mc.player.setYRot(yaw);
                mc.player.setXRot(pitch);
                mc.options.keyUse.setDown(true);
                fireToggle = 2;
                note = "shot at " + t.getName().getString();
                return;
            }
            if (!safe.on() || canCart(mc)) {
                if (cooldown <= 0 && placeAndRide(mc)) {
                    note = "cart down";
                    cooldown = wait.i();
                    return;
                }
            }
            if (cooldown > 0) cooldown--;
            if (fallback.on() && t != null && charged(stack)) {
                if (Inv.selected() != bow) Inv.select(bow);
                float yaw = boltYaw(mc, t);
                float pitch = boltPitch(mc, t);
                Aim.sendRotation(yaw, pitch);
                mc.player.setYRot(yaw);
                mc.player.setXRot(pitch);
                mc.options.keyUse.setDown(true);
                fireToggle = 2;
                note = "fallback shot at " + t.getName().getString();
                return;
            }
            if (!charged(stack)) {
                if (Inv.selected() != bow) Inv.select(bow);
                mc.options.keyUse.setDown(true);
                note = "reloading";
            } else {
                note = t == null ? "ready" : "target in sight";
            }
        }

        private boolean canCart(Minecraft mc) {
            BlockPos below = mc.player.blockPosition().below();
            if (!Build.solid(below)) return false;
            if (!mc.level.getBlockState(mc.player.blockPosition()).getFluidState().isEmpty()) return false;
            for (BlockPos pos : BlockPos.betweenClosed(below.offset(-2, -1, -2), below.offset(2, 2, 2))) {
                if (mc.level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.LAVA)) return false;
            }
            return true;
        }

        private boolean placeAndRide(Minecraft mc) {
            if (!ensureHotbar(mc, Items.MINECART)) return false;
            int cart = Inv.hotbar(Inv.is(Items.MINECART));
            BlockPos below = mc.player.blockPosition().below();
            if (!Build.solid(below)) return false;
            boolean placed = Inv.with(cart, () -> Build.useOn(Build.top(below), Inv.is(Items.MINECART), true));
            if (!placed) return false;
            for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(3.0))) {
                if (e.getType() != EntityType.MINECART) continue;
                mc.gameMode.interact(mc.player, e, InteractionHand.MAIN_HAND);
                return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ Insta Cart

    public static final class InstaCart extends Module {
        private final Setting.Bool safe = add(new Setting.Bool("Safe mode",
                "Only places the cart on solid ground and never next to lava.", true));
        private final Setting.Bool fallback = add(new Setting.Bool("Safe fallback",
                "If the cart cannot be placed, do nothing rather than dumping it into a wall.", true));
        private final Setting.Bool release = add(new Setting.Bool("Release the crossbow on mounting",
                "Fires whatever the crossbow is holding the moment you sit down.", false));
        private final Setting.Num charge = add(new Setting.Num("Release charge",
                "Minimum crossbow charge before the automatic shot.", 1.0, 0.4, 1.0, 0.05, ""))
                .showIf(() -> release.on());
        private final Setting.Info state = add(new Setting.Info("Cart", this::lines));

        private int wait;
        private int fireToggle;
        private volatile String note = "idle";

        public InstaCart() {
            super("instacart", "Insta Cart",
                    "Puts you in a minecart on the same tick you press it: one cart under your feet, one clean mount, no fiddling.",
                    Category.MOVEMENT);
            alias("cartnow", "instantcart");
        }

        private List<String> lines() {
            return List.of(note);
        }

        @Override
        protected void onDisable() {
            note = "off";
            Minecraft.getInstance().options.keyUse.setDown(false);
            fireToggle = 0;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            if (fireToggle > 0) {
                fireToggle--;
                mc.options.keyUse.setDown(fireToggle == 1);
                return;
            }
            if (ridingCart(mc)) {
                note = "riding";
                if (release.on()) {
                    int bow = Inv.hotbar(Inv.is(Items.CROSSBOW));
                    if (bow >= 0 && charged(Inv.get(bow)) && Inv.selected() != bow) Inv.select(bow);
                    if (bow >= 0) {
                        mc.options.keyUse.setDown(true);
                        fireToggle = 2;
                        note = "shot on mounting";
                    }
                }
                return;
            }
            if (wait > 0) {
                wait--;
                return;
            }
            BlockPos below = mc.player.blockPosition().below();
            if (!Build.solid(below) || (safe.on() && !mc.level.getBlockState(mc.player.blockPosition()).getFluidState().isEmpty())) {
                note = "no floor for a cart";
                return;
            }
            if (!ensureHotbar(mc, Items.MINECART)) {
                note = "no cart";
                return;
            }
            int cart = Inv.hotbar(Inv.is(Items.MINECART));
            boolean placed = Inv.with(cart, () -> Build.useOn(Build.top(below), Inv.is(Items.MINECART), true));
            if (!placed) {
                note = fallback.on() ? "could not place" : "nothing placed";
                wait = 10;
                return;
            }
            for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(3.0))) {
                if (e.getType() != EntityType.MINECART) continue;
                mc.gameMode.interact(mc.player, e, InteractionHand.MAIN_HAND);
                note = "mounted";
                wait = 8;
                return;
            }
            note = "cart placed, not mounted";
            wait = 8;
        }
    }

    // ------------------------------------------------------------------ Auto Cart Refill

    public static final class AutoCartRefill extends Module {
        private final Setting.Bool carts = add(new Setting.Bool("Carts in the hotbar", "Keeps a stack of minecarts where you can reach it.", true));
        private final Setting.Bool crossbows = add(new Setting.Bool("Crossbows in the hotbar", "Keeps a crossbow in the hotbar, pulling one in from the inventory.", true));
        private final Setting.Bool bolts = add(new Setting.Bool("Bolts", "Keeps arrows or spectral arrows in the hotbar for the crossbow.", true));
        private final Setting.Num keep = add(new Setting.Num("Keep at least", "Refill when this many or fewer are left in the hotbar.", 4, 0, 32, 1, " items"));
        private final Setting.Num pace = add(new Setting.Num("Moves per tick", "How many stacks it may move in one tick. Lower is more human.", 1, 1, 4, 1, ""));
        private final Setting.Info state = add(new Setting.Info("Cart bag", this::lines));

        private int cooldown;
        private volatile String note = "stocked";

        public AutoCartRefill() {
            super("autocartrefill", "Auto Cart Refill",
                    "Keeps minecarts, crossbows and bolts stocked in your hotbar without you opening the inventory mid-fight.",
                    Category.UTILITY);
            alias("cartrefill", "cartfill");
        }

        private List<String> lines() {
            return List.of(note);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            int moves = 0;
            if (carts.on() && low(mc, Items.MINECART)) {
                ensureHotbar(mc, Items.MINECART);
                moves++;
                note = "carts topped up";
            }
            if (moves < pace.i() && crossbows.on() && low(mc, Items.CROSSBOW)) {
                ensureHotbar(mc, Items.CROSSBOW);
                moves++;
                note = "crossbow pulled in";
            }
            if (moves < pace.i() && bolts.on() && low(mc, Items.ARROW)) {
                ensureHotbar(mc, Items.ARROW);
                moves++;
                note = "bolts topped up";
            }
            if (moves > 0) cooldown = 4;
        }

        private boolean low(Minecraft mc, net.minecraft.world.item.Item item) {
            int have = 0;
            for (int i = 0; i < 9; i++) {
                ItemStack s = Inv.get(i);
                if (s.is(item)) have += s.getCount();
            }
            return have <= keep.i();
        }
    }

    // ------------------------------------------------------------------ Pot Refill

    public static final class PotRefill extends Module {
        private final Setting.Bool auto = add(new Setting.Bool("Auto refill",
                "Top the hotbar back up with potions by itself, without a key press.", true));
        private final Setting.Bool totems = add(new Setting.Bool("Restore totems",
                "Puts a totem back in the offhand when your last one is used.", true));
        private final Setting.Bool healing = add(new Setting.Bool("Healing fallback",
                "Throws a splash healing potion at your feet when your health gets low and no one is looking.", false));
        private final Setting.Num threshold = add(new Setting.Num("Heal below",
                "Health (half hearts) that triggers the fallback throw.", 8, 2, 19, 1, ""));
        private final Setting.Num pace = add(new Setting.Num("Moves per tick",
                "How many stacks may be moved in one tick.", 1, 1, 4, 1, ""));
        private final Setting.Info state = add(new Setting.Info("Potions", this::lines));

        private int cooldown;
        private int throwCooldown;
        private volatile String note = "stocked";

        public PotRefill() {
            super("potrefill", "Pot Refill",
                    "Keeps splash potions in reach, returns a totem to your offhand and can throw a heal at your feet when you drop low.",
                    Category.PLAYER);
            alias("potions", "pots", "refillpots");
        }

        private List<String> lines() {
            return List.of(note);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            if (throwCooldown > 0) {
                throwCooldown--;
                return;
            }
            if (auto.on() && cooldown <= 0) {
                int slot = -1;
                for (int i = 0; i < 9; i++) {
                    if (Inv.get(i).is(Items.SPLASH_POTION)) {
                        slot = i;
                        break;
                    }
                }
                if (slot < 0) {
                    for (int i = 9; i < 36; i++) {
                        if (Inv.get(i).is(Items.SPLASH_POTION)) {
                            for (int h = 0; h < 9; h++) {
                                if (Inv.get(h).isEmpty()) {
                                    Inv.click(Inv.menuSlot(i), h, net.minecraft.world.inventory.ClickType.SWAP);
                                    note = "potion pulled in";
                                    cooldown = 5;
                                    break;
                                }
                            }
                            break;
                        }
                    }
                }
            }
            if (totems.on() && !Inv.offhand(Inv.is(Items.TOTEM_OF_UNDYING))) {
                int from = Inv.main(Inv.is(Items.TOTEM_OF_UNDYING));
                if (from < 0) from = Inv.main(Inv.is(Items.TOTEM_OF_UNDYING));
                if (from >= 0) {
                    Inv.toOffhand(from);
                    note = "totem restored";
                    cooldown = 5;
                }
            }
            if (healing.on() && mc.player.getHealth() <= threshold.get()) {
                int pot = Inv.hotbar(Inv.is(Items.SPLASH_POTION));
                if (pot >= 0) {
                    Aim.look(mc.player.position());
                    mc.player.setXRot(70f);
                    Inv.with(pot, () -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
                    note = "healing throw";
                    throwCooldown = 30;
                }
            }
        }
    }

    // ------------------------------------------------------------------ Silent Safe Anchor

    public static final class SilentSafeAnchor extends Module {
        private final Setting.Num range = add(new Setting.Num("Range", "How far the anchor is placed.", 4.5, 2.5, 6, 0.5, " blocks"));
        private final Setting.Num turn = add(new Setting.Num("Rotation speed",
                "Degrees per tick used when the rotation is visible at all. 0 sends it silently, without moving your view.", 0, 0, 40, 1, "°"));
        private final Setting.Bool shield = add(new Setting.Bool("Shield the blast",
                "Raises your shield for a moment before the anchor is set off, so the blast comes from behind it.", true));
        private final Setting.Num delay = add(new Setting.Num("Shield delay", "Ticks the shield is up before the blast.", 3, 1, 10, 1, " ticks"));
        private final Setting.Num cooldown = add(new Setting.Num("Cooldown", "Ticks between two anchors.", 30, 10, 80, 5, " ticks"));
        private final Setting.Info state = add(new Setting.Info("Anchor", this::lines));

        private int wait;
        private int shieldTicks;
        private boolean raised;
        private volatile String note = "idle";

        public SilentSafeAnchor() {
            super("silentsafeanchor", "Silent Safe Anchor",
                    "Places and charges a respawn anchor at the target's feet with silent rotations, then sets it off while your shield covers the blast.",
                    Category.COMBAT);
            alias("safeanchor", "silenanchor");
        }

        private List<String> lines() {
            return List.of(note);
        }

        @Override
        protected void onDisable() {
            note = "off";
            lower();
        }

        private void lower() {
            Minecraft mc = Minecraft.getInstance();
            if (raised) {
                mc.options.keyUse.setDown(false);
                raised = false;
            }
            shieldTicks = 0;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            if (shieldTicks > 0) {
                mc.options.keyUse.setDown(true);
                raised = true;
                shieldTicks--;
                if (shieldTicks == 0) {
                    lower();
                    boom(mc);
                }
                return;
            }
            if (wait > 0) {
                wait--;
                return;
            }
            Player t = target(mc, range.get());
            if (t == null) {
                note = "no target in reach";
                return;
            }
            if (!ensureHotbar(mc, Items.RESPAWN_ANCHOR) || !ensureHotbar(mc, Items.GLOWSTONE)) {
                note = "no anchor or glowstone";
                return;
            }
            BlockPos spot = t.blockPosition();
            if (!Build.canPlace(spot, range.get())) {
                note = "nowhere to put it";
                return;
            }
            aimAt(mc, Vec3.atCenterOf(spot));
            if (!Build.place(spot, Inv.is(Items.RESPAWN_ANCHOR), true, range.get())) {
                note = "anchor would not place";
                wait = 10;
                return;
            }
            aimAt(mc, Vec3.atCenterOf(spot));
            Build.useOn(new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(spot), net.minecraft.core.Direction.UP, spot, false),
                    Inv.is(Items.GLOWSTONE), true);
            note = "anchor charged";
            wait = cooldown.i();
            if (shield.on() && Inv.offhand(Inv.is(Items.SHIELD))) {
                shieldTicks = delay.i();
            } else {
                boom(mc);
            }
        }

        private void boom(Minecraft mc) {
            BlockPos anchor = findAnchor(mc);
            if (anchor == null) return;
            if (!mc.level.getBlockState(anchor).is(net.minecraft.world.level.block.Blocks.RESPAWN_ANCHOR)) return;
            aimAt(mc, Vec3.atCenterOf(anchor));
            mc.gameMode.startDestroyBlock(anchor, net.minecraft.core.Direction.UP);
            mc.player.swing(InteractionHand.MAIN_HAND);
            note = "anchor set off";
            wait = cooldown.i();
        }

        private BlockPos findAnchor(Minecraft mc) {
            for (BlockPos pos : BlockPos.betweenClosed(mc.player.blockPosition().offset(-4, -3, -4), mc.player.blockPosition().offset(4, 3, 4))) {
                if (mc.level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.RESPAWN_ANCHOR)) return pos.immutable();
            }
            return null;
        }

        private void aimAt(Minecraft mc, Vec3 point) {
            float yaw = Aim.yawTo(mc.player.getEyePosition(), point);
            float pitch = Aim.pitchTo(mc.player.getEyePosition(), point);
            if (turn.f() <= 0.01f) {
                Aim.sendRotation(yaw, pitch);
            } else {
                mc.player.setYRot(yaw);
                mc.player.setYHeadRot(yaw);
                mc.player.setXRot(pitch);
            }
        }
    }

    // ------------------------------------------------------------------ Auto Pot

    public static final class AutoPot extends Module {
        private final Setting.Num below = add(new Setting.Num("Throw below", "Health (half hearts) that makes it throw.", 10, 2, 19, 1, ""));
        private final Setting.Bool onlySplash = add(new Setting.Bool("Splash only", "Use splash potions; drinking is left to Auto Eat.", true));
        private final Setting.Num wait = add(new Setting.Num("Between throws", "Ticks between two potions.", 25, 5, 80, 5, " ticks"));
        private final Setting.Info state = add(new Setting.Info("Potion", this::lines));

        private int cooldown;
        private volatile String note = "waiting";

        public AutoPot() {
            super("autopot", "Auto Pot",
                    "Throws a splash potion straight down at your feet the moment your health drops, and drinks one if that is all you carry.",
                    Category.PLAYER);
            alias("autopotion", "pots");
        }

        private List<String> lines() {
            return List.of(note);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            if (mc.player.getHealth() > below.get() || mc.player.isUsingItem()) return;
            int pot = Inv.hotbar(s -> s.is(Items.SPLASH_POTION));
            boolean splash = pot >= 0;
            if (!splash && !onlySplash.on()) pot = Inv.hotbar(s -> s.is(Items.POTION));
            if (pot < 0) {
                note = "no potion";
                return;
            }
            if (splash) mc.player.setXRot(75f);
            if (Inv.selected() != pot) Inv.select(pot);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            mc.player.swing(InteractionHand.MAIN_HAND);
            note = splash ? "threw a splash potion" : "drank a potion";
            cooldown = wait.i();
        }
    }
}
