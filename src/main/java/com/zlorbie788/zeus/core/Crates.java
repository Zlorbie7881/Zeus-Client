package com.zlorbie788.zeus.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zlorbie788.zeus.ui.KnifeHD;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Zeus Crates: the knives you own, the crates and keys you hold, Zeus tokens earned by playing,
 * and promo codes. Everything is kept in config/zeus/crates.dat, signed so a hand-edited file is
 * noticed and reset rather than trusted.
 */
public final class Crates {
    private Crates() {
    }

    // ------------------------------------------------------------------ rarities
    public enum Rarity {
        MORTAL("Mortal", 0xFF4B69FF, 6000, 1),
        HEROIC("Heroic", 0xFF8847FF, 2500, 2),
        DIVINE("Divine", 0xFFD32CE6, 1000, 4),
        TITAN("Titan", 0xFFEB4B4B, 400, 8),
        OLYMPIAN("Olympian", 0xFFFFC83A, 100, 20);

        public final String label;
        public final int colour;
        /** Weight out of 10 000. */
        public final int weight;
        /** Tokens given back when the knife is already owned. */
        public final int refund;

        Rarity(String label, int colour, int weight, int refund) {
            this.label = label;
            this.colour = colour;
            this.weight = weight;
            this.refund = refund;
        }

        public String odds() {
            return weight % 100 == 0 ? (weight / 100) + "%" : String.format(Locale.ROOT, "%.1f%%", weight / 100f);
        }
    }

    public record Item(String id, Rarity rarity) {
    }

    public static final class Crate {
        public final String id;
        public final String name;
        public final String tagline;
        public final int colour;
        public final int dark;
        public final List<Item> items = new ArrayList<>();

        Crate(String id, String name, String tagline, int colour, int dark) {
            this.id = id;
            this.name = name;
            this.tagline = tagline;
            this.colour = colour;
            this.dark = dark;
        }

        Crate add(Rarity r, String... ids) {
            for (String s : ids) items.add(new Item(s, r));
            return this;
        }

        public Item flagship() {
            for (Item it : items) if (it.rarity == Rarity.OLYMPIAN) return it;
            return items.get(0);
        }
    }

    public static final String STARTER = "karambit_stock";
    public static final int KEY_PRICE = 30;
    public static final long TOKEN_MS = 5 * 60_000L;
    public static final long DROP_MS = 90 * 60_000L;
    private static final long IDLE_MS = 90_000L;

