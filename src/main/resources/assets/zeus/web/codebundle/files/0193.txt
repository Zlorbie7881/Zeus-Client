package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SpawnerProtect extends Module {
    private final Setting.Choice what = add(new Setting.Choice("Protect", "What you cannot break by accident.", 0, "Spawners", "Spawners and chests", "Spawners and every container"));
    private final Setting.Bool warnBreak = add(new Setting.Bool("Tell me when it stops me", "A toast when a break was stopped, so a refused click is never a mystery.", true));
    private final Setting.Bool litCheck = add(new Setting.Bool("Check light around spawners", "Lists nearby spawners that are too bright to spawn anything, which is why a farm silently stops.", true));
    private final Setting.Num range = add(new Setting.Num("Spawner list range", "How far spawners are checked for light.", 12, 4, 32, 2, " blocks")).showIf(() -> this.litCheck.on());
    private final Setting.Info spawners = add(new Setting.Info("Nearby spawners", this::lines));

    private long lastTell;

    public SpawnerProtect() {
        super("spawnerprotect", "Spawner Protect", "Refuses to break spawners (and chests if you want) and reports dark-farm light problems.", Category.WORLD);
        light();
    }

    public static boolean protects(Minecraft mc, BlockPos pos) {
        if (mc.level == null || pos == null) return false;
        SpawnerProtect m = Zeus.get(SpawnerProtect.class);
        if (m == null || !m.enabled()) return false;
        return m.guards(mc.level.getBlockState(pos).getBlock());
    }

    private boolean guards(net.minecraft.world.level.block.Block block) {
        if (block == Blocks.SPAWNER) return true;
        if (what.index() == 0) return false;
        if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL) return true;
        if (what.index() == 1) return false;
        return block == Blocks.SHULKER_BOX || block == Blocks.HOPPER || block == Blocks.FURNACE
                || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER || block == Blocks.DROPPER
                || block == Blocks.DISPENSER || block == Blocks.CRAFTER;
    }

    @Override
    protected void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return;
        if (!(mc.hitResult instanceof BlockHitResult hit)) return;
        if (!mc.options.keyAttack.isDown()) return;
        BlockPos pos = hit.getBlockPos();
        if (!guards(mc.level.getBlockState(pos).getBlock())) return;
        if (!mc.gameMode.isDestroying()) return;
        mc.gameMode.stopDestroyBlock();
        long now = System.currentTimeMillis();
        if (warnBreak.on() && now - lastTell > 1200) {
            lastTell = now;
            Zeus.toast("Spawner Protect stopped you breaking a " + mc.level.getBlockState(pos).getBlock().getName().getString().toLowerCase(Locale.ROOT), true);
        }
    }

    private List<String> lines() {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (!enabled() || mc.player == null || mc.level == null) {
            out.add("Switch it on to check the spawners around you.");
            return out;
        }
        if (!litCheck.on()) return out;
        int r = range.i();
        BlockPos centre = mc.player.blockPosition();
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
            if (mc.level.getBlockState(pos).getBlock() != Blocks.SPAWNER) continue;
            found++;
            int light = mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
            if (light > 0) {
                out.add(String.format(Locale.ROOT, "Spawner at %d, %d, %d is lit (%d): nothing will spawn",
                        pos.getX(), pos.getY(), pos.getZ(), light));
            } else if (found <= 6) {
                out.add(String.format(Locale.ROOT, "Spawner at %d, %d, %d is dark and working", pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        if (found == 0) out.add("No spawners within " + r + " blocks.");
        return out;
    }
}
