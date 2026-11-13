package com.zlorbie788.zeus.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Recipe table and the menu clicking that pulls a recipe off it. Everything here works on the
 * open container menu and does one placement per tick, so a server sees ordinary inventory
 * clicks in a believable rhythm instead of a whole grid appearing at once.
 */
public final class Craft {
    private Craft() {}

    public static final Predicate<ItemStack> LOGS = s -> s.is(ItemTags.LOGS);
    public static final Predicate<ItemStack> PLANKS = s -> s.is(ItemTags.PLANKS);
    public static final Predicate<ItemStack> COBBLE = s -> s.is(Items.COBBLESTONE) || s.is(Items.COBBLED_DEEPSLATE);
    public static final Predicate<ItemStack> STICKS = s -> s.is(Items.STICK);
    public static final Predicate<ItemStack> COAL = s -> s.is(Items.COAL) || s.is(Items.CHARCOAL);
    public static final Predicate<ItemStack> IRON = s -> s.is(Items.IRON_INGOT);
    public static final Predicate<ItemStack> WOOL = s -> s.is(ItemTags.WOOL);

    public record Recipe(String name, List<Predicate<ItemStack>> cells, Item result, int yield, String... aliases) {
        public boolean small() {
            for (int i = 0; i < 9; i++) {
                if (cells.get(i) == null) continue;
                if (i / 3 > 1 || i % 3 > 1) return false;
            }
            return true;
        }
    }

