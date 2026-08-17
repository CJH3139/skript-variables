package com.skriptvariables.util;

import ch.njol.skript.aliases.ItemType;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.util.Date;
import ch.njol.skript.util.Timespan;
import ch.njol.skript.variables.SerializedVariable;
import ch.njol.skript.variables.Variables;
import com.skriptvariables.SkriptVariables;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.regex.*;
import java.util.HexFormat;

public final class SessionUploader {

    private static final int       CHUNK_SIZE  = 10_000;
    private static final int       UPLOAD_CONCURRENCY = 6;
    private static final HexFormat HEX         = HexFormat.of();

    private static volatile boolean METHODS_INIT = false;
    private static Method M_READ_LOCK;
    private static Method M_VARIABLES;

    private static final Set<String> COMPLEX_TYPES = Set.of("location", "itemtype", "blockdata", "vector", "textcomponent", "text component", "item", "itemstack", "bound", "timespan", "date", "color", "world", "entitytype", "entity type", "gamemode", "difficulty", "biome", "sound", "potioneffect", "potion effect", "potioneffecttype", "potion effect type");

    private static final Pattern CSV_PATTERN =
        Pattern.compile("(?<=^|,)\\s*(?:([^\",]*)|\"((?:[^\"]+|\"\")*)\")\\s*(?:,|$)");

    public record UploadResult(String sessionId, int totalVars, int totalChunks) {}

    public static UploadResult upload(File csvFile) throws Exception {
        LinkedHashMap<String, String[]> live = new LinkedHashMap<>();

        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] cols = splitCsv(line);
                if (cols == null || cols.length < 2) continue;

