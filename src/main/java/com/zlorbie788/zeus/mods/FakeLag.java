package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Hits;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class FakeLag extends Module {
    private static final String[] MODES = {"Constant", "Jitter", "Pulse", "Spike", "Adaptive"};
    private static final String[] SCOPES = {"Movement only", "Movement and actions", "Everything but answers"};

    private final Setting.Choice mode = add(new Setting.Choice("Mode", "Constant: one steady delay. Jitter: the delay wanders a little. Pulse: hold for a while, then release everything at once. Spike: nothing most of the time, then a short burst. Adaptive: the delay follows the server's real answer time.", 0, MODES));
    private final Setting.Num delay = add(new Setting.Num("Delay", "How long packets wait before they are sent. 150 ms is a bad connection, 400 ms is unplayable for the server but very hard to hit.", 150, 20, 1000, 10, " ms"));
    private final Setting.Num jitter = add(new Setting.Num("Jitter", "How far the delay wanders around the value above. 0 is a fixed rhythm, which is easier to spot.", 60, 0, 400, 10, " ms")).showIf(() -> mode.index() == 1);
    private final Setting.Num hold = add(new Setting.Num("Hold for", "How long each pulse keeps packets back.", 250, 40, 2000, 10, " ms")).showIf(() -> mode.index() == 2);
    private final Setting.Num gap = add(new Setting.Num("Then release for", "How long everything flows normally between two pulses.", 250, 40, 3000, 10, " ms")).showIf(() -> mode.index() == 2);
    private final Setting.Num spike = add(new Setting.Num("Spike length", "How long each burst holds packets.", 300, 50, 2000, 10, " ms")).showIf(() -> mode.index() == 3);
    private final Setting.Num every = add(new Setting.Num("Between spikes", "How long it behaves normally between two bursts.", 3, 1, 30, 1, " s")).showIf(() -> mode.index() == 3);
    private final Setting.Num adapt = add(new Setting.Num("Follow the server by", "Adaptive only: the delay is your measured answer time times this. 1.2 waits a little longer than the server needs.", 1.2, 0.5, 3, 0.1, "x")).showIf(() -> mode.index() == 4);
    private final Setting.Choice scope = add(new Setting.Choice("What waits", "Movement only is the usual choice and the least disruptive. Actions add clicks and swings. The last one adds chat, containers and everything else, and can confuse an inventory badly.", 0, SCOPES));
    private final Setting.Bool fighting = add(new Setting.Bool("Only while fighting", "Holds packets only when you swung or landed a hit in the last two seconds, so walking around and building stay normal.", false));
    private final Setting.Bool flushHit = add(new Setting.Bool("Release everything when I am hit", "The moment you take damage, everything waiting is sent at once, so you are never fighting from a stale position.", true));
    private final Setting.Num cap = add(new Setting.Num("Most packets held", "A full queue is emptied instead of growing, which is what keeps a server from kicking you for silence.", 40, 5, 200, 5, ""));
    private final Setting.Info state = addSetting(new Setting.Info("Right now", this::lines));

    private static final class Held {
        final Packet<?> packet;
        final long due;

        Held(Packet<?> packet, long due) {
            this.packet = packet;
            this.due = due;
        }
    }

    private static final ArrayDeque<Held> QUEUE = new ArrayDeque<>();
    private static FakeLag live;
    private static boolean delivering;

    private long started;
    private long nextSpike;
    private long spikeUntil;
    private long sent;
    private long held;
    private int peak;
    private String note = "Nothing waiting.";

    public FakeLag() {
        super("fakelag", "Fake Lag", "Holds the packets you send for a moment and then releases them, so the server sees an older you. Keeps answers, containers and chat out of it, caps the queue and empties it when you are hit.", Category.UTILITY);
        alias("lag", "ping spoof", "lag switch", "blink", "packet delay");
    }

    @Override
    protected void onEnable() {
        live = this;
        QUEUE.clear();
        started = System.currentTimeMillis();
        nextSpike = started + every.i() * 1000L;
        spikeUntil = 0;
        sent = held = 0;
        peak = 0;
        note = "On.";
    }

    @Override
    protected void onDisable() {
        flushAll();
        live = null;
        note = "Off.";
    }

    @Override
    protected void cleanSlate() {
        QUEUE.clear();
    }

    public static boolean holds(Packet<?> packet) {
        FakeLag m = live;
        if (m == null || !m.enabled() || delivering) return false;
        return m.take(packet, System.currentTimeMillis());
    }

    private boolean take(Packet<?> packet, long now) {
        if (Minecraft.getInstance().player == null || never(packet)) return false;
        if (fighting.on() && !Hits.fighting(2000L)) return false;
        long due = dueFor(packet, now);
        if (due < 0) return false;
        if (QUEUE.size() >= cap.i()) flushAll();
        QUEUE.addLast(new Held(packet, Math.min(due, now + 3000L)));
        held++;
        if (QUEUE.size() > peak) peak = QUEUE.size();
        return true;
    }

    private static boolean never(Packet<?> p) {
        return p instanceof ServerboundKeepAlivePacket
                || p instanceof ServerboundPongPacket
                || p instanceof ServerboundResourcePackPacket
                || p instanceof ServerboundClientInformationPacket
                || p instanceof ServerboundCustomPayloadPacket;
    }

    private boolean movement(Packet<?> p) {
        return p instanceof ServerboundMovePlayerPacket
                || p instanceof ServerboundMoveVehiclePacket
                || p instanceof ServerboundPlayerInputPacket
                || p instanceof ServerboundPlayerCommandPacket
                || p instanceof ServerboundPaddleBoatPacket;
    }

    private boolean action(Packet<?> p) {
        return p instanceof ServerboundInteractPacket
                || p instanceof ServerboundSwingPacket
                || p instanceof ServerboundPlayerActionPacket
                || p instanceof ServerboundUseItemPacket
                || p instanceof ServerboundUseItemOnPacket;
    }

    private boolean inScope(Packet<?> p) {
        if (movement(p)) return true;
        if (scope.index() == 0) return false;
        if (action(p)) return true;
        if (scope.index() == 1) return false;
        String n = p.getClass().getSimpleName();
        return !(n.contains("Container") || n.contains("Chat") || n.contains("Recipe") || n.contains("Book") || n.contains("Sign"));
    }

    private long dueFor(Packet<?> p, long now) {
        if (!inScope(p)) return -1;
        return switch (mode.index()) {
            case 1 -> now + Math.max(0, Math.round(delay.f() + ThreadLocalRandom.current().nextDouble(-jitter.f(), jitter.f())));
            case 2 -> {
                long cycle = Math.max(80L, Math.round(hold.f() + gap.f()));
                long phase = Math.max(0L, now - started) % cycle;
                yield phase < hold.f() ? now + (Math.round(hold.f()) - phase) : -1;
            }
            case 3 -> {
                if (now >= nextSpike) {
                    spikeUntil = now + Math.round(spike.f());
                    nextSpike = spikeUntil + every.i() * 1000L;
                }
                yield now < spikeUntil ? spikeUntil : -1;
            }
            case 4 -> {
                double seen = Hits.recentLatency();
                double base = seen > 1 ? Math.min(900, seen * adapt.f()) : delay.f();
                yield now + Math.round(base);
            }
            default -> now + Math.round(delay.f());
        };
    }

    private void flushDue(long now) {
        if (QUEUE.isEmpty()) return;
        ClientPacketListener c = Minecraft.getInstance().getConnection();
        if (c == null) {
            QUEUE.clear();
            return;
        }
        delivering = true;
        try {
            while (!QUEUE.isEmpty() && QUEUE.peekFirst().due <= now) {
                c.send(QUEUE.pollFirst().packet);
                sent++;
            }
        } finally {
            delivering = false;
        }
    }

    private void flushAll() {
        if (QUEUE.isEmpty()) return;
        ClientPacketListener c = Minecraft.getInstance().getConnection();
        if (c == null) {
            QUEUE.clear();
            return;
        }
        delivering = true;
        try {
            while (!QUEUE.isEmpty()) {
                c.send(QUEUE.pollFirst().packet);
                sent++;
            }
        } finally {
            delivering = false;
        }
    }

    @Override
    protected void onTick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (flushHit.on() && mc.player != null && mc.player.hurtTime > 0 && !QUEUE.isEmpty()) {
            flushAll();
            note = "Released on damage.";
            return;
        }
        flushDue(now);
        note = QUEUE.isEmpty() ? "Nothing waiting." : QUEUE.size() + " packet" + (QUEUE.size() == 1 ? "" : "s") + " waiting.";
    }

    @Override
    protected void onHud(net.minecraft.client.gui.GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        flushDue(System.currentTimeMillis());
    }

    @Override
    public String info() {
        return QUEUE.size() + " / " + Math.round(delay.f()) + "ms";
    }

    private List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add(note);
        out.add("Mode: " + MODES[mode.index()]);
        out.add("Delay now: " + (mode.index() == 4 ? Math.round(Hits.recentLatency() * adapt.f()) + " ms, from your measured answer time" : Math.round(delay.f()) + " ms"));
        out.add("Sent " + sent + " held packets since it was switched on, " + peak + " at once at most.");
        if (scope.index() == 2) out.add("Scope includes chat and containers: that can confuse an open inventory.");
        return out;
    }
}
