package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.ui.Accent;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Media Base Finder: tells you which DonutSMP media (content creators) are on the Goliath you land on, and roughly where
 * their base is.
 *
 * <p>The finding is strict by design. After you arrive on a Goliath (a /rtp, a server hop, a respawn) the
 * module waits three seconds for the scoreboard to settle and then reads the scoreboard teams. A media account sits in a
 * team whose name starts with <b>09-</b> or whose display name carries the camera sign. A team member counts as being on
 * your Goliath when the tab list does <i>not</i> list them (the server hides media from the tab list but keeps their team),
 * or when a game-mode update packet for that account was seen, which proves the server is sending that account to you.
 * Every ten ticks it scans again and tells you who joined.</p>
 *
 * <p>On top of that Zeus gives an <b>estimated base area</b>. The Goliath is a fixed rectangle of the map, so a base on it
 * lies inside that rectangle: Zeus shows its X and Z range, its centre, and how far and in which direction that centre is
 * from you. When a media player's body is loaded in your world (they are close) their exact position is shown instead, and
 * the last place each one was seen is remembered. The area is large (a Goliath is 25k by 50k blocks or bigger): it narrows
 * the search to one Goliath, it does not point at a block.</p>
 *
 * <p>Nothing is sent to the server. The module only reads the scoreboard, the tab list and packets your game already got.</p>
 */
public final class MediaBaseFinder extends Module {
    private static volatile MediaBaseFinder instance;

    private static final String MEDIA_PREFIX = "\uD83D\uDCF9"; // the camera sign, written as an escape so no file encoding can change it
    private static final String MEDIA_TEAM = "09-";
    private static final int SETTLE_TICKS = 60;
    private static final int SCAN_TICKS = 10;
    private static final int MAX_LISTED = 8;
    private static final long RTP_WINDOW_MS = 60_000L;
    private static final int OFF_MAP = Integer.MIN_VALUE;
    private static final long SCREEN_TEXT_MS = 3600L;

    private final Setting.Bool reportEmpty = add(new Setting.Bool("Report Empty Goliaths", "Say so in chat when nobody from the media is on the Goliath you arrived on.", true));
    private final Setting.Bool statusBox = add(new Setting.Bool("Status Box", "A small card on your screen that lists the media on your Goliath and the estimated base area.", true));
    private final Setting.Bool notifications = add(new Setting.Bool("Screen Text and Sound", "After a /rtp, show the number of media bases in big letters in the middle of the screen and play a ding.", true));
    private final Setting.Bool estimate = add(new Setting.Bool("Show estimated area", "Add the X and Z range of the Goliath, its centre and how far that is from you to the chat line and the card.", true));
    private final Setting.Bool showLoaded = add(new Setting.Bool("Exact position when close", "When a media player's body is loaded in your world, show their real coordinates.", true));
    private final Setting.Bool remember = add(new Setting.Bool("Remember sightings", "Keep where each media account was last seen, per server, until you quit the game.", true));
    private final Setting.Bool checkTab = add(new Setting.Bool("Also check tab-list text", "Extra: also treat a tab-list entry that carries the media marker as a match. Off keeps the strict standard behaviour.", false));
    private final Setting.Text marker = add(new Setting.Text("Media sign", "The sign in a team display name that marks a media account.", MEDIA_PREFIX, 8));
    private final Setting.Text teamPrefix = add(new Setting.Text("Media team prefix", "Team names that start with this belong to media accounts.", MEDIA_TEAM, 8));
    private final Setting.Bool wide = add(new Setting.Bool("Wide matching", "Off by default: also match a team whose name only starts with the prefix after a colour code or symbol, and a camera sign in the team's prefix or suffix. Off keeps the strict standard behaviour.", false));
    private final Setting.Choice corner = Hud.corner(this, 0);
    private final Setting.Num scale = add(new Setting.Num("Size", "Size of the status card.", 100, 60, 200, 5, " %")).showIf(statusBox::on);
    private final Setting.Num opacity = add(new Setting.Num("Background", "How solid the status card is.", 88, 30, 100, 5, " %")).showIf(statusBox::on);
    private final Setting.Info here = add(new Setting.Info("Media on this Goliath", this::hereLines));
    private final Setting.Info whyInfo = add(new Setting.Info("Why nothing shows", this::whyLines));
    private final Setting.Action listTeams = add(new Setting.Action("List the scoreboard teams in chat", "Prints the team names this server sent you, so you can see what the media teams are called here.", this::listTeamsInChat));
    private final Setting.Action copyDiag = add(new Setting.Action("Copy diagnostics", "Copies what the finder sees (server, teams, tab list size) to the clipboard, for a bug report.", this::copyDiagnostics));
    private final Setting.Info areaInfo = add(new Setting.Info("Estimated base area", this::areaLines));
    private final Setting.Info seenInfo = add(new Setting.Info("Last seen", this::seenLines));
    private final Setting.Action addWaypoint = add(new Setting.Action("Add the area centre as a waypoint", "Saves the centre of this Goliath as a Zeus waypoint so you can fly towards it.", this::waypoint));
    private final Setting.Action copy = add(new Setting.Action("Copy the estimate", "Copies the Goliath, the media names and the area to the clipboard.", this::copyEstimate));

