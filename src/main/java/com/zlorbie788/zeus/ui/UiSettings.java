package com.zlorbie788.zeus.ui;

import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;

/** Settings of the panel itself. */
public final class UiSettings {
    private UiSettings() {}

    public static final Setting.Choice ACCENT = Zeus.UI.add(new Setting.Choice("Accent colour", "The colour of the panel, the bolt and the module list.", 0,
            "Storm blue", "Violet", "Gold", "Emerald", "Crimson", "Rainbow"));
    public static final Setting.Num RAINBOW_SPEED = Zeus.UI.add(new Setting.Num("Rainbow speed", "How fast the colours of the panel cycle when the accent is Rainbow.", 40, 5, 200, 5, "%"))
            .showIf(() -> ACCENT.index() == 5);
    public static final Setting.Choice LAYOUT = Zeus.UI.add(new Setting.Choice("Layout", "Panel: one window with a sidebar. Windows: a floating, draggable window for every category.", 1,
            "Panel", "Windows"));
    /** Set once the Windows layout has been made the default for a config that was saved before it was. Not shown. */
    public static final Setting.Bool LAYOUT_WINDOWS_DEFAULT = Zeus.UI.add(new Setting.Bool("Windows layout is the default", "Internal marker.", false)).showIf(() -> false);
    public static final Setting.Choice CARDS = Zeus.UI.add(new Setting.Choice("Module cards", "How the Panel layout lists modules. Two columns: two cards side by side when there is room. One column: one wide card per row. A card that is open for its settings always takes the full width.", 0,
            "Two columns", "One column"));
    public static final Setting.Choice SORT = Zeus.UI.add(new Setting.Choice("Sort modules", "Category order: the order they were made in. Most used first: the modules you switch on most come first (until you have a history, a built-in guess of the most popular ones is used). A to Z: alphabetical.", 0,
            "Category order", "Most used first", "A to Z"));
    public static final Setting.Choice INTRO = Zeus.UI.add(new Setting.Choice("Intro animation", "What plays when the panel opens. Video: the Zeus intro film. Video once: the film the first time after the game starts, a quick lightning after that. Any key or click skips it.", 1,
            "Video", "Video once", "Lightning", "Quick", "Off"));
    public static final Setting.Bool MENU = Zeus.UI.add(new Setting.Bool("Zeus main menu", "Replaces the game's title screen with the Zeus menu. The Classic menu link in the corner turns it off.", true));
    public static final Setting.Bool INTRO_SOUND = Zeus.UI.add(new Setting.Bool("Intro film sound", "Plays the sound of the intro films. Switch it off to watch them in silence.", true));
    public static final Setting.Bool STARTUP_VIDEO = Zeus.UI.add(new Setting.Bool("Intro film at start-up", "Plays the Zeus intro film once when the game has finished loading.", true));
    public static final Setting.Choice INTRO_FIT = Zeus.UI.add(new Setting.Choice("Intro film shape", "Stretch (the default): fills the window exactly, whatever its size. Fit: the whole picture, nothing cut, with the lightning carried on across the rest of the screen. Fill: crops the top and bottom to cover the window. ", 2,
            "Fit + glow", "Fill (crop)", "Stretch"));
    public static final Setting.Bool THUNDER = Zeus.UI.add(new Setting.Bool("Thunder sound", "The thunder of the storms on the main menu (the intro films have their own thunder in their sound).", true));
    public static final Setting.Bool MENU_STORM = Zeus.UI.add(new Setting.Bool("Storms on the main menu", "After about a minute on the Zeus menu a thunderstorm can roll in: dark clouds, rain and lightning. It clears again after two minutes.", true));
    public static final Setting.Bool MENU_SOUNDS = Zeus.UI.add(new Setting.Bool("Menu sounds", "A soft rumble when you point at a stone plate or a round button on the Zeus menu, and a crack of lightning when you click.", true));
    public static final Setting.Bool MENU_AMBIENCE = Zeus.UI.add(new Setting.Bool("Menu ambience", "Low wind and soft rain under the Zeus menu, louder in a storm, wind alone in the snow.", true));
    public static final Setting.Num AMBIENCE_VOLUME = Zeus.UI.add(new Setting.Num("Menu ambience volume", "How loud the wind and the rain on the menu are (also follows the game's Ambient slider).", 50, 0, 100, 5, "%"))
            .showIf(MENU_AMBIENCE::on);
    public static final Setting.Bool WINDOW_ICON = Zeus.UI.add(new Setting.Bool("Zeus window icon", "Shows the Zeus medallion in the task bar and the window's title bar instead of the game's icon. Takes effect when the game starts; switching it off needs a restart to bring the game's icon back.", true));
    public static final Setting.Bool PAUSE_STYLE = Zeus.UI.add(new Setting.Bool("Zeus pause menu", "The Zeus medallion above the pause menu and stone plates with the Zeus lettering for its buttons.", true));
    public static final Setting.Bool STONE_LISTS = Zeus.UI.add(new Setting.Bool("Stone plate lists (test)", "The world list and the server list as rows of stone plates, with stone plate buttons and the Zeus lettering for the title. A test: switch it off to get the game's own lists back.", true));
    public static final Setting.Choice SEASON = Zeus.UI.add(new Setting.Choice("Seasonal menu", "Snow on the menu. Automatic: in December. Always: all year (to see it now). Off: never. In a storm the snow becomes a blizzard.", 0,
            "Automatic (December)", "Always", "Off"));
    public static final Setting.Bool BACKDROP = Zeus.UI.add(new Setting.Bool("Menu sky behind game screens", "The server list, the world list, the options and the disconnect and error screens get the Zeus menu's sky instead of the game's own background (only outside a world).", true));
    public static final Setting.Bool MENU_CLOCK = Zeus.UI.add(new Setting.Bool("Date and time on the menu", "Shows today's date and the time, with seconds, in the top right corner of the Zeus menu.", true));
    public static final Setting.Bool CLOCK_24H = Zeus.UI.add(new Setting.Bool("24-hour clock", "The menu clock shows 17:30 instead of 5:30 PM.", true));
    public static final Setting.Bool MENU_AURORA = Zeus.UI.add(new Setting.Bool("Menu aurora", "A green and violet aurora in the sky of the seasonal menu, on clear winter nights. It fades out when the storm comes. Follows the Seasonal menu setting.", true));
    public static final Setting.Bool LOADING_TIPS = Zeus.UI.add(new Setting.Bool("Loading tips", "A short Zeus tip under the loading ring while a world loads.", true));
    public static final Setting.Bool LOADING = Zeus.UI.add(new Setting.Bool("Zeus loading screens", "Replaces the game's Mojang start screen and its bar with the Zeus ring and the real percent while the game starts, shows the same ring with the real percent while a singleplayer world loads, and the menu background with the logo while you join a server.", true));
    public static final Setting.Num HUD_OPACITY = Zeus.UI.add(new Setting.Num("HUD panel opacity", "How solid the boxes of the Panels page are (Info, Armor, Potion, Keystrokes, Radar...). 0% shows only the text.", 69, 0, 100, 1, "%"));
    public static final Setting.Num HUD_ROUND = Zeus.UI.add(new Setting.Num("HUD corner roundness", "How round the corners of those boxes are.", 4, 0, 10, 1, " px"));
    public static final Setting.Bool HUD_STRIPE = Zeus.UI.add(new Setting.Bool("HUD accent stripe", "The coloured bar on the left edge of the boxes.", true));
    public static final Setting.Choice PERFORMANCE = Zeus.UI.add(new Setting.Choice("Effects quality", "Auto: the thunder, sway and slide-ins are drawn lighter when your frame rate drops below 40 and come back above 55. Quality: always full. Fast: always light (best for weak computers).", 0,
            "Auto", "Quality", "Fast"));
    public static final Setting.Choice GAME_MODE = Zeus.UI.add(new Setting.Choice("Performance mode", "How much of your frame rate Zeus may use while you play. Balanced: everything as designed. Light (the default): scanners run every other tick, boxes and lines are drawn with fewer pieces and no halos. Minimal: scanners run every fourth tick and the on-screen boxes are flat. Open the Performance Monitor to see what each module costs.", 1,
            "Balanced", "Light", "Minimal"));
    public static final Setting.Bool AUTO_LIGHTEN = Zeus.UI.add(new Setting.Bool("Lighten automatically when the game lags", "When your frame rate falls below 45 Zeus steps up to at least Light, and below 30 to Minimal. It steps back down once the frame rate is healthy again.", true));
    public static final Setting.Bool SHOW_COST = Zeus.UI.add(new Setting.Bool("Show what each module costs", "Measures how much of your frame time each switched-on module uses and shows it as a small percentage next to the module in the menu windows (green is small, red is a lot). The measuring itself is a few clock reads a frame. Switch it off for none.", true));
    public static final Setting.Bool CONTRAST = Zeus.UI.add(new Setting.Bool("High contrast", "Brighter text and lines in the panel, so the small grey writing is easier to read.", false));
    public static final Setting.Bool CALM = Zeus.UI.add(new Setting.Bool("Calm mode", "No screen shake, no bright flashes and no sliding in. For anyone who finds the lightning too much.", false));
    public static final Setting.Bool TOASTS = Zeus.UI.add(new Setting.Bool("Messages", "Short pop-ups when something happens.", true));
    public static final Setting.Bool REMEMBER_MODULE_STATES = Zeus.UI.add(new Setting.Bool("Remember enabled modules", "Restore the modules you had enabled after restarting Minecraft. World-dependent modules are restored only after a world has loaded.", true));

