package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Build;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Names;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.ui.Accent;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The 0.9.8.0 power modules: Target Strafe (combat), Auto Farm, Chest Stealer, Inventory Cleaner and Loot Walker (automation),
 * Ore Compass (finding) and Entity Speed (movement). They only use the game's own calls: breaking, placing, clicking slots and
 * moving your own player.
 */
public final class OpFeatures {
    private OpFeatures() {}

    // ------------------------------------------------------------------ Target Strafe

    /** Circles the target you are fighting at a set distance, so you stay on the move around it. */
    public static final class TargetStrafe extends Module {
        private final Targets.Pick who = Targets.pick(this::add, true, true, false);
        private final Setting.Num range = add(new Setting.Num("Range", "How far away a target can be for the circling to start.", 5.0, 2.0, 8.0, 0.1, " blocks"));
        private final Setting.Num radius = add(new Setting.Num("Circle radius", "How far from the target you stay while you go round it.", 2.6, 1.2, 5.0, 0.1, " blocks"));
        private final Setting.Num speed = add(new Setting.Num("Speed", "How fast you go round it, in blocks per tick. A normal sprint is about 0.28.", 0.28, 0.10, 0.60, 0.01, ""));
        private final Setting.Choice when = add(new Setting.Choice("Works", "Holding attack: only while the attack button is held down. Always: whenever a target is in range.", 0, "Holding attack", "Always"));
        private final Setting.Bool jump = add(new Setting.Bool("Jump over obstacles", "Jumps when you run into a block while circling.", true));
        private int dir = 1;
        private String target;

        public TargetStrafe() {
            super("targetstrafe", "Target Strafe", "Circles the player or mob you fight at a set distance, so you keep moving around it instead of standing still.", Category.COMBAT);
            alias("orbit", "circle strafe");
        }

        @Override
        protected void onTick(Minecraft mc) {
            target = null;
            var me = mc.player;
            if (me == null || mc.level == null || mc.screen != null || me.isPassenger() || me.isFallFlying() || me.getAbilities().flying) return;
            if (when.index() == 0 && !mc.options.keyAttack.isDown()) return;
            LivingEntity t = Targets.nearest(mc, who, range.get());
            if (t == null) return;
            target = t.getName().getString();
            double dx = me.getX() - t.getX();
            double dz = me.getZ() - t.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 0.05) return;
            if (me.horizontalCollision) {
                if (jump.on() && me.onGround()) me.setDeltaMovement(me.getDeltaMovement().x, 0.42, me.getDeltaMovement().z);
                else dir = -dir;
            }
            double sp = speed.get();
            // sideways round the target, plus a nudge in or out to hold the circle's radius
            double tx = -dz / dist * dir, tz = dx / dist * dir;
            double pull = Math.max(-sp, Math.min(sp, (radius.get() - dist) * 0.5));
            double rx = dx / dist * pull, rz = dz / dist * pull;
            double vx = tx * sp + rx, vz = tz * sp + rz;
            double len = Math.sqrt(vx * vx + vz * vz);
            if (len > sp * 1.4) {
                vx *= sp * 1.4 / len;
                vz *= sp * 1.4 / len;
            }
            Vec3 old = me.getDeltaMovement();
            me.setDeltaMovement(vx, old.y, vz);
        }

