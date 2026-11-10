package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Conservative tunnel-mining helper for digging straight tunnels.
 * It clears a one-wide, two-high passage in the direction the player is facing.
 * It stops at fluids, block entities, unbreakable
 * blocks, missing floor support, screens, or a collision obstruction.
 */
public final class TunnelMiner extends Module {
    private final Setting.Bool autoAdvance = add(new Setting.Bool(
            "Auto advance", "Walk forward through the cleared tunnel. Turn it off to mine while standing still.", false));
    private final Setting.Num speed = add(new Setting.Num(
            "Walking speed", "Maximum horizontal movement per tick while auto advance is enabled.", 0.075, 0.025, 0.14, 0.005, " blocks/tick"));
    private final Setting.Num reach = add(new Setting.Num(
            "Mining reach", "Maximum distance to the next tunnel blocks. Actual game/server reach still applies.", 4.2, 2.5, 4.5, 0.1, " blocks"));
    private final Setting.Bool pauseOnDamage = add(new Setting.Bool(
            "Pause when hurt", "Stops mining and movement briefly after taking damage.", true));
    private final Setting.Action stop = add(new Setting.Action(
            "Emergency stop", "Immediately disables the miner and releases its movement control.", () -> setEnabled(false)));

    private String status = "Off";
    private final Setting.Info statusInfo = add(new Setting.Info("Miner status", () -> List.of(status)));
    private Direction heading = Direction.SOUTH;
    private int damagePause;
    private int mineCooldown;
    private float previousHealth = -1;
    private boolean movedLastTick;

    public TunnelMiner() {
        super("tunnelminer", "Tunnel Miner", "Clears a safe one-wide, two-high tunnel in the direction you face. It mines with the normal hold-to-break, so it also works on a server.", Category.WORLD);
    }

    @Override
    protected void onEnable() {
        damagePause = 0;
        mineCooldown = 0;
        previousHealth = -1;
        movedLastTick = false;
        status = "Ready";
    }

    @Override
    protected void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            releaseMovement(mc);
            status = "No world";
            return;
        }
        if (mc.screen != null) {
            releaseMovement(mc);
            status = "Paused while a screen is open";
            return;
        }

        Player player = mc.player;
        float health = player.getHealth();
        if (previousHealth >= 0 && health < previousHealth && pauseOnDamage.on()) damagePause = 40;
        previousHealth = health;
        if (damagePause > 0) {
            damagePause--;
            releaseMovement(mc);
            status = "Paused after damage · " + damagePause / 20.0 + "s";
            return;
        }
        if (mineCooldown > 0) mineCooldown--;

        heading = cardinal(player.getYRot());
        BlockPos feet = player.blockPosition();
        BlockPos front = feet.relative(heading, 1);
        List<BlockPos> targets = new ArrayList<>(2);
        targets.add(front);
        targets.add(front.above());

        int broken = 0;
        for (BlockPos pos : targets) {
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir()) continue;
            if (!safeToMine(mc, pos, state)) {
                releaseMovement(mc);
                status = "Stopped at protected or hazardous block · " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
                return;
            }
            if (player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > reach.get()) continue;
            if (mineCooldown == 0) {
                // the same hold-to-break the game uses, so a server sees real digging
                mc.gameMode.startDestroyBlock(pos, Direction.UP);
                mc.gameMode.continueDestroyBlock(pos, Direction.UP);
                if (mc.level.getBlockState(pos).isAir()) broken++;
            }
        }

        BlockPos next = feet.relative(heading, 1);
        BlockState lower = mc.level.getBlockState(next);
        BlockState upper = mc.level.getBlockState(next.above());
        BlockState floor = mc.level.getBlockState(next.below());
        boolean passageClear = lower.isAir() && upper.isAir()
                && lower.getFluidState().isEmpty() && upper.getFluidState().isEmpty()
                && !floor.isAir() && floor.getFluidState().isEmpty();

        if (broken > 0) mineCooldown = 2;
        if (autoAdvance.on() && passageClear && !mc.options.keyDown.isDown()
                && !mc.options.keyLeft.isDown() && !mc.options.keyRight.isDown()) {
            double yaw = Math.toRadians(player.getYRot());
            double dx = -Math.sin(yaw) * speed.get();
            double dz = Math.cos(yaw) * speed.get();
            AABB movedBox = player.getBoundingBox().move(dx, 0, dz);
            if (mc.level.noCollision(player, movedBox)) {
                Vec3 old = player.getDeltaMovement();
                player.setDeltaMovement(dx, old.y, dz);
                movedLastTick = true;
            } else {
                releaseMovement(mc);
                status = "Auto advance blocked by collision";
                return;
            }
        } else {
            releaseMovement(mc);
        }

        status = String.format(Locale.ROOT, "%s · heading %s · cleared %d block(s) · %s",
                autoAdvance.on() ? "Running" : "Mining in place", heading.getName(), broken,
                passageClear ? "path clear" : "waiting for passage");
    }

    private static Direction cardinal(float yaw) {
        int quadrant = Math.floorMod(Math.round(yaw / 90.0f), 4);
        return switch (quadrant) {
            case 0 -> Direction.SOUTH;
            case 1 -> Direction.WEST;
            case 2 -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }

    private static boolean safeToMine(Minecraft mc, BlockPos pos, BlockState state) {
        if (state.getFluidState() != null && !state.getFluidState().isEmpty()) return false;
        if (state.hasBlockEntity()) return false;
        if (state.getDestroySpeed(mc.level, pos) < 0) return false;
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return !id.equals("bedrock") && !id.equals("barrier") && !id.equals("end_portal_frame")
                && !id.equals("reinforced_deepslate") && !id.contains("command_block");
    }

    private void releaseMovement(Minecraft mc) {
        if (!movedLastTick || mc.player == null) return;
        Vec3 old = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(0, old.y, 0);
        movedLastTick = false;
    }

    @Override
    protected void onDisable() {
        releaseMovement(Minecraft.getInstance());
        status = "Off";
    }

    @Override
    public String info() {
        return enabled() ? status : null;
    }
}
