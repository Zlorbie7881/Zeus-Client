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

/**
 * Wears an opponent's armour down instead of killing them. A landed hit takes {@code max(1, damage/4)}
 * durability off every worn piece, so a full-charge sword costs one point per piece per half second
 * while a soft hit costs the same one point per half second for almost no health. The module therefore
 * swings the weakest hotbar item, waits for the armour packet that proves the hit landed before it
 * swings again, and holds fire before the target dies, leaving the armour broken and the player alive.
 */
public final class ArmorBreaker extends Module {
    private final Setting.Bool softest = add(new Setting.Bool("Weakest weapon", "Switch to whichever hotbar item hits for the least damage, because armour loses at least one point per hit however soft it is. This is the whole trick: soft and fast strips armour roughly four times faster than hard and slow, for a quarter of the health lost.", true));
    private final Setting.Num slot = add(new Setting.Num("Hotbar slot", "Which slot to hit with when automatic picking is off. Slot 1 is the leftmost.", 1, 1, 9, 1, "")).showIf(() -> !this.softest.on());
    private final Setting.Num rate = add(new Setting.Num("Hits per second", "Upper limit on swings. Swinging again only happens after the target's armour has changed, because a hit inside the half-second damage-immunity window does nothing.", 2, 1, 20, 1, "/s"));
    private final Setting.Num minHealth = add(new Setting.Num("Hold fire above", "Stop swinging once the target is this low, so the armour breaks before they do. 0 never holds fire.", 8, 0, 20, 1, "hp"));
    private final Setting.Num reach = add(new Setting.Num("Reach", "How close the target has to be. The server's own hit range is the real limit, so anything past it is ignored.", 3, 1, 6, 0.1, ""));
    private final Setting.Bool silent = add(new Setting.Bool("Silent aim", "Send a look packet at the target right before each swing, so the server sees you facing your hits while your camera stays where you left it.", true));
    private final Setting.Bool swing = add(new Setting.Bool("Swing arm", "Show the arm swinging on every hit.", true));
    private final Setting.Bool los = add(new Setting.Bool("Line of sight only", "Skip targets behind walls.", true));
    private final Setting.Info readout = addSetting(new Setting.Info("Draining", this::lines));

    /** How long the server has to show the damage before a swing is treated as a miss. */
    private static final long CONFIRM_MS = 900;

    private LivingEntity current;
    private String targetName;
    private String status;

    private int watchId = -1;
    private int watchSignal;
    private int watchPieces;
    private int hits;
    private int swings;
    private int broken;
    private long lastSwing;
    private long lastLand;

    public ArmorBreaker() {
        super("armorbreaker", "Armour Breaker", "Drains an opponent's armour durability instead of their health: soft hits on every worn piece, counted only when the armour actually records them, with a readout of how many hits are left before it breaks.", Category.COMBAT);
        alias("armor breaker", "armour breaker", "durability", "armor drain", "armour drain", "shred", "armor shred");
    }

