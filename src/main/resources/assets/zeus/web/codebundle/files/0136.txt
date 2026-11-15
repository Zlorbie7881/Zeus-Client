package com.zlorbie788.zeus.mods;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class DiscordPresence extends Module {
    private static volatile DiscordPresence self;

    private final Setting.Text appId = add(new Setting.Text("Application ID", "The number from discord.com/developers (New Application, then General Information). Its name is what people see after \"Playing\".", "", 24));
    private final Setting.Text details = add(new Setting.Text("First line", "The top line of your card.", "Playing with Zeus", 60));
    private final Setting.Text state = add(new Setting.Text("Second line", "The line under it. Empty for none.", "", 60));
    private final Setting.Text image = add(new Setting.Text("Image name", "The name of an image you uploaded under Rich Presence, Art Assets in your Discord application. Empty for no picture.", "zeus", 40));
    private final Setting.Bool showServer = add(new Setting.Bool("Show the server", "Puts the name of the server you are on in the second line (when it is empty). Off keeps it private.", false));
    private final Setting.Bool showWorld = add(new Setting.Bool("Show singleplayer / multiplayer", "Says whether you are in a singleplayer world or on a server. Off keeps that private.", true));
    private volatile String note = "Off.";
    private final Setting.Info status = addSetting(new Setting.Info("Status", () -> List.of(note)));

    private volatile String liveWhere = "In the menus";
    private volatile String liveServer = "";
    private volatile long since;
    private Link link;

    public DiscordPresence() {
        super("discordpresence", "Discord Rich Presence", "Shows what you are playing on your Discord profile. Needs the Discord app open and an Application ID.", Category.UTILITY);
        anywhere();
        alias("discord", "rpc", "presence", "rich presence");
        self = this;
    }

    @Override
    protected void onEnable() {
        since = System.currentTimeMillis() / 1000L;
        if (appId.get().trim().isEmpty()) {
            note = "Paste your Application ID first.";
            Zeus.toast("Discord Presence: paste your Application ID first (discord.com/developers)", false);
            return;
        }
        Link previous = link;
        link = new Link(appId.get().trim(), previous);
        link.start();
        note = "Connecting to Discord...";
    }

    @Override
    protected void onDisable() {
        if (link != null) link.stop();
        link = null;
        note = "Off.";
    }

    @Override
    public String info() {
        return link != null && link.connected ? "live" : null;
    }

    static void pulse(Minecraft mc) {
        DiscordPresence m = self;
        if (m == null || !m.enabled()) return;
        if (mc.level == null) {
            m.liveWhere = "In the menus";
            m.liveServer = "";
        } else if (mc.getCurrentServer() != null) {
            m.liveWhere = "On a server";
            m.liveServer = mc.getCurrentServer().name == null ? "" : mc.getCurrentServer().name;
        } else {
            m.liveWhere = "In a singleplayer world";
            m.liveServer = "";
        }
    }

    private JsonObject card() {
        JsonObject a = new JsonObject();
        String d = details.get().trim();
        if (!d.isEmpty()) a.addProperty("details", d);
        String s = state.get().trim();
        if (s.isEmpty()) {
            if (showServer.on() && !liveServer.isBlank()) s = liveServer;
            else if (showWorld.on()) s = liveWhere;
        }
        if (!s.isEmpty()) a.addProperty("state", s);
        JsonObject t = new JsonObject();
        t.addProperty("start", since);
        a.add("timestamps", t);
        String img = image.get().trim();
        if (!img.isEmpty()) {
            JsonObject assets = new JsonObject();
            assets.addProperty("large_image", img);
            assets.addProperty("large_text", "Zeus");
            a.add("assets", assets);
        }
        return a;
    }

    private final class Link implements Runnable {
        private final String id;
        private final Thread thread;
        private final Thread previous;
        private volatile boolean running = true;
        volatile boolean connected;
        private Pipe pipe;
        private String sent = "";
        private long sentAt;
        private long retry = 4000;
        private long readyAt;

        Link(String id, Link previous) {
            this.id = id;
            this.previous = previous == null ? null : previous.thread;
            this.thread = new Thread(this, "Zeus-Discord");
            this.thread.setDaemon(true);
        }

        void start() {
            thread.start();
        }

        void stop() {
            running = false;
            thread.interrupt();
        }

        @Override
        public void run() {
            try {
                if (previous != null) previous.join(1500);
            } catch (InterruptedException e) {
                return;
            }
            while (running) {
                try {
                    if (pipe == null) {
                        pipe = open(id);
                        if (pipe == null) {
                            note = "Discord is not open (or not reachable). Trying again...";
                            Thread.sleep(retry);
                            retry = Math.min(retry * 2, 30000);
                            continue;
                        }
                        retry = 4000;
                        sent = "";
                        readyAt = System.currentTimeMillis() + 1200;
                        connected = true;
                        note = "Connected to Discord.";
                    }
                    pipe.drain();
                    String now = card().toString();
                    long t = System.currentTimeMillis();
                    if (t >= readyAt && (!now.equals(sent) ? t - sentAt > 4000 : t - sentAt > 60000)) {
                        pipe.send(1, activity(now));
                        sent = now;
                        sentAt = t;
                    }
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                } catch (IOException | RuntimeException e) {
                    connected = false;
                    note = "Lost Discord (" + e.getClass().getSimpleName() + "). Trying again...";
                    closePipe();
                    try {
                        Thread.sleep(retry);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
            if (pipe != null) {
                try {
                    pipe.send(1, clear());
                } catch (IOException | RuntimeException ignored) {
                }
            }
            closePipe();
            connected = false;
        }

        private void closePipe() {
            if (pipe != null) pipe.close();
            pipe = null;
        }

        private String activity(String card) {
            JsonObject args = new JsonObject();
            args.addProperty("pid", ProcessHandle.current().pid());
            args.add("activity", JsonParser.parseString(card));
            JsonObject root = new JsonObject();
            root.addProperty("cmd", "SET_ACTIVITY");
            root.add("args", args);
            root.addProperty("nonce", UUID.randomUUID().toString());
            return root.toString();
        }

        private String clear() {
            JsonObject args = new JsonObject();
            args.addProperty("pid", ProcessHandle.current().pid());
            JsonObject root = new JsonObject();
            root.addProperty("cmd", "SET_ACTIVITY");
            root.add("args", args);
            root.addProperty("nonce", UUID.randomUUID().toString());
            return root.toString();
        }
    }

    private interface Pipe {
        void write(byte[] data) throws IOException;

        void drain() throws IOException;

        void close();

        default void send(int opcode, String json) throws IOException {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            ByteBuffer b = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
            b.putInt(opcode).putInt(body.length).put(body);
            write(b.array());
        }
    }

    private static Pipe open(String appId) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        for (int i = 0; i < 10; i++) {
            try {
                Pipe p;
                if (windows) {
                    File f = new File("\\\\?\\pipe\\discord-ipc-" + i);
                    if (!f.exists()) continue;
                    p = new WinPipe(new RandomAccessFile(f, "rw"));
                } else {
                    p = null;
                    for (Path dir : socketDirs()) {
                        Path path = dir.resolve("discord-ipc-" + i);
                        if (!path.toFile().exists()) continue;
                        SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                        try {
                            ch.connect(UnixDomainSocketAddress.of(path));
                        } catch (IOException e) {
                            ch.close();
                            continue;
                        }
                        ch.configureBlocking(false);
                        p = new UnixPipe(ch);
                        break;
                    }
                    if (p == null) continue;
                }
                JsonObject hello = new JsonObject();
                hello.addProperty("v", 1);
                hello.addProperty("client_id", appId);
                p.send(0, hello.toString());
                return p;
            } catch (IOException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private static List<Path> socketDirs() {
        List<Path> out = new ArrayList<>();
        for (String key : new String[] {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
            String v = System.getenv(key);
            if (v == null || v.isBlank()) continue;
            Path base = Path.of(v);
            out.add(base);
            out.add(base.resolve("app/com.discordapp.Discord"));
            out.add(base.resolve("snap.discord"));
        }
        out.add(Path.of("/tmp"));
        return out;
    }

    private static final class WinPipe implements Pipe {
        private final RandomAccessFile file;

        WinPipe(RandomAccessFile file) {
            this.file = file;
        }

        @Override
        public synchronized void write(byte[] data) throws IOException {
            file.write(data);
        }

        @Override
        public synchronized void drain() throws IOException {
            while (file.length() >= 8) {
                byte[] head = new byte[8];
                file.readFully(head);
                int len = ByteBuffer.wrap(head, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                if (len < 0 || len > 1 << 20) throw new IOException("bad frame");
                file.readFully(new byte[len]);
            }
        }

        @Override
        public void close() {
            try {
                file.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static final class UnixPipe implements Pipe {
        private final SocketChannel ch;
        private final ByteBuffer scratch = ByteBuffer.allocate(8192);

        UnixPipe(SocketChannel ch) {
            this.ch = ch;
        }

        @Override
        public synchronized void write(byte[] data) throws IOException {
            ByteBuffer b = ByteBuffer.wrap(data);
            int spins = 0;
            while (b.hasRemaining()) {
                if (ch.write(b) == 0 && ++spins > 2000) throw new IOException("write stalled");
            }
        }

        @Override
        public synchronized void drain() throws IOException {
            while (true) {
                scratch.clear();
                int n = ch.read(scratch);
                if (n < 0) throw new IOException("closed");
                if (n == 0) return;
            }
        }

        @Override
        public void close() {
            try {
                ch.close();
            } catch (IOException ignored) {
            }
        }
    }
}
