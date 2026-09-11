package com.skriptvariables.util;

import ch.njol.skript.aliases.ItemType;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.util.Date;
import ch.njol.skript.util.Timespan;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skriptvariables.SkriptVariables;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.*;

public final class VariableApplier {

    public record ApplyResult(int applied, int skipped, List<String> errors) {}

    public record Change(String name, String type, String value) {}

    public record PreviewResult(List<Change> sets, List<Change> deletes, List<Change> unparseable) {}

    public static PreviewResult preview(String diffJson) {
        return preview(parseChanges(diffJson));
    }

    public static PreviewResult preview(List<Change> changes) {
        List<Change> sets = new ArrayList<>();
        List<Change> deletes = new ArrayList<>();
        List<Change> unparseable = new ArrayList<>();

        for (Change change : changes) {
            if ("null".equalsIgnoreCase(change.type())) {
                deletes.add(change);
                continue;
            }
            Object parsed;
            try {
                parsed = parseValue(change.type(), change.value());
            } catch (Exception e) {
                parsed = null;
            }
            if (parsed == null) unparseable.add(change);
            else sets.add(change);
        }
        return new PreviewResult(sets, deletes, unparseable);
    }

    public static ApplyResult apply(String diffJson) {
        return apply(parseChanges(diffJson), SkriptVariableStore.INSTANCE);
    }

    public static ApplyResult apply(List<Change> changes, VariableStore store) {
        int applied = 0, skipped = 0;
        List<String> errors = new ArrayList<>();

        for (Change change : changes) {
            try {
                if (isListDelete(change)) {
                    deleteList(change.name(), store);
                    applied++;
                } else if ("null".equalsIgnoreCase(change.type())) {
                    store.set(change.name(), null);
                    applied++;
                } else {
                    Object parsed = parseValue(change.type(), change.value());
                    if (parsed == null) { skipped++; continue; }
                    store.set(change.name(), parsed);
                    applied++;
                }
            } catch (Exception e) {
                errors.add(change.name() + ": " + e.getMessage());
                skipped++;
            }
        }

        return new ApplyResult(applied, skipped, errors);
    }

    public static boolean isListDelete(Change change) {
        return "null".equalsIgnoreCase(change.type()) && change.name().endsWith("::*");
    }

    public static int deleteList(String listName, VariableStore store) {
        if (!(store.get(listName) instanceof Map<?, ?> map)) return 0;
        List<String> indices = new ArrayList<>();
        for (Object key : map.keySet()) {
            if (key != null) indices.add(key.toString());
        }
        String prefix = listName.substring(0, listName.length() - 1);
        for (String index : indices) {
            store.set(prefix + index, null);
        }
        store.set(listName, null);
        return indices.size();
    }

    public static List<String> parseNames(String diffJson) {
        return parseChanges(diffJson).stream().map(Change::name).toList();
    }