    @Override
    protected void onTick(Minecraft mc) {
        current = null;
        targetName = null;
        status = null;
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
        if (best == null) {
            watchId = -1;
            return;
        }
        current = best;
        targetName = best.getName().getString();
        long now = System.currentTimeMillis();

        if (watchId != best.getId()) {
            watchId = best.getId();
            watchSignal = signal(best);
            watchPieces = pieces(best);
            lastSwing = 0;
            lastLand = 0;
        }

        // The proof of a hit is the armour itself: the server re-sends a piece the moment it loses a
        // point, so a change that follows one of our swings is a hit that landed, and a swing with no
        // change behind it never counted.
        int sig = signal(best);
        int worn = pieces(best);
        if (sig != watchSignal) {
            boolean ours = lastSwing > 0 && now - lastSwing <= CONFIRM_MS;
            watchSignal = sig;
            if (worn < watchPieces) broken += watchPieces - worn;
            watchPieces = worn;
            if (ours) {
                hits++;
                lastLand = now;
            }
        }

        if (minHealth.get() > 0 && best.getHealth() <= minHealth.get()) {
            status = "holding fire at " + fmt(best.getHealth()) + " hp";
            return;
        }

        long sinceSwing = lastSwing == 0 ? Long.MAX_VALUE : now - lastSwing;
        long sinceLand = lastLand == 0 ? Long.MAX_VALUE : now - lastLand;
        boolean awaiting = lastSwing > 0 && lastLand < lastSwing;
        if (awaiting && sinceSwing < CONFIRM_MS) {
            status = "waiting for the armour to change";
            return;
        }
        // A confirmed hit means the target is inside the ten-tick damage-immunity window; anything
        // sent now is thrown away by the game, so sit it out.
        if (sinceLand < 520) {
            status = "immunity window";
            return;
        }
        if (sinceSwing < Math.max(200.0, 1000.0 / Math.max(1.0, rate.get()))) return;

        int want = softest.on() ? Armor.softestSlot() : Math.max(0, slot.i() - 1);
        if (want >= 0 && Inv.selected() != want) {
            Inv.select(want);
            return;
        }
        Vec3 aim = best.position()
                .add(best.getDeltaMovement().scale(0.3))
                .add(0, best.getBbHeight() * 0.55, 0);
        if (silent.on()) Aim.look(aim);
        mc.gameMode.attack(me, best);
        if (swing.on()) me.swing(InteractionHand.MAIN_HAND);
        swings++;
        lastSwing = now;
    }

    @Override
    protected void onDisable() {
        current = null;
        targetName = null;
        status = null;
        watchId = -1;
    }

    @Override
    protected void cleanSlate() {
        current = null;
        status = null;
        watchId = -1;
    }

    @Override
    public String info() {
        if (targetName == null) return null;
        return targetName + " " + hits + "/" + swings + " landed";
    }

    /** Durability of everything worn, encoded so a broken piece also reads as a change. */
    private static int signal(LivingEntity e) {
        int damage = 0;
        int worn = 0;
        for (EquipmentSlot s : Armor.SLOTS) {
            ItemStack st = e.getItemBySlot(s);
            if (st.isEmpty() || st.getMaxDamage() <= 0) continue;
            worn++;
            damage += st.getDamageValue();
        }
        // No breakable armour: health is the only evidence of a landed hit.
        if (worn == 0) return -Math.round(e.getHealth() * 100f) - 1;
        return damage + worn * 100_000;
    }

    private static int pieces(LivingEntity e) {
        int worn = 0;
        for (EquipmentSlot s : Armor.SLOTS) {
            ItemStack st = e.getItemBySlot(s);
            if (!st.isEmpty() && st.getMaxDamage() > 0) worn++;
        }
        return worn;
    }

    private List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("A landed hit takes max(1, damage / 4) from every worn piece. A swing counts only once the target's armour value actually changes, so misses and hits inside the immunity window never inflate the numbers.");
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return out;
        out.add("Swings: " + swings + "   landed: " + hits
                + (swings > 0 ? "  (" + Math.round(hits * 100.0 / swings) + "%)" : ""));
        long now = System.currentTimeMillis();
        out.add(hits == 0 ? "No confirmed hit yet." : "Last landed hit: " + fmt((now - lastLand) / 1000f) + "s ago");
        if (status != null) out.add("Status: " + status);
        if (broken > 0) out.add("Pieces broken on this target: " + broken);
        if (current == null) {
            out.add("No player in reach.");
            return out;
        }
        out.add("Target: " + current.getName().getString() + "  " + fmt(current.getHealth()) + " hp");
        EquipmentSlot weakest = Armor.weakest(current);
        if (weakest == null) {
            out.add("Target wears no armour, so only health evidence is left.");
            return out;
        }
        ItemStack piece = current.getItemBySlot(weakest);
        int left = Armor.remaining(piece);
        out.add("Closest to breaking: " + Armor.piece(weakest) + " " + Math.round(Armor.left(piece) * 100f) + "%"
                + (left > 0 && left < 10000 ? "  about " + left + " landed hits left" : ""));
        return out;
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