    public static final List<Crate> CRATES = List.of(
            new Crate("olympus", "Olympus Crate", "Marble, gold and the forge of the gods", 0xFFF5C04A, 0xFF3A2A08)
                    .add(Rarity.OLYMPIAN, "bowie_olympus")
                    .add(Rarity.TITAN, "tac_olympus", "ursus_gold")
                    .add(Rarity.DIVINE, "tac_damascus", "m9_damascus", "skeleton_case")
                    .add(Rarity.HEROIC, "tac_tiger", "bowie_damascus", "tac_case")
                    .add(Rarity.MORTAL, "huntsman_case", "stiletto_damascus", "stiletto_case"),
            new Crate("tempest", "Tempest Crate", "Storm steel, charged with lightning", 0xFF5AA8FF, 0xFF0A1A38)
                    .add(Rarity.OLYMPIAN, "tac_lightning")
                    .add(Rarity.TITAN, "huntsman_storm", "karambit_sapphire")
                    .add(Rarity.DIVINE, "tac_doppler", "bowie_sapphire", "ursus_sapphire")
                    .add(Rarity.HEROIC, "tac_urban", "ursus_urban", "skeleton_night")
                    .add(Rarity.MORTAL, "m9_night", "huntsman_night", "stiletto_night"),
            new Crate("hades", "Hades Crate", "Forged in the underworld's fire", 0xFFFF4A4A, 0xFF3A0808)
                    .add(Rarity.OLYMPIAN, "karambit_bloodstone")
                    .add(Rarity.TITAN, "karambit_inferno", "bowie_inferno")
                    .add(Rarity.DIVINE, "karambit_crimson", "tac_ruby", "m9_ruby")
                    .add(Rarity.HEROIC, "tac_crimson", "karambit_slaughter", "huntsman_crimson")
                    .add(Rarity.MORTAL, "tac_slaughter", "butterfly_slaughter", "bowie_slaughter"),
            new Crate("nyx", "Nyx Crate", "Night, nebulae and things that shimmer", 0xFFB06AFF, 0xFF1E0A3A)
                    .add(Rarity.OLYMPIAN, "karambit_nebula")
                    .add(Rarity.TITAN, "m9_phantom", "ursus_nebula")
                    .add(Rarity.DIVINE, "skeleton_phantom", "butterfly_ultraviolet", "karambit_fade")
                    .add(Rarity.HEROIC, "tac_ultraviolet", "butterfly_fade", "bowie_fade")
                    .add(Rarity.MORTAL, "tac_fade", "skeleton_fade", "stiletto_fade"),
            new Crate("poseidon", "Poseidon Crate", "Pulled up from the deep", 0xFF2AD0D0, 0xFF052A30)
                    .add(Rarity.OLYMPIAN, "skeleton_abyss")
                    .add(Rarity.TITAN, "m9_frost", "ursus_frost")
                    .add(Rarity.DIVINE, "stiletto_abyss", "tac_emerald", "butterfly_emerald")
                    .add(Rarity.HEROIC, "skeleton_emerald", "huntsman_urban", "huntsman_tiger")
                    .add(Rarity.MORTAL, "m9_camo", "m9_case"),
            new Crate("titan", "Titan Crate", "Loud colours for those who want to be seen", 0xFFFF8A2A, 0xFF3A1A04)
                    .add(Rarity.OLYMPIAN, "butterfly_sunset")
                    .add(Rarity.TITAN, "butterfly_toxic", "karambit_tiger")
                    .add(Rarity.DIVINE, "butterfly_marble", "stiletto_sunset", "tac_marble")
                    .add(Rarity.HEROIC, "huntsman_toxic", "ursus_tiger", "skeleton_marble")
                    .add(Rarity.MORTAL, "stiletto_marble", "bowie_rust", "ursus_ruby"));

    public static Crate crate(String id) {
        for (Crate c : CRATES) if (c.id.equals(id)) return c;
        return null;
    }

    /** Which crate a knife drops from, or null for the starter. */
    public static Crate crateOf(String knifeId) {
        for (Crate c : CRATES) for (Item it : c.items) if (it.id.equals(knifeId)) return c;
        return null;
    }

    public static Rarity rarityOf(String knifeId) {
        for (Crate c : CRATES) for (Item it : c.items) if (it.id.equals(knifeId)) return it.rarity;
        return Rarity.MORTAL;
    }

    // ------------------------------------------------------------------ promo codes (only hashes ship)
    private static final String SALT = "zeus-crates/v1:";
    private static final String[][] CODES = {
            {"b24fdcaaeae2ba1c4b93870c6d9118dbbd875c0b55351b9183cfb4be73399f5e", "olympus:1"},
            {"6718e66764b532e44099122170a4264d27aca09f640028078bb433c61283586e", "tempest:1"},
            {"10f1232395c1faa8b2c7f33de56c22c653bb9cc61655ea0c6a8262a13a89c6d9", "hades:1"},
            {"4a275448bd08bafda5e3d938c5c8dab872c3ba2596c611bff6970bf6cc34c192", "nyx:1"},
            {"26aa4d5d61d2645cb585aa4804a9b67b55ed42fffc859b474f667ee48332de11", "poseidon:1"},
            {"4a16fad809b3ff796ec1e9368ea66a08bb0a1c5162a5a37603633093c927fd52", "titan:1"},
            {"ef1fc15f1f67d9bee21df5421c55483a48a7a39e86e06d64d129448746cd7552", "olympus:1"},
            {"b744fccf81280c30449110f0eea18e89dab36755640b12304e32d53b38553fcb", "tempest:1"},
            {"71cb0cc7ba03bdf0cb9443ef1900a71e864848afec968119f4baf4fc062ed823", "hades:1"},
            {"60c172655ac557267c6d4b0ad9eba73b80467b27ffbd6fd25494405f3fd58f1d", "nyx:1"},
            {"dc71e9afd97ed4f267bef12957b186aebc8497aae383b8622229b19f9d34b4fb", "poseidon:1"},
            {"eaf881392eae913d4c250426f2dfd4e7c4a60380b2620bd10ae5aa2fb659e5f4", "titan:1"},
            {"e83433e8a1e889d97f1cb38a501a92872ee7a6a37e1eb4a19c807d077d3ad497", "random:1"},
            {"6ae9bcae958a658df4ac4df32201f48735efd2cb4179264f5fdd9978018dffdf", "random:1"},
            {"8d3390d145905b700a65b56cd4f483ffb258509ce411a983f488e0129399bdcf", "random:1"},
            {"0620e5852b10f3290b5b7af616ea0abda4fc5d5bf3fab859272221e6cd0d9b17", "random:1"},
            {"dd39df5f91612c820228bb0c80eac09af53a9fe608d16b607e36b814548124ef", "random:1"},
            {"d996da80f9d799993ae8c10959bf5fc6cc28952be8929a01c271bbf0c56d034b", "random:1"},
            {"985f50665a8f4b0379c776e6e9f5f5bf036d58a04e7300df760712053e9114e9", "random:2"},
            {"1047049646f83291dc4d283a8c552e4003988b8675342c24a80f13847fb503de", "random:2"},
            {"c54a27f1a8963fb13f2e02c330615a6be86598f4fc54b4683a09a5e5cf17ad4d", "random:2"},
            {"c1f6e6ecde1405ffc3173ab2a3851e88761b071132743714af3bdcd38189a72f", "random:2"},
            {"d4504159ae8a279e806d6bb5c09ab6450f56576b08f8ccc8fdf4321cabc4add7", "olympus+hades+nyx:1"},
            {"7ea743b2a423332ca763c9aa3140974e3f3fce86acf0e95013534e90470f8eb8", "tempest+poseidon+titan:1"},
            {"05fde1635bc1871f37a4108d5889b2302e47724f36c74f148f2166240af51d19", "all:1"}

    };

