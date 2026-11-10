package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Build;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Movement modules that work by steering your own player, on your side of the game. */
public final class Motion {
    private Motion() {}

    private static boolean moving(Minecraft mc) {
        return mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
    }

    private static boolean ready(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.screen == null;
    }

    /** Jump again in mid-air. */
    public static final class AirJump extends Module {
        private final Setting.Num power = add(new Setting.Num("Power", "How strong each jump is.", 0.42, 0.2, 1.0, 0.02, ""));
        private final Setting.Num limit = add(new Setting.Num("Jumps in a row", "How many times you can jump before you land (0 = no limit).", 0, 0, 20, 1, ""));
        private boolean was;
        private int used;

        public AirJump() {
            super("airjump", "Air Jump", "Jump again in mid-air.", Category.MOVEMENT);
            airborne();
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            boolean down = mc.options.keyJump.isDown();
            if (mc.player.onGround()) used = 0;
            else if (down && !was && (limit.i() == 0 || used < limit.i())) {
                Vec3 v = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(v.x, power.get(), v.z);
                mc.player.fallDistance = 0;
                used++;
            }
            was = down;
        }
    }

    /** Lifts you back up before you fall out of the world. */
    public static final class AntiVoid extends Module {
        public AntiVoid() {
            super("antivoid", "Anti Void", "Catches you and lifts you up before you fall out of the world.", Category.MOVEMENT);
            airborne();
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            if (mc.player.getY() < mc.level.getMinY() + 4 && mc.player.getDeltaMovement().y < 0.1) {
                mc.player.setDeltaMovement(0, 0.9, 0);
                mc.player.fallDistance = 0;
            }
        }
    }

    /** Hops over blocks and across gaps. */
    public static final class AutoJump extends Module {
        private final Setting.Bool edges = add(new Setting.Bool("Jump at edges", "Also jump when you are about to run off an edge (parkour).", false));

