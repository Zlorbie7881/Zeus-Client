package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;

/** Local-only stream overlay privacy. It hides selected HUD panels; it does not change server identity. */
public final class StreamerPrivacy extends Module {
    private static volatile StreamerPrivacy instance;
    private final Setting.Bool hideTab = add(new Setting.Bool("Hide player list", "Hides the on-screen tab/player list while this module is enabled.", true));
    private final Setting.Bool hideSidebar = add(new Setting.Bool("Hide scoreboard sidebar", "Hides the right-side scoreboard locally while this module is enabled.", true));

    public StreamerPrivacy() {
        super("streamerprivacy", "Streamer Privacy", "Locally hides selected identity-bearing HUD panels for screenshots/streams. Your username remains visible to the server and other players.", Category.INTERFACE);
        instance = this;
    }

    public static boolean hideTabList() { return instance != null && instance.enabled() && instance.hideTab.on(); }
    public static boolean hideSidebar() { return instance != null && instance.enabled() && instance.hideSidebar.on(); }
}
