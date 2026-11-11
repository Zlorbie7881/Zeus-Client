package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Build;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class Defense {
    private Defense() {}

    static boolean obsidianOnly(ItemStack s) {
        return s.is(Items.OBSIDIAN) || s.is(Items.CRYING_OBSIDIAN);
    }

    static boolean wallBlock(ItemStack s) {
        if (!(s.getItem() instanceof BlockItem)) return false;
        String n = com.zlorbie788.zeus.core.Names.item(s);
        for (String bad : new String[] {"tnt", "chest", "shulker", "sand", "gravel", "anvil", "door", "bed", "sign", "torch", "ladder", "button", "pressure", "rail", "redstone", "carpet", "slab", "stairs", "web", "lever", "banner", "head", "skull", "pot", "frame", "crafting", "furnace", "barrel", "hopper", "dispenser", "dropper", "piston", "observer", "lantern", "sapling", "flower", "fence", "wall", "glass_pane", "bars", "trapdoor", "powder", "candle", "bell", "cake", "sponge", "concrete_powder"}) {
            if (n.contains(bad)) return false;
        }
        return true;
    }

    abstract static class Placer extends Module {
        final Setting.Choice block = add(new Setting.Choice("Block", "What to build with.", 0, "Obsidian", "Any plain block"));
        final Setting.Num perTick = add(new Setting.Num("Blocks per tick", "How many blocks can be placed in one tick.", 2, 1, 6, 1, ""));
        final Setting.Num delay = add(new Setting.Num("Delay", "Ticks to wait between rounds.", 0, 0, 10, 1, " ticks"));
        final Setting.Bool swing = add(new Setting.Bool("Swing arm", "Show the arm swinging.", false));
        private int cd;
        private int placed;

        Placer(String id, String name, String desc, Category c) {
            super(id, name, desc, c);
        }

        Predicate<ItemStack> item() {
            return block.index() == 0 ? Defense::obsidianOnly : Defense::wallBlock;
        }

        abstract List<BlockPos> wanted(Minecraft mc);

        @Override
        protected void onTick(Minecraft mc) {
            placed = 0;
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
            if (cd > 0) {
                cd--;
                return;
            }
            Predicate<ItemStack> it = item();
            if (Inv.hotbar(it) < 0 && !Inv.offhand(it)) return;
            int left = perTick.i();
            for (BlockPos p : wanted(mc)) {
                if (left <= 0) break;
                if (!Build.canPlace(p, Build.REACH)) continue;
                if (Build.place(p, it, swing.on(), Build.REACH)) {
                    left--;
                    placed++;
                }
            }
            cd = delay.i();
        }

        @Override
        public String info() {
            return null;
        }
    }

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public static final class Surround extends Placer {
        private final Setting.Bool floor = add(new Setting.Bool("Floor", "Also fill the block under you.", true));
        private final Setting.Bool diagonals = add(new Setting.Bool("Corners", "Also the four diagonal blocks, so crystals cannot be placed next to you.", false));

        public Surround() {
            super("surround", "Surround", "Walls you in with obsidian at foot level.", Category.COMBAT);
        }

        @Override
        List<BlockPos> wanted(Minecraft mc) {
            BlockPos f = mc.player.blockPosition();
            List<BlockPos> out = new ArrayList<>();
            if (floor.on()) out.add(f.below());
            for (int[] d : SIDES) out.add(f.offset(d[0], 0, d[1]));
            if (diagonals.on()) {
                out.add(f.offset(1, 0, 1));
                out.add(f.offset(-1, 0, 1));
                out.add(f.offset(1, 0, -1));
                out.add(f.offset(-1, 0, -1));
            }
            return out;
        }
    }

    public static final class AutoTrap extends Placer {
        private final Targets.Pick who = Targets.pick(this::add, true, true, false);
        private final Setting.Num range = add(new Setting.Num("Target range", "How far away a target is picked.", 5, 2, 8, 1, " blocks"));
        private final Setting.Bool lid = add(new Setting.Bool("Lid", "A block above the target's head.", true));
        private final Setting.Bool feet = add(new Setting.Bool("Floor", "A block under the target.", false));

        public AutoTrap() {
            super("autotrap", "Auto Trap", "Traps the nearest target in a box of obsidian.", Category.COMBAT);
        }

        @Override
        List<BlockPos> wanted(Minecraft mc) {
            LivingEntity t = Targets.nearest(mc, who, range.get());
            List<BlockPos> out = new ArrayList<>();
            if (t == null) return out;
            BlockPos f = t.blockPosition();
            if (feet.on()) out.add(f.below());
            for (int[] d : SIDES) out.add(f.offset(d[0], 0, d[1]));
            for (int[] d : SIDES) out.add(f.offset(d[0], 1, d[1]));
            if (lid.on()) out.add(f.above(2));
            return out;
        }
    }

    public static final class HoleFiller extends Placer {
        private final Setting.Num range = add(new Setting.Num("Range", "How far around you holes are filled.", 4, 1, 5, 1, " blocks"));
        private final Setting.Bool nearEnemy = add(new Setting.Bool("Only near an enemy", "Fill holes only when a monster or player is close.", false));

        public HoleFiller() {
            super("holefiller", "Hole Filler", "Fills the safe holes around you with obsidian.", Category.COMBAT);
        }

        @Override
        List<BlockPos> wanted(Minecraft mc) {
            List<BlockPos> out = new ArrayList<>();
            if (nearEnemy.on()) {
                Targets.Pick all = new Targets.Pick(new Setting.Bool("", "", true), new Setting.Bool("", "", true), new Setting.Bool("", "", false));
                if (Targets.nearest(mc, all, 8) == null) return out;
            }
            int r = range.i();
            BlockPos c = mc.player.blockPosition();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dy = -2; dy <= 1; dy++) {
                        BlockPos p = c.offset(dx, dy, dz);
                        if (p.equals(c) || !Build.air(p) || !Build.air(p.above())) continue;
                        if (!Build.solid(p.below())) continue;
                        if (Hole.kind(p) == 0) continue;
                        out.add(p);
                    }
                }
            }
            out.sort((a, b) -> Double.compare(Build.distTo(a), Build.distTo(b)));
            return out;
        }
    }

    public static final class AutoWeb extends Module {
        private final Targets.Pick who = Targets.pick(this::add, true, true, false);
        private final Setting.Num range = add(new Setting.Num("Target range", "How far away a target is picked.", 5, 2, 8, 1, " blocks"));
        private final Setting.Num delay = add(new Setting.Num("Delay", "Ticks between webs.", 4, 0, 20, 1, " ticks"));
        private int cd;

        public AutoWeb() {
            super("autoweb", "Auto Web", "Throws a cobweb at the feet of the nearest target.", Category.COMBAT);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
            if (cd > 0) {
                cd--;
                return;
            }
            LivingEntity t = Targets.nearest(mc, who, range.get());
            if (t == null) return;
            BlockPos p = t.blockPosition();
            if (Build.canPlace(p, Build.REACH) && Build.place(p, Inv.is(Items.COBWEB), false, Build.REACH)) cd = delay.i();
        }
    }

    public static final class SelfWeb extends Module {
        public SelfWeb() {
            super("selfweb", "Self Web", "Puts a cobweb at your feet, so nothing can run you down.", Category.COMBAT);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
            BlockPos p = mc.player.blockPosition();
            if (Build.canPlace(p, Build.REACH)) Build.place(p, Inv.is(Items.COBWEB), false, Build.REACH);
        }
    }

    public static final class Hole {
        private Hole() {}

        public static int kind(BlockPos p) {
            int bedrock = 0;
            for (int[] d : SIDES) {
                BlockPos s = p.offset(d[0], 0, d[1]);
                var st = Build.state(s);
                if (st.is(Blocks.BEDROCK)) bedrock++;
                else if (!st.is(Blocks.OBSIDIAN) && !st.is(Blocks.CRYING_OBSIDIAN) && !st.is(Blocks.NETHERITE_BLOCK) && !st.is(Blocks.RESPAWN_ANCHOR) && !st.is(Blocks.ENDER_CHEST)) return 0;
            }
            var floor = Build.state(p.below());
            if (!floor.is(Blocks.BEDROCK) && !floor.is(Blocks.OBSIDIAN) && !floor.is(Blocks.CRYING_OBSIDIAN) && !floor.is(Blocks.NETHERITE_BLOCK)) return 0;
            return bedrock == 4 && floor.is(Blocks.BEDROCK) ? 2 : 1;
        }
    }
}
