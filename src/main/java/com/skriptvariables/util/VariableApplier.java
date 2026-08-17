package com.skriptvariables.util;

import ch.njol.skript.aliases.ItemType;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.util.Date;
import ch.njol.skript.util.Timespan;
import ch.njol.skript.variables.Variables;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VariableApplier {

    private static final Pattern KV_PATTERN =
        Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    public record ApplyResult(int applied, int skipped, List<String> errors) {}

    public static ApplyResult apply(String diffJson) {
        List<Map<String, String>> changes = parseDiff(diffJson);
        int applied = 0, skipped = 0;
        List<String> errors = new ArrayList<>();

        for (Map<String, String> change : changes) {
            String name = change.get("n");
            String type = change.get("t");
            String value = change.getOrDefault("v", "");

            if (name == null || name.isEmpty()) { skipped++; continue; }

            try {
                if ("null".equalsIgnoreCase(type)) {
                    Variables.setVariable(name, null, null, false);
                    applied++;
                } else {
                    Object parsed = parseValue(type, value);
                    if (parsed == null) { skipped++; continue; }
                    Variables.setVariable(name, parsed, null, false);
                    applied++;
                }
            } catch (Exception e) {
                errors.add(name + ": " + e.getMessage());
                skipped++;
            }
        }

        return new ApplyResult(applied, skipped, errors);
    }

    public static List<String> parseNames(String diffJson) {
        return parseDiff(diffJson).stream()
            .map(m -> m.get("n"))
            .filter(n -> n != null && !n.isEmpty())
            .toList();
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

    private static List<Map<String, String>> parseDiff(String json) {
        List<Map<String, String>> result = new ArrayList<>();
        int start = json.indexOf("[");
        int end   = json.lastIndexOf("]");
        if (start < 0 || end < 0) return result;

        for (String obj : splitObjects(json.substring(start + 1, end))) {
            Map<String, String> map = parseObject(obj.trim());
            if (!map.isEmpty()) result.add(map);
        }
        return result;
    }

    private static List<String> splitObjects(String arr) {
        List<String> objects = new ArrayList<>();
        int depth = 0, objStart = -1;
        for (int i = 0; i < arr.length(); i++) {
            char c = arr.charAt(i);
            if (c == '{') { if (depth++ == 0) objStart = i; }
            else if (c == '}' && --depth == 0 && objStart >= 0) objects.add(arr.substring(objStart, i + 1));
        }
        return objects;
    }

    private static Map<String, String> parseObject(String obj) {
        Map<String, String> map = new LinkedHashMap<>();
        Matcher m = KV_PATTERN.matcher(obj);
        while (m.find()) map.put(m.group(1), unescape(m.group(2)));
        return map;
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\t", "\t");
    }
}
