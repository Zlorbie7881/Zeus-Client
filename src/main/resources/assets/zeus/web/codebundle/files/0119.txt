package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import java.util.List;

public final class AutomationPlus {
    private AutomationPlus() {}

    public static final class TreeBot extends Module {
        private final Setting.Num radius = add(new Setting.Num("Search radius", "How far around you to look for log blocks.", 6, 2, 12, 1, " blocks"));
        private final Setting.Bool walk = add(new Setting.Bool("Walk to tree", "Move carefully toward a nearby trunk.", true));
        private final Setting.Bool leaves = add(new Setting.Bool("Include leaves", "Reserved for future canopy cleanup; off by default.", false));
        private BlockPos target;
        private int scanCooldown;

        public TreeBot() {
            super("treebot", "Tree Bot", "Finds and harvests nearby tree logs.", Category.WORLD);
        }

        @Override protected void onEnable() { target = null; scanCooldown = 0; }
        @Override protected void onDisable() { target = null; }

        @Override protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
            if (scanCooldown-- <= 0 || target == null || !mc.level.getBlockState(target).is(BlockTags.LOGS)) {
                target = findNearest(mc);
                scanCooldown = 12;
            }
            if (target == null) return;
            Vec3 eye = mc.player.getEyePosition();
            Vec3 center = Vec3.atCenterOf(target);
            double distance = eye.distanceTo(center);
            if (distance <= 4.5) {
                mc.gameMode.startDestroyBlock(target, net.minecraft.core.Direction.UP);
                mc.gameMode.continueDestroyBlock(target, net.minecraft.core.Direction.UP);
                if (!mc.level.getBlockState(target).is(BlockTags.LOGS)) {
                    target = null;
                    scanCooldown = 2;
                }
                return;
            }
            if (walk.on() && distance < radius.get() + 4) {
                Vec3 delta = center.subtract(mc.player.position());
                double len = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
                if (len > 0.01) {
                    double speed = Math.min(0.10, 0.035 + len * 0.008);
                    Vec3 old = mc.player.getDeltaMovement();
                    mc.player.setDeltaMovement(delta.x / len * speed, old.y, delta.z / len * speed);
                }
            }
        }