    public static List<Change> parseChanges(String json) {
        List<Change> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        JsonElement root = JsonParser.parseString(json);
        JsonArray array;
        if (root.isJsonArray()) {
            array = root.getAsJsonArray();
        } else if (root.isJsonObject() && root.getAsJsonObject().get("changes") instanceof JsonArray wrapped) {
            array = wrapped;
        } else {
            return out;
        }
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();
            String name = field(obj, "n");
            if (name == null || name.isEmpty()) continue;
            String type = field(obj, "t");
            String value = field(obj, "v");
            out.add(new Change(name, type == null ? "" : type, value == null ? "" : value));
        }
        return out;
    }

    private static String field(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) return null;
        return element.isJsonPrimitive() ? element.getAsString() : element.toString();
    }

    @SuppressWarnings("deprecation")
    private static Object parseValue(String type, String value) {
        return switch (type.toLowerCase()) {
            case "string" -> value;
            case "long", "integer", "int" -> {
                try { yield Long.parseLong(value); }
                catch (NumberFormatException e) { yield null; }
            }
            case "double", "float", "number" -> {
                try { yield Double.parseDouble(value); }
                catch (NumberFormatException e) { yield null; }
            }
            case "boolean" -> Boolean.parseBoolean(value);
            case "location" -> {
                String[] p = value.split(",", 6);
                if (p.length < 4) yield null;
                World world = Bukkit.getWorld(p[0].trim());
                if (world == null) yield null;
                try {
                    double x     = Double.parseDouble(p[1].trim());
                    double y     = Double.parseDouble(p[2].trim());
                    double z     = Double.parseDouble(p[3].trim());
                    float yaw   = p.length > 4 ? Float.parseFloat(p[4].trim()) : 0f;
                    float pitch = p.length > 5 ? Float.parseFloat(p[5].trim()) : 0f;
                    yield new Location(world, x, y, z, yaw, pitch);
                } catch (NumberFormatException e) { yield null; }
            }
            case "itemtype" -> Classes.parseSimple(value.trim(), ItemType.class, ParseContext.DEFAULT);
            case "blockdata" -> Classes.parseSimple(value.trim(), BlockData.class, ParseContext.DEFAULT);
            case "vector" -> {
                String[] p = value.split(",", 3);
                if (p.length < 3) yield null;
                try {
                    yield new Vector(
                        Double.parseDouble(p[0].trim()),
                        Double.parseDouble(p[1].trim()),
                        Double.parseDouble(p[2].trim())
                    );
                } catch (NumberFormatException e) { yield null; }
            }
            case "textcomponent", "text component" ->
                Classes.parseSimple(value, Component.class, ParseContext.DEFAULT);
            case "item", "itemstack" -> parseItem(value);
            case "bound" -> parseBound(value);
            case "timespan" -> Classes.parseSimple(value.trim(), Timespan.class, ParseContext.DEFAULT);
            case "date" -> {
                try { yield new Date(Long.parseLong(value.trim())); }
                catch (NumberFormatException e) { yield null; }
            }
            case "color" -> Classes.parseSimple(value.trim(), ch.njol.skript.util.Color.class, ParseContext.DEFAULT);
            case "world" -> Bukkit.getWorld(value.trim());
            case "entitytype", "entity type" -> Classes.parseSimple(value.trim(), org.bukkit.entity.EntityType.class, ParseContext.DEFAULT);
            case "gamemode"   -> Classes.parseSimple(value.trim(), org.bukkit.GameMode.class, ParseContext.DEFAULT);
            case "difficulty" -> Classes.parseSimple(value.trim(), org.bukkit.Difficulty.class, ParseContext.DEFAULT);
            case "biome"      -> Classes.parseSimple(value.trim(), org.bukkit.block.Biome.class, ParseContext.DEFAULT);
            case "sound"      -> Classes.parseSimple(value.trim(), org.bukkit.Sound.class, ParseContext.DEFAULT);
            case "potioneffect", "potion effect" -> Classes.parseSimple(value.trim(), org.bukkit.potion.PotionEffect.class, ParseContext.DEFAULT);
            case "potioneffecttype", "potion effect type" -> Classes.parseSimple(value.trim(), org.bukkit.potion.PotionEffectType.class, ParseContext.DEFAULT);
            default -> {
                if (SkriptVariables.isOopskPresent()) {
                    Object struct = StructHelper.tryParseStruct(value);
                    if (struct != null) yield struct;
                }
                if (NbtHelper.isPresent() && value.startsWith("{")) {
                    yield NbtHelper.fromJson(value);
                }
                yield null;
            }
        };
    }

    @SuppressWarnings("deprecation")
    private static ItemStack parseItem(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (!obj.has("material")) return null;
            String matKey = obj.get("material").getAsString();
            String matName = matKey.contains(":") ? matKey.split(":", 2)[1] : matKey;
            Material material = Material.matchMaterial(matName);
            if (material == null) material = Material.matchMaterial(matName.toUpperCase());
            if (material == null) return null;

            int amount = obj.has("amount") ? obj.get("amount").getAsInt() : 1;
            ItemStack item = new ItemStack(material, amount);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return item;

            if (obj.has("name")) {
                meta.displayName(LegacyComponentSerializer.legacySection()
                    .deserialize(obj.get("name").getAsString()));
            }

            if (obj.has("lore")) {
                JsonArray loreArr = obj.getAsJsonArray("lore");
                List<Component> lore = new ArrayList<>();
                for (JsonElement el : loreArr) {
                    lore.add(LegacyComponentSerializer.legacySection().deserialize(el.getAsString()));
                }
                meta.lore(lore);
            }

            if (obj.has("enchants")) {
                JsonObject enchants = obj.getAsJsonObject("enchants");
                for (String enchKey : enchants.keySet()) {
                    int level = enchants.get(enchKey).getAsInt();
                    Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(enchKey));
                    if (ench != null) meta.addEnchant(ench, level, true);
                }
            }

            if (obj.has("damage") && meta instanceof Damageable dmg) {
                dmg.setDamage(obj.get("damage").getAsInt());
            }

            if (obj.has("unbreakable") && obj.get("unbreakable").getAsBoolean()) {
                meta.setUnbreakable(true);
            }

            if (obj.has("customModelData")) {
                meta.setCustomModelData(obj.get("customModelData").getAsInt());
            }

            item.setItemMeta(meta);
            return item;
        } catch (Exception e) {
            return null;
        }
    }

    private static Object parseBound(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            Class<?> skBeeClass = Class.forName("com.shanebeestudios.skbee.SkBee");
            Object plugin = skBeeClass.getMethod("getPlugin").invoke(null);
            Object config = plugin.getClass().getMethod("getBoundConfig").invoke(plugin);
            return config.getClass().getMethod("getBoundFromID", String.class).invoke(config, id.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
