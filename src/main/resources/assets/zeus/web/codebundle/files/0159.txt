package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Aim;
import com.zlorbie788.zeus.core.Build;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Dmg;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class Macros {
    private Macros() {}

    public static final class CrystalMacro extends Module {
        private final Setting.Num placeDelay = add(new Setting.Num("Place delay", "Ticks between two crystals. 20 ticks are one second.", 1, 0, 20, 1, " ticks"));
        private final Setting.Num breakDelay = add(new Setting.Num("Break delay", "Ticks between two hits on crystals.", 1, 0, 20, 1, " ticks"));
        private final Setting.Num range = add(new Setting.Num("Range", "How far you place and hit.", 4.5, 2, 6, 0.1, " blocks"));
        private final Setting.Bool findCrystal = add(new Setting.Bool("Hit the crystal on the block", "Also hit a crystal that stands on the block you look at, even if the crosshair is a little beside it.", true));
        private final Setting.Bool antiSuicide = add(new Setting.Bool("Anti suicide", "Never use a crystal that could kill you.", true));
        private final Setting.Num maxSelf = add(new Setting.Num("Maximum self damage", "Never use a crystal that would hurt you more than this (36 = no limit).", 36, 0, 36, 1, ""));
        private final Setting.Bool rotate = add(new Setting.Bool("Look at it", "Tells the server you look at the crystal or block before you act (your own view does not move).", true));
        private final Setting.Bool swing = add(new Setting.Bool("Swing arm", "Show the arm swinging.", true));
        private int placeCd;
        private int breakCd;
        private long warned;

        public CrystalMacro() {
            super("crystalmacro", "Crystal Macro", "Hold right click on obsidian: places a crystal and hits it, over and over.", Category.COMBAT);
        }

        private static boolean base(BlockPos p) {
            var s = Build.state(p);
            return s.is(Blocks.OBSIDIAN) || s.is(Blocks.BEDROCK);
        }

        private boolean safe(Minecraft mc, Vec3 c) {
            float self = Dmg.explosion(mc, c, mc.player, 6f);
            if (self > maxSelf.get()) return false;
            return !antiSuicide.on() || self < mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1f;
        }

        private Entity crystalOn(Minecraft mc, BlockPos base) {
            Vec3 at = new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
            Entity best = null;
            double bestD = 1.5;
            for (Entity e : Env.entities(mc, range.get() + 2)) {
                if (e.getType() != EntityType.END_CRYSTAL || !e.isAlive()) continue;
                double d = e.position().add(0, 0.5, 0).distanceTo(at);
                if (d < bestD) {
                    best = e;
                    bestD = d;
                }
            }
            return best;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
            if (placeCd > 0) placeCd--;
            if (breakCd > 0) breakCd--;
            if (!mc.options.keyUse.isDown()) return;
            HitResult hr = mc.hitResult;
            Entity crystal = null;
            BlockPos baseAt = null;
            if (hr instanceof EntityHitResult ehr && ehr.getEntity().getType() == EntityType.END_CRYSTAL) crystal = ehr.getEntity();
            if (hr instanceof BlockHitResult bhr && bhr.getType() != HitResult.Type.MISS && base(bhr.getBlockPos())) baseAt = bhr.getBlockPos();
            if (crystal == null && baseAt != null && findCrystal.on()) crystal = crystalOn(mc, baseAt);

            if (crystal != null) {
                if (breakCd > 0 || mc.player.getEyePosition().distanceTo(crystal.position()) > range.get()) return;
                if (rotate.on()) Aim.look(crystal.position().add(0, 0.5, 0));
                mc.gameMode.attack(mc.player, crystal);
                if (swing.on()) mc.player.swing(InteractionHand.MAIN_HAND);
                breakCd = breakDelay.i();
                return;
            }
            if (baseAt == null || placeCd > 0) return;
            BlockPos up = baseAt.above();
            if (!Build.air(up) || !Build.air(up.above()) || Build.blocked(up) || Build.blocked(up.above())) return;
            if (Build.distTo(baseAt) > range.get() + 0.5) return;
            if (!safe(mc, new Vec3(baseAt.getX() + 0.5, baseAt.getY() + 1.0, baseAt.getZ() + 0.5))) return;
            if (rotate.on()) Aim.look(new Vec3(baseAt.getX() + 0.5, baseAt.getY() + 1.0, baseAt.getZ() + 0.5));
            if (Build.useOn(Build.top(baseAt), Inv.is(Items.END_CRYSTAL), swing.on())) {
                placeCd = placeDelay.i();
            } else if (System.currentTimeMillis() - warned > 5000) {
                warned = System.currentTimeMillis();
                Zeus.toast("Crystal Macro: no end crystals in your hotbar or offhand", false);
            }
        }
    }

    public static final class AnchorMacro extends Module {
        private final Setting.Num delay = add(new Setting.Num("Delay", "Ticks between the steps (place, charge, explode).", 1, 0, 20, 1, " ticks"));
        private final Setting.Num charges = add(new Setting.Num("Charges", "How many times the anchor is charged first. One charge is already a full explosion.", 1, 1, 4, 1, ""));
        private final Setting.Bool place = add(new Setting.Bool("Place anchors", "Also place an anchor on the block you look at when it is not an anchor.", true));
        private final Setting.Num explodeSlot = add(new Setting.Num("Set-off slot", "Hotbar slot used to set the anchor off (1 to 9). 0: the first slot that does not hold glowstone.", 0, 0, 9, 1, ""));
        private final Setting.Num range = add(new Setting.Num("Range", "How far you act.", 4.5, 2, 6, 0.1, " blocks"));
        private final Setting.Bool antiSuicide = add(new Setting.Bool("Anti suicide", "Never set off an anchor that could kill you.", true));
        private final Setting.Num maxSelf = add(new Setting.Num("Maximum self damage", "Never set off an anchor that would hurt you more than this (36 = no limit).", 36, 0, 36, 1, ""));
        private final Setting.Bool rotate = add(new Setting.Bool("Look at it", "Tells the server you look at the anchor before you act (your own view does not move).", true));
        private final Setting.Bool swing = add(new Setting.Bool("Swing arm", "Show the arm swinging.", true));
        private int cd;
        private long warned;

        public AnchorMacro() {
            super("anchormacro", "Anchor Macro", "Hold right click on a block: places an anchor, charges it and sets it off.", Category.COMBAT);
        }

        private void warn(String text) {
            if (System.currentTimeMillis() - warned > 5000) {
                warned = System.currentTimeMillis();
                Zeus.toast(text, false);
            }
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
            if (cd > 0) {
                cd--;
                return;
            }
            if (!mc.options.keyUse.isDown()) return;
            if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) return;
            if (mc.level.dimension().equals(Level.NETHER)) {
                warn("Anchor Macro: anchors do not explode in the Nether");
                return;
            }
            BlockPos at = hit.getBlockPos();
            if (Build.distTo(at) > range.get() + 0.5) return;

            if (!Build.state(at).is(Blocks.RESPAWN_ANCHOR)) {
                if (!place.on()) return;
                BlockPos np = at.relative(hit.getDirection());
                if (!Build.replaceable(np) || Build.blocked(np)) return;
                if (rotate.on()) Aim.look(hit.getLocation());
                if (Build.useOn(hit, Inv.is(Items.RESPAWN_ANCHOR), swing.on())) cd = delay.i();
                else warn("Anchor Macro: no respawn anchor in your hotbar");
                return;
            }

            int have = CrystalAnchor.AnchorAura.chargeOf(Build.state(at));
            Vec3 c = Vec3.atCenterOf(at);
            if (rotate.on()) Aim.look(c);
            if (have < charges.i()) {
                if (Build.useOn(hit, Inv.is(Items.GLOWSTONE), swing.on())) cd = Math.max(delay.i(), 1);
                else warn("Anchor Macro: no glowstone in your hotbar");
                return;
            }
            float self = Dmg.explosion(mc, c, mc.player, 5f);
            if (self > maxSelf.get() || (antiSuicide.on() && self >= mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1f)) {
                warn("Anchor Macro: that explosion would hurt you too much");
                return;
            }
            int slot = explodeSlot.i() > 0 ? explodeSlot.i() - 1 : Inv.hotbar(s -> !s.is(Items.GLOWSTONE));
            if (slot < 0 || Inv.get(slot).is(Items.GLOWSTONE)) {
                slot = -1;
                for (int i = 0; i < 9; i++) if (!Inv.get(i).is(Items.GLOWSTONE)) slot = i;
            }
            if (slot < 0) {
                warn("Anchor Macro: free a hotbar slot that is not glowstone");
                return;
            }
            Inv.with(slot, () -> {
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                if (swing.on()) mc.player.swing(InteractionHand.MAIN_HAND);
            });
            cd = delay.i();
        }
    }
}