    // ------------------------------------------------------------------ state
    private static int tokens;
    private static int keys;
    private static final Map<String, Integer> held = new LinkedHashMap<>();
    private static final Set<String> owned = new LinkedHashSet<>();
    private static final Set<String> redeemed = new LinkedHashSet<>();
    private static long activeMs;
    private static long dropMs;
    private static long earned;
    private static int opened;
    private static boolean loaded;
    private static boolean dirty;
    private static long lastSave;
    private static final SecureRandom RNG = new SecureRandom();

    // activity tracking
    private static double lx, ly, lz;
    private static float lyaw, lpitch;
    private static long lastActive;
    private static long lastTick;

    public static int tokens() {
        load();
        return tokens;
    }

    public static int keys() {
        load();
        return keys;
    }

    public static int held(String crateId) {
        load();
        return held.getOrDefault(crateId, 0);
    }

    public static int heldTotal() {
        load();
        int n = 0;
        for (int v : held.values()) n += v;
        return n;
    }

    public static int opened() {
        load();
        return opened;
    }

    public static boolean owns(String knifeId) {
        load();
        return STARTER.equals(knifeId) || owned.contains(knifeId);
    }

    public static int ownedCount() {
        load();
        int n = 1;
        for (String s : owned) if (!STARTER.equals(s)) n++;
        return n;
    }

    /** The knife id of an HD knife index (the order of knives/hd/tactical.json). */
    public static String idOf(int hd) {
        KnifeHD.Model m = KnifeHD.model(hd);
        return m == null ? "" : m.id;
    }

    public static int hdOf(String id) {
        for (int i = 0; ; i++) {
            KnifeHD.Model m = KnifeHD.model(i);
            if (m == null) return -1;
            if (m.id.equals(id)) return i;
        }
    }

    public static boolean ownsHd(int hd) {
        return owns(idOf(hd));
    }

    public static int starterHd() {
        int i = hdOf(STARTER);
        return Math.max(0, i);
    }

    /** Milliseconds of active play left before the next token. */
    public static long nextTokenIn() {
        load();
        return Math.max(0, TOKEN_MS - activeMs);
    }

    public static float tokenProgress() {
        load();
        return Math.min(1f, activeMs / (float) TOKEN_MS);
    }

    public static long nextDropIn() {
        load();
        return Math.max(0, DROP_MS - dropMs);
    }

    public static boolean activeNow() {
        return System.currentTimeMillis() - lastActive < IDLE_MS;
    }

