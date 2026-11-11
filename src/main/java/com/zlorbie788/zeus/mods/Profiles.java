package com.zlorbie788.zeus.mods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class Profiles extends Module {
    public static final int SLOTS = 20;
    private static final String PREFIX = "ZEUS2-";
    private static final String OLD_PREFIX = "ZEUS1-";
    private static final int MAX_CODE = 400_000;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String[] NAMES = new String[SLOTS];
    private static final JsonObject[] DATA = new JsonObject[SLOTS];
    private static final String[] SAVED = new String[SLOTS];

    private static final String[] IDS = new String[SLOTS];
    private static int active = -1;
    private static boolean loaded;

    private static final java.util.Map<String, Integer> SERVERS = new java.util.LinkedHashMap<>();
    private static boolean autoOn = true;

    private final String[] ids = new String[SLOTS];
    private final Setting.Text typedId;
    private final Setting.Text findId;
    private final String[] labels = new String[SLOTS];
    private final Setting.Pick slot;
    private final Setting.Text typedName;
    private final Setting.Text typedCode;
    private final Setting.Bool follow = add(new Setting.Bool("Keep the active profile up to date", "While a profile is active, every change you make is also saved into it.", true));
    private static boolean followOn = true;
    private final Setting.Bool autoSwitch = add(new Setting.Bool("Switch profile per server", "When you join a server (or a singleplayer world) that has a profile assigned in the buttons below, that profile is loaded for you automatically.", true));
    private String message = "Pick a profile, then save into it or load it.";

    public Profiles() {
        super("profiles", "Profiles", "Twenty profiles that keep all your settings, with share codes so you can swap them with friends.", Category.PROFILES);
        panelOnly();
        for (int i = 0; i < SLOTS; i++) ids[i] = String.valueOf(i + 1);
        load();
        refreshLabels();
        slot = add(new Setting.Pick("Profile", "Which of the twenty profiles the buttons below work on. A * marks the active one.", "1", ids, labels));
        follow.onChange(() -> followOn = follow.on());
        autoSwitch.onChange(() -> autoOn = autoSwitch.on());
        add(new Setting.Info("This profile", this::details));
        add(new Setting.Action("Save my settings into it", "Copies every setting you have now into this profile and makes it the active one.", this::saveHere));
        add(new Setting.Action("Load it", "Replaces every setting with the ones in this profile and makes it the active one.", this::loadHere));
        add(new Setting.Info("Server profiles", this::serverLines));
        add(new Setting.Action("Use it on the server I am on", "Assigns this profile to the server (or singleplayer) you are playing on right now. Next time you join, it is loaded for you.", this::assignHere));
        add(new Setting.Action("Stop switching on this server", "Removes the profile assigned to the server you are on now.", this::unassignHere));
        add(new Setting.Action("Name it from the clipboard", "Copy a name (up to 24 letters) anywhere, then press this.", this::rename));
        typedName = add(new Setting.Text("Or type a name", "Type the name here, then press the button under it.", "", 24).temporary());
        add(new Setting.Action("Name it with the typed name", "Gives this profile the name typed above.", this::renameTyped));
        add(new Setting.Action("Set its ID from the clipboard", "Copy an ID (letters, digits, - and _, up to 16) anywhere, then press this. An ID is your own short handle for the profile, for example pvp or base-build.", this::setIdClipboard));
        typedId = add(new Setting.Text("Or type an ID", "Type the ID here, then press the button under it.", "", 16).temporary());
        add(new Setting.Action("Give it the typed ID", "Gives this profile the ID typed above. No other profile can have the same ID.", this::setIdTyped));
        add(new Setting.Action("Take its ID away", "Removes this profile's ID.", this::clearId));
        findId = add(new Setting.Text("Load a profile by its ID", "Type the ID of a profile here, then press the button under it.", "", 16).temporary());
        add(new Setting.Action("Load the profile with that ID", "Finds the profile that has the ID typed above and loads it.", this::loadById));
        add(new Setting.Action("Copy its share code", "Puts a code on your clipboard that someone else can paste into their own profile. It holds your settings, never your webhook address.", this::copyCode));
        add(new Setting.Action("Put the code from my clipboard here", "Pastes a share code you copied into this profile and loads it.", this::pasteCode));
        typedCode = add(new Setting.Text("Or type or paste a code", "Click the box, press Ctrl+V (or type the code), then press the button under it.", "", MAX_CODE).temporary());
        add(new Setting.Action("Put the typed code here", "Puts the code typed above into this profile and loads it.", this::typedCodeHere));
        add(new Setting.Action("Empty it", "Clears this profile. The settings you have now stay as they are.", this::clearHere));
        followOn = follow.on();
        autoOn = autoSwitch.on();
    }

    static String serverKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || com.zlorbie788.zeus.core.Env.local() || mc.getCurrentServer() == null || mc.getCurrentServer().ip == null) return "singleplayer";
        String ip = mc.getCurrentServer().ip.trim().toLowerCase(java.util.Locale.ROOT);
        if (ip.endsWith(":25565")) ip = ip.substring(0, ip.length() - 6);
        return ip.isEmpty() ? "singleplayer" : ip;
    }

    public static void onWorldEntered() {
        load();
        if (!autoOn || SERVERS.isEmpty()) return;
        String key = serverKey();
        Integer slotIdx = SERVERS.get(key);
        if (slotIdx == null || slotIdx < 0 || slotIdx >= SLOTS || DATA[slotIdx] == null || slotIdx == active) return;
        Zeus.apply(DATA[slotIdx]);
        active = slotIdx;
        write();
        Zeus.toast("Profile " + (slotIdx + 1) + (NAMES[slotIdx] != null ? " (" + NAMES[slotIdx] + ")" : "") + " loaded for " + key, true);
    }

    private List<String> serverLines() {
        List<String> out = new ArrayList<>();
        out.add("This server: " + (com.zlorbie788.zeus.core.Env.inWorld() ? serverKey() : "not in a world"));
        if (SERVERS.isEmpty()) {
            out.add("No server has a profile yet.");
        } else {
            for (java.util.Map.Entry<String, Integer> e : SERVERS.entrySet()) {
                int i = e.getValue();
                out.add(e.getKey() + "  ->  " + (i + 1) + (NAMES[i] != null ? " " + NAMES[i] : ""));
            }
        }
        return out;
    }

    private void assignHere() {
        if (!com.zlorbie788.zeus.core.Env.inWorld()) {
            message = "Join a server or a world first, then press this.";
            Zeus.toast(message, false);
            return;
        }
        int i = chosen();
        String key = serverKey();
        if (DATA[i] == null) {
            message = "Profile " + (i + 1) + " is empty. Save your settings into it first.";
            Zeus.toast(message, false);
            return;
        }
        SERVERS.put(key, i);
        write();
        say("Profile " + (i + 1) + " will load on " + key);
    }

    private void unassignHere() {
        String key = serverKey();
        if (SERVERS.remove(key) != null) {
            write();
            say("No profile switch on " + key + " any more");
        } else {
            message = "No profile is assigned to " + key + ".";
            Zeus.toast(message, false);
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("zeus-profiles.json");
    }

    public static int savedCount() {
        load();
        int n = 0;
        for (int i = 0; i < SLOTS; i++) if (DATA[i] != null || SAVED[i] != null) n++;
        return n;
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.isRegularFile(file())) return;
            JsonElement root = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8));
            if (!root.isJsonObject()) return;
            JsonObject o = root.getAsJsonObject();
            try {
                if (o.has("active")) active = Math.max(-1, Math.min(SLOTS - 1, o.get("active").getAsInt()));
            } catch (RuntimeException ignored) {
                active = -1;
            }
            if (o.has("servers") && o.get("servers").isJsonObject()) {
                for (java.util.Map.Entry<String, JsonElement> e : o.getAsJsonObject("servers").entrySet()) {
                    try {
                        int v = e.getValue().getAsInt();
                        if (v >= 0 && v < SLOTS) SERVERS.put(e.getKey(), v);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            if (o.has("slots") && o.get("slots").isJsonArray()) {
                int i = 0;
                for (JsonElement e : o.getAsJsonArray("slots")) {
                    if (i >= SLOTS) break;
                    try {
                        if (e.isJsonObject()) {
                            JsonObject slot = e.getAsJsonObject();
                            NAMES[i] = slot.has("name") ? clean(slot.get("name").getAsString()) : null;
                            SAVED[i] = slot.has("saved") ? slot.get("saved").getAsString() : null;
                            IDS[i] = slot.has("id") ? cleanId(slot.get("id").getAsString()) : null;
                            DATA[i] = slot.has("data") && slot.get("data").isJsonObject() ? slot.getAsJsonObject("data") : null;
                        }
                    } catch (RuntimeException malformedSlot) {
                        Zeus.LOG.warn("Ignoring malformed Zeus profile slot {}", i + 1, malformedSlot);
                    }
                    i++;
                }
            }
        } catch (IOException | RuntimeException e) {
            Zeus.LOG.warn("Could not read the profiles", e);
        }
    }

    private static void write() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("schema", 2);
            o.addProperty("active", active);
            com.google.gson.JsonArray a = new com.google.gson.JsonArray();
            for (int i = 0; i < SLOTS; i++) {
                JsonObject s = new JsonObject();
                if (NAMES[i] != null) s.addProperty("name", NAMES[i]);
                if (SAVED[i] != null) s.addProperty("saved", SAVED[i]);
                if (IDS[i] != null) s.addProperty("id", IDS[i]);
                if (DATA[i] != null) s.add("data", DATA[i]);
                a.add(s);
            }
            o.add("slots", a);
            JsonObject servers = new JsonObject();
            for (java.util.Map.Entry<String, Integer> e : SERVERS.entrySet()) servers.addProperty(e.getKey(), e.getValue());
            o.add("servers", servers);
            Files.createDirectories(file().getParent());
            Path target = file();
            Path tmp = target.resolveSibling("zeus-profiles.json.tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            Zeus.LOG.warn("Could not save the profiles", e);
        }
    }

    public static void onSaved(JsonObject snapshot) {
        if (!loaded || !followOn || active < 0 || active >= SLOTS || DATA[active] == null && NAMES[active] == null) return;
        DATA[active] = Zeus.snapshot(true);
        SAVED[active] = stamp();
        write();
    }

    private static String stamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", java.util.Locale.ENGLISH));
    }

    private static String clean(String s) {
        if (s == null) return null;
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (b.length() >= 24) break;
            if (c >= 32 && c != 127 && c != '§') b.append(c);
        }
        String r = b.toString().trim();
        return r.isEmpty() ? null : r;
    }

    static String cleanId(String s) {
        if (s == null) return null;
        StringBuilder b = new StringBuilder();
        for (char c : s.trim().toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            if (b.length() >= 16) break;
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_') b.append(c);
        }
        return b.length() == 0 ? null : b.toString();
    }

    static int slotOfId(String id) {
        String c = cleanId(id);
        if (c == null) return -1;
        for (int i = 0; i < SLOTS; i++) if (c.equals(IDS[i])) return i;
        return -1;
    }

    private void refreshLabels() {
        for (int i = 0; i < SLOTS; i++) {
            String n = NAMES[i] != null ? NAMES[i] : DATA[i] != null ? "Profile " + (i + 1) : "Empty";
            labels[i] = (i + 1) + (i == active ? "*" : "") + " " + n + (IDS[i] != null ? "  [" + IDS[i] + "]" : "");
        }
    }

    private int chosen() {
        return slot == null ? 0 : slot.index();
    }

    private List<String> details() {
        int i = chosen();
        List<String> out = new ArrayList<>();
        out.add((NAMES[i] != null ? NAMES[i] : "Profile " + (i + 1)) + (i == active ? "  (active)" : ""));
        out.add("ID: " + (IDS[i] != null ? IDS[i] : "none"));
        out.add(DATA[i] == null ? "Nothing saved in it yet." : "Saved " + (SAVED[i] == null ? "earlier" : SAVED[i]) + ", " + DATA[i].toString().length() / 1000 + " kB of settings.");
        out.add(message);
        return out;
    }

    private void say(String m) {
        message = m;
        refreshLabels();
        Zeus.toast(m, true);
    }

    private void saveHere() {
        int i = chosen();
        DATA[i] = Zeus.snapshot(true);
        SAVED[i] = stamp();
        active = i;
        write();
        say("Profile " + (i + 1) + " saved and active");
    }

    private void loadHere() {
        int i = chosen();
        if (DATA[i] == null) {
            message = "Profile " + (i + 1) + " is empty. Save into it first.";
            Zeus.toast(message, false);
            return;
        }
        Zeus.apply(DATA[i]);
        active = i;
        write();
        say("Profile " + (i + 1) + " loaded");
    }

    private void rename() {
        String clip = "";
        try {
            clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        } catch (RuntimeException ignored) {
        }
        nameWith(clip);
    }

    private void renameTyped() {
        nameWith(typedName.get());
        typedName.set("");
    }

    private void typedCodeHere() {
        codeWith(typedCode.get());
        typedCode.set("");
    }

    private void nameWith(String clip) {
        String n = clean(clip == null ? "" : clip.replaceAll("[\\r\\n]+", " "));
        if (n == null) {
            message = "That is not a usable name.";
            Zeus.toast(message, false);
            return;
        }
        int i = chosen();
        NAMES[i] = n;
        write();
        say("Profile " + (i + 1) + " is now called " + n);
    }

    private void setIdClipboard() {
        String clip = "";
        try {
            clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        } catch (RuntimeException ignored) {
        }
        idWith(clip);
    }

    private void setIdTyped() {
        idWith(typedId.get());
        typedId.set("");
    }

    private void idWith(String raw) {
        String id = cleanId(raw);
        if (id == null) {
            message = "An ID needs letters, digits, - or _.";
            Zeus.toast(message, false);
            return;
        }
        int i = chosen();
        int other = slotOfId(id);
        if (other >= 0 && other != i) {
            message = "The ID " + id + " belongs to profile " + (other + 1) + " already.";
            Zeus.toast(message, false);
            return;
        }
        IDS[i] = id;
        write();
        say("Profile " + (i + 1) + " has the ID " + id);
    }

    private void clearId() {
        int i = chosen();
        IDS[i] = null;
        write();
        say("Profile " + (i + 1) + " has no ID now");
    }

    private void loadById() {
        String id = cleanId(findId.get());
        findId.set("");
        int i = slotOfId(id);
        if (i < 0) {
            message = "No profile has the ID " + (id == null ? "(empty)" : id) + ".";
            Zeus.toast(message, false);
            return;
        }
        slot.set(i);
        loadHere();
    }

    private void clearHere() {
        int i = chosen();
        IDS[i] = null;
        DATA[i] = null;
        NAMES[i] = null;
        SAVED[i] = null;
        if (active == i) active = -1;
        write();
        say("Profile " + (i + 1) + " emptied");
    }

    private void copyCode() {
        int i = chosen();
        JsonObject data = DATA[i] != null ? DATA[i] : Zeus.snapshot(true);
        try {
            String code = encode(NAMES[i], IDS[i], data);
            Minecraft.getInstance().keyboardHandler.setClipboard(code);
            say((DATA[i] == null ? "Your current settings" : "Profile " + (i + 1)) + " copied as a share code (" + code.length() + " letters)");
        } catch (IOException | RuntimeException e) {
            message = "Could not make a code.";
            Zeus.toast(message, false);
        }
    }

    private void pasteCode() {
        String clip = "";
        try {
            clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        } catch (RuntimeException ignored) {
        }
        codeWith(clip);
    }

    private void codeWith(String clip) {
        Object[] got = decode(clip);
        if (got == null) {
            message = "That is not a Zeus share code.";
            Zeus.toast(message, false);
            return;
        }
        int i = chosen();
        DATA[i] = (JsonObject) got[1];
        if (got[0] != null) NAMES[i] = (String) got[0];
        if (got.length > 2 && got[2] != null) {
            int other = slotOfId((String) got[2]);
            if (other < 0 || other == i) IDS[i] = (String) got[2];
        }
        SAVED[i] = stamp();
        active = i;
        write();
        Zeus.apply(DATA[i]);
        say("Code put into profile " + (i + 1) + " and loaded");
    }

    public static String encode(String name, JsonObject data) throws IOException {
        return encode(name, null, data);
    }

    public static String encode(String name, String id, JsonObject data) throws IOException {
        JsonObject d = new JsonObject();
        if (data.has("modules")) d.add("modules", data.get("modules"));
        if (data.has("ui") && data.get("ui").isJsonObject() && data.getAsJsonObject("ui").size() > 0) d.add("ui", data.get("ui"));
        JsonObject o = new JsonObject();
        if (name != null) o.addProperty("n", name);
        if (id != null) o.addProperty("i", id);
        o.add("d", d);
        byte[] in = o.toString().getBytes(StandardCharsets.UTF_8);
        Deflater def = new Deflater(Deflater.BEST_COMPRESSION, true);
        def.setInput(in);
        def.finish();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!def.finished()) bytes.write(buf, 0, def.deflate(buf));
        def.end();
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
    }

    public static Object[] decode(String text) {
        if (text == null) return null;
        String t = text.trim().replaceAll("\\s+", "");
        boolean old = t.startsWith(OLD_PREFIX);
        if ((!old && !t.startsWith(PREFIX)) || t.length() > MAX_CODE) return null;
        try {
            byte[] raw = Base64.getUrlDecoder().decode(t.substring(PREFIX.length()));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            if (old) {
                try (GZIPInputStream z = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                    int n;
                    while ((n = z.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        if (out.size() > 4_000_000) return null;
                    }
                }
            } else {
                Inflater inf = new Inflater(true);
                inf.setInput(raw);
                try {
                    while (!inf.finished()) {
                        int n = inf.inflate(buf);
                        if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                        out.write(buf, 0, n);
                        if (out.size() > 4_000_000) return null;
                    }
                } catch (java.util.zip.DataFormatException ex) {
                    return null;
                } finally {
                    inf.end();
                }
            }
            JsonElement e = JsonParser.parseString(out.toString(StandardCharsets.UTF_8));
            if (!e.isJsonObject()) return null;
            JsonObject o = e.getAsJsonObject();
            String key = o.has("d") ? "d" : "data";
            if (!o.has(key) || !o.get(key).isJsonObject()) return null;
            JsonObject data = o.getAsJsonObject(key);
            stripSecrets(data);
            String nm = o.has("n") ? o.get("n").getAsString() : o.has("name") ? o.get("name").getAsString() : null;
            String id = o.has("i") ? cleanId(o.get("i").getAsString()) : null;
            return new Object[] {nm == null ? null : clean(nm), data, id};
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private static void stripSecrets(JsonObject data) {
        try {
            if (!data.has("modules") || !data.get("modules").isJsonObject()) return;
            for (var e : data.getAsJsonObject("modules").entrySet()) {
                Module m = Zeus.get(e.getKey());
                if (m == null || !e.getValue().isJsonObject()) continue;
                JsonObject s = e.getValue().getAsJsonObject().has("settings") && e.getValue().getAsJsonObject().get("settings").isJsonObject()
                        ? e.getValue().getAsJsonObject().getAsJsonObject("settings") : null;
                if (s == null) continue;
                for (Setting st : m.settings()) if (st instanceof Setting.Secret) s.remove(st.name);
            }
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public String info() {
        return active >= 0 ? "#" + (active + 1) : null;
    }
}
