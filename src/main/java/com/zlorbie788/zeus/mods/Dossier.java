package com.zlorbie788.zeus.mods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.ui.Draw;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A file on everyone you have met, per server.
 *
 * <p>Anarchy is social, and the same handful of people keep turning up: the one who owns the base you found, the one who
 * killed you twice last week, the alt that logs in ten minutes after the main account. None of that is written down
 * anywhere, so it gets forgotten. This writes it down — when you first saw somebody, where, what they were holding and
 * wearing, how many times their kit changed, and who they keep appearing next to.</p>
 *
 * <p>Everything here is what your own game was already shown. Nothing is sent, queried or inferred from the server.</p>
 */
public final class Dossier extends Module {
    private static final Gson GSON = new GsonBuilder().create();
    private static final int MAX_PLAYERS = 400;
    private static final int MAX_KITS = 10;
    private static final int MAX_ASSOCIATES = 24;

    /** One person. */
    private static final class Rec {
        String name = "";
        long first;
        long last;
        int seen;
        double x, y, z;
        String dim = "";
        String held = "";
        String sig = "";
        int kitChanges;
        final List<long[]> kitTimes = new ArrayList<>();
        final Map<String, Integer> with = new LinkedHashMap<>();
    }

    private static final Map<String, Map<String, Rec>> DATA = new HashMap<>();
    private boolean loaded;
    private boolean dirty;
    private long lastSave;
    private long tickAt;
    private int nearCount;
    private String nearName = "";
    private double nearDist;

    private final Setting.Choice corner = Hud.corner(this, 3);
    private final Setting.Num radius = add(new Setting.Num("Watch radius", "How far around you a player is written down.", 96, 16, 256, 16, " blocks"));
    private final Setting.Bool pairs = add(new Setting.Bool("Notice who travels together", "Counts the times two of them were near each other, which is how alts and groups show up.", true));
    private final Setting.Bool quiet = add(new Setting.Bool("Only people near you", "The card stays away unless somebody is within the watch radius.", true));
    private final Setting.Action read = add(new Setting.Action("Read the dossier", "Prints everything known about the nearest player in chat.", this::readNearest));
    private final Setting.Action forget = add(new Setting.Action("Forget this server", "Removes everyone written down for this server.", this::forgetThisServer));

    public Dossier() {
        super("dossier", "Dossier", "Keeps a file on everyone you meet: first seen, where, what they were wearing, and who they travel with.", Category.PLAYER);
        hudCard();
        alias("intel", "files", "players");
    }

