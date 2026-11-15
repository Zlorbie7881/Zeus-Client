package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Names;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.mixin.LivingEntityAccessor;
import com.zlorbie788.zeus.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class QuickMods {
    private QuickMods() {}

    public static void pulse(Minecraft mc) {
        AutoReconnect.pulse(mc);
        DiscordPresence.pulse(mc);
    }

    private static boolean ready(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.gameMode != null && mc.screen == null;
    }

    public static final class CrystalOptimizer extends Module {
        private static volatile boolean on;
        private final Setting.Num reach = add(new Setting.Num("Reach", "Crystals you hit from further than this are left alone, so a hit the server will refuse does not hide a crystal that is still there.", 5.5, 3, 6.5, 0.5, " blocks"));

        public CrystalOptimizer() {
            super("crystaloptimizer", "Crystal Optimizer", "Removes an end crystal from your own view the moment your hit breaks it, so the next one can go in without waiting for the server.", Category.COMBAT);
            alias("instant crystal", "crystal opt", "crystal remove");
        }

        @Override
        protected void onEnable() {
            on = true;
        }

        @Override
        protected void onDisable() {
            on = false;
        }

        public static void hit(Player who, Entity target) {
            if (!on || target == null || who == null) return;
            Minecraft mc = Minecraft.getInstance();
            if (who != mc.player || target.getType() != EntityType.END_CRYSTAL || target.isRemoved()) return;
            CrystalOptimizer self = Zeus.get(CrystalOptimizer.class);
            double r = self == null ? 5.5 : self.reach.get();
            if (mc.player.getEyePosition().distanceTo(target.position()) > r) return;
            target.discard();
        }
    }

    public static final class SpearSwap extends Module {
        private final Setting.Bool reachPoke = add(new Setting.Bool("Reach Poke", "While you hold something else and hold attack, stab a player who is further away than your hand reaches with the spear, then swap back.", false));
        private final Setting.Bool switchBack = add(new Setting.Bool("Switch back", "Return to the slot you were on after the lunge or the poke.", true));
        private final Setting.Num pokeRange = add(new Setting.Num("Poke range", "How far a player may be for Reach Poke.", 4.4, 3.2, 5.0, 0.1, " blocks")).showIf(() -> reachPoke.on());
        private final Setting.Num pokeDelay = add(new Setting.Num("Poke delay", "Ticks between two pokes.", 10, 4, 20, 1, " ticks")).showIf(() -> reachPoke.on());
        private final Setting.Info help = add(new Setting.Info("How to use", () -> List.of("Bind the key \"Spear lunge\" in the game's Controls.", "Press it and Zeus picks your Lunge spear, lunges and swaps back.", "Reach Poke needs no key.")));
        private int backSlot = -1;
        private int backIn;
        private int pokeCd;

        public SpearSwap() {
            super("spearswap", "Spear Swap", "Swaps to your Lunge spear and lunges. Reach Poke stabs with a spear past sword reach.", Category.COMBAT);
            alias("lunge", "spear lunge", "spear");
        }

        @Override
        protected void onDisable() {
            backSlot = -1;
        }

        private static boolean spear(ItemStack s) {
            return !s.isEmpty() && Names.item(s).endsWith("_spear");
        }

        private static boolean lunges(ItemStack s) {
            try {
                return String.valueOf(s.getEnchantments()).toLowerCase(Locale.ROOT).contains("lunge");
            } catch (RuntimeException e) {
                return false;
            }
        }

        private int pick(boolean wantLunge) {
            int any = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack s = Inv.get(i);
                if (!spear(s)) continue;
                if (lunges(s)) return i;
                if (any < 0) any = i;
            }
            return wantLunge ? -1 : any;
        }

        public void lunge(Minecraft mc) {
            if (!enabled() || !ready(mc)) return;
            int slot = pick(true);
            if (slot < 0) slot = pick(false);
            if (slot < 0) {
                Zeus.toast("Spear Swap: no spear in your hotbar", false);
                return;
            }
            if (backSlot < 0) backSlot = Inv.selected();
            Inv.select(slot);
            if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit && hit.getEntity() != mc.player && !Friends.protects(hit.getEntity())) {
                mc.gameMode.attack(mc.player, hit.getEntity());
            }
            mc.player.swing(InteractionHand.MAIN_HAND);
            backIn = 3;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            if (backSlot >= 0 && --backIn <= 0) {
                if (switchBack.on()) Inv.select(backSlot);
                backSlot = -1;
            }
            if (pokeCd > 0) pokeCd--;
            if (!reachPoke.on() || backSlot >= 0 || pokeCd > 0 || !mc.options.keyAttack.isDown() || spear(mc.player.getMainHandItem())) return;
            int slot = pick(false);
            if (slot < 0) return;
            LivingEntity best = null;
            double bestD = pokeRange.get();
            for (Entity e : Env.entities(mc, pokeRange.get() + 1)) {
                if (!(e instanceof Player p) || p == mc.player || !p.isAlive() || Friends.protects(p)) continue;
                double d = mc.player.getEyePosition().distanceTo(e.getBoundingBox().getCenter());
                if (d > 3.1 && d <= bestD) {
                    best = p;
                    bestD = d;
                }
            }
            if (best == null) return;
            backSlot = Inv.selected();
            Inv.select(slot);
            mc.gameMode.attack(mc.player, best);
            mc.player.swing(InteractionHand.MAIN_HAND);
            backIn = 2;
            pokeCd = pokeDelay.i();
        }
    }

    private static void quicken(Minecraft mc, int delay) {
        MinecraftAccessor acc = (MinecraftAccessor) mc;
        if (acc.zeus$getRightClickDelay() > delay) acc.zeus$setRightClickDelay(delay);
    }

    public static final class FastXp extends Module {
        private final Setting.Num delay = add(new Setting.Num("Delay", "Ticks between two bottles. 0 is as fast as the game allows.", 0, 0, 3, 1, " ticks"));

        public FastXp() {
            super("fastxp", "FastXP", "Throws experience bottles faster while use is held.", Category.COMBAT);
            alias("fast xp", "xp throw", "bottle");
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc) || !mc.options.keyUse.isDown() || mc.player.isUsingItem()) return;
            ItemStack main = mc.player.getMainHandItem();
            boolean holds = main.is(Items.EXPERIENCE_BOTTLE) || (mc.player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE) && !(main.getItem() instanceof BlockItem));
            if (holds) quicken(mc, delay.i() + 1);
        }
    }

    public static final class FastUse extends Module {
        private final Setting.Num delay = add(new Setting.Num("Delay", "Ticks between two uses. 0 is as fast as the game allows.", 0, 0, 3, 1, " ticks"));
        private final Setting.Bool blocks = add(new Setting.Bool("Blocks", "Speed up placing blocks.", true));
        private final Setting.Bool items = add(new Setting.Bool("Items", "Speed up items that are used with a click (pearls, snowballs, bottles).", true));

        public FastUse() {
            super("fastuse", "Fast Use", "Places blocks and uses items faster while use is held.", Category.PLAYER);
            alias("fast place", "quick use");
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc) || !mc.options.keyUse.isDown() || mc.player.isUsingItem()) return;
            ItemStack held = mc.player.getMainHandItem();
            if (held.isEmpty()) held = mc.player.getOffhandItem();
            if (held.isEmpty()) return;
            if (held.getItem() instanceof BlockItem ? blocks.on() : items.on()) quicken(mc, delay.i() + 1);
        }
    }

    public static final class ClickTp extends Module {
        private final Setting.Num range = add(new Setting.Num("Range", "How far away the block may be.", 60, 10, 210, 5, " blocks"));
        private final Setting.Num cooldown = add(new Setting.Num("Cooldown", "Ticks between two teleports.", 10, 2, 40, 1, " ticks"));
        private final Setting.Bool emptyHand = add(new Setting.Bool("Only with an empty hand", "Off: it works with any item that is not a block, so it never gets in the way of placing blocks.", true));
        private int cd;
        private boolean was;

        public ClickTp() {
            super("clicktp", "Click TP", "Right click to move to the block you are looking at. A server with movement checks will pull you back.", Category.MOVEMENT);
            alias("teleport", "tp");
        }

        @Override
        protected void onEnable() {
            was = false;
            cd = 0;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (cd > 0) cd--;
            boolean down = ready(mc) && mc.options.keyUse.isDown();
            boolean fresh = down && !was;
            was = down;
            if (!fresh || cd > 0) return;
            ItemStack held = mc.player.getMainHandItem();
            if (emptyHand.on() ? !held.isEmpty() : held.getItem() instanceof BlockItem) return;
            if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult) return;
            Vec3 eye = mc.player.getEyePosition();
            Vec3 look = mc.player.getLookAngle();
            HitResult hit = mc.level.clip(new ClipContext(eye, eye.add(look.scale(range.get())), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (!(hit instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) return;
            var side = bhr.getDirection();
            double x = bhr.getBlockPos().getX() + 0.5 + side.getStepX() * (side.getStepY() == 0 ? 0.6 : 0.0);
            double y = bhr.getBlockPos().getY() + (side.getStepY() > 0 ? 1.0 : side.getStepY() < 0 ? -1.8 : 0.0);
            double z = bhr.getBlockPos().getZ() + 0.5 + side.getStepZ() * (side.getStepY() == 0 ? 0.6 : 0.0);
            if (!mc.level.noCollision(mc.player, mc.player.getBoundingBox().move(x - mc.player.getX(), y - mc.player.getY(), z - mc.player.getZ()))) {
                Zeus.toast("Click TP: no room to stand there", false);
                cd = 10;
                return;
            }
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y, z, true, false));
            mc.player.setPos(x, y, z);
            cd = cooldown.i();
        }
    }

    public static final class NoJumpDelay extends Module {
        private final Setting.Num cooldown = add(new Setting.Num("Cooldown", "Ticks of pause that are left between jumps (the game uses 10).", 0, 0, 9, 1, " ticks"));

        public NoJumpDelay() {
            super("nojumpdelay", "No Jump Delay", "Removes the pause between two jumps.", Category.MOVEMENT);
            alias("jump delay", "bhop delay");
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null) return;
            LivingEntityAccessor acc = (LivingEntityAccessor) mc.player;
            if (acc.zeus$getNoJumpDelay() > cooldown.i()) acc.zeus$setNoJumpDelay(cooldown.i());
        }
    }

    public static final class AntiGhostBlock extends Module {
        private static volatile boolean on;

        public AntiGhostBlock() {
            super("antighostblock", "Anti Ghost Block", "Keeps a broken block on your screen until the server confirms the break, so there are no invisible holes.", Category.WORLD);
            alias("ghost block", "ghost blocks");
        }

        @Override
        protected void onEnable() {
            on = true;
        }

        @Override
        protected void onDisable() {
            on = false;
        }

        public static boolean holds() {
            return on && Minecraft.getInstance().getSingleplayerServer() == null;
        }
    }

    public static final class AutoReconnect extends Module {
        private static volatile boolean on;
        private static net.minecraft.client.multiplayer.ServerData last;
        private static long dueAt;
        private static int shown = -1;
        private static int tries;
        private static final Setting.Num DELAY = new Setting.Num("Delay", "Seconds before it reconnects.", 5, 3, 60, 1, " s");
        private static final Setting.Num MAX = new Setting.Num("Give up after", "How many times in a row it tries before it stops. 0 never stops.", 0, 0, 30, 1, "");

        public AutoReconnect() {
            super("autoreconnect", "Auto Reconnect", "Reconnects to the last server after a disconnect that you did not ask for.", Category.UTILITY);
            addSetting(DELAY);
            addSetting(MAX);
            anywhere();
            alias("reconnect", "rejoin");
        }

        @Override
        protected void onEnable() {
            on = true;
        }

        @Override
        protected void onDisable() {
            on = false;
            dueAt = 0;
        }

        static void pulse(Minecraft mc) {
            if (!on) return;
            if (mc.level != null && mc.getCurrentServer() != null) {
                last = mc.getCurrentServer();
                tries = 0;
                dueAt = 0;
                return;
            }
            if (!(mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen) || last == null) {
                dueAt = 0;
                shown = -1;
                return;
            }
            long now = System.currentTimeMillis();
            if (dueAt == 0) {
                if (MAX.i() > 0 && tries >= MAX.i()) return;
                dueAt = now + DELAY.i() * 1000L;
            }
            int left = (int) Math.max(0, (dueAt - now + 999) / 1000);
            if (left != shown) {
                shown = left;
                if (left > 0) Zeus.toast("Reconnecting in " + left + " s", true);
            }
            if (now < dueAt) return;
            dueAt = 0;
            shown = -1;
            tries++;
            net.minecraft.client.gui.screens.Screen parent = mc.screen;
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(parent, mc,
                    net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(last.ip), last, false, null);
        }
    }

    public static final class SkinChange extends Module {
        private static volatile boolean on;
        private static volatile String selfName = "";
        private static volatile String otherName = "";
        private static PlayerSkin selfSkin;
        private static PlayerSkin otherSkin;
        private static long selfAt;
        private static long otherAt;
        private final Setting.Text self = add(new Setting.Text("Your skin", "The name of the player whose skin you wear. They must be on the server (in the player list). Empty: your own.", "", 16));
        private final Setting.Text others = add(new Setting.Text("Everyone else's skin", "Every other player is drawn with this player's skin. Empty: they keep their own.", "", 16));
        private final Setting.Action pickSelf = add(new Setting.Action(
                "Pick from server",
                "Choose a player from the current server list for your own skin.",
                () -> openPlayerPicker(self, "Pick your skin", name -> self.set(name))
        ));
        private final Setting.Action pickOthers = add(new Setting.Action(
                "Pick from server",
                "Choose a player from the current server list for everyone else's skin.",
                () -> openPlayerPicker(others, "Pick everyone else's skin", name -> others.set(name))
        ));

        public SkinChange() {
            super("skinchange", "Skin Change", "Wear any player's skin, on your screen only.", Category.RENDER);
            alias("skin", "skin changer", "skinchanger");
            self.onChange(() -> selfName = self.get().trim());
            others.onChange(() -> otherName = others.get().trim());
        }

        @Override
        protected void onEnable() {
            on = true;
            selfName = self.get().trim();
            otherName = others.get().trim();
            selfAt = otherAt = 0;
        }

        @Override
        protected void onDisable() {
            on = false;
            selfSkin = otherSkin = null;
        }

        public static PlayerSkin replace(AbstractClientPlayer p) {
            if (!on) return null;
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) return null;
            long now = System.currentTimeMillis();
            if (p == mc.player) {
                if (selfName.isEmpty()) return null;
                if (now - selfAt > 500) {
                    selfAt = now;
                    selfSkin = find(mc, selfName);
                }
                return selfSkin;
            }
            if (otherName.isEmpty()) return null;
            if (now - otherAt > 500) {
                otherAt = now;
                otherSkin = find(mc, otherName);
            }
            return otherSkin;
        }

        private static PlayerSkin find(Minecraft mc, String name) {
            var info = mc.getConnection().getPlayerInfo(name);
            return info == null ? null : info.getSkin();
        }

        private void openPlayerPicker(Setting.Text t, String title, Consumer<String> done) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new com.zlorbie788.zeus.ui.PlayerPickerScreen(mc.screen, title, t.get(), done));
        }

    }

    public static final class AntiTrap extends Module {
        private final Setting.Bool stands = add(new Setting.Bool("Armor stands", "Remove armor stands near you.", true));
        private final Setting.Bool carts = add(new Setting.Bool("Chest minecarts", "Remove minecarts with chests near you.", true));
        private final Setting.Num range = add(new Setting.Num("Range", "How far around you it looks.", 12, 3, 48, 1, " blocks"));
        private int ticks;
        private int removed;

        public AntiTrap() {
            super("antitrap", "Anti Trap", "Lets you escape from armor stands and chest minecarts by removing them from your own view and collision.", Category.PLAYER);
            alias("armor stand trap", "minecart trap", "escape trap");
        }

        @Override
        protected void onEnable() {
            removed = 0;
            ticks = 0;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null || ++ticks % 4 != 0) return;
            List<Entity> gone = new ArrayList<>();
            for (Entity e : Env.entities(mc, range.get())) {
                if (e == mc.player || e == mc.player.getVehicle() || e.isRemoved()) continue;
                EntityType<?> t = e.getType();
                if ((stands.on() && t == EntityType.ARMOR_STAND) || (carts.on() && t == EntityType.CHEST_MINECART)) gone.add(e);
            }
            for (Entity e : gone) e.discard();
            removed += gone.size();
        }

        @Override
        public String info() {
            return removed > 0 ? Integer.toString(removed) : null;
        }
    }
}
