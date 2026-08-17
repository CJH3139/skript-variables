package com.skriptvariables.util;

import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import com.sovdee.oopsk.Oopsk;
import com.sovdee.oopsk.core.Field;
import com.sovdee.oopsk.core.Struct;
import com.sovdee.oopsk.core.StructTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StructHelper {

    private static final Pattern KV_PATTERN =
        Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    public static @Nullable String tryFormatStruct(Object val) {
        if (!(val instanceof Struct struct)) return null;

        try {
            StructTemplate template = struct.getTemplate();
            Collection<Field<?>> fields = template.getFields();

            StringBuilder sb = new StringBuilder();
            sb.append("{\"t\":\"").append(esc(template.getName())).append("\",\"f\":[");

            boolean first = true;
            for (Field<?> field : fields) {
                if (!first) sb.append(",");
                first = false;

                boolean readOnly = field.dynamic()
                    || !field.single()
                    || Struct.class.isAssignableFrom(field.type().getC());

                String skriptTypeName = field.type().getCodeName();
                String displayValue = "";

                if (!readOnly) {
                    try {
                        @SuppressWarnings("unchecked")
                        Object[] values = struct.getFieldValue((Field<Object>) field);
                        if (values != null && values.length > 0 && values[0] != null) {
                            displayValue = Classes.toString(values[0]);
                        }
                    } catch (Exception ignored) {
                        readOnly = true;
                    }
                }

                sb.append("{\"n\":\"").append(esc(field.name()))
                  .append("\",\"kt\":\"").append(esc(skriptTypeName))
                  .append("\",\"v\":\"").append(esc(displayValue))
                  .append("\",\"ro\":").append(readOnly)
                  .append("}");
            }

            sb.append("]}");
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static @Nullable Object tryParseStruct(String json) {
        try {
            String templateName = extractString(json, "t");
            if (templateName == null) return null;

            StructTemplate template = Oopsk.getTemplateManager().getTemplate(templateName);
            if (template == null) return null;

            Struct struct = Struct.newInstance(template, null);
            if (struct == null) return null;

            String fSection = extractObjectSection(json, "f");
            if (fSection == null) return struct;

            Matcher m = KV_PATTERN.matcher(fSection);
            while (m.find()) {
                String fieldName  = m.group(1);
                String fieldValue = unescape(m.group(2));

                Field<?> field = template.getField(fieldName);
                if (field == null) continue;

                boolean readOnly = field.dynamic()
                    || !field.single()
                    || Struct.class.isAssignableFrom(field.type().getC());
                if (readOnly) continue;

                Object parsed = Classes.parseSimple(fieldValue, field.type().getC(), ParseContext.DEFAULT);
                if (parsed == null) continue;

                @SuppressWarnings("unchecked")
                Field<Object> typedField = (Field<Object>) field;
                struct.setSingleFieldValue(typedField, parsed);
            }

            return struct;
        } catch (Exception e) {
            return null;
        }
    }

    private static @Nullable String extractString(String json, String key) {
        Matcher m = Pattern
            .compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
            .matcher(json);
        return m.find() ? unescape(m.group(1)) : null;
    }

    private static @Nullable String extractObjectSection(String json, String key) {
        int keyIdx = json.indexOf("\"" + key + "\"");
        if (keyIdx < 0) return null;
        int braceStart = json.indexOf("{", keyIdx);
        if (braceStart < 0) return null;
        int depth = 0;
        for (int i = braceStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return json.substring(braceStart, i + 1);
        }
        return null;
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
