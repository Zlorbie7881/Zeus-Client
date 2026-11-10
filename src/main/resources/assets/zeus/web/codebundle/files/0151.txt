package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vanilla spoof: the client tells the server it is the ordinary game. The brand line that every client sends is rewritten
 * to "vanilla", and the channel list that mods announce (the thing a server reads to see Fabric, Fabric API or another mod
 * on the other side) is not sent at all.
 *
 * <p>It is deliberately narrow. It rewrites exactly one payload and drops exactly the channel-announcement payloads, and it
 * never touches anything the game needs to play: no chat, no movement, no container, no keep-alive. Nothing is ever
 * <i>added</i> to the stream, because a client that sends something vanilla never sends is easier to spot than one that says
 * "fabric". The panel shows what a server would see, so you can tell at a glance whether the spoof is doing its job.</p>
 */
public final class Spoof extends Module {
    private final Setting.Bool brand = add(new Setting.Bool("Vanilla client brand", "Sends the ordinary game's brand instead of the loader's name. This is the single most obvious hint a server gets.", true));
    private final Setting.Text brandText = add(new Setting.Text("Brand the server sees", "What the server is told about your client. \"vanilla\" is what the unmodded game sends; anything else is a lie a staff member can compare with the real thing.", "vanilla", 24)).showIf(() -> this.brand.on());
    private final Setting.Bool channels = add(new Setting.Bool("Hide mod channels", "Stops the client from announcing the network channels that mods register. A server (or an anti-cheat plugin) reads that list to see a modded client even when the brand says vanilla. Chat, movement, inventory and every normal action still work.", true));
    private final Setting.Bool custom = add(new Setting.Bool("Hide other mod payloads", "Also stops the small custom payloads other mods send to servers. Keep it on unless one of your server's plugins needs a mod channel.", false));
    private final Setting.Info state = addSetting(new Setting.Info("What the server sees", this::lines));

    private static Spoof live;
    private static boolean delivering;

    private int rewritten;
    private int dropped;
    private String last = "nothing yet";

    public Spoof() {
        super("spoof", "Vanilla Spoof", "Tells the server it is talking to the ordinary game: vanilla brand, no mod channel list, nothing extra sent.", Category.UTILITY);
        alias("vanilla", "client spoof", "brand spoof", "antidetect", "fabric hide");
    }

    @Override
    protected void onEnable() {
        live = this;
        rewritten = 0;
        dropped = 0;
        last = "nothing yet";
    }

    @Override
    protected void onDisable() {
        live = null;
    }

    private String target() {
        String t = brandText.get() == null ? "" : brandText.get().trim();
        return t.isEmpty() ? "vanilla" : t;
    }

    /**
     * Looks at one outgoing packet. Returns the packet to send: the same instance to leave it alone, a replacement, or
     * null to drop it. Called from the packet hook.
     */
    public static Packet<?> intercept(Packet<?> packet) {
        Spoof m = live;
        if (m == null || !m.enabled() || delivering) return packet;
        if (!(packet instanceof ServerboundCustomPayloadPacket payload)) return packet;
        CustomPacketPayload body = payload.payload();
        if (m.brand.on() && body instanceof BrandPayload bp) {
            String want = m.target();
            m.last = "brand: " + want + " (was " + bp.brand() + ")";
            if (bp.brand().equals(want)) return packet;
            m.rewritten++;
            return new ServerboundCustomPayloadPacket(new BrandPayload(want));
        }
        if (m.channels.on() && announcement(body)) {
            m.dropped++;
            m.last = "channel list hidden";
            return null;
        }
        if (m.custom.on() && modPayload(body)) {
            m.dropped++;
            m.last = "mod payload hidden: " + idOf(body);
            return null;
        }
        return packet;
    }

    /** True for the "here are the channels I know" announcements a modded client sends. */
    private static boolean announcement(CustomPacketPayload body) {
        Identifier id = idOf(body);
        if (id == null) return false;
        String path = id.getPath().toLowerCase(Locale.ROOT);
        return path.equals("register") || path.equals("unregister");
    }

    private static boolean modPayload(CustomPacketPayload body) {
        Identifier id = idOf(body);
        if (id == null) return false;
        String ns = id.getNamespace().toLowerCase(Locale.ROOT);
        return ns.equals("fabric") || ns.equals("zeus");
    }

    private static Identifier idOf(CustomPacketPayload body) {
        try {
            return body.type().id();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Sends a replacement packet exactly once, without letting this module (or the lag queue) see it again. */
    public static void deliver(Packet<?> packet) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return;
        delivering = true;
        try {
            conn.send(packet);
        } finally {
            delivering = false;
        }
    }

    public static boolean canDeliver() {
        return Minecraft.getInstance().getConnection() != null;
    }

    @Override
    public String info() {
        return brand.on() ? target() : (channels.on() ? "channels" : null);
    }

    private List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("Brand: " + (brand.on() ? target() : "unchanged"));
        out.add("Channel list: " + (channels.on() ? "not sent" : "sent as usual"));
        out.add("Other mod payloads: " + (custom.on() ? "hidden" : "sent as usual"));
        out.add("Last: " + last + " · rewritten " + rewritten + ", hidden " + dropped);
        out.add("Nothing is added to what the game sends, so the stream still looks like one client talking normally.");
        return out;
    }
}
