package com.skriptvariables.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariableApplierTest {

    private static final String DIFF = "[" +
        "{\"n\":\"kills\",\"t\":\"long\",\"v\":\"12\"}," +
        "{\"n\":\"name\",\"t\":\"string\",\"v\":\"Bob\"}," +
        "{\"n\":\"flag\",\"t\":\"boolean\",\"v\":\"true\"}," +
        "{\"n\":\"old\",\"t\":\"null\",\"v\":\"\"}," +
        "{\"n\":\"broken\",\"t\":\"long\",\"v\":\"abc\"}" +
        "]";

    @Test
    void previewSplitsSetsAndDeletes() {
        VariableApplier.PreviewResult result = VariableApplier.preview(DIFF);

        assertEquals(3, result.sets().size());
        assertEquals("kills", result.sets().get(0).name());
        assertEquals("long", result.sets().get(0).type());
        assertEquals("12", result.sets().get(0).value());
        assertEquals(1, result.deletes().size());
        assertEquals("old", result.deletes().get(0).name());
    }

    @Test
    void previewFlagsValuesThatWouldNotParse() {
        VariableApplier.PreviewResult result = VariableApplier.preview(DIFF);

        assertEquals(1, result.unparseable().size());
        assertEquals("broken", result.unparseable().get(0).name());
    }

    @Test
    void previewOfEmptyDiffIsEmpty() {
        VariableApplier.PreviewResult result = VariableApplier.preview("[]");

        assertTrue(result.sets().isEmpty());
        assertTrue(result.deletes().isEmpty());
        assertTrue(result.unparseable().isEmpty());
    }
}
