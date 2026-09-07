package com.skriptvariables.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
