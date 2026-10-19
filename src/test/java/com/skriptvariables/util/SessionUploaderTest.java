package com.skriptvariables.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionUploaderTest {

    @Test
    void readCsvReturnsEmptyMapWhenFileIsNull() throws Exception {
        LinkedHashMap<String, String[]> rows = SessionUploader.readCsv(null);
        assertTrue(rows.isEmpty());
    }

    @Test
    void readCsvReturnsEmptyMapWhenFileIsMissing(@TempDir Path dir) throws Exception {
        LinkedHashMap<String, String[]> rows = SessionUploader.readCsv(dir.resolve("nope.csv").toFile());
        assertTrue(rows.isEmpty());
    }

    @Test
    void readCsvParsesRowsAndDropsNullTyped(@TempDir Path dir) throws Exception {
        File csv = dir.resolve("variables.csv").toFile();
        Files.writeString(csv.toPath(), String.join("\n",
            "# comment",
            "\"kills\", \"long\", \"0a\"",
            "\"gone\", \"string\", \"aa\"",
            "\"gone\", \"null\", \"\"",
            ""), StandardCharsets.UTF_8);

        LinkedHashMap<String, String[]> rows = SessionUploader.readCsv(csv);

        assertEquals(1, rows.size());
        assertArrayEquals(new String[]{"kills", "long", "0a"}, rows.get("kills"));
        assertFalse(rows.containsKey("gone"));
    }

    @Test
    void rowForUsesSerializedTypeAndHexWhenSerializerSucceeds() {
        String[] row = SessionUploader.rowFor("kills", 5L,
            (n, v) -> new SessionUploader.Serialized("long", new byte[]{0x0a}),
            v -> "long");

        assertArrayEquals(new String[]{"kills", "long", "0a"}, row);
    }

    @Test
    void rowForFallsBackToTypeNameWhenSerializerThrows() {
        String[] row = SessionUploader.rowFor("big", "stack",
            (n, v) -> { throw new IllegalStateException("Value must be within range [1;99]: 512"); },
            v -> "itemstack");

        assertArrayEquals(new String[]{"big", "itemstack", ""}, row);
    }

    @Test
    void rowForReturnsNullWhenSerializerReturnsNull() {
        String[] row = SessionUploader.rowFor("weird", new Object(), (n, v) -> null, v -> "object");

        assertNull(row);
    }

    @Test
    void stackAmountsOutsideOneToNinetyNineCannotBeSerialized() {
        assertTrue(SessionUploader.exceedsSerializableStack(100));
        assertTrue(SessionUploader.exceedsSerializableStack(512));
        assertTrue(SessionUploader.exceedsSerializableStack(0));
        assertFalse(SessionUploader.exceedsSerializableStack(1));
        assertFalse(SessionUploader.exceedsSerializableStack(64));
        assertFalse(SessionUploader.exceedsSerializableStack(99));
    }

    @Test
    void escEscapesControlCharacters() {
        assertEquals("a\\nb\\tc\\rd", SessionUploader.esc("a\nb\tc\rd"));
        assertEquals("\\u0001", SessionUploader.esc(""));
        assertEquals("q\\\"b\\\\", SessionUploader.esc("q\"b\\"));
    }

    @Test
    void chunkJsonHasNoRawControlCharactersAndRoundTrips() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"motd", "textcomponent", ""});
        Map<String, String> display = Map.of("motd", "<gray>line one\nline two\ttabbed");
        Map<String, String> none = Map.of();

        String json = SessionUploader.buildChunkJson(rows, display, none, none, none);

        assertFalse(json.contains("\n"), json);
        assertFalse(json.contains("\t"), json);
        var arr = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
        assertEquals("<gray>line one\nline two\ttabbed", arr.get(0).getAsJsonObject().get("v").getAsString());
    }

    @Test
    void forEachSkippingFailuresProcessesTheRestAndReportsSkipped() {
        List<String> seen = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        int count = SessionUploader.forEachSkippingFailures(List.of("a", "boom", "c"), item -> {
            if (item.equals("boom")) throw new IllegalStateException("bad");
            seen.add(item);
        }, item -> item, skipped);

        assertEquals(List.of("a", "c"), seen);
        assertEquals(List.of("boom"), skipped);
        assertEquals(1, count);
    }

    @Test
    void forEachSkippingFailuresAlsoSurvivesLinkageErrors() {
        List<String> seen = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        SessionUploader.forEachSkippingFailures(List.of("x", "y"), item -> {
            if (item.equals("x")) throw new NoSuchMethodError("removed api");
            seen.add(item);
        }, item -> item, skipped);

        assertEquals(List.of("y"), seen);
        assertEquals(List.of("x"), skipped);
    }
}