        private BlockPos findNearest(Minecraft mc) {
            BlockPos origin = mc.player.blockPosition();
            int r = radius.i();
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (int x = -r; x <= r; x++) for (int y = -3; y <= 5; y++) for (int z = -r; z <= r; z++) {
                BlockPos p = origin.offset(x, y, z);
                if (!mc.level.getBlockState(p).is(BlockTags.LOGS)) continue;
                double d = mc.player.distanceToSqr(p.getX() + .5, p.getY() + .5, p.getZ() + .5);
                if (d < bestD) { bestD = d; best = p.immutable(); }
            }
            return best;
        }
    }

    static boolean isBoat(Entity e) {
        if (e == null) return false;
        String n = e.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        return n.contains("boat") || n.contains("raft");
    }

    public static final class BoatFly extends Module {
        private final Setting.Choice mode = add(new Setting.Choice("Flight mode", "Full flight sinks slowly when idle; Hover holds altitude; Glide keeps normal gravity.", 1, "Full flight", "Hover", "Glide"));
        private final Setting.Num speed = add(new Setting.Num("Horizontal speed", "Boat movement speed in blocks per tick.", 0.65, 0.15, 1.8, 0.05, " blocks/tick"));
        private final Setting.Num vertical = add(new Setting.Num("Vertical speed", "How quickly jump and sneak move the boat.", 0.35, 0.05, 0.9, 0.05, " blocks/tick"));
        private final Setting.Bool collision = add(new Setting.Bool("Collision safety", "Rejects movement that would push the boat into a solid block.", true));
        private Entity controlledBoat;
        private boolean originalNoGravity;
        private boolean originalNoPhysics;
        private String status = "Waiting for boat";

        public BoatFly() {
            super("boatfly", "Boat Fly", "Fly a boat with movement keys. Server-authoritative multiplayer movement may be corrected by the server.", Category.MOVEMENT);
        }

        @Override protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null) { status = "No world"; restoreBoat(); return; }
            if (mc.screen != null) { status = "Paused: screen open"; restoreBoat(); return; }
            Entity boat = mc.player.getVehicle();
            if (!isBoat(boat)) {
                status = "Mount a boat";
                restoreBoat();
                return;
            }
            if (controlledBoat != boat) {
                restoreBoat();
                controlledBoat = boat;
                originalNoGravity = boat.isNoGravity();
                originalNoPhysics = boat.noPhysics;
            }
            boolean flight = mode.index() != 2;
            boat.setNoGravity(flight);
            double forward = (mc.options.keyUp.isDown() ? 1.0 : 0.0) - (mc.options.keyDown.isDown() ? 1.0 : 0.0);
            double strafe = (mc.options.keyLeft.isDown() ? 1.0 : 0.0) - (mc.options.keyRight.isDown() ? 1.0 : 0.0);
            double yaw = Math.toRadians(mc.player.getYRot());
            double dx = -Math.sin(yaw) * forward + Math.cos(yaw) * strafe;
            double dz = Math.cos(yaw) * forward + Math.sin(yaw) * strafe;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 1.0) { dx /= len; dz /= len; }
            dx *= speed.get();
            dz *= speed.get();

            double dy;
            if (mc.options.keyJump.isDown()) dy = vertical.get();
            else if (mc.options.keyShift.isDown()) dy = -vertical.get();
            else if (mode.index() == 0) dy = -0.035;
            else if (mode.index() == 1) dy = 0.0;
            else dy = boat.getDeltaMovement().y;

            if (collision.on()) {
                AABB box = boat.getBoundingBox();
                if (!mc.level.noCollision(boat, box.move(dx, dy, dz))) {
                    if (mc.level.noCollision(boat, box.move(dx, 0, dz))) dy = 0;
                    else { dx = 0; dz = 0; }
                    if (!mc.level.noCollision(boat, box.move(dx, dy, dz))) dy = 0;
                }
            }
            Vec3 step = new Vec3(dx, dy, dz);
            boat.move(net.minecraft.world.entity.MoverType.SELF, step);
            boat.setDeltaMovement(0.0, mode.index() == 2 ? dy : 0.0, 0.0);
            status = mode.value() + " · " + String.format(java.util.Locale.ROOT, "%.2f m/t", speed.get());
        }

        private void restoreBoat() {
            if (controlledBoat != null) {
                controlledBoat.setNoGravity(originalNoGravity);
                controlledBoat.noPhysics = originalNoPhysics;
                controlledBoat = null;
            }
        }

        @Override protected void onDisable() { restoreBoat(); status = "Off"; }
        @Override public String info() { return status; }
    }

    public static final class BoatNoClip extends Module {

        private static final double STEP = 0.9;
        private static final int MAX_PACKETS = 12;
        private static final double MAX_PER_TICK = 6.0;
        private static final double MAX_LEAD = 12.0;

        private final Setting.Num speed = add(new Setting.Num("Horizontal speed", "How far the boat travels each tick in open air.", 1.2, 0.2, 6.0, 0.1, " blocks/tick"));
        private final Setting.Num insideSpeed = add(new Setting.Num("Speed inside blocks", "Slower travel while the boat is inside a block, which keeps the server from refusing the move.", 0.7, 0.2, 6.0, 0.1, " blocks/tick"));
        private final Setting.Num vertical = add(new Setting.Num("Vertical speed", "How far the boat rises or sinks each tick while you hold jump or sneak.", 0.5, 0.1, 3.0, 0.1, " blocks/tick"));
        private final Setting.Bool gravity = add(new Setting.Bool("Keep falling", "Still drift downward with no key held. Off floats in place, which is what you want inside a wall.", false));
        private final Setting.Bool ignoreCorrections = add(new Setting.Bool("Ignore server corrections", "Drops the 'move the vehicle back' packets a server sends while you fly. On a multiplayer server the server still owns the real position, this only hides the snap back.", true));
        private final Setting.Info status = add(new Setting.Info("Status", this::statusLines));

        private AbstractBoat boat;
        private boolean oldNoPhysics;
        private boolean oldNoGravity;
        private String state = "Mount a boat";

        private volatile Vec3 serverPos;
        private static volatile BoatNoClip active;
        private static volatile boolean dropCorrections;

        public BoatNoClip() {
            super("boatnoclip", "Boat NoClip", "Drive a boat or raft through blocks and entities. Fully client side, so a multiplayer server may still pull the boat back.", Category.MOVEMENT);
        }

        public static BoatNoClip getActive() { return active; }

        public static boolean ignoresCorrections() { return dropCorrections; }

        public static boolean owns(Entity e) {
            BoatNoClip m = active;
            if (m == null || m.boat == null || m.boat != e) return false;
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null && mc.player.getVehicle() == e;
        }

        private List<String> statusLines() {
            if (!enabled()) return List.of("Switched off");
            Vec3 sp = serverPos;
            return List.of(
                    "State: " + state,
                    "Ahead of the server: " + (sp == null || boat == null ? "-" : String.format(java.util.Locale.ROOT, "%.1f", boat.position().distanceTo(sp)) + " blocks"));
        }

        @Override protected void onEnable() {
            BoatFly fly = Zeus.get(BoatFly.class);
            if (fly != null && fly.enabled()) fly.setEnabled(false);
            active = this;
            dropCorrections = ignoreCorrections.on();
            state = "Mount a boat";
        }

        @Override protected void onDisable() {
            active = null;
            dropCorrections = false;
            Minecraft mc = Minecraft.getInstance();
            release(mc);
            state = "Off";
        }

        private void release(Minecraft mc) {
            if (boat != null) {
                boat.noPhysics = oldNoPhysics;
                boat.setNoGravity(oldNoGravity);
                mirror(mc, boat, oldNoPhysics, false);
                boat = null;
            }
            serverPos = null;
        }

        @Override protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null) {
                release(mc);
                state = "No world";
                return;
            }
            if (!(mc.player.getVehicle() instanceof AbstractBoat b)) {
                release(mc);
                state = "Mount a boat";
                return;
            }
            if (boat != b) {
                release(mc);
                boat = b;
                oldNoPhysics = b.noPhysics;
                oldNoGravity = b.isNoGravity();
            }
            dropCorrections = ignoreCorrections.on();
            b.noPhysics = true;
            b.setNoGravity(!gravity.on());
            if (mc.screen != null) {
                b.setDeltaMovement(Vec3.ZERO);
                state = "Paused: screen open";
                mirror(mc, b, true, true);
                return;
            }
            state = "Active · flying through blocks";
            mirror(mc, b, true, true);
        }

        public Vec3 drive(Entity entity, Vec3 movement) {
            if (!(entity instanceof AbstractBoat b) || b != boat) return movement;
            Minecraft mc = Minecraft.getInstance();
            var player = mc.player;
            if (player == null || b.level() != mc.level) return movement;
            if (player.getVehicle() != b || b.getControllingPassenger() != player) return movement;
            b.noPhysics = true;
            b.setNoGravity(!gravity.on());
            Vec3 plan = plan(mc, b, player);
            b.setDeltaMovement(Vec3.ZERO);
            if (plan.lengthSqr() > 1e-9) report(mc, b, plan);
            return plan;
        }

        private Vec3 plan(Minecraft mc, AbstractBoat b, net.minecraft.client.player.LocalPlayer player) {
            double forward = (mc.options.keyUp.isDown() ? 1.0 : 0.0) - (mc.options.keyDown.isDown() ? 1.0 : 0.0);
            double strafe = (mc.options.keyLeft.isDown() ? 1.0 : 0.0) - (mc.options.keyRight.isDown() ? 1.0 : 0.0);
            double yaw = Math.toRadians(player.getYRot());
            double dx = -Math.sin(yaw) * forward + Math.cos(yaw) * strafe;
            double dz = Math.cos(yaw) * forward + Math.sin(yaw) * strafe;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 1e-6) { dx /= len; dz /= len; }
            double horizontal = insideBlocks(mc, b) ? insideSpeed.get() : speed.get();
            double dy;
            if (mc.options.keyJump.isDown()) dy = vertical.get();
            else if (mc.options.keyShift.isDown()) dy = -vertical.get();
            else dy = gravity.on() ? -0.08 : 0.0;
            Vec3 move = new Vec3(dx * horizontal, dy, dz * horizontal);
            double allowed = budget();
            double length = move.length();
            if (length > allowed && length > 1e-6) move = move.scale(allowed / length);
            return move;
        }

        private double budget() {
            Vec3 sp = serverPos;
            double ahead = sp == null || boat == null ? 0.0 : boat.position().distanceTo(sp);
            return Math.max(0.0, Math.min(MAX_PER_TICK, MAX_LEAD - ahead));
        }

        private void report(Minecraft mc, AbstractBoat b, Vec3 plan) {
            var connection = mc.getConnection();
            if (connection == null) return;
            double length = plan.length();
            int steps = (int) Math.ceil(length / STEP);
            steps = Math.max(1, Math.min(steps, MAX_PACKETS));
            Vec3 from = b.position();
            for (int i = 1; i < steps; i++) {
                Vec3 at = from.add(plan.scale((double) i / steps));
                connection.send(new net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket(at, b.getYRot(), b.getXRot(), b.onGround()));
            }
        }

        private static boolean insideBlocks(Minecraft mc, Entity b) {
            if (mc.level == null) return false;
            try {
                return !mc.level.noCollision(b, b.getBoundingBox().deflate(0.05));
            } catch (RuntimeException e) {
                return false;
            }
        }

        private void mirror(Minecraft mc, Entity b, boolean noPhysics, boolean place) {
            var server = mc.getSingleplayerServer();
            if (server == null || b == null) {
                serverPos = null;
                return;
            }
            final int id = b.getId();
            final double x = b.getX(), y = b.getY(), z = b.getZ();
            final float yaw = b.getYRot();
            serverPos = b.position();
            server.execute(() -> {
                for (var level : server.getAllLevels()) {
                    Entity se = level.getEntity(id);
                    if (se == null) continue;
                    se.noPhysics = noPhysics;
                    if (place) {
                        se.setPos(x, y, z);
                        se.setYRot(yaw);
                        se.setDeltaMovement(Vec3.ZERO);
                    }
                    serverPos = new Vec3(se.getX(), se.getY(), se.getZ());
                    break;
                }
            });
        }

        @Override public String info() { return state; }
    }

    public static final class ElytraWaypoint extends Module {
        private final Setting.Text x = add(new Setting.Text("Destination X", "Type the exact target X coordinate.", "0", 16));
        private final Setting.Text y = add(new Setting.Text("Destination Y", "Type the target altitude.", "120", 8));
        private final Setting.Text z = add(new Setting.Text("Destination Z", "Type the exact target Z coordinate.", "0", 16));
        private final Setting.Choice altitudeMode = add(new Setting.Choice("Altitude control", "Use the destination Y coordinate, or hold a separate fixed cruise altitude while travelling.", 0, "Destination Y", "Fixed cruise Y"));
        private final Setting.Num cruiseAltitude = add(new Setting.Num("Cruise altitude Y", "The Y level the autopilot tries to maintain when Fixed cruise Y is selected.", 120, -64, 320, 1, " Y")).showIf(() -> altitudeMode.index() == 1);
        private final Setting.Bool steering = add(new Setting.Bool("Steer automatically", "Smoothly turns your real view toward the destination while gliding.", true));
        private final Setting.Num turnRate = add(new Setting.Num("Turn rate", "Maximum yaw correction per tick.", 3.0, 0.5, 8.0, 0.5, "°/tick"));
        private final Setting.Num pitchRate = add(new Setting.Num("Pitch rate", "Maximum pitch correction per tick.", 2.0, 0.5, 6.0, 0.5, "°/tick"));
        private final Setting.Bool obstacleAvoidance = add(new Setting.Bool("Obstacle awareness", "Checks a forward corridor with centre and side rays, then chooses the clearest heading.", true));
        private final Setting.Num lookAhead = add(new Setting.Num("Look-ahead distance", "Distance used for short-range collision checks.", 18, 6, 36, 1, " blocks")).showIf(() -> obstacleAvoidance.on());
        private final Setting.Bool loadedOnly = add(new Setting.Bool("Stay in loaded chunks", "Rejects a candidate corridor if its sampled path enters an unloaded chunk.", true)).showIf(() -> obstacleAvoidance.on());
        private final Setting.Choice routeStyle = add(new Setting.Choice("Route planning", "Direct is quickest; Balanced allows gentle detours; Cautious checks wider turns.", 1, "Direct", "Balanced", "Cautious")).showIf(() -> obstacleAvoidance.on());
        private final Setting.Num terrainClearance = add(new Setting.Num("Terrain clearance", "Minimum target height above terrain sampled ahead of the player.", 12, 4, 40, 1, " blocks"));
        private final Setting.Bool landingAssist = add(new Setting.Bool("Landing approach", "Reduces pitch and speed changes near the destination, using its configured Y as the landing height.", true));
        private final Setting.Bool rocketAssist = add(new Setting.Bool("Automatic fireworks", "Uses a hotbar rocket when speed is low, the route is clear and the destination is far away.", true));
        private final Setting.Num rocketInterval = add(new Setting.Num("Rocket interval", "Minimum delay between automatic fireworks.", 8, 3, 20, 1, " seconds")).showIf(() -> rocketAssist.on());
        private final Setting.Bool showHeading = add(new Setting.Bool("Show heading HUD", "Show bearing, remaining distance, speed and altitude difference.", true));
        private double distance;
        private double bearing;
        private int rocketCooldown;
        private String status = "Waiting for Elytra";

        public ElytraWaypoint() {
            super("elytrawaypoint", "Advanced Elytra Autopilot", "Navigates to coordinates with terrain clearance, multi-angle obstacle avoidance, landing approach and optional firework assist.", Category.MOVEMENT);
            hudCard();
            add(new Setting.Action("Set destination from crosshair", "Uses the block position currently under your crosshair as the destination.", this::setDestinationFromCrosshair));
            add(new Setting.Action("Emergency stop", "Immediately stops automatic steering and rocket use.", () -> setEnabled(false)));
        }

        private static double coordinate(Setting.Text field, double fallback) {
            try {
                double value = Double.parseDouble(field.get().trim());
                return Double.isFinite(value) ? Math.max(-30_000_000, Math.min(30_000_000, value)) : fallback;
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }

        private void setDestinationFromCrosshair() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)) {
                com.zlorbie788.zeus.core.Zeus.toast("Look at a block first", false);
                return;
            }
            BlockPos pos = hit.getBlockPos();
            x.set(Integer.toString(pos.getX()));
            y.set(Integer.toString(pos.getY() + 1));
            z.set(Integer.toString(pos.getZ()));
            com.zlorbie788.zeus.core.Zeus.toast("Elytra destination set", true);
        }

        @Override protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null) { status = "No world"; return; }
            if (rocketCooldown > 0) rocketCooldown--;
            double targetX = coordinate(x, mc.player.getX());
            double targetY = altitudeMode.index() == 1 ? cruiseAltitude.get() : coordinate(y, mc.player.getY());
            double targetZ = coordinate(z, mc.player.getZ());
            double dx = targetX - mc.player.getX();
            double dz = targetZ - mc.player.getZ();
            double dy = targetY - mc.player.getY();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            distance = Math.sqrt(horizontal * horizontal + dy * dy);
            bearing = (Math.toDegrees(Math.atan2(-dx, dz)) + 360.0) % 360.0;
            if (distance <= 6.0) {
                status = "Destination reached";
                setEnabled(false);
                com.zlorbie788.zeus.core.Zeus.toast("Elytra waypoint reached", true);
                return;
            }
            if (mc.screen != null) { status = "Paused: screen open"; return; }
            if (!mc.player.isFallFlying()) { status = "Waiting for Elytra glide"; return; }
            status = "Navigating";
            if (!steering.on()) { status = "Guidance only"; return; }

            float desiredYaw = (float) bearing;
            float desiredPitch = (float) Math.max(-30.0, Math.min(20.0, -Math.toDegrees(Math.atan2(dy, Math.max(1.0, horizontal)))));
            if (landingAssist.on() && horizontal < 36 && Math.abs(dy) < 18) {
                desiredPitch = (float) Math.max(-8.0, Math.min(12.0, desiredPitch * 0.55));
            }
            int terrainY = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    mc.player.getBlockX() + (int) Math.round(-Math.sin(Math.toRadians(desiredYaw)) * 12),
                    mc.player.getBlockZ() + (int) Math.round(Math.cos(Math.toRadians(desiredYaw)) * 12));
            if (mc.player.getY() < terrainY + terrainClearance.get()) desiredPitch = Math.min(desiredPitch, -12f);
            if (obstacleAvoidance.on()) {
                float[] route = chooseClearHeading(mc, desiredYaw, desiredPitch);
                if (route != null) {
                    desiredYaw = route[0];
                    desiredPitch = route[1];
                } else {
                    status = "No clear corridor — manual control";
                    return;
                }
            }
            float yawDelta = wrap(desiredYaw - mc.player.getYRot());
            float pitchDelta = desiredPitch - mc.player.getXRot();
            mc.player.setYRot(mc.player.getYRot() + clamp(yawDelta, (float) turnRate.get()));
            mc.player.setXRot(Math.max(-45f, Math.min(35f, mc.player.getXRot() + clamp(pitchDelta, (float) pitchRate.get()))));

            if (rocketAssist.on() && rocketCooldown == 0 && horizontal > 90 && !status.startsWith("No clear")) {
                Vec3 velocity = mc.player.getDeltaMovement();
                double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
                double headingError = Math.abs(wrap((float) bearing - mc.player.getYRot()));
                int slot = com.zlorbie788.zeus.core.Inv.hotbar(com.zlorbie788.zeus.core.Inv.is(Items.FIREWORK_ROCKET));
                if (slot >= 0 && speed < 0.78 && headingError < 55 && mc.gameMode != null) {
                    com.zlorbie788.zeus.core.Inv.with(slot, () -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
                    rocketCooldown = rocketInterval.i() * 20;
                }
            }
        }

        private float[] chooseClearHeading(Minecraft mc, float yaw, float pitch) {
            Vec3 origin = mc.player.getEyePosition();
            float[] yawOffsets = switch (routeStyle.index()) {
                case 0 -> new float[] {0f};
                case 2 -> new float[] {0f, -20f, 20f, -35f, 35f, -55f, 55f};
                default -> new float[] {0f, -12f, 12f, -25f, 25f, -40f, 40f};
            };
            float[] pitchOffsets = {-12f, -6f, 0f, 7f, 14f};
            float bestCost = Float.MAX_VALUE;
            float[] best = null;
            for (float yo : yawOffsets) for (float po : pitchOffsets) {
                float candidateYaw = yaw + yo;
                float candidatePitch = Math.max(-38f, Math.min(28f, pitch + po));
                if (!clearPath(mc, origin, candidateYaw, candidatePitch)) continue;
                float cost = Math.abs(yo) * 0.9f + Math.abs(po) * 0.55f;
                if (po < 0 && mc.player.getY() > coordinate(y, mc.player.getY()) + 20) cost += Math.abs(po) * 0.35f;
                if (po > 0 && mc.player.getY() < coordinate(y, mc.player.getY()) - 12) cost += po * 0.6f;
                if (cost < bestCost) { bestCost = cost; best = new float[] {candidateYaw, candidatePitch}; }
            }
            return best;
        }

        private boolean clearPath(Minecraft mc, Vec3 origin, float yaw, float pitch) {
            double yr = Math.toRadians(yaw);
            double pr = Math.toRadians(pitch);
            Vec3 direction = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
            Vec3 side = new Vec3(direction.z, 0, -direction.x).normalize().scale(0.55);
            double distance = lookAhead.get();
            Vec3 end = origin.add(direction.scale(distance));
            if (loadedOnly.on() && (!loadedCorridor(mc, origin, end) || !loadedCorridor(mc, origin.add(side), end.add(side))
                    || !loadedCorridor(mc, origin.subtract(side), end.subtract(side)))) return false;
            return clearRay(mc, origin, end)
                    && clearRay(mc, origin.add(side), end.add(side))
                    && clearRay(mc, origin.subtract(side), end.subtract(side));
        }

        private boolean loadedCorridor(Minecraft mc, Vec3 start, Vec3 end) {
            Vec3 delta = end.subtract(start);
            double length = delta.length();
            int samples = Math.max(1, (int) Math.ceil(length / 4.0));
            for (int i = 1; i <= samples; i++) {
                Vec3 point = start.add(delta.scale(i / (double) samples));
                if (!mc.level.hasChunk(((int) Math.floor(point.x)) >> 4, ((int) Math.floor(point.z)) >> 4)) return false;
            }
            return true;
        }

        private boolean clearRay(Minecraft mc, Vec3 start, Vec3 end) {
            HitResult hit = mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            return hit.getType() == HitResult.Type.MISS;
        }

        private static float wrap(float angle) {
            angle %= 360f;
            if (angle >= 180f) angle -= 360f;
            if (angle < -180f) angle += 360f;
            return angle;
        }

        private static float clamp(float value, float limit) {
            return Math.max(-limit, Math.min(limit, value));
        }

        @Override protected void onHud(net.minecraft.client.gui.GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (!showHeading.on() || mc.player == null) return;
            Vec3 velocity = mc.player.getDeltaMovement();
            double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 20.0;
            float sc = 0.82f;
            String head = String.format(java.util.Locale.ROOT, "%s  ·  %.0fm  ·  %.0f°", status, distance, bearing);
            String tail = String.format(java.util.Locale.ROOT, "ΔY %.0f  ·  %.1f m/s", coordinate(y, mc.player.getY()) - mc.player.getY(), speed);
            int bw = Math.max(Math.round(Draw.widthS(head, sc)), Math.round(Draw.widthS(tail, sc))) + 16;
            int bh = 22;
            int bx = (w - bw) / 2;
            int by = h - 76;
            Hud.record(bx, by, bw, bh);
            Hud.panel(g, bx, by, bw, bh);
            Hud.iconDot(g, bx + 8, by + 7, Hud.accent(), 2f);
            Draw.textS(g, head, bx + 13, by + 3, 0xFFE7F0FF, sc);
            Draw.textS(g, tail, bx + 13, by + 12, 0xFF92A8C9, sc);
        }

        @Override public String info() { return String.format(java.util.Locale.ROOT, "%.0fm · %.0f°", distance, bearing); }
    }

    public static final class SwordPvpCoach extends Module {
        private final Setting.Bool sprintHint = add(new Setting.Bool("Sprint reset hint", "Show a reminder when sprint is active and an attack cooldown is ready.", true));
        private final Setting.Bool cooldownHud = add(new Setting.Bool("Attack cooldown HUD", "Show the current attack-strength percentage.", true));
        private int strength;

        public SwordPvpCoach() {
            super("swordpvpcoach", "Sword PvP Coach", "Practice feedback for attack timing and sprint-reset rhythm.", Category.COMBAT);
            hudCard();
        }

        @Override protected void onTick(Minecraft mc) {
            if (mc.player == null) return;
            strength = Math.round(mc.player.getAttackStrengthScale(0) * 100);
        }

        @Override protected void onHud(net.minecraft.client.gui.GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null || (!cooldownHud.on() && !sprintHint.on())) return;
            String text = (cooldownHud.on() ? "Attack " + strength + "%" : "") + (sprintHint.on() && mc.player.isSprinting() && strength >= 95 ? "  • Sprint reset ready" : "");
            if (text.isBlank()) return;
            int width = Draw.width(text);
            int px = (w - width) / 2;
            int py = h / 2 + 34;
            Hud.record(px - 6, py - 4, width + 12, 15);
            Hud.panel(g, px - 6, py - 4, width + 12, 15);
            Draw.text(g, text, px, py, 0xFFFFFFFF);
        }

        @Override public String info() { return strength + "%"; }
    }
}
