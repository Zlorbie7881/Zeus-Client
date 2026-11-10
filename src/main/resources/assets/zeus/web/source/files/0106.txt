package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * The part of cheating that is about not being noticed.
 *
 * <p>Being banned is rarely about one hit. It is about a staff member walking past you while Kill Aura is mid-swing, or
 * about a pattern a person cannot make. This keeps a watch on who is on the server, and the moment a staff member is there
 * it stops what you asked it to stop, tells you once, and gives it back when they are gone. It also has the human part: a
 * swing that is deliberately not sent, so a perfect rhythm is not the only thing the server ever sees from you.</p>
 *
 * <p>Nothing here reads anything the client is not given: the tab list is public, the markers are visible in every name, and
 * no packet is sent to ask the server a question.</p>
 */
public final class Ghost extends Module {
    private static final String[] STARTS = {"★", "☆", "✦", "✧", "✪", "✯", "✨", "⭐"};

    private final Setting.Bool watch = add(new Setting.Bool("Watch the tab list", "Rank words and star markers in the player list are how staff show themselves.", true));
    private final Setting.Bool onlyNear = add(new Setting.Bool("Only when they are close", "Wait until a staff member is actually within reach before stopping anything.", false));
    private final Setting.Num nearRange = add(new Setting.Num("Close enough", "How near a staff member has to be for the guard to act.", 32, 8, 96, 4, " blocks")).showIf(() -> this.onlyNear.on());
    private final Setting.Choice action = add(new Setting.Choice("What to do", "Warn only, switch off everything that fights, or switch every module off at once.", 1, "Warn only", "Turn off fighting", "Turn everything off"));
    private final Setting.Num forget = add(new Setting.Num("Give it back after", "How long after the last sighting the modules you had on come back. 0 leaves them off until you switch them on.", 30, 0, 300, 5, " s"));
    private final Setting.Num hold = add(new Setting.Num("Hold a swing", "The chance that one aura swing is not sent, so your rhythm is not perfect. 0 keeps every hit.", 4, 0, 30, 1, "%"));
    private final Setting.Info now = add(new Setting.Info("Now", this::lines));

    private final Set<String> staff = new LinkedHashSet<>();
    private final List<String> turnedOff = new ArrayList<>();
    private final Random random = new Random();
    private long lastSeen;
    private long lastTell;

    public Ghost() {
        super("ghost", "Ghost", "Stops what you told it to stop while staff are around, and gives your clicking a human rhythm.", Category.UTILITY);
        light();
        anywhere();
    }

    @Override
    protected void onEnable() {
        staff.clear();
        turnedOff.clear();
        lastSeen = 0;
    }

    @Override
    protected void onDisable() {
        giveBack();
    }

    /** True when the aura should not send this swing. */
    public static boolean skipSwing() {
        Ghost g = Zeus.get(Ghost.class);
        return g != null && g.enabled() && g.holds();
    }

    private boolean holds() {
        int pct = hold.i();
        return pct > 0 && random.nextInt(100) < pct;
    }

    @Override
    protected void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        if (!watch.on()) return;
        Set<String> here = new LinkedHashSet<>();
        double range = nearRange.get();
        if (mc.getConnection() != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                String name = info.getProfile().name();
                String team = info.getTeam() == null ? "" : info.getTeam().getName();
                if (!looksLikeStaff(name) && !looksLikeStaff(team)) continue;
                if (onlyNear.on() && distance(mc, name) > range) continue;
                here.add(name);
            }
        }
        long now = System.currentTimeMillis();
        staff.clear();
        staff.addAll(here);
        if (!here.isEmpty()) {
            lastSeen = now;
            act(mc, here);
        } else if (!turnedOff.isEmpty() && forget.i() > 0 && now - lastSeen > forget.i() * 1000L) {
            giveBack();
        }
    }

    private static double distance(Minecraft mc, String name) {
        for (Player p : mc.level.players()) {
            if (p.getName().getString().equals(name)) return mc.player.distanceTo(p);
        }
        return Double.MAX_VALUE;
    }

    private static boolean looksLikeStaff(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String word : StaffMatcher.DEFAULT_RANK_WORDS.split(",")) {
            String w = word.trim();
            if (!w.isEmpty() && lower.contains(w)) return true;
        }
        for (String star : STARTS) if (text.contains(star)) return true;
        // the private-use glyphs servers use for rank icons (✔, ❤ and friends live there too, so only the whole name is checked)
        return StaffMatcher.isPrivateUse(text.codePointAt(0));
    }

    private void act(Minecraft mc, Set<String> here) {
        long now = System.currentTimeMillis();
        if (now - lastTell > 15000) {
            lastTell = now;
            Zeus.toast("Staff here: " + String.join(", ", here), false);
        }
        if (action.index() == 0 || !turnedOff.isEmpty()) return;
        if (action.index() == 1) {
            for (Module m : Zeus.all()) {
                if (m.category != Category.COMBAT || !m.enabled()) continue;
                m.setEnabled(false);
                turnedOff.add(m.id);
            }
        } else {
            for (Module m : Zeus.all()) {
                if (!m.enabled() || m == this) continue;
                m.setEnabled(false);
                turnedOff.add(m.id);
            }
        }
        if (!turnedOff.isEmpty()) Zeus.toast("Ghost switched off " + turnedOff.size() + " module" + (turnedOff.size() == 1 ? "" : "s"), false);
    }

    private void giveBack() {
        for (String id : turnedOff) {
            Module m = Zeus.get(id);
            if (m != null && !m.enabled()) m.setEnabled(true);
        }
        turnedOff.clear();
    }

    private List<String> lines() {
        List<String> out = new ArrayList<>();
        if (!watch.on()) {
            out.add("The tab list is not watched, so nothing is guarded.");
            return out;
        }
        if (staff.isEmpty()) {
            out.add("No staff in the player list.");
            if (!turnedOff.isEmpty()) out.add(turnedOff.size() + " modules are still off from the last visit.");
        } else {
            out.add("Staff: " + String.join(", ", staff));
            out.add(action.index() == 0 ? "Warn only, nothing was switched off." : "Switched off: " + (turnedOff.isEmpty() ? "not yet" : String.join(", ", turnedOff)));
        }
        out.add(hold.i() == 0 ? "Every swing is sent." : "One swing in " + (100 / Math.max(1, hold.i())) + " is held back.");
        return out;
    }
}
