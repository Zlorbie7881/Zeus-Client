package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Build;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Names;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Clutch and survival modules: everything that keeps you alive in a fight you are losing.
 */
public final class Clutch {
    private Clutch() {}

    private static boolean ready(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.gameMode != null && mc.screen == null;
    }

    private static boolean falling(Player p) {
        return !p.onGround() && !p.isInWater() && !p.isInLava() && !p.isFallFlying() && !p.getAbilities().flying;
    }

    /** The first solid block under the player within the given blocks, or null if water or nothing is there. */
    private static BlockPos ground(Minecraft mc, int max) {
        BlockPos base = mc.player.blockPosition();
        for (int i = 1; i <= max; i++) {
            BlockPos pos = base.below(i);
            if (!mc.level.getBlockState(pos).getFluidState().isEmpty()) return null;
            if (Build.solid(pos)) return pos;
        }
        return null;
    }

    private static boolean has(Minecraft mc, net.minecraft.world.item.Item item) {
        return Inv.hotbar(Inv.is(item)) >= 0 || Inv.offhand(Inv.is(item));
    }

    private static Player nearestPlayer(Minecraft mc, double range) {
        Player best = null;
        double bestD = range * range;
        for (Player other : mc.level.players()) {
            if (other == mc.player || !other.isAlive() || other.isSpectator()) continue;
            double d = mc.player.distanceToSqr(other);
            if (d < bestD) {
                bestD = d;
                best = other;
            }
        }
        return best;
    }

    private static boolean sword(ItemStack s) {
        return Names.item(s).endsWith("_sword");
    }

    private static boolean axe(ItemStack s) {
        return Names.item(s).endsWith("_axe");
    }

    // ------------------------------------------------------------------ Auto MLG

    public static final class AutoMlg extends Module {
        private final Setting.Choice method = add(new Setting.Choice("What to use",
                "Water is the most reliable. Cobweb works in the Nether and anywhere water cannot go.",
                2, "Water bucket", "Cobweb", "Water, then cobweb"));
        private final Setting.Num fall = add(new Setting.Num("Fall distance",
                "How far you have to fall before it acts. It still waits until the ground is within reach.", 3, 2, 30, 1, " blocks"));
        private final Setting.Bool human = add(new Setting.Bool("Human timing",
                "Waits a human reaction instead of placing on the exact tick the fall starts.", true));

        private long armed;
        private int cooldown;
        private String note = "watching";
        private int saved;

        public AutoMlg() {
            super("automlg", "Auto MLG",
                    "Catches a bad fall with a water bucket or a cobweb, so one missed jump does not cost you the fight.",
                    Category.PLAYER);
            alias("mlg", "clutch", "waterbucket");
        }

        @Override
        protected void onDisable() {
            armed = 0;
            cooldown = 0;
            note = "off";
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) {
                armed = 0;
                return;
            }
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            Player p = mc.player;
            if (!falling(p)) {
                armed = 0;
                note = "watching";
                return;
            }
            double distance = p.fallDistance;
            if (distance < fall.get()) {
                armed = 0;
                return;
            }
            BlockPos landing = ground(mc, 3);
            if (landing == null) {
                note = "looking for the ground";
                return;
            }
            if (Build.distTo(landing) > 4.4) {
                note = "waiting for reach";
                return;
            }
            if (armed == 0L) {
                armed = System.currentTimeMillis() + (human.on() ? Phantom.reaction() : 0L);
                note = "falling " + Math.round(distance);
                return;
            }
            if (System.currentTimeMillis() < armed) return;
            boolean water = method.index() != 1;
            boolean cobweb = method.index() != 0;
            if (water && has(mc, Items.WATER_BUCKET)) {
                Phantom.aim(p, Vec3.atCenterOf(landing));
                if (Build.useOn(Build.top(landing), Inv.is(Items.WATER_BUCKET), true)) {
                    cooldown = 30;
                    saved++;
                    note = "water at " + landing.getX() + " " + landing.getY() + " " + landing.getZ();
                    return;
                }
            }
            if (cobweb && has(mc, Items.COBWEB)) {
                BlockPos spot = landing.above();
                Phantom.aim(p, Vec3.atCenterOf(spot));
                if (Build.place(spot, Inv.is(Items.COBWEB), true, 4.4)) {
                    cooldown = 30;
                    saved++;
                    note = "cobweb at " + spot.getX() + " " + spot.getY() + " " + spot.getZ();
                    return;
                }
            }
            note = method.index() == 0 ? "no water bucket" : method.index() == 1 ? "no cobweb" : "no water or cobweb";
        }