    /** What the module remembers about one media account. */
    private static final class Sighting {
        String name;
        String goliath = "";
        int code = OFF_MAP;
        long at;
        boolean loaded;
        double x, y, z;
    }

    private final Queue<UUID> modeUpdates = new ConcurrentLinkedQueue<>();
    private final Set<String> proven = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private Set<String> found = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, Sighting> sightings = new LinkedHashMap<>();
    private final Tracker tracker = new Tracker();
    private String goliath = "-";
    private int code = OFF_MAP;
    private int settle;
    private int ticks;
    private boolean reported;
    private boolean afterRtp;
    private volatile long rtpAt;
    private String screenTitle = "";
    private String screenSub = "";
    private long screenUntil;
    private String lastServer = "";
    private int dbgTeams;
    private int dbgMediaTeams;
    private int dbgTab;
    private int dbgScans;
    private String dbgBrand = "";
    private String dbgSample = "";

    public MediaBaseFinder() {
        super("mediabasefinder", "Media Base Finder", "Tells you which DonutSMP media are on the Goliath you land on (after /rtp) and the area where their base most likely is.", Category.SCANNER);
        light();
        hudCard();
        instance = this;
    }

    // ------------------------------------------------------------------ hooks called by the mixins

    /** Called for every player-info packet before the game applies it. A game-mode action on an account proves the server sends it to you. */
    public static void notePlayerInfoUpdate(ClientboundPlayerInfoUpdatePacket packet) {
        MediaBaseFinder f = instance;
        if (f == null || !f.enabled() || packet == null) return;
        try {
            var actions = packet.actions();
            if (!actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE) || actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) return;
            for (var entry : packet.entries()) {
                if (entry != null && entry.profileId() != null) f.modeUpdates.offer(entry.profileId());
            }
        } catch (RuntimeException ignored) {
            // a changed packet layout must never break packet handling
        }
    }

    /** Called with every command your game sends, without the slash. A /rtp followed by a Goliath change makes the arrival an RTP arrival. */
    public static void noteOutboundCommand(String command) {
        MediaBaseFinder f = instance;
        if (f == null || !f.enabled() || command == null) return;
        String text = command.trim().toLowerCase(Locale.ROOT);
        if (text.equals("rtp") || text.startsWith("rtp ")) f.rtpAt = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ life cycle

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        tracker.clear();
        arrive(mc);
    }

    @Override
    protected void onDisable() {
        tracker.clear();
        modeUpdates.clear();
        proven.clear();
        found = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        screenUntil = 0;
    }

    private void arrive(Minecraft mc) {
        modeUpdates.clear();
        proven.clear();
        found = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        settle = SETTLE_TICKS;
        reported = false;
        dbgScans = 0;
        goliath = labelNow(mc);
        long sent = rtpAt;
        afterRtp = sent != 0L && System.currentTimeMillis() - sent < RTP_WINDOW_MS;
    }

    @Override
    protected void onTick(Minecraft mc) {
        ClientPacketListener connection = mc.getConnection();
        AbstractClientPlayer player = mc.player;
        if (connection == null || player == null || mc.level == null) {
            tracker.clear();
            return;
        }
        String server = mc.getCurrentServer() == null ? "singleplayer" : String.valueOf(mc.getCurrentServer().ip);
        if (!server.equals(lastServer)) {
            lastServer = server;
            if (!remember.on()) sightings.clear();
        }
        if (tracker.moved(mc)) arrive(mc);
        drainModeUpdates(connection);
        if (settle > 0) {
            settle--;
            return;
        }
        if (reported && ++ticks % SCAN_TICKS != 0) return;
        Set<String> now = scan(mc, connection, player);
        goliath = labelNow(mc);
        code = codeNow(mc);
        noteSightings(mc, now, server);
        if (!reported) {
            reported = true;
            found = now;
            report(mc, now);
        } else {
            List<String> arrived = new ArrayList<>();
            for (String name : now) if (!found.contains(name)) arrived.add(name);
            found = now;
            if (!arrived.isEmpty()) announce(mc, arrived, now.size(), false);
        }
    }

    // ------------------------------------------------------------------ the finding (strict rules)

    private boolean isMedia(PlayerTeam team) {
        if (team == null) return false;
        String prefix = teamPrefix.get();
        String sign = marker.get();
        String name = team.getName();
        if (!prefix.isEmpty() && name != null && name.startsWith(prefix)) return true;
        if (!sign.isEmpty()) {
            Component display = team.getDisplayName();
            if (display != null && display.getString().contains(sign)) return true;
        }
        if (!wide.on()) return false;
        if (!prefix.isEmpty() && name != null) {
            String clean = clean(name);
            if (clean.startsWith(prefix.toLowerCase(Locale.ROOT))) return true;
        }
        if (!sign.isEmpty()) {
            Component pre = team.getPlayerPrefix();
            Component suf = team.getPlayerSuffix();
            if (pre != null && pre.getString().contains(sign)) return true;
            if (suf != null && suf.getString().contains(sign)) return true;
        }
        return false;
    }

    /** A team name without colour codes and leading symbols, lower case. */
    private static String clean(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00A7') {
                i++;
                continue;
            }
            if (out.length() == 0 && !Character.isLetterOrDigit(c)) continue;
            out.append(Character.toLowerCase(c));
        }
        return out.toString();
    }

    private Set<String> scan(Minecraft mc, ClientPacketListener connection, AbstractClientPlayer player) {
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        String self = player.getGameProfile().name();
        int teams = 0;
        int mediaTeams = 0;
        StringBuilder sample = new StringBuilder();
        for (PlayerTeam team : mc.level.getScoreboard().getPlayerTeams()) {
            teams++;
            if (sample.length() < 400 && teams <= 12) sample.append(sample.length() == 0 ? "" : ", ").append(team.getName());
            if (!isMedia(team)) continue;
            mediaTeams++;
            for (String member : team.getPlayers()) {
                if (member == null || member.equalsIgnoreCase(self)) continue;
                if (connection.getPlayerInfo(member) == null || proven.contains(member)) out.add(member);
            }
        }
        dbgTeams = teams;
        dbgMediaTeams = mediaTeams;
        dbgSample = sample.toString();
        dbgTab = connection.getOnlinePlayers().size();
        dbgScans++;
        String b = Tracker.brand(connection);
        dbgBrand = b == null ? "" : b;
        if (checkTab.on()) {
            String sign = marker.get();
            if (!sign.isEmpty()) {
                for (PlayerInfo info : connection.getOnlinePlayers()) {
                    String name = info.getProfile().name();
                    Component shown = info.getTabListDisplayName();
                    if (name.equalsIgnoreCase(self) || shown == null) continue;
                    if (shown.getString().contains(sign)) out.add(name);
                }
            }
        }
        return out;
    }

    private void drainModeUpdates(ClientPacketListener connection) {
        UUID id;
        while ((id = modeUpdates.poll()) != null) {
            PlayerInfo info = connection.getPlayerInfo(id);
            if (info != null && info.getTeam() != null && isMedia(info.getTeam())) {
                String name = info.getProfile().name();
                if (proven.add(name)) Zeus.LOG.info("[Media Base Finder] game mode update places {} on {}", name, goliath);
            }
        }
    }

    private void report(Minecraft mc, Set<String> now) {
        rtpAt = 0L;
        Zeus.LOG.info("[Media Base Finder] {}: {} media {}", goliath, now.size(), now);
        if (now.isEmpty()) {
            if (reportEmpty.on()) say(mc, Component.literal("No media on ").withStyle(ChatFormatting.GRAY).append(Component.literal(goliath).withStyle(ChatFormatting.WHITE)).append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
            return;
        }
        announce(mc, new ArrayList<>(now), now.size(), true);
    }

    private void announce(Minecraft mc, List<String> names, int total, boolean summary) {
        String list = names(names);
        String area = areaText(mc);
        String bases = total + (total == 1 ? " media base" : " media bases") + (area == null ? "" : " within " + area + " blocks");
        Component line;
        if (summary) {
            line = Component.literal(bases + " on ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(goliath).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(list).withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            line = Component.literal(list).withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(" joined ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(goliath).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(", now " + bases + ".").withStyle(ChatFormatting.GRAY));
            Zeus.LOG.info("[Media Base Finder] {} joined {}, now {}", list, goliath, total);
        }
        say(mc, line);
        String est = estimate.on() ? estimateLine(mc) : null;
        if (est != null) say(mc, Component.literal(est).withStyle(ChatFormatting.AQUA));
        if (summary) Zeus.LOG.info("[Media Base Finder] screen text: afterRtp={} notifications={}", afterRtp, notifications.on());
        if (summary && afterRtp && notifications.on()) {
            screenTitle = total + (total == 1 ? " Media Base" : " Media Bases");
            screenSub = area == null ? "on this goliath" : "within " + area + " blocks";
            screenUntil = System.currentTimeMillis() + SCREEN_TEXT_MS;
            if (mc.getSoundManager() != null) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.4f));
        }
    }

    private static void say(Minecraft mc, Component body) {
        if (mc.gui == null) return;
        mc.gui.getChat().addMessage(Component.literal("[Media Base Finder] ").withStyle(ChatFormatting.LIGHT_PURPLE).append(body));
    }

    private static String names(List<String> names) {
        StringBuilder out = new StringBuilder();
        int shown = Math.min(names.size(), MAX_LISTED);
        for (int i = 0; i < shown; i++) out.append(i == 0 ? "" : ", ").append(names.get(i));
        if (names.size() > shown) out.append(" +").append(names.size() - shown);
        return out.toString();
    }

    // ------------------------------------------------------------------ the Goliath and the estimated area

    static boolean overworld(Minecraft mc) {
        return mc.level != null && mc.level.dimension().equals(Level.OVERWORLD);
    }

    static int codeNow(Minecraft mc) {
        if (mc.player == null || !overworld(mc)) return OFF_MAP;
        int c = GoliathGrid.at(mc.player.getX(), mc.player.getZ());
        return c < 0 ? OFF_MAP : c;
    }

    private static String labelNow(Minecraft mc) {
        if (mc.player == null || !overworld(mc)) return "-";
        return GoliathGrid.label(GoliathGrid.at(mc.player.getX(), mc.player.getZ()));
    }

    /** "25k x 50k", the size of your Goliath in blocks as the Goliath map writes it, or null off the map. */
    private static String areaText(Minecraft mc) {
        int c = codeNow(mc);
        if (c == OFF_MAP) return null;
        int[] b = GoliathGrid.bounds(c);
        if (b == null) return null;
        String w = blocks(b[2] - b[0]);
        String l = blocks(b[3] - b[1]);
        return w.equals(l) ? w : w + " x " + l;
    }

    private static String blocks(int squares) {
        int value = squares * GoliathGrid.SQUARE;
        return value % 1000 == 0 ? value / 1000 + "k" : String.format(Locale.ROOT, "%.1fk", value / 1000.0);
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%,d", Math.round(v));
    }

    /** The estimate as one chat line: where on the map the base can be, and how far the middle of that area is from you. */
    private String estimateLine(Minecraft mc) {
        if (mc.player == null) return null;
        double[] a = GoliathGrid.blockArea(code == OFF_MAP ? codeNow(mc) : code);
        if (a == null) return null;
        double cx = (a[0] + a[2]) / 2;
        double cz = (a[1] + a[3]) / 2;
        return "Estimated base area: X " + num(a[0]) + " to " + num(a[2]) + ", Z " + num(a[1]) + " to " + num(a[3])
                + " | centre " + num(cx) + ", " + num(cz) + " | " + away(mc, cx, cz);
    }

    private static String away(Minecraft mc, double x, double z) {
        double dx = x - mc.player.getX();
        double dz = z - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        return (dist >= 1000 ? String.format(Locale.ROOT, "%.1fk", dist / 1000.0) : Long.toString(Math.round(dist))) + " blocks " + compass(dx, dz);
    }

    private static String compass(double dx, double dz) {
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        double ang = Math.toDegrees(Math.atan2(dx, -dz));
        int i = (int) Math.floor(((ang % 360 + 360) % 360 + 22.5) / 45.0) % 8;
        return names[i];
    }

    // ------------------------------------------------------------------ sightings

    private void noteSightings(Minecraft mc, Set<String> now, String server) {
        long t = System.currentTimeMillis();
        for (String name : now) {
            String key = server.toLowerCase(Locale.ROOT) + "|" + name.toLowerCase(Locale.ROOT);
            Sighting s = sightings.computeIfAbsent(key, k -> new Sighting());
            s.name = name;
            s.goliath = goliath;
            s.code = code;
            s.at = t;
            s.loaded = false;
            if (showLoaded.on()) {
                for (var p : mc.level.players()) {
                    if (p.getName().getString().equalsIgnoreCase(name)) {
                        s.loaded = true;
                        s.x = p.getX();
                        s.y = p.getY();
                        s.z = p.getZ();
                        break;
                    }
                }
            }
        }
        while (sightings.size() > 200) sightings.remove(sightings.keySet().iterator().next());
    }

    private Sighting sightingOf(String name) {
        String server = lastServer.toLowerCase(Locale.ROOT);
        return sightings.get(server + "|" + name.toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ diagnostics

    /** A short reason for an empty result, shown on the card. */
    private String shortReason() {
        if (dbgScans == 0) return "No scan yet";
        if (dbgTeams == 0) return "No teams on this server";
        if (dbgMediaTeams == 0) return "No media teams here (DonutSMP only)";
        return "None on " + goliath;
    }

    private List<String> whyLines() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return List.of("Join a server first.");
        List<String> out = new ArrayList<>();
        String server = mc.getCurrentServer() == null ? "singleplayer" : String.valueOf(mc.getCurrentServer().ip);
        out.add("Server " + server + (dbgBrand.isEmpty() ? "" : "  |  brand " + dbgBrand));
        if (settle > 0) {
            out.add("Waiting for the scoreboard to settle (" + (settle / 20 + 1) + " s).");
            return out;
        }
        out.add("Scans " + dbgScans + "  |  scoreboard teams " + dbgTeams + "  |  media teams " + dbgMediaTeams + "  |  tab list " + dbgTab);
        if (dbgScans == 0) out.add("No scan has run yet.");
        else if (dbgTeams == 0) out.add("This server sent no scoreboard teams, so there is nothing to match.");
        else if (dbgMediaTeams == 0) out.add("None of the " + dbgTeams + " teams starts with \"" + teamPrefix.get() + "\" or carries the camera sign. The finder only works on DonutSMP, where media accounts sit in 09- teams.");
        else if (found.isEmpty()) out.add(dbgMediaTeams + " media team(s) exist, but every member is listed in the tab list and no game-mode packet proved them present, so none count as being on your Goliath.");
        else out.add("Working: " + found.size() + " found.");
        if (!dbgSample.isEmpty()) out.add("Some team names: " + dbgSample);
        return out;
    }

    private void listTeamsInChat() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            Zeus.toast("Join a server first", false);
            return;
        }
        int n = 0;
        int media = 0;
        say(mc, Component.literal("Scoreboard teams on this server (first 25):").withStyle(ChatFormatting.GRAY));
        for (PlayerTeam team : mc.level.getScoreboard().getPlayerTeams()) {
            n++;
            boolean m = isMedia(team);
            if (m) media++;
            if (n <= 25) {
                say(mc, Component.literal((m ? "[media] " : "") + team.getName() + "  members " + team.getPlayers().size() + "  display \"" + team.getDisplayName().getString() + "\"")
                        .withStyle(m ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GRAY));
            }
        }
        say(mc, Component.literal(n + " teams, " + media + " match the media rules.").withStyle(ChatFormatting.GRAY));
    }

    private void copyDiagnostics() {
        Minecraft mc = Minecraft.getInstance();
        StringBuilder out = new StringBuilder("Media Base Finder diagnostics\n");
        for (String line : whyLines()) out.append(line).append('\n');
        out.append("Goliath ").append(goliath).append("  found ").append(found).append("  proven ").append(proven).append('\n');
        out.append("prefix \"").append(teamPrefix.get()).append("\"  sign \"").append(marker.get()).append("\"  wide ").append(wide.on()).append("  tabCheck ").append(checkTab.on());
        try {
            mc.keyboardHandler.setClipboard(out.toString());
            Zeus.toast("Diagnostics copied", true);
        } catch (RuntimeException e) {
            Zeus.toast("Could not reach the clipboard", false);
        }
    }

    // ------------------------------------------------------------------ settings pages

    private List<String> hereLines() {
        if (settle > 0) return List.of("Checking... " + (settle / 20 + 1) + " s");
        if (found.isEmpty()) return List.of(shortReason());
        List<String> out = new ArrayList<>();
        out.add(found.size() + (found.size() == 1 ? " media base on " : " media bases on ") + goliath);
        for (String name : found) {
            Sighting s = sightingOf(name);
            out.add(name + (s != null && s.loaded ? "  |  " + Math.round(s.x) + " " + Math.round(s.y) + " " + Math.round(s.z) : ""));
        }
        return out;
    }

    private List<String> areaLines() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return List.of("Join a server first.");
        int c = code == OFF_MAP ? codeNow(mc) : code;
        double[] a = GoliathGrid.blockArea(c);
        if (a == null) return List.of("You are not on a mapped Goliath (or not in the overworld).");
        double cx = (a[0] + a[2]) / 2;
        double cz = (a[1] + a[3]) / 2;
        List<String> out = new ArrayList<>();
        out.add(GoliathGrid.label(c) + "  |  " + areaText(mc) + " blocks");
        out.add("X " + num(a[0]) + " to " + num(a[2]));
        out.add("Z " + num(a[1]) + " to " + num(a[3]));
        out.add("Centre " + num(cx) + ", " + num(cz) + "  |  " + away(mc, cx, cz));
        out.add("A base is somewhere inside this area; Zeus cannot narrow it further from what the server sends.");
        return out;
    }

    private List<String> seenLines() {
        if (sightings.isEmpty()) return List.of("Nobody seen yet.");
        List<String> out = new ArrayList<>();
        List<Sighting> list = new ArrayList<>(sightings.values());
        list.sort((a, b) -> Long.compare(b.at, a.at));
        long t = System.currentTimeMillis();
        for (Sighting s : list.subList(0, Math.min(10, list.size()))) {
            String where = s.loaded ? Math.round(s.x) + " " + Math.round(s.y) + " " + Math.round(s.z) : s.goliath;
            out.add(s.name + "  |  " + where + "  |  " + Math.max(0, (t - s.at) / 1000) + " s ago");
        }
        return out;
    }

    private void waypoint() {
        Minecraft mc = Minecraft.getInstance();
        int c = code == OFF_MAP ? codeNow(mc) : code;
        double[] a = GoliathGrid.blockArea(c);
        Module wp = Zeus.get("waypoints");
        if (a == null || !(wp instanceof Extras.Waypoints w)) {
            Zeus.toast("No Goliath area to save here", false);
            return;
        }
        double cx = (a[0] + a[2]) / 2;
        double cz = (a[1] + a[3]) / 2;
        boolean added = w.addAt("Media area " + GoliathGrid.label(c), cx, 100, cz);
        Zeus.toast(added ? "Waypoint saved at the centre of " + GoliathGrid.label(c) : "A waypoint is already saved there", added);
    }

    private void copyEstimate() {
        Minecraft mc = Minecraft.getInstance();
        StringBuilder out = new StringBuilder(goliath);
        if (!found.isEmpty()) out.append(": ").append(String.join(", ", found));
        String est = estimateLine(mc);
        if (est != null) out.append("\n").append(est);
        try {
            mc.keyboardHandler.setClipboard(out.toString());
            Zeus.toast("Estimate copied", true);
        } catch (RuntimeException e) {
            Zeus.toast("Could not reach the clipboard", false);
        }
    }

    @Override
    public String info() {
        return settle > 0 ? "..." : found.isEmpty() ? null : Integer.toString(found.size());
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (mc.player == null) return;
        if (statusBox.on()) drawCard(g, mc, w, h);
    }

    @Override
    protected void onHudWorld(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (mc.player == null) return;
        long t = System.currentTimeMillis();
        if (t < screenUntil) drawScreenText(g, w, h, t);
    }

    private void drawScreenText(GuiGraphics g, int w, int h, long t) {
        long left = screenUntil - t;
        float alpha = left < 600 ? left / 600f : Math.min(1f, (SCREEN_TEXT_MS - left) / 200f);
        int color = Draw.rgba(0xFFFF55FF, Math.max(0f, alpha));
        Draw.boldCenterS(g, screenTitle, w / 2, h / 4, color, 3f);
        Draw.textCenterS(g, screenSub, w / 2, h / 4 + 34, Draw.rgba(0xFFFFFFFF, Math.max(0f, alpha)), 1.2f);
    }

    private void drawCard(GuiGraphics g, Minecraft mc, int w, int h) {
        float sc = scale.f() / 100f;
        List<String> names = new ArrayList<>(found);
        int shown = Math.min(names.size(), MAX_LISTED);
        int rowH = Math.round(14 * sc);
        boolean checking = settle > 0;
        int lines = checking || names.isEmpty() ? 1 : shown + (names.size() > shown ? 1 : 0);
        double[] a = estimate.on() ? GoliathGrid.blockArea(code == OFF_MAP ? codeNow(mc) : code) : null;
        int extra = a == null ? 0 : Math.round(34 * sc);
        int head = Math.round(32 * sc);
        int bw = Math.round(236 * sc);
        int bh = head + lines * rowH + extra + Math.round(8 * sc);
        int[] at = Hud.place(corner.index(), bw, bh, w, h);
        float bg = opacity.f() / 100f;
        int ac = Hud.accent();
        Hud.panel(g, at[0], at[1], bw, bh);
        Draw.rrectLine(g, at[0], at[1], bw, bh, 7, Draw.rgba(ac, 0.6f));
        String title = names.size() > 1 ? "MEDIA BASES ON YOUR GOLIATH" : "MEDIA BASE ON YOUR GOLIATH";
        Draw.boldS(g, Draw.fit(title, Math.round(bw / Math.max(0.5f, sc)) - 18), at[0] + Math.round(9 * sc), at[1] + Math.round(6 * sc), 0xFFFFFFFF, sc);
        Draw.textS(g, goliath, at[0] + Math.round(9 * sc), at[1] + Math.round(18 * sc), Draw.rgba(Accent.DIM, 1f), sc);
        int y = at[1] + head;
        int x = at[0] + Math.round(9 * sc);
        if (checking) {
            Draw.textS(g, "Checking...", x, y, 0xFFFFC850, sc);
            y += rowH;
        } else if (names.isEmpty()) {
            Draw.textS(g, Draw.fit(shortReason(), Math.round((bw - 18 * sc) / Math.max(0.5f, sc))), x, y, 0xFF9AA3B5, sc);
            y += rowH;
        } else {
            ClientPacketListener connection = mc.getConnection();
            for (int i = 0; i < shown; i++) {
                String name = names.get(i);
                int fs = Math.round(11 * sc);
                PlayerInfo info = connection == null ? null : connection.getPlayerInfo(name);
                boolean drawn = false;
                if (info != null) {
                    try {
                        PlayerFaceRenderer.draw(g, info.getSkin(), x, y - 1, fs, 0xFFFFFFFF);
                        drawn = true;
                    } catch (RuntimeException ignored) {
                        // fall through to the plain tile
                    }
                }
                if (!drawn) {
                    Draw.rrect(g, x, y - 1, fs, fs, 3, Draw.rgba(0xFFB06BFF, 0.55f));
                    Draw.textCenterS(g, name.substring(0, 1).toUpperCase(Locale.ROOT), x + fs / 2, y + Math.round(1 * sc), 0xFFFFFFFF, sc * 0.9f);
                }
                Sighting s = sightingOf(name);
                String right = s != null && s.loaded ? Math.round(s.x) + " " + Math.round(s.z) : "";
                int rw = right.isEmpty() ? 0 : Draw.widthS(right, sc);
                Draw.textS(g, Draw.fit(name, Math.max(20, Math.round((bw - 28 * sc - rw) / Math.max(0.5f, sc)))), x + fs + Math.round(5 * sc), y, 0xFFFFFFFF, sc);
                if (!right.isEmpty()) Draw.textRightS(g, right, at[0] + bw - Math.round(9 * sc), y, 0xFF83E6B0, sc);
                y += rowH;
            }
            if (names.size() > shown) {
                Draw.textS(g, "+" + (names.size() - shown) + " more", x, y, Draw.rgba(Accent.DIM, 1f), sc);
                y += rowH;
            }
        }
        if (a != null) {
            g.fill(at[0] + 8, y + 1, at[0] + bw - 8, y + 2, Draw.rgba(ac, 0.35f));
            Draw.textS(g, "Base area  X " + num(a[0]) + " .. " + num(a[2]), x, y + Math.round(5 * sc), 0xFFC9D4EC, sc);
            Draw.textS(g, "                Z " + num(a[1]) + " .. " + num(a[3]), x, y + Math.round(15 * sc), 0xFFC9D4EC, sc);
            double cx = (a[0] + a[2]) / 2;
            double cz = (a[1] + a[3]) / 2;
            Draw.textS(g, "Centre " + num(cx) + ", " + num(cz) + "  |  " + away(mc, cx, cz), x, y + Math.round(25 * sc), 0xFF7FD6FF, sc * 0.9f);
        }
    }

    // ------------------------------------------------------------------ Goliath tracker

    /** Notices that you arrived somewhere new: another level, another player body, another Goliath or another server brand. */
    static final class Tracker {
        private Object level;
        private net.minecraft.world.entity.player.Player self;
        private String brand;
        private int goliath = OFF_MAP;

        boolean moved(Minecraft mc) {
            if (mc.level == null || mc.player == null) {
                clear();
                return false;
            }
            Object nowLevel = mc.level;
            net.minecraft.world.entity.player.Player nowSelf = mc.player;
            String nowBrand = brand(mc.getConnection());
            int nowGoliath = codeNow(mc);
            // the four tests that start a new search: another level, another body (unless the old one died), another Goliath, another server brand
            boolean moved = level != null && (nowLevel != level || (nowSelf != self && !self.isDeadOrDying()) || nowGoliath != goliath
                    || (nowBrand != null && brand != null && !nowBrand.equals(brand)));
            level = nowLevel;
            self = nowSelf;
            brand = nowBrand;
            goliath = nowGoliath;
            return moved;
        }

        void clear() {
            level = null;
            self = null;
            brand = null;
            goliath = OFF_MAP;
        }

        private static java.lang.reflect.Method brandMethod;
        private static boolean brandSearched;

        /** The server brand string, read by name so a renamed accessor only costs the brand check, not the build. */
        private static String brand(ClientPacketListener connection) {
            if (connection == null) return null;
            try {
                if (!brandSearched) {
                    brandSearched = true;
                    for (java.lang.reflect.Method m : connection.getClass().getMethods()) {
                        if (m.getParameterCount() == 0 && m.getReturnType() == String.class && m.getName().toLowerCase(Locale.ROOT).contains("brand")) {
                            brandMethod = m;
                            break;
                        }
                    }
                }
                return brandMethod == null ? null : (String) brandMethod.invoke(connection);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
    }
}