    public static final Setting.Action HUD_EDITOR = Zeus.UI.add(new Setting.Action("Open HUD editor", "Drag, scale, hide and arrange every Zeus HUD widget on screen.", () -> {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.setScreen(new HudEditorScreen(mc.screen));
    }));

    public static final Setting.Action PLAY = Zeus.UI.add(new Setting.Action("Play intro film", "Shows the intro film now.", () -> {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen back = mc.screen;
        if (VideoIntroScreen.available()) mc.setScreen(new VideoIntroScreen(() -> back, false));
    }));

    public static final Setting.Action PLAY_PANEL = Zeus.UI.add(new Setting.Action("Play panel intro", "Shows the five second film that plays when you press Z.", () -> {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen back = mc.screen;
        if (VideoIntroScreen.available(VideoIntroScreen.PANEL)) mc.setScreen(new VideoIntroScreen(VideoIntroScreen.PANEL, () -> back, false));
    }));

    public static final Setting.Action RESET = Zeus.UI.add(new Setting.Action("Reset windows", "Puts every window back where it started and unfolds it.", UiState::reset));

    public static final Setting.Action DESTRUCTION = Zeus.UI.add(new Setting.Action("Self destruction", "Switches every Zeus feature off and takes the panel, the HUD and the menu away, so the game looks like it did before Zeus was installed. Everything is kept and nothing is deleted: type /zeus in chat to bring all of it back.", Zeus::destroy));

    static {
        CONTRAST.onChange(() -> Accent.contrast(CONTRAST.on()));
    }

    /** Makes sure the settings exist before they are loaded. */
    public static void touch() {
        Accent.contrast(CONTRAST.on());
    }
}
