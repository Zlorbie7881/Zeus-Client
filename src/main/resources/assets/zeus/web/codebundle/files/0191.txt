package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ServerSwitch extends Module {
    private static final ServerSwitch INSTANCE = new ServerSwitch();
    private static final Map<String, Setting.Bool> KEEP = new LinkedHashMap<>();
    private static final Setting.Choice MODE = INSTANCE.addSetting(new Setting.Choice(
            "Reconnect policy",
            "Choose which enabled modules Zeus restores after leaving a world/server and joining another. Defaults to Everything; choose Safe only or None if you prefer manual activation.",
            2, "Safe only", "None", "Everything", "Customize"));

    private ServerSwitch() {
        super("serverswitch", "Server Switch Persistence",
                "Choose which modules stay enabled when you leave a server and join another. Open settings to configure.",
                Category.INTERFACE);
        panelOnly();
    }

    public static ServerSwitch instance() { return INSTANCE; }

    public static void configure(List<Module> modules) {
        KEEP.clear();
        for (Module module : modules) {
            if (module == INSTANCE || !module.toggleable()) continue;
            String label = "Keep: " + module.name;
            Setting.Bool choice = INSTANCE.addSetting(new Setting.Bool(label,
                    "Restore " + module.name + " after joining another world/server. Only enable modules that are safe to restart in a fresh world.",
                    !module.startsOff()).showIf(() -> MODE.index() == 3));
            KEEP.put(module.id, choice);
        }
    }

    public static int mode() { return MODE.index(); }

    public static boolean keep(String moduleId) {
        Setting.Bool setting = KEEP.get(moduleId);
        return setting != null && setting.on();
    }
}
