package com.zlorbie788.zeus.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.ui.UiSettings;
import com.zlorbie788.zeus.ui.ZButton;
import com.zlorbie788.zeus.ui.ZeusMenu;
import com.zlorbie788.zeus.ui.ZeusScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class ZeusClient implements ClientModInitializer {
    public static final String MOD_ID = "zeus";

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

    public static KeyMapping OPEN;

    public static KeyMapping TRICK;

    public static KeyMapping TRICK_NEXT;

    public static KeyMapping SPEAR_LUNGE;

    public static KeyMapping PALETTE;
    public static KeyMapping ASK_AI;

    private static boolean pendingOpen;
    private static boolean pendingHudEditor;
    private static boolean pendingKeybinds;

    public static void openLater() {
        pendingOpen = true;
    }

    public static void openKeybindsLater() {
        pendingKeybinds = true;
    }

    public static void openHudEditorLater() {
        pendingHudEditor = true;
    }

    @Override
    public void onInitializeClient() {
        UiSettings.touch();
        OPEN = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.zeus.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY));
        TRICK = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.zeus.trick", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY));
        TRICK_NEXT = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.zeus.trick_next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        SPEAR_LUNGE = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.zeus.spear_lunge", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        PALETTE = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.zeus.palette", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));
        ASK_AI = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.zeus.ask_ai", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY));
        Zeus.init();
        for (Module m : Zeus.all()) {
            if (!m.toggleable()) continue;
            m.key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.zeus.module." + m.id, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        }
        try {
            Commands.init();
        } catch (Throwable t) {
            Zeus.LOG.error("Zeus commands could not be registered; /zeus will be missing", t);
        }
        try {
            ZButton.register();
        } catch (Throwable t) {
            Zeus.LOG.error("The Zeus button could not be registered", t);
        }
        HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, Identifier.fromNamespaceAndPath(MOD_ID, "hud"), (g, tracker) -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                com.zlorbie788.zeus.ui.ZeusLoading.worldFade(g, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
                if (mc.options.hideGui) return;
                if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) return;
                Zeus.hud(g, mc, tracker.getGameTimeDeltaPartialTick(false), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            } catch (Throwable t) {
                hudErrors++;
                long now = System.currentTimeMillis();
                if (now - lastHudError > 4000L) {
                    lastHudError = now;
                    Zeus.LOG.error("Zeus HUD drawing threw; the HUD was skipped", t);
                }
                if (hudErrors > 200 && !Zeus.destroyed()) Zeus.destroy();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(ZeusClient::onTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            try {
                com.zlorbie788.zeus.client.web.LocalControlServer.stop();
                Zeus.save();
                com.zlorbie788.zeus.mods.AuctionMind.flush();
            } catch (Throwable t) {
                Zeus.LOG.error("Zeus could not finish saving while the game closed", t);
            }
        });
        Zeus.LOG.info("Zeus {} loaded. Press Z in a world (or type /zeus) to open the panel.", Zeus.VERSION);
    }

    private static long lastTickError;
    private static int tickErrors;
    private static long lastHudError;
    private static int hudErrors;

    private static void onTick(Minecraft mc) {
        try {
            onTickBody(mc);
        } catch (Throwable t) {
            tickErrors++;
            long now = System.currentTimeMillis();
            if (now - lastTickError > 4000L) {
                lastTickError = now;
                Zeus.LOG.error("Zeus threw during a client tick; that tick was skipped", t);
            }
            if (tickErrors > 40 && !Zeus.destroyed()) {
                Zeus.destroy();
                Zeus.LOG.error("Zeus kept failing and switched itself off for this session");
            }
        }
    }

    private static void onTickBody(Minecraft mc) {
        if (Zeus.destroyed()) {
            if (mc.screen instanceof ZeusScreen) mc.setScreen(null);
            return;
        }
        com.zlorbie788.zeus.ui.MenuAudio.tick();
        if (com.zlorbie788.zeus.ui.UiSettings.WINDOW_ICON.on()) WindowIcon.tick(mc);
        ZeusMenu.tick(mc);
        if (pendingOpen) {
            pendingOpen = false;
            if (mc.level != null) mc.setScreen(ZeusScreen.make(null));
        }
        if (pendingKeybinds) {
            pendingKeybinds = false;
            com.zlorbie788.zeus.mods.Quality.Keybinds.open();
        }
        if (pendingHudEditor) {
            pendingHudEditor = false;
            if (mc.level != null) mc.setScreen(new com.zlorbie788.zeus.ui.HudEditorScreen(null));
            else Zeus.toast("Open a world before editing the HUD", false);
        }
        if (mc.screen == null && mc.level != null) {
            while (OPEN.consumeClick()) mc.setScreen(ZeusScreen.make(null));
            while (TRICK.consumeClick()) com.zlorbie788.zeus.mods.KnifeSwing.pressTrick();
            while (TRICK_NEXT.consumeClick()) com.zlorbie788.zeus.mods.KnifeSwing.nextTrick();
            while (SPEAR_LUNGE.consumeClick()) {
                Module spear = Zeus.get("spearswap");
                if (spear instanceof com.zlorbie788.zeus.mods.QuickMods.SpearSwap sp) sp.lunge(mc);
            }
            while (PALETTE.consumeClick()) {
                if (controlDown(mc) && !Zeus.destroyed()) mc.setScreen(new com.zlorbie788.zeus.ui.PaletteScreen(null));
            }
            while (ASK_AI.consumeClick()) {
                // Opens chat with /zeus ai already typed, so asking the AI is one key away.
                if (mc.screen == null && !Zeus.destroyed()) mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("/zeus ai ", false));
            }
            if (mc.screen == null) {
                for (Module m : Zeus.all()) {
                    if (m.key == null) continue;
                    while (m.key.consumeClick()) m.toggle();
                }
            }
        } else {
            while (OPEN.consumeClick()) {
            }
            while (TRICK.consumeClick()) {
            }
            while (TRICK_NEXT.consumeClick()) {
            }
            while (SPEAR_LUNGE.consumeClick()) {  }
            while (PALETTE.consumeClick()) {  }
            while (ASK_AI.consumeClick()) {  }
            for (Module m : Zeus.all()) {
                if (m.key == null) continue;
                while (m.key.consumeClick()) {
                }
            }
        }
        com.zlorbie788.zeus.mods.QuickMods.pulse(mc);
        Zeus.tick(mc);
    }

    private static boolean controlDown(Minecraft mc) {
        try {
            return InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                    || InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
