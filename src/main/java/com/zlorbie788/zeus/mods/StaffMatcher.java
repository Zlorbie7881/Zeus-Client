package com.zlorbie788.zeus.mods;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides whether a player's tab-list text says "staff". Written to follow the common DonutSMP staff conventions and kept
 * compatible with it: rank words (also written in small capitals or with accents), marker glyphs (stars and private-use icon
 * glyphs), a team name that starts with a two digit rank number, and a list of names that are always staff.
 */
public final class StaffMatcher {
    public static final String DEFAULT_RANK_WORDS = "owner, co-owner, manager, admin, administrator, developer, dev, sr-admin, sr-mod, senior-mod, moderator, mod, trial-mod, sr-helper, senior-helper, helper, trial-helper, support, staff";
    public static final String DEFAULT_MARKERS = "U+2605 U+2606 U+2726 U+2727 U+272A U+272F U+2730 U+2B50";
    public static final String[] MODES = {"Ranks + Markers + Team", "Ranks", "Markers", "Team Rank"};

    /** How a match was made, from the strongest to the weakest evidence. */
    public static final int KIND_KNOWN = 0;
    public static final int KIND_RANK = 1;
    public static final int KIND_TEAM = 2;
    public static final int KIND_MARKER = 3;

    private static final Pattern CODEPOINT = Pattern.compile("(?i)(?:u\\+|\\\\u)([0-9a-f]{4,6})");
    private static final Pattern TEAM_RANK = Pattern.compile("^(\\d{2})-");
    private static final int MAX_SPAN = 3;
    private static final int TEAM_RANK_SLOTS = 100;
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("owner", "Owner"), Map.entry("coowner", "Co-Owner"), Map.entry("manager", "Manager"), Map.entry("admin", "Admin"),
            Map.entry("administrator", "Admin"), Map.entry("sradmin", "Sr. Admin"), Map.entry("developer", "Dev"), Map.entry("dev", "Dev"),
            Map.entry("srmod", "Sr. Mod"), Map.entry("seniormod", "Sr. Mod"), Map.entry("moderator", "Mod"), Map.entry("mod", "Mod"),
            Map.entry("trialmod", "Trial Mod"), Map.entry("srhelper", "Sr. Helper"), Map.entry("seniorhelper", "Sr. Helper"),
            Map.entry("helper", "Helper"), Map.entry("trialhelper", "Trial Helper"), Map.entry("support", "Support"),
            Map.entry("staff", "Staff"), Map.entry("builder", "Builder"));
    private static final Map<Integer, Character> SMALL_CAPS = smallCaps();

    private final boolean useRanks;
    private final boolean useMarkers;
    private final boolean useTeamRank;
    private final int teamRankMax;
    private final boolean iconGlyphs;
    private final Map<String, Integer> wordIndex = new HashMap<>();
    private final List<Word> words = new ArrayList<>();
    private final Set<Integer> markers;
    private final Set<String> knownNames;
    private final Set<String> ignoredNames;

    public StaffMatcher(String mode, String rankWords, String markerGlyphs, boolean iconGlyphs, int teamRankMax, String knownNames, String ignoredNames) {
        boolean all = MODES[0].equals(mode);
        this.useRanks = all || MODES[1].equals(mode);
        this.useMarkers = all || MODES[2].equals(mode);
        this.useTeamRank = all || MODES[3].equals(mode);
        this.teamRankMax = teamRankMax;
        this.iconGlyphs = iconGlyphs;
        for (String raw : rankWords.split(",")) {
            String typed = raw.trim();
            String folded = fold(typed).replace(" ", "");
            if (!folded.isEmpty() && !wordIndex.containsKey(folded)) {
                wordIndex.put(folded, words.size());
                words.add(new Word(folded, LABELS.getOrDefault(folded, capitalize(typed))));
            }
        }
        this.markers = parseMarkers(markerGlyphs);
        this.knownNames = parseNames(knownNames);
        this.ignoredNames = parseNames(ignoredNames);
    }

    /** The result of a match: the rank to show, how senior it is (lower is more senior) and how it was found. */
    public record Match(String rank, int seniority, int kind) {}

    private record Word(String folded, String label) {}

    public Match match(String name, String teamName, String... tabTexts) {
        if (name == null || name.isEmpty()) return null;
        String key = name.toLowerCase(Locale.ROOT);
        if (ignoredNames.contains(key)) return null;
        StringBuilder combined = new StringBuilder();
        for (String text : tabTexts) {
            if (text != null && !text.isEmpty()) combined.append(text).append(' ');
        }
        if (teamName != null && !teamName.isEmpty()) combined.append(teamName).append(' ');
        String around = withoutName(stripLegacyCodes(combined.toString()), name);
        Word rank = useRanks ? bestRank(around) : null;
        if (rank != null) return new Match(rank.label(), wordIndex.get(rank.folded()), KIND_RANK);
        int teamRank = useTeamRank ? teamRank(teamName) : -1;
        if (teamRank >= 0 && teamRank <= teamRankMax) return new Match("Rank " + teamRankLabel(teamRank), words.size() + teamRank, KIND_TEAM);
        if (knownNames.contains(key)) return new Match("Staff", words.size() + TEAM_RANK_SLOTS, KIND_KNOWN);
        if (useMarkers && hasMarker(around)) return new Match("Staff", words.size() + TEAM_RANK_SLOTS, KIND_MARKER);
        return null;
    }

    public static int teamRank(String teamName) {
        if (teamName == null || teamName.length() < 3) return -1;
        Matcher m = TEAM_RANK.matcher(teamName);
        return m.lookingAt() ? Integer.parseInt(m.group(1)) : -1;
    }

    public static String teamRankLabel(int teamRank) {
        return teamRank < 10 ? "0" + teamRank : Integer.toString(teamRank);
    }

    private Word bestRank(String text) {
        if (words.isEmpty()) return null;
        List<String> tokens = tokens(text);
        int best = Integer.MAX_VALUE;
        int matchedSpan;
        for (int i = 0; i < tokens.size(); i += Math.max(1, matchedSpan)) {
            matchedSpan = 0;
            for (int span = Math.min(MAX_SPAN, tokens.size() - i); span >= 1; span--) {
                Integer index = wordIndex.get(String.join("", tokens.subList(i, i + span)));
                if (index != null) {
                    best = Math.min(best, index);
                    matchedSpan = span;
                    break;
                }
            }
        }
        return best == Integer.MAX_VALUE ? null : words.get(best);
    }

    static List<String> tokens(String text) {
        String folded = fold(text);
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < folded.length(); i++) {
            char c = folded.charAt(i);
            if (c >= 'a' && c <= 'z') {
                current.append(c);
            } else if (current.length() > 0) {
                out.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) out.add(current.toString());
        return out;
    }

    static String stripLegacyCodes(String text) {
        if (text.indexOf('§') < 0) return text;
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§') i++;
            else out.append(c);
        }
        return out.toString();
    }

    static String fold(String text) {
        String decomposed = Normalizer.normalize(text.replace('ǫ', 'q'), Normalizer.Form.NFKD);
        StringBuilder out = new StringBuilder(decomposed.length());
        int i = 0;
        while (i < decomposed.length()) {
            int cp = decomposed.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.getType(cp) == Character.NON_SPACING_MARK) continue;
            Character small = SMALL_CAPS.get(cp);
            if (small != null) {
                out.append(small.charValue());
            } else {
                int lower = Character.toLowerCase(cp);
                out.append(lower >= 'a' && lower <= 'z' ? (char) lower : ' ');
            }
        }
        return out.toString();
    }

    private boolean hasMarker(String text) {
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (markers.contains(cp) || iconGlyphs && isPrivateUse(cp)) return true;
        }
        return false;
    }

    public static boolean isPrivateUse(int cp) {
        return cp >= 0xE000 && cp <= 0xF8FF || cp >= 0xF0000 && cp <= 0xFFFFD || cp >= 0x100000 && cp <= 0x10FFFD;
    }

    static Set<Integer> parseMarkers(String raw) {
        Set<Integer> out = new HashSet<>();
        for (String token : raw.split("[,\\s]+")) {
            if (token.isEmpty()) continue;
            Matcher m = CODEPOINT.matcher(token);
            if (m.matches()) {
                int cp = Integer.parseInt(m.group(1), 16);
                if (Character.isValidCodePoint(cp)) out.add(cp);
            } else {
                token.codePoints().forEach(out::add);
            }
        }
        return out;
    }

    private static Set<String> parseNames(String raw) {
        Set<String> out = new HashSet<>();
        for (String part : raw.split("[,\\s]+")) {
            if (!part.isEmpty()) out.add(part.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static String withoutName(String text, String name) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (!lowerText.contains(lowerName)) return text;
        StringBuilder out = new StringBuilder(text.length());
        int from = 0;
        for (int at = lowerText.indexOf(lowerName); at >= 0; at = lowerText.indexOf(lowerName, from)) {
            out.append(text, from, at).append(' ');
            from = at + lowerName.length();
        }
        return out.append(text, from, text.length()).toString();
    }

    private static String capitalize(String word) {
        String trimmed = word.trim();
        return trimmed.isEmpty() ? "Staff" : trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1);
    }

    private static Map<Integer, Character> smallCaps() {
        int[] codepoints = {0x1D00, 0x0299, 0x1D04, 0x1D05, 0x1D07, 0xA730, 0x0262, 0x029C, 0x026A, 0x1D0A, 0x1D0B, 0x029F, 0x1D0D, 0x0274, 0x1D0F, 0x1D18, 0xA7AF, 0x0280, 0xA731, 0x1D1B, 0x1D1C, 0x1D20, 0x1D21, 0x028F, 0x1D22};
        String letters = "abcdefghijklmnopqrstuvwyz";
        Map<Integer, Character> map = new HashMap<>();
        for (int i = 0; i < codepoints.length; i++) map.put(codepoints[i], letters.charAt(i));
        return Map.copyOf(map);
    }
}
