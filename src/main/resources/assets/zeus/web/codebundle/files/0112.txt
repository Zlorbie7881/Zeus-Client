package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.ui.Accent;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AntiVanish extends Module {
    private static volatile AntiVanish self;
    private final Setting.Bool tab = add(new Setting.Bool("Body missing from the player list", "Flag a player you can see who is not in the tab list.", true));
    private final Setting.Bool digger = add(new Setting.Bool("Unseen digger", "Flag block-breaking by a player you cannot see.", true));
    private final Setting.Bool tabPacket = add(new Setting.Bool("Track tab-list removal packets", "Correlate a server tab removal with a player entity that remains visible nearby.", true));
    private final Setting.Num minConfidence = add(new Setting.Num("Minimum alert confidence", "Raise an alert only when the evidence score reaches this threshold.", 45, 25, 90, 5, " %"));
    private final Setting.Bool showEvidence = add(new Setting.Bool("Show evidence score", "Display the evidence confidence in the alert card.", true));
    private final Setting.Bool spec = add(new Setting.Bool("Spectators close to you", "Warn when a player in spectator mode is near you. The server sends them to your game like any player, you just do not see them.", true));
    private final Setting.Num specRange = add(new Setting.Num("Spectator range", "How close a spectator has to be.", 48, 8, 128, 4, " blocks")).showIf(() -> this.spec.on());
    private final Setting.Num near = add(new Setting.Num("Digger range", "Only breaking this close to you counts.", 24, 6, 64, 2, " blocks"));
    private final Setting.Num show = add(new Setting.Num("Show the card for", "How long the warning stays.", 6, 2, 20, 1, " s"));
    private final Setting.Bool chat = add(new Setting.Bool("Also a message at the top", "A short message as well as the card.", true));

    private record Sus(UUID id, String name, long since, long until, String why, int x, int y, int z, int confidence) {}

    private final Map<UUID, Long> pending = new HashMap<>();
    private final Map<UUID, Long> removedFromTab = new HashMap<>();
    private final Map<UUID, String> knownNames = new HashMap<>();
    private final Map<UUID, Long> alerted = new HashMap<>();
    private final Map<UUID, Integer> evidenceScores = new HashMap<>();
    private final Map<UUID, Long> evidenceTimes = new HashMap<>();
    private final Map<UUID, java.util.Set<String>> evidenceReasons = new HashMap<>();
    private Sus current;
    private int ticks;
    private long lastLeak;

    public AntiVanish() {
        super("antivanish", "Anti Vanish", "Warns you when a vanished player is close: \"NAME is watching you\".", Category.UTILITY);
        self = this;
    }

    public static void leak(int entityId, BlockPos pos) {
        AntiVanish a = self;
        if (a == null || !a.enabled() || !a.digger.on()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || entityId == mc.player.getId() || mc.level.getEntity(entityId) != null) return;
        if (mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > a.near.get() * a.near.get()) return;
        long now = System.currentTimeMillis();
        if (now - a.lastLeak < 4000) return;
        a.lastLeak = now;
        a.raise(null, "Unknown player", "block-breaking packet from an entity not present in your client world", pos.getX(), pos.getY(), pos.getZ(), 45);
    }

    public static void noteTabRemoval(Object packet) {
        AntiVanish a = self;
        if (a == null || !a.enabled() || !a.tabPacket.on() || packet == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        try {
            for (java.lang.reflect.Field field : packet.getClass().getDeclaredFields()) {
                if (!Iterable.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Object value = field.get(packet);
                if (!(value instanceof Iterable<?> values)) continue;
                for (Object entry : values) if (entry instanceof UUID id) {
                    var info = mc.getConnection().getPlayerInfo(id);
                    if (info != null) a.knownNames.put(id, info.getProfile().name());
                    a.removedFromTab.put(id, System.currentTimeMillis());
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private boolean raise(UUID id, String name, String why, int x, int y, int z, int weight) {
        long now = System.currentTimeMillis();
        int confidence = weight;
        String evidence = why;
        if (id != null) {
            long previousAt = evidenceTimes.getOrDefault(id, 0L);
            if (now - previousAt > 20_000) { evidenceScores.remove(id); evidenceReasons.remove(id); }
            java.util.Set<String> reasons = evidenceReasons.computeIfAbsent(id, ignored -> new java.util.HashSet<>());
            if (reasons.add(why)) {
                int previous = evidenceScores.getOrDefault(id, 0);
                confidence = Math.min(100, previous == 0 ? weight : previous + Math.max(10, weight / 2));
                evidenceScores.put(id, confidence);
            } else confidence = evidenceScores.getOrDefault(id, weight);
            evidenceTimes.put(id, now);
            evidence = String.join("; ", reasons);
        }
        if (confidence < minConfidence.get()) return false;
        long ms = (long) (show.get() * 1000);
        current = new Sus(id, name, now, now + ms, evidence, x, y, z, confidence);
        if (chat.on()) Zeus.toast(name + " may be vanished (" + confidence + "% evidence)", false);
        return true;
    }

    @Override
    protected void onEnable() {
        pending.clear();
        removedFromTab.clear();
        knownNames.clear();
        alerted.clear(); evidenceScores.clear(); evidenceTimes.clear(); evidenceReasons.clear();
        current = null;
    }

    @Override
    protected void onDisable() {
        current = null;
    }

    @Override
    protected void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.getConnection() == null) return;
        if (++ticks % 10 != 0) return;
        long now = System.currentTimeMillis();
        if (tabPacket.on()) {
            for (var entry : new java.util.ArrayList<>(removedFromTab.entrySet())) {
                UUID id = entry.getKey();
                if (now - entry.getValue() < 2200) continue;
                AbstractClientPlayer body = find(mc, id);
                if (body != null && body.isAlive() && mc.getConnection().getPlayerInfo(id) == null) {
                    Long last = alerted.get(id);
                    if (last == null || now - last >= 60000) {
                        if (raise(id, knownNames.getOrDefault(id, body.getName().getString()),
                                "tab-removal packet while player entity remained loaded", body.getBlockX(), body.getBlockY(), body.getBlockZ(), 85)) alerted.put(id, now);
                    }
                }
                removedFromTab.remove(id);
            }
        }
        if (spec.on()) {
            double r2 = specRange.get() * specRange.get();
            for (AbstractClientPlayer p : mc.level.players()) {
                if (p == mc.player || !p.isSpectator() || mc.player.distanceToSqr(p) > r2) continue;
                UUID id = p.getUUID();
                Long last = alerted.get(id);
                if (last != null && now - last < 45000) continue;
                if (raise(id, p.getName().getString(), "spectator mode close to you (weak clue)", p.getBlockX(), p.getBlockY(), p.getBlockZ(), 35)) alerted.put(id, now);
            }
        }
        if (!tab.on()) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == mc.player) continue;
            UUID id = p.getUUID();
            if (mc.getConnection().getPlayerInfo(id) != null) {
                pending.remove(id);
                continue;
            }
            Long first = pending.putIfAbsent(id, now);
            if (first == null || now - first < 1800) continue;
            Long last = alerted.get(id);
            if (last != null && now - last < 60000) continue;
            boolean packetProof = removedFromTab.containsKey(id);
            if (raise(id, p.getName().getString(), "visible entity missing from tab list", p.getBlockX(), p.getBlockY(), p.getBlockZ(), packetProof ? 85 : 55)) alerted.put(id, now);
        }
        pending.keySet().removeIf(id -> find(mc, id) == null);
        knownNames.keySet().removeIf(id -> find(mc, id) == null && !removedFromTab.containsKey(id));
        evidenceTimes.entrySet().removeIf(e -> now - e.getValue() > 60_000);
        evidenceScores.keySet().retainAll(evidenceTimes.keySet());
        evidenceReasons.keySet().retainAll(evidenceTimes.keySet());
    }

    @Override
    public String info() {
        return current != null && current.until > System.currentTimeMillis() ? "!" : null;
    }

    @Override
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        Sus c = current;
        if (c == null) return;
        long now = System.currentTimeMillis();
        if (now > c.until) {
            current = null;
            return;
        }
        float in = Math.min(1f, (now - c.since) / 350f);
        float out = Math.min(1f, (c.until - now) / 400f);
        float a = Math.min(in, out);
        float ease = 1f - (1f - in) * (1f - in);
        int cw = 230;
        int ch = 62;
        int x = w / 2 - cw / 2;
        int y = Math.round(-ch + (ch + 14) * ease);
        int danger = 0xFFFF4D5E;
        Draw.shadow(g, x, y, cw, ch, 10, 8, 0.5f * a);
        Draw.rrect(g, x, y, cw, ch, 10, Draw.rgba(0xFF0D1220, 0.94f * a), Draw.rgba(0xFF171D31, 0.94f * a));
        Draw.rrectLine(g, x, y, cw, ch, 10, Draw.rgba(danger, 0.8f * a));
        int hs = 40;
        int hx = x + 12;
        int hy = y + (ch - hs) / 2;
        float pulse = 0.55f + 0.45f * (float) Math.sin(now / 160.0);
        Draw.glow(g, hx - 3, hy - 3, hs + 6, hs + 6, 6, danger, 5, 0.35f * pulse * a);
        Draw.rrect(g, hx - 2, hy - 2, hs + 4, hs + 4, 5, Draw.rgba(danger, 0.9f * a));
        drawHead(g, mc, c, hx, hy, hs, a);
        int tx = hx + hs + 12;
        String line1 = Draw.fit(c.name + " is watching you", cw - (tx - x) - 10);
        Draw.bold(g, line1, tx, y + 14, Draw.rgba(0xFFFFFFFF, a));
        String evidence = showEvidence.on() ? "Evidence confidence: " + c.confidence + "%" : c.why;
        Draw.text(g, Draw.fit(evidence, cw - (tx - x) - 10), tx, y + 30, Draw.rgba(0xFFFFB3BA, a));
        String where = Math.round(Math.sqrt(mc.player.distanceToSqr(c.x + 0.5, c.y, c.z + 0.5))) + " blocks away";
        Draw.text(g, Draw.fit(where, cw - (tx - x) - 10), tx, y + 44, Draw.rgba(Accent.DIM, a));
    }

    private static AbstractClientPlayer find(Minecraft mc, UUID id) {
        if (mc.level == null) return null;
        for (AbstractClientPlayer p : mc.level.players()) if (p.getUUID().equals(id)) return p;
        return null;
    }

    private void drawHead(GuiGraphics g, Minecraft mc, Sus c, int x, int y, int size, float alpha) {
        int tint = Draw.rgba(0xFFFFFFFF, alpha);
        try {
            if (c.id != null && mc.getConnection() != null && mc.getConnection().getPlayerInfo(c.id) != null) {
                PlayerFaceRenderer.draw(g, mc.getConnection().getPlayerInfo(c.id).getSkin(), x, y, size, tint);
                return;
            }
            AbstractClientPlayer acp = c.id == null ? null : find(mc, c.id);
            if (acp != null) {
                PlayerFaceRenderer.draw(g, acp.getSkin(), x, y, size, tint);
                return;
            }
            PlayerFaceRenderer.draw(g, DefaultPlayerSkin.get(c.id == null ? new UUID(0L, 0L) : c.id), x, y, size, tint);
        } catch (RuntimeException e) {
            Draw.rrect(g, x, y, size, size, 4, 0xFF2A3350);
            Draw.boldCenter(g, "?", x + size / 2, y + size / 2 - 4, 0xFFFFFFFF);
        }
    }

    public static List<String> help() {
        return List.of("Correlates visible-but-unlisted entities, tab-removal packets, spectator state and unseen block-breaking packets.",
                "Scores are heuristic evidence, not proof. The client cannot detect players the server never sends.");
    }
}
