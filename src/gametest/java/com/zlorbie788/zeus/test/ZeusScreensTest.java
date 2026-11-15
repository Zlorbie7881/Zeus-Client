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
import org.lwjgl.glfw.GLFW;
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
        ctx.runOnClient(c -> grant());
        run(ctx, 3.0f);
        ctx.takeScreenshot("title_1080");
        // unroll the news scroll and photograph it
        ctx.runOnClient(c -> {
            try {
                Field nf = c.screen.getClass().getDeclaredField("news");
                nf.setAccessible(true);
                Object news = nf.get(c.screen);
                set(news, "rolled", false);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
        run(ctx, 2.0f);
        ctx.takeScreenshot("title_news_1080");
        ctx.getInput().resizeWindow(854, 480);
        run(ctx, 0.6f);
        ctx.takeScreenshot("title_854");
        ctx.getInput().resizeWindow(1920, 1080);

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
            String[] tabNames = {"crates", "collection", "history", "redeem", "daily"};
            ctx.runOnClient(c -> {
                try {
                    com.zlorbie788.zeus.core.Quests.list();
                    Field tf = com.zlorbie788.zeus.core.Quests.class.getDeclaredField("targets");
                    Field pf = com.zlorbie788.zeus.core.Quests.class.getDeclaredField("progress");
                    Field df = com.zlorbie788.zeus.core.Quests.class.getDeclaredField("done");
                    tf.setAccessible(true);
                    pf.setAccessible(true);
                    df.setAccessible(true);
                    int[] tg = (int[]) tf.get(null);
                    double[] pr = (double[]) pf.get(null);
                    boolean[] dn = (boolean[]) df.get(null);
                    pr[0] = tg[0];
                    dn[0] = true;
                    pr[1] = tg[1] * 0.45;
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            });
            for (int tab = 1; tab <= 4; tab++) {
                int t = tab;
                ctx.runOnClient(c -> {
                    set(c.screen, "tab", t);
                    set(c.screen, "tabAt", -10f);
                });
                run(ctx, 0.6f);
                ctx.takeScreenshot(tabNames[t] + "_" + tag);
            }
            ctx.runOnClient(c -> set(c.screen, "tab", 0));
            ctx.runOnClient(c -> open(c, "hades"));
            float[] at = {0.25f, 0.7f, 1.0f, 2.2f, 4.8f, 6.75f, 7.25f, 7.75f, 8.6f, 10.0f};
            float done = 0f;
            for (float a : at) {
                run(ctx, a - done);
                done = a;
                ctx.takeScreenshot("open_" + tag + "_" + Math.round(a * 100));
            }
            ctx.runOnClient(c -> set(c.screen, "opening", null));
            ctx.runOnClient(c -> {
                try {
                    Method m = CratesScreen.class.getDeclaredMethod("openInspect", Crates.Item.class);
                    m.setAccessible(true);
                    m.invoke(c.screen, new Crates.Item("karambit_bloodstone", Crates.Rarity.OLYMPIAN));
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            });
            run(ctx, 0.9f);
            ctx.takeScreenshot("inspect_" + tag);
            ctx.setScreen(() -> null);
            ctx.waitTicks(4);
        }

        ctx.getInput().resizeWindow(1920, 1080);
        ctx.runOnClient(c -> {
            c.options.guiScale().set(0);
            c.resizeDisplay();
        });
        int first = com.zlorbie788.zeus.ui.KnifeArt.allKnives().length - com.zlorbie788.zeus.ui.KnifeArt.HD_COUNT;
        ctx.setScreen(() -> new com.zlorbie788.zeus.ui.KnifePickerScreen(null, first + Crates.starterHd(),
                new int[com.zlorbie788.zeus.ui.KnifeArt.allKnives().length], (k, f) -> { }));
        run(ctx, 0.8f);
        ctx.takeScreenshot("picker_karambit");
        ctx.setScreen(() -> null);
        // keybinds keyboard: link everyday modules (two share G), then photograph it at a few sizes
        ctx.runOnClient(c -> {
            Object[][] binds = {{"zoom", GLFW.GLFW_KEY_C}, {"freelook", GLFW.GLFW_KEY_V}, {"fullbright", GLFW.GLFW_KEY_G}, {"timechanger", GLFW.GLFW_KEY_G},
                    {"waypoints", GLFW.GLFW_KEY_J}, {"coordsnapper", GLFW.GLFW_KEY_K}, {"breadcrumbs", GLFW.GLFW_KEY_N}, {"autofish", GLFW.GLFW_KEY_M},
                    {"treebot", GLFW.GLFW_KEY_R}, {"autoeat", GLFW.GLFW_KEY_Y}, {"discordpresence", GLFW.GLFW_KEY_F6}, {"handmodel", GLFW.GLFW_KEY_KP_5},
                    {"chunkborders", GLFW.GLFW_KEY_H}};
            for (Object[] b : binds) {
                for (com.zlorbie788.zeus.core.Module m : com.zlorbie788.zeus.core.Zeus.all()) {
                    if (m.id.equals(b[0]) && m.key != null) m.key.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate((Integer) b[1]));
                }
            }
            for (com.zlorbie788.zeus.core.Module m : com.zlorbie788.zeus.core.Zeus.all()) {
                if (m.id.equals("reticle") && m.key != null) m.key.setKey(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(3));
            }
            net.minecraft.client.KeyMapping.resetMapping();
        });
        // Minecraft's own keybinds list, for comparison
        ctx.getInput().resizeWindow(1920, 1080);
        ctx.runOnClient(c -> {
            c.options.guiScale().set(2);
            c.resizeDisplay();
        });
        ctx.setScreen(() -> new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(null, Minecraft.getInstance().options));
        run(ctx, 0.5f);
        ctx.takeScreenshot("vanilla_keys_1920_gui2");
        int[][] ksizes = {{1920, 1080, 0}, {1920, 1080, 2}, {854, 480, 0}};
        for (int[] s : ksizes) {
            ctx.getInput().resizeWindow(s[0], s[1]);
            ctx.runOnClient(c -> {
                c.options.guiScale().set(s[2]);
                c.resizeDisplay();
            });
            String tag = s[0] + "_" + (s[2] == 0 ? "auto" : "gui" + s[2]);
            ctx.setScreen(() -> new com.zlorbie788.zeus.ui.KeybindScreen(null));
            run(ctx, 0.5f);
            ctx.takeScreenshot("keys_" + tag);
            ctx.runOnClient(c -> {
                try {
                    Method sel = c.screen.getClass().getDeclaredMethod("select", Class.forName("com.zlorbie788.zeus.ui.KeybindScreen$Cap"));
                    Method cap = c.screen.getClass().getDeclaredMethod("capFor", com.mojang.blaze3d.platform.InputConstants.Key.class);
                    sel.setAccessible(true);
                    cap.setAccessible(true);
                    sel.invoke(c.screen, cap.invoke(c.screen, com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_G)));
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            });
            run(ctx, 0.5f);
            ctx.takeScreenshot("keys_g_" + tag);
            ctx.runOnClient(c -> set(c.screen, "filter", 1));
            run(ctx, 0.3f);
            ctx.takeScreenshot("keys_g_linked_" + tag);
            ctx.setScreen(() -> null);
            ctx.waitTicks(2);
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
            aiTest(ctx, sp, "get me an iron pickaxe", "iron_pickaxe", 1, "iron_ingot 3", "oak_log 2");
            aiTest(ctx, sp, "get me a lantern", "lantern", 1, "iron_ingot 1", "coal 1", "oak_log 2");
            aiTest(ctx, sp, "get me a shield", "shield", 1, "iron_ingot 1", "oak_log 3");
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
    /** Asks Zeus AI for an item the way a player would (no AI key: the built-in parser and the planner) and checks the result. */
    private static void aiTest(ClientGameTestContext ctx, TestSingleplayerContext sp, String ask, String item, int count, String... give) {
        sp.getServer().runCommand("gamemode survival @a");
        sp.getServer().runCommand("clear @a");
        sp.getServer().runCommand("time set day");
        sp.getServer().runCommand("difficulty peaceful");
        for (String g : give) sp.getServer().runCommand("give @a " + g);
        ctx.waitTicks(10);
        ctx.runOnClient(c -> {
            for (com.zlorbie788.zeus.core.Module m : com.zlorbie788.zeus.core.Zeus.all()) {
                if (m instanceof com.zlorbie788.zeus.mods.AiPilot && !m.enabled()) m.toggle();
            }
            System.out.println("ZEUS-AI-TEST ask: " + ask + " -> " + com.zlorbie788.zeus.mods.AiPilot.ask(ask));
        });
        ctx.waitTicks(5);
        int waited = 0;
        while (waited < 1800) {
            boolean busy = ctx.computeOnClient(c -> com.zlorbie788.zeus.mods.AiPilot.working());
            if (!busy) break;
            if (waited == 40) ctx.takeScreenshot("ai_" + item + "_working");
            ctx.waitTicks(20);
            waited += 20;
        }
        int have = ctx.computeOnClient(c -> {
            int n = 0;
            var inv = c.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                var st = inv.getItem(i);
                if (!st.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().equals(item)) n += st.getCount();
            }
            return n;
        });
        ctx.runOnClient(c -> {
            for (String e : com.zlorbie788.zeus.mods.AiPilot.recentEvents()) System.out.println("ZEUS-AI-TEST event: " + e);
        });
        System.out.println("ZEUS-AI-TEST " + (have >= count ? "PASS" : "FAIL") + ": '" + ask + "' -> " + have + " " + item + " after " + waited + " ticks");
        ctx.takeScreenshot("ai_" + item + "_done");
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
    }

    private static void grant() {
        try {
            Crates.load();
            set(null, Crates.class, "tokens", 42);
            set(null, Crates.class, "keys", 30);
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
            // a remembered world, so the menu shows its Continue card
            Class<?> lp = Class.forName("com.zlorbie788.zeus.ui.LastPlayed");
            Class<?> en = Class.forName("com.zlorbie788.zeus.ui.LastPlayed$Entry");
            var ctor = en.getDeclaredConstructor(boolean.class, String.class, String.class, long.class);
            ctor.setAccessible(true);
            Object entry = ctor.newInstance(false, "New World", "Survival with friends", System.currentTimeMillis() - 2 * 3600_000L);
            set(null, lp, "last", entry);
            set(null, lp, "loaded", true);
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
