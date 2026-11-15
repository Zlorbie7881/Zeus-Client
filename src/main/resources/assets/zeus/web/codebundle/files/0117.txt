package com.zlorbie788.zeus.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;

public final class AuctionMind {
    private AuctionMind() {}

    public record Verdict(boolean buy, double score, double discount, double fair, double confidence, int samples) {}

    private static final class Book {
        double mean;
        double deviation;
        int samples;
        double ema;
        long last;
    }

    private static final Map<String, Book> BOOK = new ConcurrentHashMap<>();
    private static final java.util.regex.Pattern CLEAN = java.util.regex.Pattern.compile("[^a-z0-9]+");
    private static final int MAX_BOOKS = 4000;
    private static final int SAVED_BOOKS = 800;
    private static final long SAVE_EVERY_MS = 60_000L;
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("zeus-prices.json");
    private static volatile boolean loaded;
    private static volatile boolean dirty;
    private static volatile long savedAt;

    private static void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.isRegularFile(FILE)) return;
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                JsonArray a = entry.getValue().getAsJsonArray();
                Book book = new Book();
                book.mean = a.get(0).getAsDouble();
                book.deviation = a.get(1).getAsDouble();
                book.samples = a.get(2).getAsInt();
                book.ema = a.size() > 3 && Double.isFinite(a.get(3).getAsDouble()) ? a.get(3).getAsDouble() : book.mean;
                book.last = a.size() > 4 ? a.get(4).getAsLong() : 0L;
                if (book.samples > 0 && Double.isFinite(book.mean) && book.mean > 0) BOOK.put(entry.getKey(), book);
            }
        } catch (Exception ignored) {
            BOOK.clear();
        }
    }

    public static void flush() {
        savedAt = 0;
        save();
    }

    static void save() {
        if (!dirty || System.currentTimeMillis() - savedAt < SAVE_EVERY_MS) return;
        dirty = false;
        savedAt = System.currentTimeMillis();
        JsonObject root = new JsonObject();
        BOOK.entrySet().stream()
                .sorted((a, b) -> Integer.compare(samples(b.getValue()), samples(a.getValue())))
                .limit(SAVED_BOOKS)
                .forEach(entry -> {
                    Book book = entry.getValue();
                    synchronized (book) {
                        JsonArray a = new JsonArray();
                        a.add(book.mean);
                        a.add(book.deviation);
                        a.add(book.samples);
                        a.add(book.ema);
                        a.add(book.last);
                        root.add(entry.getKey(), a);
                    }
                });
        try {
            Files.createDirectories(FILE.getParent());
            Path temp = FILE.resolveSibling("zeus-prices.json.tmp");
            Files.writeString(temp, root.toString(), StandardCharsets.UTF_8);
            Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }

    private static int samples(Book book) {
        synchronized (book) {
            return book.samples;
        }
    }

    static void observe(String name, double price) {
        load();
        if (name == null || !Double.isFinite(price) || price <= 0) return;
        String key = key(name);
        if (key.isEmpty()) return;
        Book book = BOOK.computeIfAbsent(key, k -> new Book());
        synchronized (book) {
            book.samples++;
            if (book.samples == 1) {
                book.mean = price;
                book.deviation = 0;
            } else {
                double delta = price - book.mean;
                book.mean += delta / book.samples;
                book.deviation += delta * (price - book.mean);
            }
            book.ema = book.ema <= 0 ? price : book.ema * 0.68 + price * 0.32;
            book.last = System.currentTimeMillis();
        }
        if (BOOK.size() > MAX_BOOKS) trim();
        dirty = true;
        save();
    }

    static int books() {
        load();
        return BOOK.size();
    }

    static void trim() {
        BOOK.entrySet().removeIf(entry -> {
            Book book = entry.getValue();
            synchronized (book) {
                return book.samples < 2;
            }
        });
        if (BOOK.size() > MAX_BOOKS) BOOK.clear();
    }

    static double fair(String name) {
        load();
        Book book = BOOK.get(key(name));
        if (book == null) return -1;
        synchronized (book) {
            if (book.samples < 3) return -1;
            return book.ema > 0 ? book.mean * 0.72 + book.ema * 0.28 : book.mean;
        }
    }

    static double momentum(String name) {
        load();
        Book book = BOOK.get(key(name));
        if (book == null) return 0;
        synchronized (book) {
            if (book.samples < 4 || book.ema <= 0 || book.mean <= 0) return 0;
            return (book.ema - book.mean) / book.mean;
        }
    }

    static double spread(String name) {
        Book book = BOOK.get(key(name));
        if (book == null) return 0;
        synchronized (book) {
            return book.samples >= 3 ? Math.sqrt(Math.max(0, book.deviation / (book.samples - 1))) : 0;
        }
    }

    static Verdict judge(String name, double price, int count, double ruleMax, double learned, double wantUnderMarket) {
        double seen = fair(name);
        double fair = blend(learned, seen);
        double confidence = confidence(name, learned);
        double spread = spread(name);
        double limit;
        if (ruleMax > 0) limit = ruleMax;
        else if (fair > 0) {
            double want = Math.max(1, Math.min(95, wantUnderMarket));
            double volatility = fair > 0 ? Math.min(15, spread / fair * 100.0 * 0.6) : 0;
            double drift = momentum(name);
            double adjust = drift < -0.12 ? 6 : drift > 0.12 ? -4 : 0;
            limit = fair * (1.0 - Math.min(0.92, (want + volatility + adjust) / 100.0));
        } else {
            return new Verdict(false, 0, 0, -1, 0, samples(name) + (learned > 0 ? 3 : 0));
        }
        if (price <= 0 || price > limit) {
            return new Verdict(false, 0, 0, fair, confidence, samples(name));
        }
        double base = ruleMax > 0 ? ruleMax : fair;
        double discount = base > 0 ? Math.max(0, (1.0 - price / base) * 100.0) : 0;
        double margin = (fair > 0 ? fair - price : base - price) * Math.max(1, count);
        double stability = 1.0 / (1.0 + (fair > 0 ? spread / Math.max(1.0, fair) : 0));
        double score = margin * (0.35 + 0.65 * confidence) * (0.55 + 0.45 * stability);
        return new Verdict(true, score, discount, fair, confidence, samples(name));
    }

    private static double blend(double learned, double seen) {
        if (learned > 0 && seen > 0) return learned * 0.55 + seen * 0.45;
        if (learned > 0) return learned;
        return seen;
    }

    private static double confidence(String name, double learned) {
        int count = samples(name);
        double fromBook = Math.min(1.0, count / 20.0);
        double fromLearned = learned > 0 ? 0.45 : 0;
        double fresh = 0;
        Book book = BOOK.get(key(name));
        if (book != null) {
            synchronized (book) {
                if (book.last > 0 && System.currentTimeMillis() - book.last < 7_200_000L) fresh = 0.2;
            }
        }
        return Math.min(1.0, Math.max(fromBook, fromLearned) + fresh);
    }

    private static int samples(String name) {
        Book book = BOOK.get(key(name));
        if (book == null) return 0;
        synchronized (book) {
            return book.samples;
        }
    }

    private static String key(String name) {
        return name == null ? "" : CLEAN.matcher(name.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }
}