        @Override
        public String info() {
            return target;
        }
    }

    // ------------------------------------------------------------------ Auto Farm

    /** Harvests ripe crops around you and plants them again. */
    public static final class AutoFarm extends Module {
        private final Setting.Num radius = add(new Setting.Num("Radius", "How far around you it looks for ripe crops. Your reach also limits this.", 4, 1, 6, 1, " blocks"));
        private final Setting.Bool replant = add(new Setting.Bool("Plant again", "Plants the crop back on the same soil with seeds, carrots, potatoes or nether wart from your hotbar.", true));
        private final Setting.Bool wart = add(new Setting.Bool("Nether wart", "Also harvest ripe nether wart.", true));
        private final Setting.Bool gourds = add(new Setting.Bool("Pumpkins and melons", "Also break pumpkins and melons. They are not planted again.", true));
        private final Setting.Num delay = add(new Setting.Num("Delay", "Ticks between two actions.", 2, 0, 10, 1, " ticks"));
        private final Setting.Bool swing = add(new Setting.Bool("Swing hand", "Show the hand swing.", false));
        private int cd;
        private int done;
        private BlockPos replantAt;
        private Item replantItem;

        public AutoFarm() {
            super("autofarm", "Auto Farm", "Breaks ripe crops around you and plants them again, so a field keeps itself going while you walk through it.", Category.WORLD);
            alias("harvest", "crop bot");
        }

        private static Item seedFor(Block b) {
            if (b == Blocks.WHEAT) return Items.WHEAT_SEEDS;
            if (b == Blocks.CARROTS) return Items.CARROT;
            if (b == Blocks.POTATOES) return Items.POTATO;
            if (b == Blocks.BEETROOTS) return Items.BEETROOT_SEEDS;
            if (b == Blocks.NETHER_WART) return Items.NETHER_WART;
            return null;
        }

        private boolean ripe(BlockState st) {
            Block b = st.getBlock();
            if (b instanceof CropBlock c) return c.isMaxAge(st);
            if (wart.on() && b instanceof NetherWartBlock) return st.getValue(NetherWartBlock.AGE) >= 3;
            return gourds.on() && (b == Blocks.PUMPKIN || b == Blocks.MELON);
        }

        @Override
        protected void onEnable() {
            replantAt = null;
            replantItem = null;
            done = 0;
        }

        @Override
        protected void onTick(Minecraft mc) {
            var me = mc.player;
            if (me == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
            if (cd > 0) {
                cd--;
                return;
            }
            // first plant back what was just broken
            if (replantAt != null) {
                BlockPos soil = replantAt.below();
                if (Build.air(replantAt) && Build.distTo(replantAt) <= Build.REACH && Build.useOn(Build.top(soil), Inv.is(replantItem), swing.on())) {
                    done++;
                }
                replantAt = null;
                replantItem = null;
                cd = delay.i();
                return;
            }
            BlockPos c = me.blockPosition();
            int r = radius.i();
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (int x = -r; x <= r; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -r; z <= r; z++) {
                        BlockPos p = c.offset(x, y, z);
                        BlockState st = mc.level.getBlockState(p);
                        if (st.isAir() || !ripe(st)) continue;
                        double d = Build.distTo(p);
                        if (d > Math.min(radius.get() + 0.5, Build.REACH) || d >= bestD) continue;
                        best = p;
                        bestD = d;
                    }
                }
            }
            if (best == null) return;
            BlockState st = mc.level.getBlockState(best);
            Item seed = replant.on() ? seedFor(st.getBlock()) : null;
            mc.gameMode.startDestroyBlock(best, Direction.UP);
            mc.gameMode.continueDestroyBlock(best, Direction.UP);
            if (swing.on()) me.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (mc.level.getBlockState(best).isAir() || !ripe(mc.level.getBlockState(best))) {
                if (seed != null && Inv.hotbar(Inv.is(seed)) >= 0) {
                    replantAt = best;
                    replantItem = seed;
                }
            }
            cd = delay.i();
        }

        @Override
        public String info() {
            return done > 0 ? done + " planted" : null;
        }
    }

    // ------------------------------------------------------------------ Chest Stealer

    /** Moves the contents of an open chest, barrel, shulker box, hopper or dispenser into your inventory. */
    public static final class ChestStealer extends Module {
        private final Setting.Choice what = add(new Setting.Choice("Takes", "Everything: every stack. Valuables only: enchanted gear, diamonds, netherite, gold, emeralds, totems, golden apples, elytra, shulker boxes and similar.", 0, "Everything", "Valuables only"));
        private final Setting.Num delay = add(new Setting.Num("Delay", "Ticks between two stacks. 0 takes one stack every tick.", 1, 0, 10, 1, " ticks"));
        private final Setting.Bool close = add(new Setting.Bool("Close when done", "Closes the container once nothing wanted is left in it.", false));
        private int cd;
        private int taken;

        public ChestStealer() {
            super("cheststealer", "Chest Stealer", "Takes the items out of an open chest, barrel, shulker box or hopper for you, stack by stack.", Category.UTILITY);
            alias("auto loot", "stealer");
        }

        private static boolean valuable(ItemStack s) {
            if (s.isEnchanted()) return true;
            String n = Names.item(s);
            return n.contains("diamond") || n.contains("netherite") || n.contains("gold") || n.contains("emerald") || n.contains("totem") || n.contains("elytra")
                    || n.contains("shulker_box") || n.contains("enchanted") || n.contains("trident") || n.contains("mace") || n.contains("nether_star") || n.contains("beacon")
                    || n.contains("ender_chest") || n.contains("experience_bottle") || n.contains("end_crystal") || n.contains("obsidian") || n.contains("ender_pearl") || n.contains("golden_apple");
        }

        private static boolean supported(AbstractContainerMenu m) {
            return m instanceof ChestMenu || m instanceof ShulkerBoxMenu || m instanceof HopperMenu || m instanceof DispenserMenu;
        }

        @Override
        protected void onEnable() {
            cd = 0;
            taken = 0;
        }

        @Override
        protected void onTick(Minecraft mc) {
            var me = mc.player;
            if (me == null || mc.gameMode == null || !(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
            AbstractContainerMenu menu = screen.getMenu();
            if (menu == null || menu == me.inventoryMenu || !supported(menu)) return;
            if (cd > 0) {
                cd--;
                return;
            }
            int container = menu.slots.size() - 36;       // the player's own 36 slots come last
            boolean left = false;
            for (int i = 0; i < container; i++) {
                ItemStack s = menu.slots.get(i).getItem();
                if (s.isEmpty() || what.index() == 1 && !valuable(s)) continue;
                left = true;
                mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, me);
                taken++;
                cd = delay.i();
                return;
            }
            if (!left && close.on()) me.closeContainer();
        }

        @Override
        public String info() {
            return taken > 0 ? taken + " stacks" : null;
        }
    }

    // ------------------------------------------------------------------ Inventory Cleaner

    /** Throws junk out of your inventory so it never fills up. */
    // ------------------------------------------------------------------ Inventory Cleaner

    /** Throws junk out of your inventory so it never fills up. */
    public static final class InventoryCleaner extends Module {
        private final Setting.Bool blocks = add(new Setting.Bool("Common blocks", "Cobblestone, deepslate, dirt, gravel, stone types, netherrack and tuff.", true));
        private final Setting.Bool drops = add(new Setting.Bool("Mob junk", "Rotten flesh, spider eyes, poisonous potatoes, bones and tipped nothing.", true));
        private final Setting.Bool tools = add(new Setting.Bool("Wooden and stone tools", "Wooden and stone tools and weapons.", false));
        private final Setting.Bool hotbar = add(new Setting.Bool("Leave the hotbar alone", "Only clean the 27 slots above the hotbar.", true));
        private final Setting.Num keep = add(new Setting.Num("Only when fewer free than", "Cleans only once your inventory has fewer free slots than this. 0 cleans all the time.", 0, 0, 20, 1, " slots"));
        private final Setting.Num delay = add(new Setting.Num("Delay", "Ticks between two throws.", 3, 1, 20, 1, " ticks"));
        private int cd;
        private int thrown;

        public InventoryCleaner() {
            super("inventorycleaner", "Inventory Cleaner", "Throws junk out of your inventory (cobblestone, rotten flesh and the like) so it does not fill up while you mine or fight.", Category.PLAYER);
            alias("auto drop", "junk dropper");
        }

        private boolean junk(ItemStack s) {
            if (s.isEmpty() || s.isEnchanted()) return false;
            String n = Names.item(s);
            if (blocks.on() && (n.equals("cobblestone") || n.equals("cobbled_deepslate") || n.equals("dirt") || n.equals("gravel") || n.equals("andesite") || n.equals("diorite")
                    || n.equals("granite") || n.equals("netherrack") || n.equals("tuff") || n.equals("stone") || n.equals("deepslate") || n.equals("blackstone") || n.equals("cobbled_blackstone"))) return true;
            if (drops.on() && (n.equals("rotten_flesh") || n.equals("spider_eye") || n.equals("poisonous_potato") || n.equals("bone"))) return true;
            return tools.on() && (n.startsWith("wooden_") || n.startsWith("stone_")) && (n.endsWith("_sword") || n.endsWith("_pickaxe") || n.endsWith("_axe") || n.endsWith("_shovel") || n.endsWith("_hoe"));
        }

        @Override
        protected void onTick(Minecraft mc) {
            var me = mc.player;
            if (me == null || mc.gameMode == null || mc.screen != null) return;
            if (cd > 0) {
                cd--;
                return;
            }
            var inv = me.getInventory();
            if (keep.i() > 0) {
                int free = 0;
                for (int i = 0; i < 36; i++) if (inv.getItem(i).isEmpty()) free++;
                if (free >= keep.i()) return;
            }
            for (int i = hotbar.on() ? 9 : 0; i < 36; i++) {
                if (!junk(inv.getItem(i))) continue;
                if (mc.player.isCreative()) {
                    Inv.click(Inv.menuSlot(i), 1, ClickType.THROW);
                } else {
                    ItemStack stack = inv.getItem(i);
                    if (!stack.isEmpty()) {
                        stack.setCount(1);
                        net.minecraft.world.entity.item.ItemEntity it = new net.minecraft.world.entity.item.ItemEntity(mc.level, me.getX(), me.getY() - 0.25, me.getZ(), stack);
                        mc.level.addFreshEntity(it);
                        inv.setItem(i, ItemStack.EMPTY);
                    }
                }
                thrown++;
                cd = delay.i();
                return;
            }
        }

        @Override
        public String info() {
            return thrown > 0 ? thrown + " thrown" : null;
        }
    }

    // ------------------------------------------------------------------ Loot Walker

    /** Walks toward the nearest dropped item, so what you broke or killed ends up in your pockets. */
    public static final class LootWalker extends Module {
        private final Setting.Num radius = add(new Setting.Num("Radius", "How far away it looks for dropped items.", 8, 2, 20, 1, " blocks"));
        private final Setting.Num speed = add(new Setting.Num("Speed", "How fast it walks, in blocks per tick.", 0.20, 0.05, 0.40, 0.01, ""));
        private final Setting.Bool jump = add(new Setting.Bool("Jump over obstacles", "Jumps when it runs into a block on the way.", true));
        private final Setting.Bool stopOnHit = add(new Setting.Bool("Pause when hurt", "Stops walking for a moment when you take damage, so it never drags you out of a fight.", true));
        private float lastHealth = -1f;
        private int pause;
        private String what;

        public LootWalker() {
            super("lootwalker", "Loot Walker", "Walks to the nearest dropped item and picks it up. Handy after mining, farming or a fight.", Category.WORLD);
            alias("item magnet", "auto collect");
        }

        @Override
        protected void onTick(Minecraft mc) {
            what = null;
            var me = mc.player;
            if (me == null || mc.level == null || mc.screen != null || me.isPassenger() || me.isFallFlying()) return;
            float hp = me.getHealth();
            if (stopOnHit.on() && lastHealth >= 0f && hp < lastHealth) pause = 30;
            lastHealth = hp;
            if (pause > 0) {
                pause--;
                return;
            }
            ItemEntity best = null;
            double bestD = radius.get() * radius.get();
            for (Entity e : Env.entities(mc, radius.get())) {
                if (!(e instanceof ItemEntity it) || !it.isAlive()) continue;
                double d = me.distanceToSqr(it);
                if (d < bestD) {
                    best = it;
                    bestD = d;
                }
            }
            if (best == null) return;
            what = best.getItem().getHoverName().getString();
            double dx = best.getX() - me.getX(), dz = best.getZ() - me.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.35) return;
            double sp = Math.min(speed.get(), len);
            Vec3 old = me.getDeltaMovement();
            double up = me.horizontalCollision && jump.on() && me.onGround() ? 0.42 : old.y;
            me.setDeltaMovement(dx / len * sp, up, dz / len * sp);
        }

        @Override
        public String info() {
            return what;
        }
    }

    // ------------------------------------------------------------------ Entity Speed

    /** Makes the horse, boat or other mount you ride go faster. */
    public static final class EntitySpeed extends Module {
        private final Setting.Num speed = add(new Setting.Num("Speed", "How fast the mount goes, in blocks per tick. A galloping horse is about 0.4, a boat about 0.4.", 1.2, 0.2, 3.0, 0.05, ""));
        private final Setting.Bool needKey = add(new Setting.Bool("Only while I hold a move key", "Keeps the mount's own speed while no move key is down, so you can still stop.", true));
        private final Setting.Bool keepY = add(new Setting.Bool("Keep up and down", "Leaves the mount's own up and down movement alone, so jumping and falling still work.", true));

        public EntitySpeed() {
            super("entityspeed", "Entity Speed", "Makes the horse, pig, strider, boat or minecart you ride go much faster in the direction you steer.", Category.MOVEMENT);
            alias("horse speed", "mount speed");
        }

        @Override
        protected void onTick(Minecraft mc) {
            var me = mc.player;
            if (me == null || mc.screen != null) return;
            Entity v = me.getVehicle();
            if (v == null) return;
            var o = mc.options;
            double f = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
            double s = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
            if (f == 0 && s == 0 && needKey.on()) return;
            double yaw = Math.toRadians(me.getYRot());
            double sin = Math.sin(yaw), cos = Math.cos(yaw);
            double vx = -sin * f + cos * s;
            double vz = cos * f + sin * s;
            double len = Math.sqrt(vx * vx + vz * vz);
            if (len < 1e-4) return;
            vx = vx / len * speed.get();
            vz = vz / len * speed.get();
            Vec3 old = v.getDeltaMovement();
            v.setDeltaMovement(vx, keepY.on() ? old.y : 0.0, vz);
        }

        @Override
        public String info() {
            return speed.text();
        }
    }

    // ------------------------------------------------------------------ Ore Compass

    /** A small panel: the nearest ore of each kind, how far, which way and how high or low. Reads only blocks the game already has. */
    public static final class OreCompass extends Module {
        private final Setting.Choice corner = Hud.corner(this, 0);
        private final Setting.Num radius = add(new Setting.Num("Radius", "How far around you it looks. A bigger radius costs more.", 16, 6, 32, 2, " blocks"));
        private final Setting.Num rows = add(new Setting.Num("Rows", "How many kinds of ore the panel lists.", 6, 2, 10, 1, ""));
        private final Setting.Bool nether = add(new Setting.Bool("Nether ores", "Also list nether quartz and nether gold.", true));
        private final Setting.Bool deep = add(new Setting.Bool("Deepslate ores", "Count the deepslate version of each ore too.", true));

        private static final class Hit {
            String label;
            BlockPos pos;
            double dist = Double.MAX_VALUE;
            int count;
        }

        private final Map<String, Hit> found = new LinkedHashMap<>();
        private final List<Hit> shown = new ArrayList<>();
        private int scan;

        public OreCompass() {
            super("orecompass", "Ore Compass", "A panel that lists the nearest ore of each kind with its distance, direction and height. It only reads blocks your game has been sent.", Category.PANELS);
            light();
            anywhere();
            alias("ore radar", "ore list");
        }

        private static String label(String block) {
            String n = block.replace("deepslate_", "").replace("nether_", "").replace("_ore", "");
            if (n.isEmpty()) return null;
            return Character.toUpperCase(n.charAt(0)) + n.substring(1).replace('_', ' ');
        }

        private void rescan(Minecraft mc) {
            found.clear();
            var me = mc.player;
            BlockPos c = me.blockPosition();
            int r = radius.i();
            int minY = mc.level.getMinY(), maxY = mc.level.getMinY() + mc.level.getHeight() - 1;
            for (int y = Math.max(minY, c.getY() - r); y <= Math.min(maxY, c.getY() + r); y++) {
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        BlockPos p = new BlockPos(c.getX() + x, y, c.getZ() + z);
                        BlockState st = mc.level.getBlockState(p);
                        if (st.isAir()) continue;
                        String n = Names.block(st.getBlock());
                        if (!n.endsWith("_ore")) continue;
                        if (!deep.on() && n.startsWith("deepslate_")) continue;
                        if (!nether.on() && n.startsWith("nether_")) continue;
                        String l = label(n);
                        if (l == null) continue;
                        Hit h = found.computeIfAbsent(l, k -> {
                            Hit nh = new Hit();
                            nh.label = k;
                            return nh;
                        });
                        h.count++;
                        double d = me.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                        if (d < h.dist) {
                            h.dist = d;
                            h.pos = p.immutable();
                        }
                    }
                }
            }
            shown.clear();
            shown.addAll(found.values());
            shown.sort((a, b) -> Double.compare(a.dist, b.dist));
        }

        private static String heading(double dx, double dz) {
            // yaw 0 is south: +z south, +x east, -z north, -x west
            double deg = Math.toDegrees(Math.atan2(dx, -dz));
            String[] n = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
            return n[(int) Math.floorMod(Math.round(deg / 45.0), 8L)];
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null) {
                shown.clear();
                return;
            }
            // about once a second; the light modes slow the tick down further
            if (scan-- > 0) return;
            scan = 18;
            rescan(mc);
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            var me = mc.player;
            if (me == null) return;
            int n = Math.min(rows.i(), shown.size());
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Hit t = shown.get(i);
                double dx = t.pos.getX() + 0.5 - me.getX(), dz = t.pos.getZ() + 0.5 - me.getZ();
                int dy = t.pos.getY() - me.blockPosition().getY();
                lines.add(t.label + "  " + Math.round(Math.sqrt(t.dist)) + "m " + heading(dx, dz) + (dy == 0 ? "" : dy > 0 ? " +" + dy : " " + dy) + "  x" + t.count);
            }
            if (lines.isEmpty()) lines.add("No ore in range");
            int bw = 0;
            for (String s : lines) bw = Math.max(bw, Draw.width(s) + 14);
            bw = Math.max(bw, Draw.widthBold("ORE COMPASS") + 14);
            int bh = lines.size() * 11 + 17;
            int[] at = Hud.place(corner.index(), bw, bh, w, h);
            Hud.panel(g, at[0], at[1], bw, bh);
            Draw.bold(g, "ORE COMPASS", at[0] + 7, at[1] + 4, Accent.TEXT);
            for (int i = 0; i < lines.size(); i++) Draw.text(g, lines.get(i), at[0] + 7, at[1] + 16 + i * 11, shown.isEmpty() ? Accent.DIM : Accent.TEXT);
        }

        @Override
        public String info() {
            return shown.isEmpty() ? null : shown.size() + " kinds";
        }
    }
}
