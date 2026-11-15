package com.zlorbie788.zeus.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Remembers the last world or server you played, so the menu can take you straight back.
 * Stored in config/zeus/last_played.json.
 */
public final class LastPlayed {
    private LastPlayed() {
    }

    public static final class Entry {
        public final boolean server;
        public final String id;      // world folder, or server address
        public final String name;    // world name, or server name
        public final long at;

        Entry(boolean server, String id, String name, long at) {
            this.server = server;
            this.id = id;
            this.name = name;
            this.at = at;
        }
    }

    private static Entry last;
    private static boolean loaded;
    private static boolean recorded;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("zeus").resolve("last_played.json");
    }

    public static Entry get() {
        if (!loaded) {
            loaded = true;
            try {
                if (Files.exists(file())) {
                    JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
                    last = new Entry(o.get("server").getAsBoolean(), o.get("id").getAsString(), o.get("name").getAsString(), o.get("at").getAsLong());
                }
            } catch (Exception ignored) {
            }
        }
        return last;
    }

    /** Called every client tick: notes the world or server once per session, then keeps the time fresh. */
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            recorded = false;
            return;
        }
        if (recorded && mc.player.tickCount % 1200 != 0) return;
        recorded = true;
        try {
            Entry e;
            if (mc.getSingleplayerServer() != null) {
                var srv = mc.getSingleplayerServer();
                Path p = srv.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                String folder = p.getFileName() == null ? "" : p.getFileName().toString();
                if (folder.isEmpty()) return;
                e = new Entry(false, folder, srv.getWorldData().getLevelName(), System.currentTimeMillis());
            } else if (mc.getCurrentServer() != null) {
                ServerData d = mc.getCurrentServer();
                if (d.isRealm()) return;
                e = new Entry(true, d.ip, d.name == null || d.name.isBlank() ? d.ip : d.name, System.currentTimeMillis());
            } else {
                return;
            }
            last = e;
            loaded = true;
            JsonObject o = new JsonObject();
            o.addProperty("server", e.server);
            o.addProperty("id", e.id);
            o.addProperty("name", e.name);
            o.addProperty("at", e.at);
            Files.createDirectories(file().getParent());
            Files.writeString(file(), o.toString());
        } catch (Exception ignored) {
        }
    }

    /** Joins the remembered world or server. */
    public static void resume(Minecraft mc, Screen back) {
        Entry e = get();
        if (e == null) return;
        if (e.server) {
            ServerData d = new ServerData(e.name, e.id, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(back, mc, ServerAddress.parseString(e.id), d, false, null);
        } else {
            mc.createWorldOpenFlows().openWorld(e.id, () -> mc.setScreen(back));
        }
    }

    public static String ago(long at) {
        long s = Math.max(0, (System.currentTimeMillis() - at) / 1000);
        if (s < 120) return "just now";
        if (s < 3600) return (s / 60) + " min ago";
        if (s < 86400) return (s / 3600) + " h ago";
        long d = s / 86400;
        return d == 1 ? "yesterday" : d + " days ago";
    }
}
