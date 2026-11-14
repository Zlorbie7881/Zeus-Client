package com.zlorbie788.zeus.test;

import com.zlorbie788.zeus.core.Crates;
import com.zlorbie788.zeus.mods.KnifeSwing;
import com.zlorbie788.zeus.ui.CratesScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import com.zlorbie788.zeus.ui.UiSettings;
import com.zlorbie788.zeus.ui.ZeusTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

/**
 * Photographs the Zeus menus in the real game: title screen, every Crates tab and an opening from
 * burst to reveal, at the GUI sizes players actually use, then a world with the knife in hand.
 */
@SuppressWarnings("UnstableApiUsage")
public class ZeusScreensTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.getInput().resizeWindow(1920, 1080);
        // the intro video plays first; wait for the Zeus menu itself, then let its buttons slide in
        ctx.waitFor(c -> c.screen instanceof ZeusTitleScreen, 20 * 60);
        run(ctx, 3.0f);
        ctx.takeScreenshot("title_1080");

        ctx.runOnClient(c -> grant());

        int[][] sizes = {{1920, 1080, 0}, {1920, 1080, 2}, {854, 480, 0}};
        for (int[] s : sizes) {
            ctx.getInput().resizeWindow(s[0], s[1]);
            ctx.runOnClient(c -> {
                c.options.guiScale().set(s[2]);
                c.resizeDisplay();
            });
            String tag = s[0] + "_" + (s[2] == 0 ? "auto" : "gui" + s[2]);
            ctx.setScreen(() -> new CratesScreen(null));
            run(ctx, 0.8f);
            ctx.takeScreenshot("crates_" + tag);
            for (int tab = 1; tab <= 2; tab++) {
                int t = tab;
                ctx.runOnClient(c -> set(c.screen, "tab", t));
                run(ctx, 0.5f);
                ctx.takeScreenshot((t == 1 ? "collection_" : "redeem_") + tag);
            }
            ctx.runOnClient(c -> set(c.screen, "tab", 0));
            ctx.runOnClient(c -> open(c, "hades"));
            float[] at = {0.35f, 1.6f, 4.5f, 7.45f, 8.4f, 10.2f};
            float done = 0f;
            for (float a : at) {
                run(ctx, a - done);
                done = a;
                ctx.takeScreenshot("open_" + tag + "_" + Math.round(a * 100));
            }
            ctx.setScreen(() -> null);
            ctx.waitTicks(4);
        }

        ctx.getInput().resizeWindow(1920, 1080);
        ctx.runOnClient(c -> {
            c.options.guiScale().set(0);
            c.resizeDisplay();
        });
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            ctx.runOnClient(c -> KnifeSwing.equipHd(Crates.hdOf("karambit_bloodstone")));
            ctx.waitTicks(40);
            ctx.takeScreenshot("hand_bloodstone");
            ctx.runOnClient(c -> KnifeSwing.equipHd(Crates.starterHd()));
            ctx.waitTicks(30);
            ctx.takeScreenshot("hand_starter");
        }
        // the test framework wants to finish on the vanilla title screen
        ctx.runOnClient(c -> UiSettings.MENU.set(false));
        ctx.setScreen(TitleScreen::new);
        ctx.waitTicks(5);
    }

    /** Keeps the game ticking and drawing for this many real seconds. */
    private static void run(ClientGameTestContext ctx, float seconds) {
        long end = System.nanoTime() + (long) (seconds * 1e9);
        while (System.nanoTime() < end) {
            ctx.waitTick();
            sleep(15);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(Math.max(0, ms));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private static void grant() {
        try {
            Crates.load();
            set(null, Crates.class, "tokens", 42);
            set(null, Crates.class, "keys", 8);
            Field h = Crates.class.getDeclaredField("held");
            h.setAccessible(true);
            Map<String, Integer> held = (Map<String, Integer>) h.get(null);
            for (Crates.Crate c : Crates.CRATES) held.put(c.id, 2);
            Field o = Crates.class.getDeclaredField("owned");
            o.setAccessible(true);
            Set<String> owned = (Set<String>) o.get(null);
            owned.add("karambit_bloodstone");
            owned.add("tac_lightning");
            owned.add("m9_frost");
            owned.add("butterfly_fade");
            owned.add("bowie_olympus");
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void open(Minecraft c, String crate) {
        try {
            Method m = CratesScreen.class.getDeclaredMethod("startOpening", Crates.Crate.class);
            m.setAccessible(true);
            m.invoke(c.screen, Crates.crate(crate));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void set(Object target, String field, Object value) {
        set(target, target.getClass(), field, value);
    }

    private static void set(Object target, Class<?> cls, String field, Object value) {
        try {
            Field f = cls.getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
