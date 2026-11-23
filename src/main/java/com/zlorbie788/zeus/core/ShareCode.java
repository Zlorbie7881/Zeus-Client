package com.zlorbie788.zeus.core;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Short share codes for Zeus profiles.
 *
 * <p>A profile code is a long block of text that is fine to paste into a chat once, but not to
 * type or remember. This uploads that block to a plain key/value text service and hands back a
 * short code such as {@code SC-482913} or {@code SC-PVP}, which is what the code turns back into
 * the full profile on the other side. Nothing but the profile itself is uploaded: a share code
 * never contains a webhook address or any other secret, because profiles are encoded with
 * {@link com.zlorbie788.zeus.core.Zeus#snapshot(boolean) snapshot(true)}.
 */
public final class ShareCode {
    public static final String PREFIX = "SC-";
    public static final int NAME_MIN = 3;
    public static final int NAME_MAX = 16;

    private static final String BASE = "https://textdb.online/";
    private static final String KEY = "zeusclient-cfg-";
    private static final Pattern SHORT = Pattern.compile("(?i)^[SY]C-?([A-Za-z0-9_]{3,16})$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8L))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private ShareCode() {
    }

    /** The code somebody copied, without the prefix, or null when the text is not a short code. */
    public static String shortCode(String text) {
        if (text == null) return null;
        Matcher m = SHORT.matcher(text.replaceAll("\\s+", ""));
        return m.matches() ? m.group(1).toUpperCase(Locale.ROOT) : null;
    }

    /** A share name made of letters, digits and _ only, at most {@link #NAME_MAX} long. */
    public static String cleanName(String text) {
        if (text == null) return "";
        String name = text.strip();
        if (name.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) name = name.substring(PREFIX.length());
        name = name.replaceAll("[^A-Za-z0-9_]", "").toUpperCase(Locale.ROOT);
        return name.length() > NAME_MAX ? name.substring(0, NAME_MAX) : name;
    }

    /**
     * Uploads a profile code and returns the short code to hand out. An empty {@code name} asks
     * for a random six digit code.
     */
    public static CompletableFuture<String> upload(String payload, String name) {
        return CompletableFuture.supplyAsync(() -> {
            if (payload == null || payload.isBlank()) throw new IllegalArgumentException("There is nothing to upload.");
            if (name != null && !name.isEmpty()) {
                if (name.length() < NAME_MIN) {
                    throw new IllegalArgumentException("A share name needs at least " + NAME_MIN + " letters or numbers.");
                }
                if (!read(name).isEmpty()) {
                    throw new IllegalArgumentException("SC-" + name + " is already taken. Pick another share name.");
                }
                write(name, payload);
                return PREFIX + name;
            }
            for (int attempt = 0; attempt < 6; attempt++) {
                String digits = Integer.toString(100000 + RANDOM.nextInt(900000));
                if (read(digits).isEmpty()) {
                    write(digits, payload);
                    return PREFIX + digits;
                }
            }
            throw new IllegalStateException("No free code was found. Try again or set a share name.");
        });
    }

    /** Reads the profile code behind a short code. */
    public static CompletableFuture<String> download(String code) {
        return CompletableFuture.supplyAsync(() -> {
            String clean = cleanName(code);
            if (clean.length() < NAME_MIN) throw new IllegalArgumentException("That short code is too short.");
            String payload = read(clean);
            if (payload.isEmpty()) throw new IllegalArgumentException("There is no config with the code " + PREFIX + clean + ".");
            return payload;
        });
    }

    private static void write(String code, String payload) {
        String body = "value=" + URLEncoder.encode(payload, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "update/?key=" + key(code)))
                .timeout(Duration.ofSeconds(20L))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        String answer = send(request);
        if (!answer.replace(" ", "").contains("\"status\":1")) throw new IllegalStateException("The upload was refused.");
        if (!read(code).equals(payload)) throw new IllegalStateException("The upload could not be checked.");
    }

    private static String read(String code) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + key(code)))
                .timeout(Duration.ofSeconds(15L))
                .GET()
                .build();
        return send(request).strip();
    }

    private static String key(String code) {
        return KEY + code.toLowerCase(Locale.ROOT);
    }

    private static String send(HttpRequest request) {
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("The share server answered " + response.statusCode() + ".");
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The connection was interrupted.");
        } catch (IOException e) {
            throw new IllegalStateException("The share server could not be reached.");
        }
    }
}
