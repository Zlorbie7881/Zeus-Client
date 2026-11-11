package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;

public final class Movement {
    private Movement() {}

    public static final class Flight extends Module {
        private final Setting.Num speed = add(new Setting.Num("Speed", "Sideways speed. 1x is about creative flight.", 2.0, 0.5, 10.0, 0.5, "x"));
        private final Setting.Num vertical = add(new Setting.Num("Up and down speed", "How fast jump and sneak move you.", 1.0, 0.2, 5.0, 0.1, "x"));
        private final Setting.Bool hover = add(new Setting.Bool("Hold position", "Stay in the air when you let go of every key. Off: you slowly sink.", true));

        public Flight() {
            super("flight", "Flight", "Fly with your movement keys, jump to rise and sneak to sink.", Category.MOVEMENT);
            airborne();
        }

        @Override
        protected void onTick(Minecraft mc) {
            var p = mc.player;
            if (p == null || mc.screen != null || p.isPassenger() || p.isFallFlying()) return;
            double sp = 0.55 * speed.get();
            double f = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
            double l = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
            double yaw = Math.toRadians(p.getYRot());
            double dx = -Math.sin(yaw) * f + Math.cos(yaw) * l;
            double dz = Math.cos(yaw) * f + Math.sin(yaw) * l;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 1e-6) {
                dx = dx / len * sp;
                dz = dz / len * sp;
            } else {
                dx = 0;
                dz = 0;
            }
            double dy = hover.on() ? 0 : -0.04;
            double vs = 0.42 * vertical.get();
            if (mc.options.keyJump.isDown()) dy = vs;
            else if (mc.options.keyShift.isDown()) dy = -vs;
            p.setDeltaMovement(dx, dy, dz);
            p.fallDistance = 0;
        }