        @Override
        public String info() {
            return note + (saved > 0 ? " · saved " + saved : "");
        }
    }

    // ------------------------------------------------------------------ Pearl Catch

    public static final class PearlCatch extends Module {
        private final Setting.Num fall = add(new Setting.Num("Fall distance",
                "How far you have to fall before a pearl is thrown.", 6, 3, 60, 1, " blocks"));
        private final Setting.Bool withoutWater = add(new Setting.Bool("Only without a water bucket",
                "Leaves the fall to Auto MLG when you still have a bucket to place.", true));
        private final Setting.Num cooldown = add(new Setting.Num("Wait after a throw",
                "Ticks before another pearl may be thrown.", 30, 10, 100, 5, " ticks"));

        private int wait;
        private String note = "watching";

        public PearlCatch() {
            super("pearlcatch", "Pearl Catch",
                    "Throws an ender pearl straight down when a fall is going to hurt, teleporting you onto the ground instead.",
                    Category.PLAYER);
            alias("pearlclutch", "catch");
        }

        @Override
        protected void onDisable() {
            note = "off";
            wait = 0;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            if (wait > 0) {
                wait--;
                return;
            }
            Player p = mc.player;
            if (!falling(p) || p.fallDistance < fall.get()) {
                note = "watching";
                return;
            }
            if (withoutWater.on() && has(mc, Items.WATER_BUCKET)) {
                note = "leaving it to the bucket";
                return;
            }
            int slot = Inv.hotbar(Inv.is(Items.ENDER_PEARL));
            boolean offhand = Inv.offhand(Inv.is(Items.ENDER_PEARL));
            if (slot < 0 && !offhand) {
                note = "no pearl";
                return;
            }
            if (ground(mc, 40) == null) {
                note = "nothing to land on";
                return;
            }
            if (Phantom.miss()) return;
            Phantom.aimDown(p);
            if (offhand) {
                mc.gameMode.useItem(p, InteractionHand.OFF_HAND);
                p.swing(InteractionHand.OFF_HAND);
            } else {
                Inv.with(slot, () -> {
                    mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
                    p.swing(InteractionHand.MAIN_HAND);
                });
            }
            wait = cooldown.i();
            note = "pearl away at " + Math.round(p.fallDistance) + " blocks";
        }

        @Override
        public String info() {
            return note;
        }
    }

    // ------------------------------------------------------------------ Auto Wind Charge

    public static final class AutoWindCharge extends Module {
        private final Setting.Choice when = add(new Setting.Choice("When",
                "Catch a fall launches you back up mid-air. Boost a jump fires while you hold jump.",
                0, "Catch a fall", "Boost a jump"));
        private final Setting.Num fall = add(new Setting.Num("Fall distance",
                "Falling this far arms a wind charge.", 3, 1, 30, 1, " blocks")).showIf(() -> when.index() == 0);
        private final Setting.Num cooldown = add(new Setting.Num("Cooldown",
                "Ticks between two wind charges.", 25, 10, 100, 5, " ticks"));

        private int wait;
        private String note = "ready";
        private int used;

        public AutoWindCharge() {
            super("autowindcharge", "Auto Wind Charge",
                    "Fires a wind charge at your feet to catch a fall or boost a jump, using the knockback as movement.",
                    Category.MOVEMENT);
            alias("windcharge", "windjump");
        }

        @Override
        protected void onDisable() {
            wait = 0;
            note = "off";
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            if (wait > 0) {
                wait--;
                return;
            }
            Player p = mc.player;
            if (when.index() == 0) {
                if (!falling(p) || p.fallDistance < fall.get()) {
                    note = "ready";
                    return;
                }
            } else {
                if (p.onGround() || !mc.options.keyJump.isDown()) {
                    note = "ready";
                    return;
                }
            }
            int slot = Inv.hotbar(Inv.is(Items.WIND_CHARGE));
            if (slot < 0) {
                note = "no wind charge";
                return;
            }
            if (Phantom.miss()) return;
            Phantom.aimDown(p);
            Inv.with(slot, () -> {
                mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
                p.swing(InteractionHand.MAIN_HAND);
            });
            wait = cooldown.i();
            used++;
            note = "launched " + used;
        }

        @Override
        public String info() {
            return note;
        }
    }

    // ------------------------------------------------------------------ Right Click Shield

    public static final class RightClickShield extends Module {
        private final Setting.Bool nearby = add(new Setting.Bool("When players are near",
                "Raises the shield while a player is inside the range below.", true));
        private final Setting.Bool arrows = add(new Setting.Bool("When arrows are coming",
                "Raises the shield at arrows or tridents heading at you.", true));
        private final Setting.Num range = add(new Setting.Num("Range",
                "How close a player has to be for the shield to go up.", 6, 3, 24, 1, " blocks")).showIf(() -> nearby.on());

        private int previous = -1;
        private boolean raised;
        private String note = "down";

        public RightClickShield() {
            super("rightclickshield", "Right Click Shield",
                    "Keeps a shield up by itself when someone is close or an arrow is on the way, so you never get caught with it down.",
                    Category.COMBAT);
            alias("rightclip", "autoshield", "shieldup");
        }

        @Override
        protected void onDisable() {
            lower();
            note = "off";
        }

        private void lower() {
            Minecraft mc = Minecraft.getInstance();
            if (raised) {
                mc.options.keyUse.setDown(false);
                raised = false;
            }
            if (previous >= 0 && previous < 9 && mc.player != null) {
                mc.player.getInventory().setSelectedSlot(previous);
                previous = -1;
            }
        }

        private boolean danger(Minecraft mc) {
            Player p = mc.player;
            if (nearby.on()) {
                for (Player other : mc.level.players()) {
                    if (other == p || !other.isAlive() || other.isSpectator()) continue;
                    if (p.distanceToSqr(other) <= range.get() * range.get()) return true;
                }
            }
            if (arrows.on()) {
                for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(10.0))) {
                    if (!(e instanceof Projectile arrow)) continue;
                    Vec3 v = arrow.getDeltaMovement();
                    if (v.lengthSqr() < 0.05) continue;
                    Vec3 to = p.position().add(0, p.getBbHeight() * 0.5, 0).subtract(arrow.position());
                    if (to.lengthSqr() < 1.0e-4) return true;
                    if (v.normalize().dot(to.normalize()) > 0.55) return true;
                }
            }
            return false;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) {
                lower();
                return;
            }
            Player p = mc.player;
            if (p.isUsingItem() && !p.isBlocking()) {
                return;
            }
            if (!danger(mc)) {
                if (raised) {
                    lower();
                    note = "down";
                }
                return;
            }
            if (Inv.offhand(Inv.is(Items.SHIELD))) {
                mc.options.keyUse.setDown(true);
                raised = true;
                note = "up";
                return;
            }
            int slot = Inv.hotbar(Inv.is(Items.SHIELD));
            if (slot < 0) {
                note = "no shield";
                return;
            }
            if (Inv.selected() != slot) {
                if (previous < 0) previous = Inv.selected();
                Inv.select(slot);
            }
            mc.options.keyUse.setDown(true);
            raised = true;
            note = "up";
        }

        @Override
        public String info() {
            return note;
        }
    }

    // ------------------------------------------------------------------ Auto Stun

    public static final class AutoStun extends Module {
        private final Setting.Num reach = add(new Setting.Num("Reach",
                "How close the target has to be to swing the axe.", 4.4, 2.5, 6, 0.1, " blocks"));
        private final Setting.Bool blockingOnly = add(new Setting.Bool("Only while they block",
                "Only swap when the target actually has a shield up.", true));
        private final Setting.Bool swapBack = add(new Setting.Bool("Swap back",
                "Returns to the sword, or the slot you came from, straight after the axe hit.", true));
        private final Setting.Num wait = add(new Setting.Num("Wait between hits",
                "Ticks between two stun attempts.", 10, 4, 40, 1, " ticks"));

        private int cooldown;
        private String note = "waiting";

        public AutoStun() {
            super("autostun", "Auto Stun",
                    "Hits a raised shield with an axe to disable it, then puts the sword back in your hand for the follow-up.",
                    Category.COMBAT);
            alias("stun", "axestun");
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            Player p = mc.player;
            Player target = nearestPlayer(mc, reach.get());
            if (target == null) {
                note = "waiting";
                return;
            }
            if (blockingOnly.on() && !target.isBlocking()) {
                note = "they are not blocking";
                return;
            }
            if (p.getAttackStrengthScale(0.5f) < 0.9f) {
                note = "waiting for the swing";
                return;
            }
            if (Phantom.miss()) return;
            int axeSlot = Inv.hotbar(Clutch::axe);
            if (axeSlot < 0) {
                note = "no axe";
                return;
            }
            int swordSlot = Inv.hotbar(Clutch::sword);
            int from = Inv.selected();
            Inv.select(axeSlot);
            mc.gameMode.attack(p, target);
            p.swing(InteractionHand.MAIN_HAND);
            if (swapBack.on() && swordSlot >= 0) Inv.select(swordSlot);
            else if (swapBack.on()) Inv.select(from);
            cooldown = wait.i();
            note = "stunned " + target.getName().getString();
        }

        @Override
        public String info() {
            return note;
        }
    }

    // ------------------------------------------------------------------ Auto Drain

    public static final class AutoDrain extends Module {
        private final Setting.Num radius = add(new Setting.Num("Radius",
                "How far around you it looks for water to scoop up.", 4, 2, 8, 1, " blocks"));
        private final Setting.Num vertical = add(new Setting.Num("Height",
                "How far above and below you it looks.", 2, 1, 4, 1, " blocks"));
        private final Setting.Bool lava = add(new Setting.Bool("Lava too",
                "Also scoop lava. Only do this where you can survive the bucket going wrong.", false));
        private final Setting.Bool holes = add(new Setting.Bool("Only water in holes",
                "Only drains water that is boxed in, which is the water that blocks a crystal or an anchor.", true));
        private final Setting.Num delay = add(new Setting.Num("Between scoops",
                "Ticks between two buckets.", 6, 2, 30, 1, " ticks"));

        private int cooldown;
        private int drained;
        private String note = "nothing to drain";

        public AutoDrain() {
            super("autodrain", "Auto Drain",
                    "Scoops water out of the holes around you so crystals and anchors can be placed, and stops you fighting in a pool.",
                    Category.WORLD);
            alias("drain", "waterdrain");
        }

        @Override
        protected void onDisable() {
            note = "off";
            cooldown = 0;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            Player p = mc.player;
            int bucket = Inv.hotbar(Inv.is(Items.BUCKET));
            if (bucket < 0) {
                note = "no empty bucket";
                return;
            }
            BlockPos found = find(mc);
            if (found == null) {
                note = "nothing to drain";
                return;
            }
            Phantom.aim(p, Vec3.atCenterOf(found));
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(found), Direction.UP, found, false);
            Inv.with(bucket, () -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit));
            drained++;
            cooldown = delay.i();
            note = "scooped " + drained;
        }

        private BlockPos find(Minecraft mc) {
            Player p = mc.player;
            BlockPos base = p.blockPosition();
            int r = radius.i();
            int v = vertical.i();
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(base.offset(-r, -v, -r), base.offset(r, v, r))) {
                if (pos.equals(base)) continue;
                BlockState state = mc.level.getBlockState(pos);
                boolean water = state.is(Blocks.WATER);
                boolean hot = lava.on() && state.is(Blocks.LAVA);
                if (!water && !hot) continue;
                if (!state.getFluidState().isSource()) continue;
                if (holes.on() && !boxed(mc, pos)) continue;
                double d = Build.distTo(pos);
                if (d > 4.3 || d >= bestD) continue;
                bestD = d;
                best = pos.immutable();
            }
            return best;
        }

        private boolean boxed(Minecraft mc, BlockPos pos) {
            if (!Build.solid(pos.below())) return false;
            int walls = 0;
            for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                if (Build.solid(pos.relative(d))) walls++;
            }
            return walls >= 3;
        }

        @Override
        public String info() {
            return note;
        }
    }
}