                String name = cols[0];
                String type = cols[1];
                if ("null".equalsIgnoreCase(type)) {
                    live.remove(name);
                } else {
                    live.put(name, cols);
                }
            }
        }

        Map<String, Object> flat = mergeLiveOnly(live);

        List<String[]> vars = new ArrayList<>(live.values());
        int total = vars.size();
        int totalChunks = (int) Math.ceil((double) total / CHUNK_SIZE);

        ValueMaps maps = buildValueMaps(vars, flat);

        String sessionId = ApiClient.createSession(total);

        uploadChunksInParallel(sessionId, vars, total, totalChunks, maps);

        ApiClient.markReady(sessionId, totalChunks);
        return new UploadResult(sessionId, total, totalChunks);
    }

    private static void uploadChunksInParallel(String sessionId, List<String[]> vars, int total,
                                               int totalChunks, ValueMaps maps) throws Exception {
        if (totalChunks <= 1) {
            if (totalChunks == 1) {
                String json = buildChunkJson(vars, maps.display(), maps.structs(), maps.items(), maps.nbts());
                ApiClient.uploadChunk(sessionId, 0, json);
            }
            return;
        }

        int threads = Math.min(UPLOAD_CONCURRENCY, totalChunks);
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "skv-upload");
            t.setDaemon(true);
            return t;
        });

        try {
            List<Future<?>> futures = new ArrayList<>(totalChunks);
            for (int i = 0; i < totalChunks; i++) {
                final int idx = i;
                int from = i * CHUNK_SIZE;
                int to = Math.min(from + CHUNK_SIZE, total);
                final String json = buildChunkJson(vars.subList(from, to),
                    maps.display(), maps.structs(), maps.items(), maps.nbts());
                futures.add(pool.submit(() -> {
                    ApiClient.uploadChunk(sessionId, idx, json);
                    return null;
                }));
            }

            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof Exception ex) throw ex;
                    throw new Exception("Chunk upload failed", cause);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static Map<String, Object> mergeLiveOnly(LinkedHashMap<String, String[]> live) {
        Map<String, Object> flat = getSkriptFlatVars();
        if (flat == null) return null;

        final boolean oopsk = SkriptVariables.isOopskPresent();
        live.keySet().removeIf(name -> {
            Object v = flat.get(name);
            if (v != null && !(v instanceof Map)) return false;
            if (!oopsk) return true;
            try {
                Object val = Variables.getVariable(name, null, false);
                return val == null || StructHelper.tryFormatStruct(val) == null;
            } catch (Exception e) {
                return true;
            }
        });

        for (Map.Entry<String, Object> entry : flat.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) continue;
            if (name.startsWith("_")) continue;
            SerializedVariable sv = Variables.serialize(name, value);
            if (sv == null || sv.value == null) {
                if (oopsk) {
                    try {
                        if (StructHelper.tryFormatStruct(value) != null)
                            live.put(name, new String[]{name, "struct", ""});
                    } catch (Exception ignored) {}
                }
                continue;
            }
            live.put(name, new String[]{name, sv.value.type, bytesToHex(sv.value.data)});
        }
        return flat;
    }

    @SuppressWarnings("unchecked")
    private static void flattenVars(TreeMap<String, Object> tree, String prefix, TreeMap<String, Object> out) {
        for (Map.Entry<String, Object> entry : tree.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (key == null) {
                if (!prefix.isEmpty() && val != null) out.put(prefix, val);
            } else {
                if (prefix.isEmpty() && key.startsWith("_")) continue;
                String name = prefix.isEmpty() ? key : prefix + "::" + key;
                if (val instanceof TreeMap) {
                    flattenVars((TreeMap<String, Object>) val, name, out);
                } else if (val != null) {
                    out.put(name, val);
                }
            }
        }
    }

    private static void initMethods() {
        if (METHODS_INIT) return;
        try { M_READ_LOCK = Variables.class.getDeclaredMethod("getReadLock"); M_READ_LOCK.setAccessible(true); } catch (ReflectiveOperationException ignored) {}
        try { M_VARIABLES = Variables.class.getDeclaredMethod("getVariables"); M_VARIABLES.setAccessible(true); } catch (ReflectiveOperationException ignored) {}
        METHODS_INIT = true;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getSkriptFlatVars() {
        initMethods();
        if (M_VARIABLES == null) return null;
        Lock lock = null;
        if (M_READ_LOCK != null) {
            try { lock = (Lock) M_READ_LOCK.invoke(null); lock.lock(); } catch (ReflectiveOperationException e) { lock = null; }
        }
        try {
            TreeMap<String, Object> root = (TreeMap<String, Object>) M_VARIABLES.invoke(null);
            if (root == null) return null;
            TreeMap<String, Object> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            flattenVars(root, "", out);
            return out;
        } catch (ReflectiveOperationException e) {
            return null;
        } finally {
            if (lock != null) lock.unlock();
        }
    }

    private static String bytesToHex(byte[] bytes) {
        return HEX.formatHex(bytes);
    }

    private record ValueMaps(Map<String, String> display,
                             Map<String, String> structs,
                             Map<String, String> items,
                             Map<String, String> nbts) {}

    private static Object lookup(Map<String, Object> flat, String name) {
        if (flat != null) {
            Object v = flat.get(name);
            if (v != null) return v;
        }
        try {
            return Variables.getVariable(name, null, false);
        } catch (Exception e) {
            return null;
        }
    }

    private static ValueMaps buildValueMaps(List<String[]> vars, Map<String, Object> flat) {
        final boolean oopsk  = SkriptVariables.isOopskPresent();
        final boolean nbtApi = NbtHelper.isPresent();

        Map<String, String> display = new HashMap<>();
        Map<String, String> structs = new HashMap<>();
        Map<String, String> items   = new HashMap<>();
        Map<String, String> nbts    = new HashMap<>();

        for (String[] cols : vars) {
            String name = cols[0];
            String type = cols[1].toLowerCase();
            Object val  = lookup(flat, name);

            if (COMPLEX_TYPES.contains(type)) {
                String hex = cols.length > 2 ? cols[2] : "";
                display.put(name, resolveDisplay(val, type, hex));
                if (val instanceof ItemStack item && (type.equals("item") || type.equals("itemstack"))) {
                    String json = itemToJson(item);
                    if (json != null) items.put(name, json);
                }
                continue;
            }

            if (val == null) continue;

            if (oopsk) {
                try {
                    String json = StructHelper.tryFormatStruct(val);
                    if (json != null) {
                        structs.put(name, json);
                        continue;
                    }
                } catch (Exception ignored) {}
            }

            if (nbtApi) {
                try {
                    if (NbtHelper.isNbtCompound(val)) {
                        String json = NbtHelper.toJson(val);
                        if (json != null) nbts.put(name, json);
                    }
                } catch (Exception ignored) {}
            }
        }
        return new ValueMaps(display, structs, items, nbts);
    }

    @SuppressWarnings("deprecation")
    private static String resolveDisplay(Object val, String type, String hexFallback) {
        try {
            if (val == null) return hexFallback;
            return switch (type) {
                case "item", "itemstack" -> {
                    if (!(val instanceof ItemStack item)) yield hexFallback;
                    String mat = item.getType().getKey().toString();
                    yield item.getAmount() > 1 ? mat + " ×" + item.getAmount() : mat;
                }
                case "location" -> {
                    if (!(val instanceof Location loc)) yield hexFallback;
                    if (loc.getWorld() == null) yield hexFallback;
                    yield loc.getWorld().getName()
                        + "," + loc.getX()
                        + "," + loc.getY()
                        + "," + loc.getZ()
                        + "," + loc.getYaw()
                        + "," + loc.getPitch();
                }
                case "itemtype" -> {
                    if (!(val instanceof ItemType)) yield hexFallback;
                    yield Classes.toString(val);
                }
                case "blockdata" -> {
                    if (!(val instanceof BlockData)) yield hexFallback;
                    yield Classes.toString(val);
                }
                case "vector" -> {
                    if (!(val instanceof Vector vec)) yield hexFallback;
                    yield vec.getX() + "," + vec.getY() + "," + vec.getZ();
                }
                case "textcomponent", "text component" -> Classes.toString(val);
                case "timespan" -> {
                    if (!(val instanceof Timespan)) yield hexFallback;
                    yield Classes.toString(val);
                }
                case "date" -> {
                    if (!(val instanceof Date date)) yield hexFallback;
                    yield String.valueOf(date.getTimestamp());
                }
                case "color" -> Classes.toString(val);
                case "world" -> {
                    if (!(val instanceof org.bukkit.World w)) yield hexFallback;
                    yield w.getName();
                }
                case "entitytype", "entity type" -> Classes.toString(val);
                case "gamemode", "difficulty", "biome", "sound",
                     "potioneffect", "potion effect",
                     "potioneffecttype", "potion effect type" -> Classes.toString(val);
                case "bound" -> {
                    try {
                        String id = (String) val.getClass().getMethod("getId").invoke(val);
                        Location lesser  = (Location) val.getClass().getMethod("getLesserCorner").invoke(val);
                        Location greater = (Location) val.getClass().getMethod("getGreaterCorner").invoke(val);
                        String worldName = lesser.getWorld() != null ? lesser.getWorld().getName() : "";
                        yield id + "," + worldName
                            + "," + lesser.getX()  + "," + lesser.getY()  + "," + lesser.getZ()
                            + "," + greater.getX() + "," + greater.getY() + "," + greater.getZ();
                    } catch (Exception e) { yield hexFallback; }
                }
                default -> hexFallback;
            };
        } catch (Exception e) {
            return hexFallback;
        }
    }

    private static String buildChunkJson(List<String[]> rows, Map<String, String> display, Map<String, String> structs, Map<String, String> items, Map<String, String> nbts) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sb.append(",");
            String[] cols = rows.get(i);
            String name = cols[0];
            String type = cols[1];
            String itemJson   = items.get(name);
            String structJson = structs.get(name);
            String nbtJson    = nbts.get(name);
            if (itemJson != null) {
                String value = display.getOrDefault(name, "");
                sb.append("{\"n\":\"").append(esc(name))
                  .append("\",\"t\":\"").append(esc(type))
                  .append("\",\"v\":\"").append(esc(value))
                  .append("\",\"s\":").append(itemJson)
                  .append("}");
            } else if (structJson != null) {
                sb.append("{\"n\":\"").append(esc(name))
                  .append("\",\"t\":\"").append(esc(type))
                  .append("\",\"v\":\"\",\"s\":").append(structJson)
                  .append("}");
            } else if (nbtJson != null) {
                sb.append("{\"n\":\"").append(esc(name))
                  .append("\",\"t\":\"").append(esc(type))
                  .append("\",\"v\":\"\",\"s\":").append(nbtJson)
                  .append("}");
            } else {
                String value = display.getOrDefault(name, cols.length > 2 ? cols[2] : "");
                sb.append("{\"n\":\"").append(esc(name))
                  .append("\",\"t\":\"").append(esc(type))
                  .append("\",\"v\":\"").append(esc(value))
                  .append("\"}");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static String itemToJson(ItemStack item) {
        if (item == null) return null;
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"material\":\"").append(esc(item.getType().getKey().toString())).append("\"");
        sb.append(",\"amount\":").append(item.getAmount());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Component displayName = meta.displayName();
            if (displayName != null) {
                String nameStr = LegacyComponentSerializer.legacySection().serialize(displayName);
                sb.append(",\"name\":\"").append(esc(nameStr)).append("\"");
            }
            List<Component> loreComponents = meta.lore();
            if (loreComponents != null && !loreComponents.isEmpty()) {
                sb.append(",\"lore\":[");
                for (int i = 0; i < loreComponents.size(); i++) {
                    if (i > 0) sb.append(",");
                    String line = LegacyComponentSerializer.legacySection().serialize(loreComponents.get(i));
                    sb.append("\"").append(esc(line)).append("\"");
                }
                sb.append("]");
            }
            if (meta.hasEnchants()) {
                sb.append(",\"enchants\":{");
                boolean first = true;
                for (Map.Entry<Enchantment, Integer> e : meta.getEnchants().entrySet()) {
                    if (!first) sb.append(",");
                    sb.append("\"").append(e.getKey().getKey().getKey()).append("\":").append(e.getValue());
                    first = false;
                }
                sb.append("}");
            }
            if (meta instanceof Damageable dmg && dmg.hasDamage()) {
                sb.append(",\"damage\":").append(dmg.getDamage());
            }
            if (meta.isUnbreakable()) {
                sb.append(",\"unbreakable\":true");
            }
            if (meta.hasCustomModelData()) {
                sb.append(",\"customModelData\":").append(meta.getCustomModelData());
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String[] splitCsv(String line) {
        Matcher m = CSV_PATTERN.matcher(line);
        int lastEnd = 0;
        List<String> parts = new ArrayList<>(3);
        while (m.find()) {
            if (lastEnd != m.start()) return null;
            parts.add(m.group(1) != null
                ? m.group(1).trim()
                : m.group(2).replace("\"\"", "\""));
            lastEnd = m.end();
        }
        return lastEnd == line.length() ? parts.toArray(new String[0]) : null;
    }
}