    // ------------------------------------------------------------------ storage

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("zeus-dossiers.json");
    }

    private static String server() {
        return Brain.ctx().server;
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.isRegularFile(file())) return;
            JsonElement root = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8));
            if (!root.isJsonObject()) return;
            for (Map.Entry<String, JsonElement> s : root.getAsJsonObject().entrySet()) {
                Map<String, Rec> people = new HashMap<>();
                for (Map.Entry<String, JsonElement> p : s.getValue().getAsJsonObject().entrySet()) {
                    JsonObject o = p.getValue().getAsJsonObject();
                    Rec r = new Rec();
                    r.name = o.get("name").getAsString();
                    r.first = o.get("first").getAsLong();
                    r.last = o.get("last").getAsLong();
                    r.seen = o.get("seen").getAsInt();
                    r.x = o.get("x").getAsDouble();
                    r.y = o.get("y").getAsDouble();
                    r.z = o.get("z").getAsDouble();
                    r.dim = o.has("dim") ? o.get("dim").getAsString() : "";
                    r.held = o.has("held") ? o.get("held").getAsString() : "";
                    r.sig = o.has("sig") ? o.get("sig").getAsString() : "";
                    r.kitChanges = o.has("kits") ? o.get("kits").getAsInt() : 0;
                    if (o.has("kitTimes")) for (JsonElement t : o.getAsJsonArray("kitTimes")) r.kitTimes.add(new long[]{t.getAsLong()});
                    if (o.has("with")) for (Map.Entry<String, JsonElement> w : o.getAsJsonObject("with").entrySet()) r.with.put(w.getKey(), w.getValue().getAsInt());
                    people.put(p.getKey(), r);
                }
                DATA.put(s.getKey(), people);
            }
        } catch (IOException | RuntimeException e) {
            Zeus.LOG.warn("Could not read the dossiers", e);
        }
    }

    private void save() {
        try {
            JsonObject root = new JsonObject();
            for (Map.Entry<String, Map<String, Rec>> s : DATA.entrySet()) {
                JsonObject people = new JsonObject();
                for (Map.Entry<String, Rec> p : s.getValue().entrySet()) {
                    Rec r = p.getValue();
                    JsonObject o = new JsonObject();
                    o.addProperty("name", r.name);
                    o.addProperty("first", r.first);
                    o.addProperty("last", r.last);
                    o.addProperty("seen", r.seen);
                    o.addProperty("x", Math.round(r.x * 10) / 10.0);
                    o.addProperty("y", Math.round(r.y * 10) / 10.0);
                    o.addProperty("z", Math.round(r.z * 10) / 10.0);
                    o.addProperty("dim", r.dim);
                    o.addProperty("held", r.held);
                    o.addProperty("sig", r.sig);
                    o.addProperty("kits", r.kitChanges);
                    JsonArray times = new JsonArray();
                    for (long[] t : r.kitTimes) times.add(t[0]);
                    o.add("kitTimes", times);
                    JsonObject with = new JsonObject();
                    for (Map.Entry<String, Integer> w : r.with.entrySet()) with.addProperty(w.getKey(), w.getValue());
                    o.add("with", with);
                    people.add(p.getKey(), o);
                }
                root.add(s.getKey(), people);
            }
            Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
            dirty = false;
            lastSave = System.currentTimeMillis();
        } catch (IOException | RuntimeException e) {
            Zeus.LOG.warn("Could not save the dossiers", e);
        }
    }

    // ------------------------------------------------------------------ watching

    @Override
    protected void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        long now = System.currentTimeMillis();
        if (now - tickAt < 900) return;
        tickAt = now;
        load();
        Map<String, Rec> people = DATA.computeIfAbsent(server(), k -> new LinkedHashMap<>());
        double r = radius.get();
        double r2 = r * r;
        List<Player> near = new ArrayList<>();
        nearName = "";
        nearDist = 0;
        Player closest = null;
        double closestD = 0;
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isSpectator()) continue;
            double d = p.distanceToSqr(mc.player);
            if (d > r2) continue;
            near.add(p);
            if (closest == null || d < closestD) {
                closest = p;
                closestD = d;
            }
            Rec rec = people.computeIfAbsent(p.getUUID().toString(), k -> new Rec());
            if (rec.first == 0) rec.first = now;
            rec.name = p.getGameProfile().name();
            rec.last = now;
            rec.seen++;
            rec.x = p.getX();
            rec.y = p.getY();
            rec.z = p.getZ();
            rec.dim = mc.level.dimension().identifier().toString();
            rec.held = item(p.getMainHandItem());
            String sig = kit(p);
            if (!sig.equals(rec.sig)) {
                if (!rec.sig.isEmpty()) {
                    rec.kitChanges++;
                    rec.kitTimes.add(new long[]{now});
                    if (rec.kitTimes.size() > MAX_KITS) rec.kitTimes.remove(0);
                    // a changed kit in front of you is worth a word: it usually means they went and got something
                    if (rec.kitChanges > 1) Zeus.toast(rec.name + " changed kit", true);
                }
                rec.sig = sig;
            }
            dirty = true;
        }
        nearCount = near.size();
        if (closest != null) {
            nearName = closest.getGameProfile().name();
            nearDist = Math.sqrt(closest.distanceToSqr(mc.player));
        }
        if (pairs.on()) {
            for (int i = 0; i < near.size(); i++) {
                for (int j = i + 1; j < near.size(); j++) {
                    if (near.get(i).distanceToSqr(near.get(j)) > 32 * 32) continue;
                    pair(people, near.get(i).getGameProfile().name(), near.get(j).getGameProfile().name());
                    pair(people, near.get(j).getGameProfile().name(), near.get(i).getGameProfile().name());
                }
            }
        }
        if (people.size() > MAX_PLAYERS) {
            String oldest = null;
            long at = Long.MAX_VALUE;
            for (Map.Entry<String, Rec> e : people.entrySet()) {
                if (e.getValue().last < at) {
                    at = e.getValue().last;
                    oldest = e.getKey();
                }
            }
            if (oldest != null) people.remove(oldest);
        }
        if (dirty && now - lastSave > 5000) save();
    }

    private static void pair(Map<String, Rec> people, String a, String b) {
        Rec ra = null;
        for (Rec r : people.values()) if (r.name.equals(a)) { ra = r; break; }
        if (ra == null) return;
        int n = ra.with.getOrDefault(b, 0);
        if (n >= 500) return;
        ra.with.put(b, n + 1);
        if (ra.with.size() > MAX_ASSOCIATES) {
            String drop = null;
            int low = Integer.MAX_VALUE;
            for (Map.Entry<String, Integer> e : ra.with.entrySet()) {
                if (e.getValue() < low) {
                    low = e.getValue();
                    drop = e.getKey();
                }
            }
            if (drop != null) ra.with.remove(drop);
        }
    }

    private static String item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    private static String kit(Player p) {
        StringBuilder b = new StringBuilder();
        b.append(item(p.getMainHandItem())).append('|')
                .append(item(p.getItemBySlot(EquipmentSlot.OFFHAND))).append('|');
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            b.append(item(p.getItemBySlot(s))).append(',');
        }
        return b.toString();
    }

    private static int pieces(Player p) {
        int n = 0;
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!p.getItemBySlot(s).isEmpty()) n++;
        }
        return n;
    }

    /** "3d" / "6h" / "12m" — how long ago something was. */
    private static String ago(long then) {
        if (then <= 0) return "never";
        long ms = Math.max(0, System.currentTimeMillis() - then);
        long m = ms / 60_000L;
        if (m < 1) return "now";
        if (m < 60) return m + "m";
        long h = m / 60;
        if (h < 48) return h + "h";
        return (h / 24) + "d";
    }

    // ------------------------------------------------------------------ reading it

    private void readNearest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            Zeus.toast("Not in a world", false);
            return;
        }
        load();
        Map<String, Rec> people = DATA.getOrDefault(server(), Map.of());
        Player closest = null;
        double best = Double.MAX_VALUE;
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            double d = p.distanceToSqr(mc.player);
            if (d < best) {
                best = d;
                closest = p;
            }
        }
        if (closest == null) {
            Zeus.toast("Nobody is around", false);
            return;
        }
        Rec r = people.get(closest.getUUID().toString());
        if (r == null) {
            Zeus.toast("Nothing written down about " + closest.getGameProfile().name() + " yet", false);
            return;
        }
        int others = 0;
        String closestFriend = null;
        int high = 0;
        for (Map.Entry<String, Integer> e : r.with.entrySet()) {
            others += e.getValue();
            if (e.getValue() > high) {
                high = e.getValue();
                closestFriend = e.getKey();
            }
        }
        Zeus.toast(r.name, true);
        Zeus.toast("First seen " + ago(r.first) + " ago, last " + ago(r.last) + " ago, " + r.seen + " sightings", true);
        Zeus.toast("Last at " + Math.round(r.x) + " " + Math.round(r.y) + " " + Math.round(r.z) + " in " + shortDim(r.dim), true);
        Zeus.toast("Holding " + (r.held.isEmpty() ? "nothing" : r.held) + "  ·  kit changed " + r.kitChanges + " times", true);
        if (closestFriend != null) Zeus.toast("Seen with " + closestFriend + " " + high + " times (" + others + " sightings with others)", true);
        else Zeus.toast("Never seen with anyone else", true);
    }

    /** One person's last known place, for the map. */
    public static final class Sight {
        public final String name;
        public final double x;
        public final double z;
        public final long last;
        public final int seen;

        Sight(String name, double x, double z, long last, int seen) {
            this.name = name;
            this.x = x;
            this.z = z;
            this.last = last;
            this.seen = seen;
        }
    }

    /** Everyone written down on this server, newest sighting first. Read-only, for the local website's map. */
    public static List<Sight> sightings() {
        Dossier d = Zeus.get(Dossier.class);
        if (d == null) return List.of();
        d.load();
        List<Sight> out = new ArrayList<>();
        for (Rec r : DATA.getOrDefault(server(), Map.of()).values()) out.add(new Sight(r.name, r.x, r.z, r.last, r.seen));
        out.sort((a, b) -> Long.compare(b.last, a.last));
        return out;
    }

    private static String shortDim(String dim) {
        int i = dim.indexOf(':');
        return i < 0 ? dim : dim.substring(i + 1);
    }

    private void forgetThisServer() {
        load();
        Map<String, Rec> gone = DATA.remove(server());
        dirty = true;
        save();
        Zeus.toast("Forgot " + (gone == null ? 0 : gone.size()) + " people on this server", true);
    }

    // ------------------------------------------------------------------ the card

    @Override
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (mc.player == null || mc.level == null) return;
        if (quiet.on() && nearCount == 0) return;
        load();
        Rec r = null;
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            double d = p.distanceToSqr(mc.player);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        if (nearCount > 0 && nearest != null) {
            r = DATA.getOrDefault(server(), Map.of()).get(nearest.getUUID().toString());
        }
        String who = nearest == null ? "Nobody near" : (r == null ? nearest.getGameProfile().name() : r.name);
        int dist = nearest == null ? 0 : (int) Math.round(Math.sqrt(best));
        String kit = r == null ? "new" : (r.kitChanges == 0 ? "same kit" : "kit \u00D7" + (r.kitChanges + 1));
        String line2 = nearest == null ? "" :
                (item(nearest.getMainHandItem()).isEmpty() ? "empty hands" : item(nearest.getMainHandItem()))
                        + "  \u00B7  " + pieces(nearest) + "/4";

        int bw = Math.max(150, Hud.captionWidth("Dossier") + Hud.chipWidth((dist > 0 ? dist + "m" : "")) + 26);
        int bh = Hud.TOP + Hud.ROW + 12;
        int[] at = Hud.place(corner.index(), bw, bh, w, h);
        Hud.panel(g, at[0], at[1], bw, bh);
        Hud.header(g, at[0], at[1], bw, "Dossier", nearest == null ? null : dist + "m", Hud.accent());
        int x = at[0] + Hud.PAD;
        int rw = bw - Hud.PAD * 2;
        int y = at[1] + Hud.TOP;
        int nameCol = r == null ? 0xFFFFC857 : 0xFFE6EEF9;
        Draw.bold(g, Draw.fit(who, rw - 46), x, y, nameCol);
        if (r != null) Draw.textRight(g, r.seen + "\u00D7", x + rw, y, 0xFF7F8CA4);
        else if (nearest != null) Draw.textRight(g, kit, x + rw, y, 0xFFFFC857);
        Draw.text(g, Draw.fit(line2, rw), x, y + 12, 0xFF8794AC);
    }

    @Override
    public String info() {
        if (nearCount == 0) return null;
        return nearName;
    }

    private List<String> lines() {
        load();
        Map<String, Rec> people = DATA.getOrDefault(server(), Map.of());
        List<String> out = new ArrayList<>();
        out.add(people.size() + " people written down on this server");
        out.add(String.format(Locale.ROOT, "%.0f", radius.get()) + " block watch radius");
        return out;
    }
}
