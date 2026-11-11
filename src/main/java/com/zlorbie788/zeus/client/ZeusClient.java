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

    public static KeyMapping ATTRIBUTE_SWAP;
    public static KeyMapping SPEAR_LUNGE;

    public static KeyMapping PALETTE;

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
        ATTRIBUTE_SWAP = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.zeus.attribute_swap", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        SPEAR_LUNGE = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.zeus.spear_lunge", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        PALETTE = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.zeus.palette", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));
        Zeus.init();
        for (Module m : Zeus.all()) {
            if (!m.toggleable()) continue;
            m.key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.zeus.module." + m.id, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        }
        Commands.init();
        ZButton.register();
        HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, Identifier.fromNamespaceAndPath(MOD_ID, "hud"), (g, tracker) -> {
            Minecraft mc = Minecraft.getInstance();
            com.zlorbie788.zeus.ui.ZeusLoading.worldFade(g, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            if (mc.options.hideGui) return;
            if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) return;
            Zeus.hud(g, mc, tracker.getGameTimeDeltaPartialTick(false), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        });
        ClientTickEvents.END_CLIENT_TICK.register(ZeusClient::onTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            com.zlorbie788.zeus.client.web.LocalControlServer.stop();
            Zeus.save();
        });
        Zeus.LOG.info("Zeus {} loaded. Press Z in a world (or type /zeus) to open the panel.", Zeus.VERSION);
    }

    private static void onTick(Minecraft mc) {
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
            while (ATTRIBUTE_SWAP.consumeClick()) {
                Module swap = Zeus.get("attributeswap");
                if (swap instanceof com.zlorbie788.zeus.mods.Combat.AttributeSwap a) a.swap(mc);
            }
            while (SPEAR_LUNGE.consumeClick()) {
                Module spear = Zeus.get("spearswap");
                if (spear instanceof com.zlorbie788.zeus.mods.QuickMods.SpearSwap sp) sp.lunge(mc);
            }
            while (PALETTE.consumeClick()) {
                if (controlDown(mc) && !Zeus.destroyed()) mc.setScreen(new com.zlorbie788.zeus.ui.PaletteScreen(null));
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
            while (ATTRIBUTE_SWAP.consumeClick()) {  }
            while (SPEAR_LUNGE.consumeClick()) {  }
            while (PALETTE.consumeClick()) {  }
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