        @Override
        public String info() {
            return speed.text();
        }
    }

    public static final class Speed extends Module {
        private final Setting.Num factor = add(new Setting.Num("Speed", "How much faster you run than normal.", 2.0, 1.1, 8.0, 0.1, "x"));

        public Speed() {
            super("speed", "Speed", "Run faster.", Category.MOVEMENT);
            factor.onChange(this::apply);
        }

        private void apply() {
            if (enabled()) Env.attribute("movement_speed", "speed", factor.get() - 1.0, "add_multiplied_total");
        }

        @Override
        protected void onEnable() {
            apply();
        }

        @Override
        protected void onDisable() {
            Env.attributeOff("movement_speed", "speed");
        }

        @Override
        protected void cleanSlate() {
            onDisable();
        }

        @Override
        public String info() {
            return factor.text();
        }
    }

    public static final class HighJump extends Module {
        private final Setting.Num power = add(new Setting.Num("Jump power", "1x is a normal jump. 3x is about a nine block jump.", 2.5, 1.2, 10.0, 0.1, "x"));

        public HighJump() {
            super("highjump", "High Jump", "Jump much higher.", Category.MOVEMENT);
            power.onChange(this::apply);
        }

        private void apply() {
            if (enabled()) Env.attribute("jump_strength", "jump", power.get() - 1.0, "add_multiplied_total");
        }

        @Override
        protected void onEnable() {
            apply();
        }

        @Override
        protected void onDisable() {
            Env.attributeOff("jump_strength", "jump");
        }

        @Override
        protected void cleanSlate() {
            onDisable();
        }

        @Override
        public String info() {
            return power.text();
        }
    }

    public static final class Step extends Module {
        private final Setting.Num height = add(new Setting.Num("Step height", "How tall a block you can walk up without jumping. Normal is 0.6.", 2.0, 1.0, 8.0, 0.5, " blocks"));

        public Step() {
            super("step", "Step", "Walk up blocks without jumping.", Category.MOVEMENT);
            height.onChange(this::apply);
        }

        private void apply() {
            if (enabled()) Env.attribute("step_height", "step", height.get() - 0.6, "add_value");
        }

        @Override
        protected void onEnable() {
            apply();
        }

        @Override
        protected void onDisable() {
            Env.attributeOff("step_height", "step");
        }

        @Override
        protected void cleanSlate() {
            onDisable();
        }

        @Override
        public String info() {
            return height.text();
        }
    }

    public static final class NoFall extends Module {
        private static volatile boolean on;
        private static volatile boolean hookSeen;
        private static volatile boolean rewriteBroken;
        private final Setting.Num distance = add(new Setting.Num("From a fall of", "How far you have fallen before the flag is set. Damage starts above 3 blocks.", 2.0, 1.0, 3.0, 0.25, " blocks"));
        private final Setting.Bool water = add(new Setting.Bool("Skip over water", "Do nothing while you are landing in water (it does not hurt anyway).", true));
        private static double from = 2.0;
        private static boolean waterSkip = true;
        private int ticks;

        public NoFall() {
            super("nofall", "No Fall", "Fall from any height without damage.", Category.MOVEMENT);
            distance.onChange(() -> from = distance.get());
            water.onChange(() -> waterSkip = water.on());
        }

        public static void ping() {
            hookSeen = true;
        }

        public static void rewrite(net.minecraft.network.protocol.game.ServerboundMovePlayerPacket packet) {
            if (!on || rewriteBroken) return;
            Minecraft mc = Minecraft.getInstance();
            var p = mc.player;
            if (p == null || p.onGround() || p.isFallFlying() || p.getAbilities().flying || p.isInWater() && waterSkip || p.isPassenger()) return;
            if (p.fallDistance < from) return;
            try {
                ((com.zlorbie788.zeus.mixin.MovePacketAccessor) (Object) packet).zeus$onGround(true);
            } catch (ClassCastException | LinkageError e) {
                rewriteBroken = true;
            }
        }

        @Override
        protected void onEnable() {
            on = true;
            from = distance.get();
            waterSkip = water.on();
            hookSeen = false;
            rewriteBroken = false;
            ticks = 0;
        }

        @Override
        protected void onDisable() {
            on = false;
        }

        @Override
        protected void onTick(Minecraft mc) {
            var p = mc.player;
            var net = mc.getConnection();
            ticks++;
            if (p == null || net == null || p.onGround() || p.isFallFlying() || p.getAbilities().flying || p.isPassenger()) return;
            boolean backup = ticks > 40 && (!hookSeen || rewriteBroken);
            if (backup && p.fallDistance > from && p.getDeltaMovement().y < 0 && !(p.isInWater() && water.on())) {
                net.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly(true, p.horizontalCollision));
                p.fallDistance = 0;
            }
        }

        @Override
        public String info() {
            return ticks > 40 && (!hookSeen || rewriteBroken) ? "backup" : null;
        }
    }

    public static final class AntiHunger extends Module {
        private static volatile boolean on;
        private static volatile boolean dropSprint = true;
        private static volatile boolean flag = true;
        private final Setting.Bool sprint = add(new Setting.Bool("Do not tell the server about sprinting", "The sprint start and stop messages are not sent, so sprinting costs no hunger.", true));
        private final Setting.Bool ground = add(new Setting.Bool("Send \"in the air\" while on the ground", "The ground flag of your movement is sent as false while you are not falling. Hunger from walking and jumping drops.", true));

        public AntiHunger() {
            super("antihunger", "Anti Hunger", "Sprint and jump without losing food.", Category.MOVEMENT);
            sprint.onChange(() -> dropSprint = sprint.on());
            ground.onChange(() -> flag = ground.on());
        }

        @Override
        protected void onEnable() {
            on = true;
            dropSprint = sprint.on();
            flag = ground.on();
        }

        @Override
        protected void onDisable() {
            on = false;
        }

        public static boolean drops(Object packet) {
            if (!on || !dropSprint) return false;
            if (packet instanceof net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket c) {
                String a = c.getAction().name();
                return a.equals("START_SPRINTING") || a.equals("STOP_SPRINTING");
            }
            return false;
        }

        public static void rewrite(net.minecraft.network.protocol.game.ServerboundMovePlayerPacket packet) {
            if (!on || !flag) return;
            Minecraft mc = Minecraft.getInstance();
            var p = mc.player;
            if (p == null || p.fallDistance > 0.0 || p.isFallFlying() || p.getAbilities().flying || p.isInWater() || p.isPassenger()) return;
            if (mc.gameMode != null && mc.gameMode.isDestroying()) return;
            try {
                ((com.zlorbie788.zeus.mixin.MovePacketAccessor) (Object) packet).zeus$onGround(false);
            } catch (ClassCastException | LinkageError ignored) {
            }
        }

        @Override
        public String info() {
            return null;
        }
    }

    public static final class LowGravity extends Module {
        private final Setting.Num gravity = add(new Setting.Num("Gravity", "How strong gravity is. 1x is normal, 0.1x is almost floating.", 0.3, 0.02, 1.0, 0.02, "x"));

        public LowGravity() {
            super("lowgravity", "Low Gravity", "Fall slowly and jump far, like on the moon.", Category.MOVEMENT);
            gravity.onChange(this::apply);
        }

        private void apply() {
            if (enabled()) Env.attribute("gravity", "gravity", gravity.get() - 1.0, "add_multiplied_total");
        }

        @Override
        protected void onEnable() {
            apply();
        }

        @Override
        protected void onDisable() {
            Env.attributeOff("gravity", "gravity");
        }

        @Override
        protected void cleanSlate() {
            onDisable();
        }

        @Override
        public String info() {
            return gravity.text();
        }
    }

    public static final class AutoSprint extends Module {
        public AutoSprint() {
            super("autosprint", "Auto Sprint", "Sprints for you whenever you walk forward.", Category.MOVEMENT);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.screen != null) return;
            if (mc.options.keyUp.isDown() && !mc.options.keyShift.isDown()) mc.options.keySprint.setDown(true);
        }
    }
}
