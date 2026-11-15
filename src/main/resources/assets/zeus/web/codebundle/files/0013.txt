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
        if (System.getenv("ZEUS_FILM") != null) {
            film(ctx, System.getenv("ZEUS_FILM"));
            ctx.runOnClient(c -> com.zlorbie788.zeus.core.Clock.manual = -1L);
            ctx.runOnClient(c -> UiSettings.MENU.set(false));
            ctx.setScreen(TitleScreen::new);
            ctx.waitTicks(5);
            return;
        }
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
        ctx.setScreen(() -> new com.zlorbie788.zeus.ui.ZeusAboutScreen(new ZeusTitleScreen()));
        run(ctx, 0.8f);
        ctx.takeScreenshot("about_1080");
        ctx.setScreen(() -> com.zlorbie788.zeus.ui.KeybindScreen.forMenuKey(null));
        run(ctx, 0.5f);
        ctx.takeScreenshot("keys_menu_listen");
        ctx.setScreen(ZeusTitleScreen::new);
        run(ctx, 1.0f);

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
            for (float lid : new float[] {0.15f, 0.45f, 0.7f, 1.0f}) {
                ctx.runOnClient(c -> set(null, CratesScreen.class, "previewLid", lid));
                ctx.waitTicks(2);
                ctx.takeScreenshot("lid_" + tag + "_" + Math.round(lid * 100));
            }
            ctx.runOnClient(c -> set(null, CratesScreen.class, "previewLid", 0f));
            ctx.runOnClient(c -> Crates.setQuick(false));
            ctx.runOnClient(c -> open(c, "hades"));
            float[] at = {0.25f, 0.7f, 0.92f, 1.0f, 1.1f, 2.2f, 4.8f, 6.75f, 7.25f, 7.75f, 8.6f, 10.0f};
            float done = 0f;
            for (float a : at) {
                run(ctx, a - done);
                done = a;
                ctx.takeScreenshot("open_" + tag + "_" + Math.round(a * 100));
            }
            ctx.runOnClient(c -> set(c.screen, "opening", null));
            // a Chroma drop, straight to its reveal
            ctx.runOnClient(c -> {
                try {
                    Crates.Crate cr = Crates.crate("poseidon");
                    Crates.Result res = new Crates.Result(new Crates.Item("chroma_butterfly", Crates.Rarity.CHROMA), cr, false, 0);
                    Class<?> oc = Class.forName("com.zlorbie788.zeus.ui.CratesScreen$Opening");
                    var ctor = oc.getDeclaredConstructors()[0];
                    ctor.setAccessible(true);
                    Object op = ctor.newInstance(c.screen, cr, res, c.getWindow().getGuiScaledWidth() / 2, c.getWindow().getGuiScaledHeight() / 2, 80);
                    set(c.screen, "opening", op);
                    Method sk = oc.getDeclaredMethod("skip");
                    sk.setAccessible(true);
                    sk.invoke(op);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            });
            for (float a : new float[] {0.15f, 0.6f, 1.6f}) {
                run(ctx, a);
                ctx.takeScreenshot("chroma_reveal_" + tag + "_" + Math.round(a * 100));
            }
            ctx.runOnClient(c -> set(c.screen, "opening", null));
            ctx.runOnClient(c -> {
                try {
                    Method m = CratesScreen.class.getDeclaredMethod("openInspect", Crates.Item.class);
                    m.setAccessible(true);
                    m.invoke(c.screen, new Crates.Item("chroma_phoenix", Crates.Rarity.CHROMA));
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
            ctx.runOnClient(c -> {
                try {
                    var f = c.screen.getClass().getDeclaredField("search");
                    f.setAccessible(true);
                    var box = (net.minecraft.client.gui.components.EditBox) f.get(c.screen);
                    box.setFocused(true);
                    box.setValue("fly");
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            });
            run(ctx, 0.3f);
            ctx.takeScreenshot("keys_search_" + tag);
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
            for (String k : new String[] {"chroma_karambit", "chroma_m9", "chroma_crystal", "chroma_phoenix", "chroma_kunai"}) {
                ctx.runOnClient(c -> KnifeSwing.equipHd(Crates.hdOf(k)));
                ctx.waitTicks(30);
                ctx.takeScreenshot("hand_" + k);
            }
            // the Zeus menu over the world
            ctx.setScreen(() -> new com.zlorbie788.zeus.ui.ZeusScreen());
            ctx.waitTicks(30);
            ctx.takeScreenshot("zeus_menu_1920");
            ctx.setScreen(() -> null);
            ctx.waitTicks(5);
            // Name Tags on mobs in armour (players only off), one hurt, one with worn armour.
            // Effects quality "High": the test machine runs below 40 fps, which would draw the light version.
            ctx.runOnClient(c -> com.zlorbie788.zeus.ui.UiSettings.PERFORMANCE.set(1));
            ctx.runOnClient(c -> {
                var m = com.zlorbie788.zeus.core.Zeus.get("nametags");
                try {
                    var f = m.getClass().getDeclaredField("onlyPlayers");
                    f.setAccessible(true);
                    ((com.zlorbie788.zeus.core.Setting.Bool) f.get(m)).set(false);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
                m.setEnabled(true);
            });
            sp.getServer().runCommand("execute as @a at @s run summon zombie ^-2 ^ ^6 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Health:9f,CustomName:\"Hades\",equipment:{head:{id:\"minecraft:diamond_helmet\",components:{\"minecraft:damage\":300}},chest:{id:\"minecraft:netherite_chestplate\"},legs:{id:\"minecraft:iron_leggings\",components:{\"minecraft:damage\":180}},feet:{id:\"minecraft:diamond_boots\"},mainhand:{id:\"minecraft:netherite_sword\"}}}");
            sp.getServer().runCommand("execute as @a at @s run summon skeleton ^2.5 ^ ^9 {NoAI:1b,Silent:1b,PersistenceRequired:1b,equipment:{head:{id:\"minecraft:golden_helmet\",components:{\"minecraft:damage\":70}},mainhand:{id:\"minecraft:bow\"}}}");
            ctx.waitTicks(40);
            ctx.takeScreenshot("nametags_1920");
            // Zeus AI chat window with a short exchange and the model thinking
            ctx.runOnClient(c -> {
                var ai = com.zlorbie788.zeus.mods.AiPilot.find();
                try {
                    var f = ai.getClass().getDeclaredField("chatHud");
                    f.setAccessible(true);
                    ((com.zlorbie788.zeus.core.Setting.Bool) f.get(ai)).set(true);
                    var b = ai.getClass().getDeclaredField("busy");
                    b.setAccessible(true);
                    b.set(null, true);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
                ai.setEnabled(true);
                com.zlorbie788.zeus.mods.AiPilot.talk(true, "mine 10 iron and bring it back");
                com.zlorbie788.zeus.mods.AiPilot.talk(false, "On it: I'll make a stone pickaxe first, then dig down to iron level and waypoint every vein I find.");
                com.zlorbie788.zeus.mods.AiPilot.talk(true, "and light the tunnel");
            });
            ctx.waitTicks(10);
            ctx.takeScreenshot("ai_chat_1920");
            ctx.runOnClient(c -> {
                try {
                    var b = com.zlorbie788.zeus.mods.AiPilot.class.getDeclaredField("busy");
                    b.setAccessible(true);
                    b.set(null, false);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
                com.zlorbie788.zeus.mods.AiPilot.find().setEnabled(false);
                com.zlorbie788.zeus.ui.UiSettings.PERFORMANCE.set(0);
            });
            sp.getServer().runCommand("kill @e[type=!player]");
            ctx.runOnClient(c -> com.zlorbie788.zeus.core.Zeus.get("nametags").setEnabled(false));
            ctx.waitTicks(5);
            // at night the knife and arm dim with the world; the Chroma glow and sparkles still show
            sp.getServer().runCommand("time set midnight");
            ctx.runOnClient(c -> KnifeSwing.equipHd(Crates.hdOf("chroma_karambit")));
            ctx.waitTicks(40);
            ctx.takeScreenshot("hand_night_chroma_karambit");
            sp.getServer().runCommand("time set day");
            ctx.waitTicks(20);
            // the butterfly flicks open when it is drawn, then plays its tricks: photograph the flip as it goes
            for (String k : new String[] {"chroma_butterfly", "butterfly_fade"}) {
                ctx.runOnClient(c -> KnifeSwing.equipHd(Crates.hdOf(k)));
                for (int i = 0; i < 6; i++) {
                    ctx.waitTicks(2);
                    ctx.takeScreenshot("flip_" + k + "_open_" + i);
                }
                ctx.waitTicks(30);
                ctx.takeScreenshot("flip_" + k + "_idle");
                ctx.runOnClient(c -> KnifeSwing.pressTrick());
                for (int i = 0; i < 10; i++) {
                    ctx.waitTicks(3);
                    ctx.takeScreenshot("flip_" + k + "_trick_" + i);
                }
                ctx.waitTicks(200);
            }
            aiTest(ctx, sp, "get me an iron pickaxe", "iron_pickaxe", 1, "iron_ingot 3", "oak_log 2");
            aiTest(ctx, sp, "get me a lantern", "lantern", 1, "iron_ingot 1", "coal 1", "oak_log 2");
            aiTest(ctx, sp, "get me a shield", "shield", 1, "iron_ingot 1", "oak_log 3");
            aiTest(ctx, sp, "get me 3 iron ingots", "iron_ingot", 3, "raw_iron 3", "coal 1", "cobblestone 8");
            aiTest(ctx, sp, "get me 2 cooked beef", "cooked_beef", 2, "beef 2", "coal 1");
            aiTest(ctx, sp, "get me full iron armor", "worn:iron", 4, "iron_ingot 24", "oak_log 1");
            // leather only comes from mobs: the planner sends the loot job after the cows
            aiTest(ctx, sp, "get me an item frame", "item_frame", 1, "oak_log 1",
                    "/execute at @p run summon cow ~4 ~ ~", "/execute at @p run summon cow ~-4 ~ ~2", "/execute at @p run summon cow ~2 ~ ~-4",
                    "/execute at @p run summon cow ~-3 ~ ~-3", "/execute at @p run summon cow ~5 ~ ~4", "/execute at @p run summon cow ~-5 ~ ~5");
        }
        // the test framework wants to finish on the vanilla title screen
        ctx.runOnClient(c -> UiSettings.MENU.set(false));
        ctx.setScreen(TitleScreen::new);
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------------ film mode (ZEUS_FILM=1): smooth 30 fps footage
    private static final long FRAME = 33_333_333L;

    /** Records n frames: the UI clock moves exactly one video frame per picture. */
    private static void shoot(ClientGameTestContext ctx, String name, int n) {
        for (int i = 0; i < n; i++) {
            ctx.runOnClient(c -> com.zlorbie788.zeus.core.Clock.manual += FRAME);
            ctx.waitTicks(1);
            ctx.takeScreenshot(String.format("film_%s_%04d", name, i));
        }
    }

    private static float screenNow(Minecraft c) {
        try {
            Method m = CratesScreen.class.getDeclaredMethod("now");
            m.setAccessible(true);
            return (float) m.invoke(c.screen);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void startOpening(Minecraft c, String crate, String knife, Crates.Rarity rarity, boolean skip) {
        try {
            Crates.Crate cr = Crates.crate(crate);
            Crates.Result res = new Crates.Result(new Crates.Item(knife, rarity), cr, false, 0);
            Class<?> oc = Class.forName("com.zlorbie788.zeus.ui.CratesScreen$Opening");
            var ctor = oc.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            Object op = ctor.newInstance(c.screen, cr, res, c.getWindow().getGuiScaledWidth() / 2 - 40, c.getWindow().getGuiScaledHeight() / 2, 70);
            set(c.screen, "opening", op);
            if (skip) {
                Method sk = oc.getDeclaredMethod("skip");
                sk.setAccessible(true);
                sk.invoke(op);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void film(ClientGameTestContext ctx, String part) {
        ctx.waitFor(c -> c.screen instanceof ZeusTitleScreen, 20 * 60);
        ctx.runOnClient(c -> grant());
        ctx.runOnClient(c -> Crates.setQuick(false));
        run(ctx, 2.0f);
        if ("world".equals(part)) {
            filmWorld(ctx);
            return;
        }
        // the main menu building itself
        ctx.runOnClient(c -> com.zlorbie788.zeus.core.Clock.manual = System.nanoTime());
        ctx.setScreen(ZeusTitleScreen::new);
        shoot(ctx, "title", 165);
        // crates: the screen opening, picking crates, the tabs
        ctx.runOnClient(c -> { c.options.guiScale().set(0); c.resizeDisplay(); });
        ctx.setScreen(() -> new CratesScreen(null));
        shoot(ctx, "crates", 60);
        for (int i : new int[] {1, 3, 5}) {
            ctx.runOnClient(c -> { set(c.screen, "sel", i); set(c.screen, "selAt", screenNow(c)); });
            shoot(ctx, "crates_sel" + i, 40);
        }
        ctx.runOnClient(c -> { set(c.screen, "sel", 0); set(c.screen, "selAt", screenNow(c)); });
        shoot(ctx, "crates_sel0", 30);
        for (int t : new int[] {4, 1}) {
            ctx.runOnClient(c -> { set(c.screen, "prevTab", 0); set(c.screen, "tab", t); set(c.screen, "tabAt", screenNow(c)); });
            shoot(ctx, "crates_tab" + t, 50);
        }
        ctx.runOnClient(c -> { set(c.screen, "prevTab", 1); set(c.screen, "tab", 0); set(c.screen, "tabAt", screenNow(c)); });
        shoot(ctx, "crates_back", 20);
        // a full opening that lands on an Olympian karambit, then inspecting it
        ctx.runOnClient(c -> startOpening(c, "hades", "karambit_bloodstone", Crates.Rarity.OLYMPIAN, false));
        shoot(ctx, "opening", 330);
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
        shoot(ctx, "inspect", 120);
        ctx.runOnClient(c -> set(c.screen, "inspect", null));
        // the Chroma drop, from the reel stopping to the reveal
        ctx.runOnClient(c -> startOpening(c, "poseidon", "chroma_butterfly", Crates.Rarity.CHROMA, false));
        ctx.runOnClient(c -> {
            try {
                Object op = c.screen.getClass().getDeclaredField("opening");
                Field f = (Field) op;
                f.setAccessible(true);
                Object o = f.get(c.screen);
                Field sb = o.getClass().getDeclaredField("skipBy");
                sb.setAccessible(true);
                sb.set(o, 5.6f);           // start near the end of the spin
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
        shoot(ctx, "chroma", 210);
        ctx.runOnClient(c -> set(c.screen, "opening", null));
        ctx.setScreen(() -> null);
        // the keyboard: the backlight, then a key with its module list
        ctx.setScreen(() -> new com.zlorbie788.zeus.ui.KeybindScreen(null));
        shoot(ctx, "keys", 90);
        ctx.runOnClient(c -> {
            try {
                Method sel = c.screen.getClass().getDeclaredMethod("select", Class.forName("com.zlorbie788.zeus.ui.KeybindScreen$Cap"));
                Method cap = c.screen.getClass().getDeclaredMethod("capFor", com.mojang.blaze3d.platform.InputConstants.Key.class);
                sel.setAccessible(true);
                cap.setAccessible(true);
                sel.invoke(c.screen, cap.invoke(c.screen, com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G)));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
        shoot(ctx, "keys_pick", 60);
        ctx.setScreen(() -> null);
    }

    /** In a world: knives in the hand, tricks, the butterfly, the Zeus menu. */
    private static void filmWorld(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            sp.getServer().runCommand("time set 1000");
            sp.getServer().runCommand("gamerule advance_time false");
            ctx.runOnClient(c -> com.zlorbie788.zeus.core.Clock.manual = System.nanoTime());
            ctx.runOnClient(c -> KnifeSwing.equipHd(Crates.hdOf("karambit_bloodstone")));
            shoot(ctx, "hand_draw", 60);
            ctx.runOnClient(c -> KnifeSwing.pressTrick());
            shoot(ctx, "hand_trick1", 120);
            ctx.runOnClient(c -> { KnifeSwing.nextTrick(); KnifeSwing.nextTrick(); KnifeSwing.pressTrick(); });
            shoot(ctx, "hand_trick2", 120);
            ctx.runOnClient(c -> KnifeSwing.equipHd(Crates.hdOf("tac_lightning")));
            shoot(ctx, "hand_lightning", 75);
            ctx.runOnClient(c -> KnifeSwing.pressTrick());
            shoot(ctx, "hand_lightning_trick", 105);
            ctx.runOnClient(c -> KnifeSwing.equipHd(Crates.hdOf("chroma_phoenix")));
            shoot(ctx, "hand_phoenix", 75);
            ctx.runOnClient(c -> KnifeSwing.pressTrick());
            shoot(ctx, "hand_phoenix_trick", 105);
            ctx.runOnClient(c -> KnifeSwing.equipHd(Crates.hdOf("chroma_butterfly")));
            shoot(ctx, "hand_butterfly", 75);
            ctx.runOnClient(c -> KnifeSwing.pressTrick());
            shoot(ctx, "hand_butterfly_trick", 150);
            ctx.runOnClient(c -> KnifeSwing.equipHd(Crates.hdOf("chroma_karambit")));
            shoot(ctx, "hand_galaxy", 90);
            ctx.setScreen(() -> new com.zlorbie788.zeus.ui.ZeusScreen());
            shoot(ctx, "menu", 150);
            ctx.setScreen(() -> null);
            sp.getServer().runCommand("time set midnight");
            ctx.waitTicks(10);
            shoot(ctx, "night_galaxy", 75);
        }
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
        for (String g : give) sp.getServer().runCommand(g.startsWith("/") ? g.substring(1) : "give @a " + g);
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
            if (waited == 40) ctx.takeScreenshot("ai_" + item.replace(':', '_') + "_working");
            if (waited % 200 == 0) ctx.runOnClient(c -> System.out.println("ZEUS-AI-TEST status: " + com.zlorbie788.zeus.mods.AiPilot.status()));
            ctx.waitTicks(20);
            waited += 20;
        }
        int have = ctx.computeOnClient(c -> {
            int n = 0;
            if (item.startsWith("worn:")) {        // count armour pieces of this material actually being worn
                for (var slot : new net.minecraft.world.entity.EquipmentSlot[] {net.minecraft.world.entity.EquipmentSlot.HEAD,
                        net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
                    var st = c.player.getItemBySlot(slot);
                    if (!st.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().startsWith(item.substring(5) + "_")) n++;
                }
                return n;
            }
            var inv = c.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                var st = inv.getItem(i);
                if (!st.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().equals(item)) n += st.getCount();
            }
            return n;
        });
        ctx.runOnClient(c -> {
            for (String e : com.zlorbie788.zeus.mods.AiPilot.recentEvents()) System.out.println("ZEUS-AI-TEST event: " + e);
            StringBuilder b = new StringBuilder();
            var inv = c.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) if (!inv.getItem(i).isEmpty()) b.append(" [").append(i).append("]").append(inv.getItem(i).getCount()).append("x").append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(inv.getItem(i).getItem()).getPath());
            System.out.println("ZEUS-AI-TEST client inventory:" + b);
            StringBuilder m = new StringBuilder();
            var menu = c.player.inventoryMenu;
            for (int i = 0; i < 5; i++) if (!menu.getSlot(i).getItem().isEmpty()) m.append(" [").append(i).append("]").append(menu.getSlot(i).getItem().getCount()).append("x").append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(menu.getSlot(i).getItem().getItem()).getPath());
            System.out.println("ZEUS-AI-TEST client craft grid:" + m + "  carried=" + c.player.containerMenu.getCarried());
        });
        sp.getServer().runOnServer(srv -> {
            for (var pl : srv.getPlayerList().getPlayers()) {
                StringBuilder b = new StringBuilder();
                var inv = pl.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) if (!inv.getItem(i).isEmpty()) b.append(" [").append(i).append("]").append(inv.getItem(i).getCount()).append("x").append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(inv.getItem(i).getItem()).getPath());
                System.out.println("ZEUS-AI-TEST server inventory:" + b);
                StringBuilder m = new StringBuilder();
                for (int i = 0; i < 5; i++) if (!pl.inventoryMenu.getSlot(i).getItem().isEmpty()) m.append(" [").append(i).append("]").append(pl.inventoryMenu.getSlot(i).getItem().getCount()).append("x").append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(pl.inventoryMenu.getSlot(i).getItem().getItem()).getPath());
                System.out.println("ZEUS-AI-TEST server craft grid:" + m + "  carried=" + pl.containerMenu.getCarried());
            }
        });
        System.out.println("ZEUS-AI-TEST " + (have >= count ? "PASS" : "FAIL") + ": '" + ask + "' -> " + have + " " + item + " after " + waited + " ticks");
        ctx.takeScreenshot("ai_" + item.replace(':', '_') + "_done");
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
            for (String k : new String[] {"chroma_karambit", "chroma_m9", "chroma_crystal", "chroma_phoenix", "chroma_butterfly", "chroma_kunai"}) owned.add(k);
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
