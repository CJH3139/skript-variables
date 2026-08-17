package com.skriptvariables.util;

import com.google.gson.*;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.*;

public final class NbtHelper {

    private static final String[] PKGS = {
        "de.tr7zw.changeme.nbtapi.",
        "de.tr7zw.nbtapi."
    };

    private static Class<?> compoundCls  = null;
    private static Class<?> containerCls = null;

    static {
        for (String p : PKGS) {
            try {
                compoundCls  = Class.forName(p + "NBTCompound");
                containerCls = Class.forName(p + "NBTContainer");
                break;
            } catch (ClassNotFoundException ignored) {}
        }
    }

    public static boolean isPresent() { return compoundCls != null; }

    public static boolean isNbtCompound(Object val) {
        return compoundCls != null && compoundCls.isInstance(val);
    }

    public static @Nullable String toJson(Object compound) {
        if (!isNbtCompound(compound)) return null;
        try {
            return new Gson().toJson(serializeCompound(compound));
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static JsonObject serializeCompound(Object compound) throws Exception {
        JsonObject obj = new JsonObject();
        Set<String> keys = (Set<String>) compound.getClass().getMethod("getKeys").invoke(compound);
        for (String key : keys) {
            try {
                JsonObject entry = buildEntry(compound, key);
                if (entry != null) obj.add(key, entry);
            } catch (Exception ignored) {}
        }
        return obj;
    }

    private static @Nullable JsonObject buildEntry(Object compound, String key) throws Exception {
        Object nbtType = compound.getClass().getMethod("getType", String.class).invoke(compound, key);
        int id = getNbtTypeId(nbtType);

        JsonObject entry = new JsonObject();
        switch (id) {
            case 1 -> {
                entry.addProperty("t", "byte");
                entry.addProperty("v", (Byte) compound.getClass().getMethod("getByte", String.class).invoke(compound, key));
            }
            case 2 -> {
                entry.addProperty("t", "short");
                entry.addProperty("v", (Short) compound.getClass().getMethod("getShort", String.class).invoke(compound, key));
            }
            case 3 -> {
                entry.addProperty("t", "int");
                entry.addProperty("v", (Integer) compound.getClass().getMethod("getInteger", String.class).invoke(compound, key));
            }
            case 4 -> {
                entry.addProperty("t", "long");
                entry.addProperty("v", (Long) compound.getClass().getMethod("getLong", String.class).invoke(compound, key));
            }
            case 5 -> {
                entry.addProperty("t", "float");
                entry.addProperty("v", (Float) compound.getClass().getMethod("getFloat", String.class).invoke(compound, key));
            }
            case 6 -> {
                entry.addProperty("t", "double");
                entry.addProperty("v", (Double) compound.getClass().getMethod("getDouble", String.class).invoke(compound, key));
            }
            case 7 -> {
                entry.addProperty("t", "byte_array");
                byte[] arr = (byte[]) compound.getClass().getMethod("getByteArray", String.class).invoke(compound, key);
                JsonArray ja = new JsonArray();
                for (byte b : arr) ja.add(b);
                entry.add("v", ja);
            }
            case 8 -> {
                entry.addProperty("t", "string");
                entry.addProperty("v", (String) compound.getClass().getMethod("getString", String.class).invoke(compound, key));
            }
            case 9 -> {
                Object listType = compound.getClass().getMethod("getListType", String.class).invoke(compound, key);
                return buildListEntry(compound, key, getNbtTypeId(listType));
            }
            case 10 -> {
                entry.addProperty("t", "compound");
                Object nested = compound.getClass().getMethod("getCompound", String.class).invoke(compound, key);
                entry.add("v", serializeCompound(nested));
            }
            case 11 -> {
                entry.addProperty("t", "int_array");
                int[] arr = (int[]) compound.getClass().getMethod("getIntArray", String.class).invoke(compound, key);
                JsonArray ja = new JsonArray();
                for (int i : arr) ja.add(i);
                entry.add("v", ja);
            }
            case 12 -> {
                entry.addProperty("t", "long_array");
                long[] arr = (long[]) compound.getClass().getMethod("getLongArray", String.class).invoke(compound, key);
                JsonArray ja = new JsonArray();
                for (long l : arr) ja.add(l);
                entry.add("v", ja);
            }
            default -> { return null; }
        }
        return entry;
    }

    @SuppressWarnings("unchecked")
    private static @Nullable JsonObject buildListEntry(Object compound, String key, int elemId) throws Exception {
        JsonObject entry = new JsonObject();
        JsonArray arr = new JsonArray();
        switch (elemId) {
            case 8 -> {
                entry.addProperty("t", "list:string");
                for (String s : (Iterable<String>) compound.getClass().getMethod("getStringList", String.class).invoke(compound, key))
                    arr.add(s);
            }
            case 3 -> {
                entry.addProperty("t", "list:int");
                for (Integer i : (Iterable<Integer>) compound.getClass().getMethod("getIntegerList", String.class).invoke(compound, key))
                    arr.add(i);
            }
            case 4 -> {
                entry.addProperty("t", "list:long");
                for (Long l : (Iterable<Long>) compound.getClass().getMethod("getLongList", String.class).invoke(compound, key))
                    arr.add(l);
            }
            case 5 -> {
                entry.addProperty("t", "list:float");
                for (Float f : (Iterable<Float>) compound.getClass().getMethod("getFloatList", String.class).invoke(compound, key))
                    arr.add(f);
            }
            case 6 -> {
                entry.addProperty("t", "list:double");
                for (Double d : (Iterable<Double>) compound.getClass().getMethod("getDoubleList", String.class).invoke(compound, key))
                    arr.add(d);
            }
            case 10 -> {
                entry.addProperty("t", "list:compound");
                for (Object c : (Iterable<Object>) compound.getClass().getMethod("getCompoundList", String.class).invoke(compound, key))
                    arr.add(serializeCompound(c));
            }
            default -> { return null; }
        }
        entry.add("v", arr);
        return entry;
    }

    public static @Nullable Object fromJson(String json) {
        if (containerCls == null || json == null || json.isBlank()) return null;
        try {
            Object container = containerCls.getDeclaredConstructor().newInstance();
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            applyToCompound(obj, container);
            return container;
        } catch (Exception e) {
            return null;
        }
    }

    private static void applyToCompound(JsonObject obj, Object compound) {
        for (Map.Entry<String, JsonElement> kv : obj.entrySet()) {
            try {
                if (kv.getValue().isJsonObject())
                    applyEntry(compound, kv.getKey(), kv.getValue().getAsJsonObject());
            } catch (Exception ignored) {}
        }
    }

    private static void applyEntry(Object compound, String key, JsonObject entry) throws Exception {
        String type = entry.get("t").getAsString();
        JsonElement val = entry.get("v");
        Class<?> cls = compound.getClass();
        switch (type) {
            case "string"     -> cls.getMethod("setString",  String.class, String.class).invoke(compound, key, val.getAsString());
            case "int"        -> cls.getMethod("setInteger", String.class, Integer.class).invoke(compound, key, val.getAsInt());
            case "byte"       -> cls.getMethod("setByte",    String.class, Byte.class).invoke(compound, key, val.getAsByte());
            case "short"      -> cls.getMethod("setShort",   String.class, Short.class).invoke(compound, key, val.getAsShort());
            case "long"       -> cls.getMethod("setLong",    String.class, Long.class).invoke(compound, key, val.getAsLong());
            case "float"      -> cls.getMethod("setFloat",   String.class, Float.class).invoke(compound, key, val.getAsFloat());
            case "double"     -> cls.getMethod("setDouble",  String.class, Double.class).invoke(compound, key, val.getAsDouble());
            case "byte_array" -> {
                JsonArray ja = val.getAsJsonArray();
                byte[] arr = new byte[ja.size()];
                for (int i = 0; i < ja.size(); i++) arr[i] = ja.get(i).getAsByte();
                cls.getMethod("setByteArray", String.class, byte[].class).invoke(compound, key, arr);
            }
            case "int_array" -> {
                JsonArray ja = val.getAsJsonArray();
                int[] arr = new int[ja.size()];
                for (int i = 0; i < ja.size(); i++) arr[i] = ja.get(i).getAsInt();
                cls.getMethod("setIntArray", String.class, int[].class).invoke(compound, key, arr);
            }
            case "long_array" -> {
                JsonArray ja = val.getAsJsonArray();
                long[] arr = new long[ja.size()];
                for (int i = 0; i < ja.size(); i++) arr[i] = ja.get(i).getAsLong();
                cls.getMethod("setLongArray", String.class, long[].class).invoke(compound, key, arr);
            }
            case "compound" -> {
                Object nested = getOrCreateCompound(cls, compound, key);
                applyToCompound(val.getAsJsonObject(), nested);
            }
            case "list:string" -> {
                Object list = cls.getMethod("getStringList", String.class).invoke(compound, key);
                Method add = list.getClass().getMethod("add", Object.class);
                for (JsonElement el : val.getAsJsonArray()) add.invoke(list, el.getAsString());
            }
            case "list:int" -> {
                Object list = cls.getMethod("getIntegerList", String.class).invoke(compound, key);
                Method add = list.getClass().getMethod("add", Object.class);
                for (JsonElement el : val.getAsJsonArray()) add.invoke(list, el.getAsInt());
            }
            case "list:long" -> {
                Object list = cls.getMethod("getLongList", String.class).invoke(compound, key);
                Method add = list.getClass().getMethod("add", Object.class);
                for (JsonElement el : val.getAsJsonArray()) add.invoke(list, el.getAsLong());
            }
            case "list:float" -> {
                Object list = cls.getMethod("getFloatList", String.class).invoke(compound, key);
                Method add = list.getClass().getMethod("add", Object.class);
                for (JsonElement el : val.getAsJsonArray()) add.invoke(list, el.getAsFloat());
            }
            case "list:double" -> {
                Object list = cls.getMethod("getDoubleList", String.class).invoke(compound, key);
                Method add = list.getClass().getMethod("add", Object.class);
                for (JsonElement el : val.getAsJsonArray()) add.invoke(list, el.getAsDouble());
            }
            case "list:compound" -> {
                Object list = cls.getMethod("getCompoundList", String.class).invoke(compound, key);
                Method addC = list.getClass().getMethod("addCompound");
                for (JsonElement el : val.getAsJsonArray()) {
                    Object nested = addC.invoke(list);
                    applyToCompound(el.getAsJsonObject(), nested);
                }
            }
        }
    }

    private static Object getOrCreateCompound(Class<?> cls, Object compound, String key) throws Exception {
        try {
            return cls.getMethod("getOrCreateCompound", String.class).invoke(compound, key);
        } catch (NoSuchMethodException e) {
            return cls.getMethod("getCompound", String.class).invoke(compound, key);
        }
    }

    private static int getNbtTypeId(Object nbtType) {
        try {
            return (int) nbtType.getClass().getMethod("getId").invoke(nbtType);
        } catch (Exception e) {
            return ((Enum<?>) nbtType).ordinal();
        }
    }
}
