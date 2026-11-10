package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.client.web.LocalControlServer;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The Zeus website, served from your own computer. Switching this module on starts a small web server that listens only on
 * 127.0.0.1 (never on the network); the same pages can be hosted anywhere as a plain website, but only this copy can control
 * the game. The pages are public to your computer; everything that reads or changes the game needs the access token.
 */
public final class WebDashboard extends Module {
    private static volatile WebDashboard instance;

    private final Setting.Info status = add(new Setting.Info("Website", this::statusLines));
    private final Setting.Action open = add(new Setting.Action("Open the website", "Opens the Zeus website in your browser. Switch the module on first.", this::openSite));
    private final Setting.Action copyToken = add(new Setting.Action("Copy the access token", "Copies the token the control page asks for. Keep it private.", this::copyToken));
    private final Setting.Action announce = add(new Setting.Action("Show the link and token in chat", "Prints the clickable link and the clickable token in the chat.", this::announceInChat));
    private final Setting.Num port = add(new Setting.Num("Port", "The local port. Change it only if 8765 is taken; switch the module off and on again to apply.", 8765, 1024, 65535, 1, ""));
    private final Setting.Bool keepToken = add(new Setting.Bool("Keep the same access token", "Remembers the token in config/zeus/web-token.txt so a bookmarked, signed-in page keeps working after a restart. Off makes a new random token every time.", false));
    private final Setting.Text discord = add(new Setting.Text("Discord invite link", "Shown on the website's Join Discord buttons. Leave empty to hide them.", "https://discord.gg/GcbryFXgcR", 120));
    private final Setting.Text download = add(new Setting.Text("Download link", "Where the website's Download button goes (for example your release page). Leave empty to show the build instructions instead.", "", 160));
    private final Setting.Text youtube = add(new Setting.Text("YouTube link", "Shown in the website footer. Leave empty to hide it.", "", 120));
    private final Setting.Text twitter = add(new Setting.Text("Twitter / X link", "Shown in the website footer. Leave empty to hide it.", "", 120));
    private final Setting.Text tutorial = add(new Setting.Text("Tutorial video link", "Where the Watch Tutorial button goes. Leave empty to open the written tutorial.", "", 160));

    public WebDashboard() {
        super("webdashboard", "Web Dashboard", "The Zeus website on your own computer, with a live control page for the game. Local only.", Category.INTERFACE);
        instance = this;
    }

    // ------------------------------------------------------------------ the values the website shows (read by the server)

    public static String discordUrl() { return link(instance == null ? "" : instance.discord.get()); }
    public static String downloadUrl() { return link(instance == null ? "" : instance.download.get()); }
    public static String youtubeUrl() { return link(instance == null ? "" : instance.youtube.get()); }
    public static String twitterUrl() { return link(instance == null ? "" : instance.twitter.get()); }
    public static String tutorialUrl() { return link(instance == null ? "" : instance.tutorial.get()); }
    public static boolean keepTokenOn() { return instance != null && instance.keepToken.on(); }
    public static int portNow() { return instance == null ? 8765 : instance.port.i(); }

    /** Only plain http(s) addresses are passed on to the website. */
    private static String link(String raw) {
        String t = raw == null ? "" : raw.trim();
        String lower = t.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") ? t : "";
    }

    // ------------------------------------------------------------------ the server follows the switch

    @Override
    protected void onEnable() {
        startServer(false);
    }

    @Override
    protected void onDisable() {
        LocalControlServer.stop();
    }

    /** Starts the server (if it is not running) and, when asked, prints the link and token in chat. */
    public static boolean startServer(boolean tell) {
        WebDashboard d = instance;
        try {
            LocalControlServer.start(d == null ? 8765 : d.port.i(), d != null && d.keepToken.on());
        } catch (IOException e) {
            Zeus.toast("Website could not start: " + e.getMessage(), false);
            Zeus.LOG.warn("Zeus website could not start", e);
            return false;
        }
        if (tell || Minecraft.getInstance().level != null) announceNow();
        return true;
    }

    private void announceInChat() {
        if (!LocalControlServer.running()) {
            Zeus.toast("Switch the Web Dashboard on first", false);
            return;
        }
        announceNow();
    }

    private static Component rule() {
        return Component.literal("                                                    ").withStyle(style -> style.withColor(ChatFormatting.DARK_AQUA).withStrikethrough(true));
    }

    private static Component button(String label, ChatFormatting colour, ClickEvent click, String hint) {
        return Component.literal("[ " + label + " ]").withStyle(style -> style.withColor(colour).withBold(true).withClickEvent(click)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hint))));
    }

    /** Prints the website card: the address, a hidden token and buttons to open the site and copy the link or the token. */
    public static void announceNow() {
        Minecraft mc = Minecraft.getInstance();
        String url = LocalControlServer.url();
        String token = LocalControlServer.token();
        if (mc.gui == null || url == null || token == null) return;
        var chat = mc.gui.getChat();
        chat.addMessage(rule());
        chat.addMessage(Component.literal(" ZEUS ").withStyle(style -> style.withColor(ChatFormatting.AQUA).withBold(true))
                .append(Component.literal("WEBSITE").withStyle(style -> style.withColor(ChatFormatting.WHITE).withBold(true)))
                .append(Component.literal("    \u25CF online").withStyle(ChatFormatting.GREEN)));
        chat.addMessage(Component.literal(" Address  ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(url).withStyle(style -> style.withColor(ChatFormatting.WHITE)
                        .withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url)))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to open the Zeus website"))))));
        chat.addMessage(Component.literal(" Token    ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("  hidden, copy it with the button").withStyle(ChatFormatting.DARK_GRAY)));
        chat.addMessage(Component.literal(" ")
                .append(button("Open", ChatFormatting.GREEN, new ClickEvent.OpenUrl(java.net.URI.create(url)), "Open the website in your browser"))
                .append(Component.literal("  "))
                .append(button("Copy link", ChatFormatting.AQUA, new ClickEvent.CopyToClipboard(url), "Copy the website address"))
                .append(Component.literal("  "))
                .append(button("Copy token", ChatFormatting.GOLD, new ClickEvent.CopyToClipboard(token), "Copy the private access token, then paste it on the site")));
        chat.addMessage(Component.literal(" Only this computer can open it. Stop it with /zeus website stop.").withStyle(ChatFormatting.DARK_GRAY));
        chat.addMessage(rule());
    }

    private void openSite() {
        if (!LocalControlServer.running()) {
            Zeus.toast("Switch the Web Dashboard on first", false);
            return;
        }
        try {
            if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().browse(java.net.URI.create(LocalControlServer.url()));
            else Zeus.toast("Open " + LocalControlServer.url() + " in your browser", true);
        } catch (IOException | RuntimeException e) {
            Zeus.toast("Open " + LocalControlServer.url() + " in your browser", true);
        }
    }

    private void copyToken() {
        String token = LocalControlServer.token();
        if (token == null) {
            Zeus.toast("Switch the Web Dashboard on first", false);
            return;
        }
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(token);
            Zeus.toast("Access token copied", true);
        } catch (RuntimeException e) {
            Zeus.toast("Could not reach the clipboard", false);
        }
    }

    private List<String> statusLines() {
        List<String> out = new ArrayList<>();
        if (LocalControlServer.running()) {
            out.add("Running at " + LocalControlServer.url() + " (this computer only)");
            out.add("Open it, go to Settings, and paste the access token.");
        } else {
            out.add("Stopped. Switch the module on to start it.");
        }
        return out;
    }

    @Override
    public String info() {
        return LocalControlServer.running() ? ":" + LocalControlServer.portNow() : null;
    }
}