    private static List<Predicate<ItemStack>> cells(Object... spec) {
        List<Predicate<ItemStack>> out = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) out.add(null);
        for (int i = 0; i < spec.length && i < 9; i++) {
            //noinspection unchecked
            out.set(i, (Predicate<ItemStack>) spec[i]);
        }
        return out;
    }

    private static final List<Recipe> RECIPES = List.of(
            new Recipe("planks", cells(LOGS), Items.OAK_PLANKS, 4, "plank", "wood planks", "oak planks"),
            new Recipe("stick", cells(PLANKS, null, null, PLANKS), Items.STICK, 4, "sticks"),
            new Recipe("crafting_table", cells(PLANKS, PLANKS, null, PLANKS, PLANKS), Items.CRAFTING_TABLE, 1,
                    "table", "workbench", "work bench", "crafting bench"),
            new Recipe("furnace", cells(COBBLE, COBBLE, COBBLE, COBBLE, null, COBBLE, COBBLE, COBBLE, COBBLE),
                    Items.FURNACE, 1, "oven", "smelter"),
            new Recipe("chest", cells(PLANKS, PLANKS, PLANKS, PLANKS, null, PLANKS, PLANKS, PLANKS, PLANKS),
                    Items.CHEST, 1, "box"),
            new Recipe("torch", cells(COAL, null, null, STICKS), Items.TORCH, 4, "torches"),
            new Recipe("stone_pickaxe", cells(COBBLE, COBBLE, COBBLE, null, STICKS, null, null, STICKS, null),
                    Items.STONE_PICKAXE, 1, "pickaxe", "pick"),
            new Recipe("stone_axe", cells(COBBLE, COBBLE, null, COBBLE, STICKS, null, null, STICKS, null),
                    Items.STONE_AXE, 1, "axe", "hatchet"),
            new Recipe("stone_shovel", cells(COBBLE, null, null, STICKS, null, null, STICKS, null, null),
                    Items.STONE_SHOVEL, 1, "shovel", "spade"),
            new Recipe("stone_sword", cells(COBBLE, null, null, COBBLE, null, null, STICKS, null, null),
                    Items.STONE_SWORD, 1, "sword"),
            new Recipe("stone_hoe", cells(COBBLE, COBBLE, null, null, STICKS, null, null, STICKS, null),
                    Items.STONE_HOE, 1, "hoe", "hoe item"),
            new Recipe("iron_pickaxe", cells(IRON, IRON, IRON, null, STICKS, null, null, STICKS, null),
                    Items.IRON_PICKAXE, 1, "iron pick"),
            new Recipe("iron_sword", cells(IRON, null, null, IRON, null, null, STICKS, null, null),
                    Items.IRON_SWORD, 1, "iron blade"),
            new Recipe("bucket", cells(IRON, null, IRON, null, IRON, null), Items.BUCKET, 1, "water bucket"),
            new Recipe("bed", cells(WOOL, WOOL, WOOL, PLANKS, PLANKS, PLANKS), Items.WHITE_BED, 1,
                    "white bed", "bed item")
    );

    public static List<Recipe> all() {
        return RECIPES;
    }

    public static String tidy(String wanted) {
        if (wanted == null) return "";
        return wanted.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
    }

    public static Recipe find(String wanted) {
        String key = tidy(wanted);
        if (key.isEmpty()) return null;
        for (Recipe r : RECIPES) {
            if (r.name.equals(key)) return r;
            for (String alias : r.aliases()) {
                if (alias.replace(' ', '_').equals(key)) return r;
            }
        }
        // "craft 4 planks" and "craft oak planks" both end up here
        for (Recipe r : RECIPES) {
            if (key.endsWith("_" + r.name) || key.contains(r.name)) return r;
        }
        return null;
    }

    public static int playerStart(AbstractContainerMenu menu) {
        return Math.max(0, menu.slots.size() - 36);
    }

    public static void click(Minecraft mc, AbstractContainerMenu menu, int slot, int button, ClickType type) {
        if (mc.player == null || mc.gameMode == null) return;
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, mc.player);
    }

    public static int find(AbstractContainerMenu menu, Predicate<ItemStack> want) {
        for (int i = playerStart(menu); i < menu.slots.size(); i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty() && want.test(stack)) return i;
        }
        return -1;
    }

    public static boolean hasAll(AbstractContainerMenu menu, Recipe recipe) {
        List<ItemStack> inv = new ArrayList<>();
        for (int i = playerStart(menu); i < menu.slots.size(); i++) inv.add(menu.getSlot(i).getItem());
        int[] left = new int[inv.size()];
        for (int i = 0; i < inv.size(); i++) left[i] = inv.get(i).getCount();
        for (Predicate<ItemStack> cell : recipe.cells()) {
            if (cell == null) continue;
            boolean got = false;
            for (int i = 0; i < inv.size(); i++) {
                if (left[i] <= 0 || inv.get(i).isEmpty() || !cell.test(inv.get(i))) continue;
                left[i]--;
                got = true;
                break;
            }
            if (!got) return false;
        }
        return true;
    }

    /** A recipe whose product is a material this one still needs and whose own materials we hold. */
    public static Recipe subFor(AbstractContainerMenu menu, Recipe recipe) {
        for (Predicate<ItemStack> cell : recipe.cells()) {
            if (cell == null) continue;
            for (Recipe other : RECIPES) {
                if (other.name.equals(recipe.name)) continue;
                ItemStack product = new ItemStack(other.result());
                if (!cell.test(product)) continue;
                if (hasAll(menu, other)) return other;
            }
        }
        return null;
    }

    public static boolean ready(AbstractContainerMenu menu, Recipe recipe, boolean table) {
        for (int i = 0; i < 9; i++) {
            Predicate<ItemStack> want = recipe.cells().get(i);
            if (want == null) continue;
            int slot = slotFor(i, table);
            if (slot < 0) return false;
            ItemStack have = menu.getSlot(slot).getItem();
            if (have.isEmpty() || !want.test(have)) return false;
        }
        return true;
    }

    /** Puts one missing ingredient in place and returns true, or true when the grid is already full. */
    public static boolean fill(Minecraft mc, AbstractContainerMenu menu, Recipe recipe, boolean table) {
        for (int i = 0; i < 9; i++) {
            Predicate<ItemStack> want = recipe.cells().get(i);
            if (want == null) continue;
            int slot = slotFor(i, table);
            if (slot < 0) continue;
            ItemStack have = menu.getSlot(slot).getItem();
            if (!have.isEmpty() && want.test(have)) continue;
            if (!have.isEmpty()) {
                click(mc, menu, slot, 0, ClickType.QUICK_MOVE);
                return false;
            }
            int from = find(menu, want);
            if (from < 0) return false;
            click(mc, menu, from, 0, ClickType.PICKUP);
            click(mc, menu, slot, 1, ClickType.PICKUP);
            click(mc, menu, from, 0, ClickType.PICKUP);
            return false;
        }
        return true;
    }

    public static void clearGrid(Minecraft mc, AbstractContainerMenu menu, boolean table) {
        int size = table ? 9 : 4;
        for (int i = 0; i < size; i++) {
            int slot = 1 + i;
            if (!menu.getSlot(slot).getItem().isEmpty()) {
                click(mc, menu, slot, 0, ClickType.QUICK_MOVE);
            }
        }
    }

    private static int slotFor(int cell, boolean table) {
        int row = cell / 3;
        int col = cell % 3;
        if (table) return 1 + row * 3 + col;
        if (row > 1 || col > 1) return -1;
        return 1 + row * 2 + col;
    }

    public static boolean itemMatches(ItemStack stack, String wanted) {
        if (stack.isEmpty() || wanted == null || wanted.isBlank()) return false;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        String key = wanted.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        if (key.endsWith("_ore")) key = key.substring(0, key.length() - 4);
        if (key.endsWith("s") && key.length() > 3) key = key.substring(0, key.length() - 1);
        return id.equals(key) || id.endsWith("_" + key) || id.contains(key);
    }

    /**
     * The vanilla recipe book knows every recipe the player has unlocked, which is far more than the
     * table above. When the item is not in the table, look it up here and let the server fill the grid.
     */
    public record Book(RecipeDisplayEntry entry, boolean table) {}

    public static Book book(Minecraft mc, String wanted) {
        if (mc.player == null || mc.level == null) return null;
        ContextMap context = SlotDisplayContext.fromLevel(mc.level);
        Item want = exact(wanted);
        Book loose = null;
        for (RecipeCollection collection : mc.player.getRecipeBook().getCollections()) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                ItemStack result;
                try {
                    result = entry.display().result().resolveForFirstStack(context);
                } catch (RuntimeException e) {
                    continue;
                }
                if (result.isEmpty()) continue;
                if (want != null && result.is(want)) return bookOf(entry, context);
                if (loose == null && itemMatches(result, wanted)) loose = bookOf(entry, context);
            }
        }
        return loose;
    }

    private static Book bookOf(RecipeDisplayEntry entry, ContextMap context) {
        boolean table = false;
        try {
            ItemStack station = entry.display().craftingStation().resolveForFirstStack(context);
            table = !station.isEmpty() && station.is(Items.CRAFTING_TABLE);
        } catch (RuntimeException ignored) {
        }
        return new Book(entry, table);
    }

    public static void bookPlace(Minecraft mc, AbstractContainerMenu menu, RecipeDisplayEntry entry) {
        if (mc.getConnection() == null || mc.player == null) return;
        mc.getConnection().send(new ServerboundPlaceRecipePacket(menu.containerId, entry.id(), false));
    }

    private static final java.util.Map<String, String> ALIASES = java.util.Map.of(
            "iron", "iron_ingot",
            "gold", "gold_ingot",
            "cobble", "cobblestone",
            "wood", "oak_planks",
            "plank", "oak_planks",
            "netherite", "netherite_ingot"
    );

    /** Resolves a spoken name to exactly one item: "iron" is an ingot, never iron ore or a sword. */
    public static Item exact(String wanted) {
        if (wanted == null) return null;
        String key = wanted.toLowerCase(Locale.ROOT).trim().replace(' ', '_').replace("minecraft:", "");
        Item found = byId(key);
        if (found != null) return found;
        String alias = ALIASES.get(key);
        if (alias != null) {
            found = byId(alias);
            if (found != null) return found;
        }
        if (key.endsWith("s") && key.length() > 3) {
            found = byId(key.substring(0, key.length() - 1));
            if (found != null) return found;
        }
        return null;
    }

    private static Item byId(String id) {
        net.minecraft.resources.Identifier key = net.minecraft.resources.Identifier.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return null;
        return BuiltInRegistries.ITEM.getValue(key);
    }

    public static Predicate<ItemStack> only(String wanted) {
        Item item = exact(wanted);
        return item == null ? null : s -> s.is(item);
    }

    public static Predicate<ItemStack> smeltInput(String wanted) {
        String key = wanted == null ? "" : wanted.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        return switch (key) {
            case "iron", "iron_ingot" -> s -> s.is(Items.RAW_IRON) || s.is(Items.IRON_ORE)
                    || s.is(Items.DEEPSLATE_IRON_ORE);
            case "gold", "gold_ingot" -> s -> s.is(Items.RAW_GOLD) || s.is(Items.GOLD_ORE)
                    || s.is(Items.DEEPSLATE_GOLD_ORE) || s.is(Items.NETHER_GOLD_ORE);
            case "copper", "copper_ingot" -> s -> s.is(Items.RAW_COPPER) || s.is(Items.COPPER_ORE)
                    || s.is(Items.DEEPSLATE_COPPER_ORE);
            default -> only(wanted);
        };
    }

    /** The first ingredient that is not fully available, so the AI knows what to go and get. */
    public static Predicate<ItemStack> missing(AbstractContainerMenu menu, Recipe recipe) {
        List<ItemStack> inv = new ArrayList<>();
        for (int i = playerStart(menu); i < menu.slots.size(); i++) inv.add(menu.getSlot(i).getItem());
        int[] left = new int[inv.size()];
        for (int i = 0; i < inv.size(); i++) left[i] = inv.get(i).getCount();
        for (Predicate<ItemStack> cell : recipe.cells()) {
            if (cell == null) continue;
            boolean got = false;
            for (int i = 0; i < inv.size(); i++) {
                if (left[i] <= 0 || inv.get(i).isEmpty() || !cell.test(inv.get(i))) continue;
                left[i]--;
                got = true;
                break;
            }
            if (!got) return cell;
        }
        return null;
    }

    public static int count(AbstractContainerMenu menu, Predicate<ItemStack> p) {
        int n = 0;
        for (int i = playerStart(menu); i < menu.slots.size(); i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty() && p.test(stack)) n += stack.getCount();
        }
        return n;
    }

    public static boolean fuel(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)
                || stack.is(ItemTags.PLANKS) || stack.is(ItemTags.LOGS)
                || stack.is(Items.STICK) || stack.is(Items.BLAZE_ROD) || stack.is(Items.LAVA_BUCKET));
    }
}