    // ------------------------------------------------------------------ actions
    public static boolean buyKey() {
        load();
        if (tokens < KEY_PRICE) return false;
        tokens -= KEY_PRICE;
        keys++;
        save(true);
        return true;
    }

    public record Result(Item item, Crate crate, boolean duplicate, int refund) {
    }

    /** Rolls a knife from a crate without spending anything: the reel is built from these. */
    public static Item roll(Crate c) {
        int total = 0;
        Map<Rarity, List<Item>> by = new HashMap<>();
        for (Item it : c.items) by.computeIfAbsent(it.rarity, k -> new ArrayList<>()).add(it);
        for (Rarity r : by.keySet()) total += r.weight;
        int pick = RNG.nextInt(total);
        for (Rarity r : Rarity.values()) {
            List<Item> l = by.get(r);
            if (l == null) continue;
            if (pick < r.weight) return l.get(RNG.nextInt(l.size()));
            pick -= r.weight;
        }
        return c.items.get(0);
    }

    /** Opens one crate with one key. Returns null when either is missing. */
    public static Result open(Crate c) {
        load();
        if (held(c.id) <= 0 || keys <= 0) return null;
        held.merge(c.id, -1, Integer::sum);
        if (held.get(c.id) <= 0) held.remove(c.id);
        keys--;
        opened++;
        Item it = roll(c);
        boolean dup = owns(it.id);
        int refund = dup ? it.rarity.refund : 0;
        if (dup) tokens += refund;
        else owned.add(it.id);
        save(true);
        return new Result(it, c, dup, refund);
    }

    public record Redeem(boolean ok, String message) {
    }

    public static Redeem redeem(String raw) {
        load();
        String code = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace(' ', '-');
        if (code.isEmpty()) return new Redeem(false, "Type a code first.");
        if (!code.startsWith("ZEUS-") && code.length() == 8) code = "ZEUS-" + code.substring(0, 4) + "-" + code.substring(4);
        String h = sha(SALT + code);
        for (String[] c : CODES) {
            if (!c[0].equals(h)) continue;
            if (redeemed.contains(h)) return new Redeem(false, "That code has already been used on this account.");
            redeemed.add(h);
            String[] parts = c[1].split(":");
            int n = Integer.parseInt(parts[1]);
            List<String> got = new ArrayList<>();
            List<String> which = new ArrayList<>();
            if (parts[0].equals("all")) for (Crate cr : CRATES) which.add(cr.id);
            else if (parts[0].equals("random")) for (int i = 0; i < n; i++) which.add(CRATES.get(RNG.nextInt(CRATES.size())).id);
            else for (String s : parts[0].split("\\+")) for (int i = 0; i < n; i++) which.add(s);
            for (String id : which) {
                held.merge(id, 1, Integer::sum);
                keys++;
                got.add(crate(id).name);
            }
            save(true);
            return new Redeem(true, "Unlocked " + summary(got) + (which.size() == 1 ? " and a key" : " and " + which.size() + " keys") + ".");
        }
        return new Redeem(false, "That code is not valid.");
    }

    private static String summary(List<String> names) {
        Map<String, Integer> c = new LinkedHashMap<>();
        for (String s : names) c.merge(s, 1, Integer::sum);
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Integer> e : c.entrySet()) {
            if (b.length() > 0) b.append(", ");
            b.append(e.getValue() > 1 ? e.getValue() + "x " : "").append(e.getKey());
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ play time
    /** Called every client tick: counts active play towards tokens and crate drops. */
    public static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        long dt = lastTick == 0 ? 0 : Math.min(1000, now - lastTick);
        lastTick = now;
        if (mc.player == null || mc.level == null || mc.isPaused()) return;
        load();
        var p = mc.player;
        if (Math.abs(p.getX() - lx) > 0.05 || Math.abs(p.getY() - ly) > 0.05 || Math.abs(p.getZ() - lz) > 0.05
                || Math.abs(p.getYRot() - lyaw) > 0.5f || Math.abs(p.getXRot() - lpitch) > 0.5f || p.swinging) {
            lastActive = now;
        }
        lx = p.getX();
        ly = p.getY();
        lz = p.getZ();
        lyaw = p.getYRot();
        lpitch = p.getXRot();
        if (!activeNow()) return;          // standing AFK earns nothing
        activeMs += dt;
        dropMs += dt;
        if (activeMs >= TOKEN_MS) {
            activeMs -= TOKEN_MS;
            tokens++;
            earned++;
            dirty = true;
            if (tokens == KEY_PRICE) Zeus.toast("Zeus Crates: you have " + KEY_PRICE + " tokens, enough for a key", true);
            else Zeus.toast("Zeus Crates: +1 Zeus token (" + tokens + ")", true);
        }
        if (dropMs >= DROP_MS) {
            dropMs -= DROP_MS;
            Crate c = CRATES.get(RNG.nextInt(CRATES.size()));
            held.merge(c.id, 1, Integer::sum);
            dirty = true;
            Zeus.toast("Zeus Crates: a " + c.name + " dropped for you!", true);
        }
        if (now - lastSave > 30_000L) save(false);
    }

