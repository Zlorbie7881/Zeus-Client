package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Aim;
import com.zlorbie788.zeus.core.Armor;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wears an opponent's armour down instead of killing them. A landed hit takes {@code max(1, damage/4)}
 * durability off every worn piece, so a full-charge sword costs one point per piece per half second
 * while a soft hit costs the same one point per half second for almost no health. The module therefore
 * swings the weakest hotbar item on the game's own rhythm and holds fire before the target dies,
 * leaving the armour broken and the player alive.
 */
public final class ArmorBreaker extends Module {
    private final Setting.Bool softest = add(new Setting.Bool("Weakest weapon", "Switch to whichever hotbar item hits for the least damage, because armour loses at least one point per hit however soft it is. This is the whole trick: soft and fast strips armour roughly four times faster than hard and slow, for a quarter of the health lost.", true));
    private final Setting.Num slot = add(new Setting.Num("Hotbar slot", "Which slot to hit with when automatic picking is off. Slot 1 is the leftmost.", 1, 1, 9, 1, "")).showIf(() -> !this.softest.on());
    private final Setting.Num rate = add(new Setting.Num("Hits per second", "How often to swing. The game only accepts one damaging hit per half second (the damage-immunity window), so two a second is everything that lands; more just sends swings the server throws away.", 2, 1, 20, 1, "/s"));
    private final Setting.Num minHealth = add(new Setting.Num("Hold fire above", "Stop swinging once the target is this low, so the armour breaks before they do. 0 never holds fire.", 8, 0, 20, 1, "hp"));
    private final Setting.Num reach = add(new Setting.Num("Reach", "How close the target has to be. The server's own hit range is the real limit, so anything past it is ignored.", 3, 1, 6, 0.1, ""));
    private final Setting.Bool silent = add(new Setting.Bool("Silent aim", "Send a look packet at the target right before each swing, so the server sees you facing your hits while your camera stays where you left it.", true));
    private final Setting.Bool swing = add(new Setting.Bool("Swing arm", "Show the arm swinging on every hit.", true));
    private final Setting.Bool los = add(new Setting.Bool("Line of sight only", "Skip targets behind walls.", true));
    private final Setting.Info readout = addSetting(new Setting.Info("Draining", this::lines));

    private LivingEntity current;
    private String targetName;
    private long nextHit;
    private int hits;
    private String holding;

    public ArmorBreaker() {
        super("armorbreaker", "Armour Breaker", "Drains an opponent's armour durability instead of their health: soft, fast hits on every worn piece, with a readout of how many hits are left before it breaks.", Category.COMBAT);
        alias("armor breaker", "armour breaker", "durability", "armor drain", "armour drain", "shred", "armor shred");
    }

    @Override
    protected void onTick(Minecraft mc) {
        current = null;
        targetName = null;
        holding = null;
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
        Player me = mc.player;
        if (me.isUsingItem()) return;
        double allowed = Math.min(reach.get(), Combat.Reach.entityRange());
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : Env.entities(mc, allowed + 1.0)) {
            if (!(e instanceof LivingEntity le) || e == me || !le.isAlive() || le.isSpectator()) continue;
            if (!(e instanceof Player) || Friends.protects(e)) continue;
            if (Aim.reachTo(e) > allowed) continue;
            if (los.on() && !me.hasLineOfSight(e)) continue;
            double d = me.distanceToSqr(e);
            if (d < bestD) {
                bestD = d;
                best = le;
            }
        }
        if (best == null) return;
        current = best;
        targetName = best.getName().getString();
        if (minHealth.get() > 0 && best.getHealth() <= minHealth.get()) {
            holding = "holding fire at " + fmt(best.getHealth()) + " hp";
            return;
        }
        int want = softest.on() ? Armor.softestSlot() : Math.max(0, slot.i() - 1);
        if (want >= 0 && Inv.selected() != want) {
            Inv.select(want);
            return;
        }
        Vec3 aim = best.position()
                .add(best.getDeltaMovement().scale(0.3))
                .add(0, best.getBbHeight() * 0.55, 0);
        if (silent.on()) Aim.look(aim);
        long now = System.currentTimeMillis();
        if (now < nextHit) return;
        // A hit inside the ten-tick damage-immunity window does nothing, so never swing so fast that
        // the next one is wasted.
        double interval = Math.max(520.0, 1000.0 / Math.max(1.0, rate.get()));
        interval *= ThreadLocalRandom.current().nextDouble(1.0, 1.10);
        nextHit = now + (long) interval;
        mc.gameMode.attack(me, best);
        if (swing.on()) me.swing(InteractionHand.MAIN_HAND);
        hits++;
    }

    @Override
    protected void onDisable() {
        current = null;
        targetName = null;
        holding = null;
    }

    @Override
    protected void cleanSlate() {
        current = null;
        holding = null;
    }

    @Override
    public String info() {
        if (targetName == null) return null;
        return targetName + " " + hits + " hits";
    }

    private List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("A landed hit takes max(1, damage / 4) from every worn piece, and the game only allows one per half second, so soft hits strip armour with almost no health lost.");
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return out;
        out.add("Hits landed here: " + hits);
        if (current == null) {
            out.add("No player in reach.");
            return out;
        }
        out.add("Target: " + current.getName().getString() + "  " + fmt(current.getHealth()) + " hp"
                + (holding == null ? "" : "  (" + holding + ")"));
        EquipmentSlot weakest = Armor.weakest(current);
        if (weakest == null) {
            out.add("Target wears no armour.");
            return out;
        }
        ItemStack piece = current.getItemBySlot(weakest);
        int left = Armor.remaining(piece);
        out.add("Closest to breaking: " + Armor.piece(weakest) + " " + Math.round(Armor.left(piece) * 100f) + "%"
                + (left > 0 && left < 10000 ? "  about " + left + " hits left" : ""));
        return out;
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