        public AutoJump() {
            super("autojump", "Auto Jump", "Jumps by itself over blocks in your way.", Category.MOVEMENT);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc) || !mc.player.onGround() || !moving(mc) || mc.player.isShiftKeyDown()) return;
            Vec3 v = mc.player.getDeltaMovement();
            boolean jump = mc.player.horizontalCollision;
            if (!jump && edges.on() && mc.level.noCollision(mc.player, mc.player.getBoundingBox().move(v.x * 2, -0.65, v.z * 2))) jump = true;
            if (jump) mc.player.setDeltaMovement(v.x, 0.42, v.z);
        }
    }

    /** Holds the forward key for you. */
    public static final class AutoWalk extends Module {
        public AutoWalk() {
            super("autowalk", "Auto Walk", "Walks forward for you.", Category.MOVEMENT);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (ready(mc)) mc.options.keyUp.setDown(true);
        }

        @Override
        protected void onDisable() {
            Minecraft.getInstance().options.keyUp.setDown(false);
        }
    }

    /** Climbs ladders and vines quickly. */
    public static final class FastClimb extends Module {
        private final Setting.Num speed = add(new Setting.Num("Speed", "Blocks per tick going up.", 0.3, 0.15, 1.0, 0.05, ""));

        public FastClimb() {
            super("fastclimb", "Fast Climb", "Climb ladders and vines faster.", Category.MOVEMENT);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc) || !mc.player.onClimbable()) return;
            if (mc.options.keyJump.isDown() || mc.player.horizontalCollision) {
                Vec3 v = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(v.x, speed.get(), v.z);
            }
        }
    }

    /** Climbs walls. */
    public static final class Spider extends Module {
        private final Setting.Num speed = add(new Setting.Num("Speed", "Blocks per tick up a wall.", 0.2, 0.1, 0.6, 0.05, ""));

        public Spider() {
            super("spider", "Spider", "Climb walls by walking into them.", Category.MOVEMENT);
            airborne();
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc) || !mc.player.horizontalCollision || !moving(mc)) return;
            Vec3 v = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(v.x, speed.get(), v.z);
            mc.player.fallDistance = 0;
        }
    }

    /**
     * Walks on water. The game already has a rule for standing on a fluid (it is how striders walk on lava): an entity can
     * answer "yes" when asked whether it can stand on a fluid. Water Walk (once called Jesus) answers yes for your own player, so the water is a real
     * floor, with normal walking, jumping and sprinting. Sneaking sinks you. If you are already under the surface, a gentle
     * push lifts you out. If the game's rule cannot be reached, the old way (a small steady lift in the water) takes over.
     */
    public static final class Jesus extends Module {
        private static volatile boolean on;
        private static volatile boolean lavaToo;
        private static volatile boolean hooked;
        private final Setting.Bool lava = add(new Setting.Bool("Lava too", "Also stand on lava. Careful: lava still burns you if you fall in.", false));
        private final Setting.Bool dip = add(new Setting.Bool("Sneak to sink", "Hold sneak to go down into the water.", true));
        private int ticks;

        public Jesus() {
            super("jesus", "Water Walk", "Walk on top of water as if it were ground. Also known as Jesus.", Category.MOVEMENT);
            alias("Jesus", "Jesus Walk", "Waterwalk", "Liquid Walk");
            airborne();
            lava.onChange(() -> lavaToo = lava.on());
        }

        /** Called from the game's "can this entity stand on this fluid" question. */
        public static boolean wants(Object entity, net.minecraft.world.level.material.FluidState fluid) {
            if (!on) return false;
            Minecraft mc = Minecraft.getInstance();
            if (entity != mc.player || mc.player == null) return false;
            hooked = true;
            Jesus self = com.zlorbie788.zeus.core.Zeus.get(Jesus.class);
            if (self != null && self.dip.on() && mc.player.isShiftKeyDown()) return false;
            if (fluid == null) return false;
            if (fluid.is(net.minecraft.tags.FluidTags.WATER)) return true;
            return lavaToo && fluid.is(net.minecraft.tags.FluidTags.LAVA);
        }

        @Override
        protected void onEnable() {
            on = true;
            lavaToo = lava.on();
            hooked = false;
            ticks = 0;
        }

        @Override
        protected void onDisable() {
            on = false;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            ticks++;
            boolean sneaking = dip.on() && mc.options.keyShift.isDown();
            if (sneaking) return;
            boolean in = mc.player.isInWater() || (lava.on() && mc.player.isInLava());
            if (!in) return;
            Vec3 v = mc.player.getDeltaMovement();
            if (hooked) {
                // under the surface: rise until the water holds you up
                if (v.y < 0.18) mc.player.setDeltaMovement(v.x, Math.min(0.18, v.y + 0.12), v.z);
            } else if (ticks > 40 && !mc.options.keyJump.isDown()) {
                // the game's rule is not reachable: hold yourself at the surface instead
                mc.player.setDeltaMovement(v.x, 0.11, v.z);
                mc.player.fallDistance = 0;
            }
        }

        @Override
        public String info() {
            return hooked ? null : ticks > 40 ? "fallback" : null;
        }
    }

    /** A long, flat jump. */
    public static final class LongJump extends Module {
        private final Setting.Num boost = add(new Setting.Num("Boost", "How far the jump carries you forward.", 1.6, 1.1, 4.0, 0.1, "x"));
        private boolean was;

        public LongJump() {
            super("longjump", "Long Jump", "Jump much further forward.", Category.MOVEMENT);
            airborne();
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            boolean up = !mc.player.onGround() && mc.player.getDeltaMovement().y > 0.3;
            if (up && !was && moving(mc)) {
                Vec3 v = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(v.x * boost.get(), v.y, v.z * boost.get());
            }
            was = up;
        }
    }

    /**
     * Never falls off an edge. The game has one rule for this (it is what sneaking does: a player who "stays on the ground
     * surface" is stopped at the edge of a block), and Safe Walk asks the game to apply it without sneaking: the pose, the
     * speed and the camera stay as they are. If that rule cannot be reached, it falls back to steering your speed at an edge.
     */
    public static final class SafeWalk extends Module {
        private static volatile boolean on;
        private static volatile boolean hooked;
        private int ticks;

        public SafeWalk() {
            super("safewalk", "Safe Walk", "You cannot walk off the edge of a block, without sneaking.", Category.MOVEMENT);
        }

        /** Called from the game's "is this player staying on the ground surface" question. */
        public static boolean wants(Object entity) {
            if (!on) return false;
            Minecraft mc = Minecraft.getInstance();
            if (entity != mc.player || mc.player == null) return false;
            hooked = true;
            return true;
        }

        @Override
        protected void onEnable() {
            on = true;
            hooked = false;
            ticks = 0;
        }

        @Override
        protected void onDisable() {
            on = false;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc) || hooked || ++ticks < 30 || !mc.player.onGround()) return;
            // fallback: the game's rule was never asked, so keep the next step on the block
            Vec3 v = mc.player.getDeltaMovement();
            if (v.y > 0.05) return;
            double dx = v.x * 2.2 + Math.signum(v.x) * 0.1;
            double dz = v.z * 2.2 + Math.signum(v.z) * 0.1;
            var box = mc.player.getBoundingBox();
            if (mc.level.noCollision(mc.player, box.move(dx, -0.61, dz))) {
                boolean keepX = !mc.level.noCollision(mc.player, box.move(dx, -0.61, 0));
                boolean keepZ = !mc.level.noCollision(mc.player, box.move(0, -0.61, dz));
                mc.player.setDeltaMovement(keepX ? v.x : 0, v.y, keepZ ? v.z : 0);
            }
        }

        @Override
        public String info() {
            return !hooked && ticks >= 30 ? "fallback" : null;
        }
    }

    /** Snaps you back down when you step off a ledge. */
    public static final class ReverseStep extends Module {
        private final Setting.Num height = add(new Setting.Num("Height", "Furthest drop that is cut short.", 2, 1, 4, 0.5, " blocks"));

        public ReverseStep() {
            super("reversestep", "Reverse Step", "Drops you to the ground at once when you step off a ledge.", Category.MOVEMENT);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc) || mc.player.onGround() || mc.player.isInWater() || mc.player.isFallFlying()) return;
            Vec3 v = mc.player.getDeltaMovement();
            if (v.y > 0 || v.y < -1.0) return;
            if (!mc.level.noCollision(mc.player, mc.player.getBoundingBox().move(0, -height.get(), 0))) {
                mc.player.setDeltaMovement(v.x, -1.0, v.z);
            }
        }
    }

    /** Pushes you forward faster while gliding. */
    public static final class ElytraBoost extends Module {
        private final Setting.Num power = add(new Setting.Num("Boost", "How hard each tick pushes while you hold forward.", 0.05, 0.01, 0.2, 0.01, ""));
        private final Setting.Num cap = add(new Setting.Num("Top speed", "Blocks per tick you will not go faster than.", 2.5, 1.0, 6.0, 0.25, ""));

        public ElytraBoost() {
            super("elytraboost", "Elytra Boost", "Hold forward while gliding to speed up.", Category.MOVEMENT);
            airborne();
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc) || !mc.player.isFallFlying() || !mc.options.keyUp.isDown()) return;
            Vec3 v = mc.player.getDeltaMovement();
            if (v.length() > cap.get()) return;
            mc.player.setDeltaMovement(v.add(mc.player.getLookAngle().scale(power.get())));
        }
    }

    /** Sneaks all the time. */
    public static final class Sneak extends Module {
        public Sneak() {
            super("sneak", "Sneak", "Holds sneak for you.", Category.MOVEMENT);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (ready(mc)) mc.options.keyShift.setDown(true);
        }

        @Override
        protected void onDisable() {
            Minecraft.getInstance().options.keyShift.setDown(false);
        }
    }

    /** Builds a bridge under your feet. */
    public static final class Scaffold extends Module {
        private final Setting.Bool tower = add(new Setting.Bool("Tower", "Hold jump to build straight up.", true));
        private final Setting.Num expand = add(new Setting.Num("Expand", "Also fills this many blocks ahead.", 0, 0, 4, 1, " blocks"));
        private final Setting.Bool swing = add(new Setting.Bool("Swing hand", "Show the hand swing for every block.", false));

        public Scaffold() {
            super("scaffold", "Scaffold", "Places blocks under your feet as you walk.", Category.MOVEMENT);
        }

        private static boolean plain(ItemStack s) {
            return Defense.wallBlock(s);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc) || mc.gameMode == null) return;
            BlockPos feet = BlockPos.containing(mc.player.getX(), mc.player.getY() - 0.5, mc.player.getZ());
            if (tower.on() && mc.options.keyJump.isDown() && !moving(mc) && !Build.solid(feet.below()) && Build.solid(feet)) {
                Vec3 v = mc.player.getDeltaMovement();
                if (v.y < 0.2 && mc.player.onGround()) mc.player.setDeltaMovement(v.x, 0.42, v.z);
            }
            Vec3 look = mc.player.getLookAngle();
            double fx = Math.abs(look.x) > Math.abs(look.z) ? Math.signum(look.x) : 0;
            double fz = fx == 0 ? Math.signum(look.z) : 0;
            for (int i = 0; i <= expand.i(); i++) {
                BlockPos p = feet.offset((int) (fx * i), 0, (int) (fz * i));
                if (Build.replaceable(p) && !Build.solid(p)) {
                    if (Build.place(p, Scaffold::plain, swing.on(), Build.REACH)) return;
                }
            }
        }
    }

    /** Jumps by itself at the edge of a block, so you can run across gaps without timing the jump. */
    public static final class Parkour extends Module {
        private final Setting.Num ahead = add(new Setting.Num("Look ahead", "How far in front of your feet it looks for the edge.", 0.35, 0.2, 0.9, 0.05, " blocks"));
        private int cooldown;

        public Parkour() {
            super("parkour", "Parkour", "Jumps for you at the edge of a block while you run.", Category.MOVEMENT);
            airborne();
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            if (cooldown > 0) cooldown--;
            var p = mc.player;
            if (!p.onGround() || p.isShiftKeyDown() || p.isInWater() || p.onClimbable() || cooldown > 0 || !moving(mc)) return;
            Vec3 v = p.getDeltaMovement();
            double speed = Math.sqrt(v.x * v.x + v.z * v.z);
            if (speed < 0.05) return;
            double look = ahead.get() + speed;
            double dx = v.x / speed * look;
            double dz = v.z / speed * look;
            var box = p.getBoundingBox();
            // nothing under the next step and nothing to stand on one block down
            if (mc.level.noCollision(p, box.move(dx, -0.5, dz)) && mc.level.noCollision(p, box.move(dx, -1.0, dz))) {
                p.setDeltaMovement(v.x, 0.42, v.z);
                cooldown = 6;
            }
        }
    }
}