    // ------------------------------------------------------------------ storage
    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("zeus").resolve("crates.dat");
    }

    private static byte[] secret() {
        // spread out so the key is not one plain string in the jar
        byte[] a = "olympus-forge".getBytes(StandardCharsets.UTF_8);
        byte[] b = new byte[32];
        for (int i = 0; i < b.length; i++) b[i] = (byte) (a[i % a.length] * 31 + i * 17 ^ 0x5A ^ (i * i));
        return b;
    }

    private static String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret(), "HmacSHA256"));
            return hex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    private static String sha(String s) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    private static String hex(byte[] d) {
        StringBuilder b = new StringBuilder();
        for (byte x : d) b.append(String.format("%02x", x));
        return b.toString();
    }

    private static void starter() {
        tokens = 0;
        keys = 0;
        held.clear();
        owned.clear();
        owned.add(STARTER);
        redeemed.clear();
        activeMs = 0;
        dropMs = 0;
        earned = 0;
        opened = 0;
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        starter();
        Path f = file();
        if (!Files.exists(f)) {
            save(true);
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            String body = root.get("data").getAsString();
            if (!sign(body).equals(root.get("sig").getAsString())) {
                Zeus.LOG.warn("Zeus Crates: crates.dat was edited by hand and has been reset");
                Zeus.toast("Zeus Crates: your crate data was edited outside the game, so it was reset", false);
                save(true);
                return;
            }
            JsonObject d = JsonParser.parseString(body).getAsJsonObject();
            tokens = d.get("tokens").getAsInt();
            keys = d.get("keys").getAsInt();
            activeMs = d.get("active").getAsLong();
            dropMs = d.get("drop").getAsLong();
            earned = d.has("earned") ? d.get("earned").getAsLong() : 0;
            opened = d.has("opened") ? d.get("opened").getAsInt() : 0;
            for (var e : d.getAsJsonObject("crates").entrySet()) held.put(e.getKey(), e.getValue().getAsInt());
            for (var e : d.getAsJsonArray("owned")) owned.add(e.getAsString());
            for (var e : d.getAsJsonArray("redeemed")) redeemed.add(e.getAsString());
            owned.add(STARTER);
        } catch (Exception e) {
            Zeus.LOG.warn("Zeus Crates: could not read crates.dat", e);
            starter();
        }
    }

    public static synchronized void save(boolean now) {
        if (!now && !dirty && System.currentTimeMillis() - lastSave < 120_000L) return;
        lastSave = System.currentTimeMillis();
        dirty = false;
        JsonObject d = new JsonObject();
        d.addProperty("tokens", tokens);
        d.addProperty("keys", keys);
        d.addProperty("active", activeMs);
        d.addProperty("drop", dropMs);
        d.addProperty("earned", earned);
        d.addProperty("opened", opened);
        JsonObject c = new JsonObject();
        held.forEach(c::addProperty);
        d.add("crates", c);
        JsonArray o = new JsonArray();
        owned.forEach(o::add);
        d.add("owned", o);
        JsonArray r = new JsonArray();
        redeemed.forEach(r::add);
        d.add("redeemed", r);
        String body = new Gson().toJson(d);
        JsonObject root = new JsonObject();
        root.addProperty("data", body);
        root.addProperty("sig", sign(body));
        try {
            Files.createDirectories(file().getParent());
            Path tmp = file().resolveSibling("crates.dat.tmp");
            Files.writeString(tmp, new Gson().toJson(root));
            Files.move(tmp, file(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Zeus.LOG.warn("Zeus Crates: could not save crates.dat", e);
        }
    }
}
