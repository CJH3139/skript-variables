package com.skriptvariables.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariableApplierTest {

    private static final String DIFF = "[" +
        "{\"n\":\"kills\",\"t\":\"long\",\"v\":\"12\"}," +
        "{\"n\":\"name\",\"t\":\"string\",\"v\":\"Bob\"}," +
        "{\"n\":\"flag\",\"t\":\"boolean\",\"v\":\"true\"}," +
        "{\"n\":\"old\",\"t\":\"null\",\"v\":\"\"}," +
        "{\"n\":\"broken\",\"t\":\"long\",\"v\":\"abc\"}" +
        "]";

    private static final class FakeStore implements VariableStore {
        final Map<String, Object> values = new HashMap<>();
        final List<String> sets = new ArrayList<>();

        @Override
        public Object get(String name) {
            return values.get(name);
        }

        @Override
        public void set(String name, Object value) {
            sets.add(name + "=" + value);
        }
    }

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

    @Test
    void parseChangesReadsTheChangesWrapper() {
        List<VariableApplier.Change> changes = VariableApplier.parseChanges(
            "{\"changes\":[{\"n\":\"settings::*\",\"t\":\"null\"},{\"n\":\"bank::a\",\"t\":\"long\",\"v\":\"5\"}]}"
        );

        assertEquals(2, changes.size());
        assertEquals("settings::*", changes.get(0).name());
        assertEquals("null", changes.get(0).type());
        assertEquals("5", changes.get(1).value());
    }

    @Test
    void parseChangesKeepsEscapedQuotesAndBackslashes() {
        List<VariableApplier.Change> changes = VariableApplier.parseChanges(
            "{\"changes\":[{\"n\":\"msg::a\",\"t\":\"string\",\"v\":\"say \\\"hi\\\" \\\\ bye\"}]}"
        );

        assertEquals("say \"hi\" \\ bye", changes.get(0).value());
    }

    @Test
    void parseChangesSkipsEntriesWithoutAName() {
        List<VariableApplier.Change> changes = VariableApplier.parseChanges(
            "{\"changes\":[{\"t\":\"null\"},{\"n\":\"\",\"t\":\"null\"},{\"n\":\"a::b\",\"t\":\"null\"}]}"
        );

        assertEquals(1, changes.size());
        assertEquals("a::b", changes.get(0).name());
    }

    @Test
    void recognisesListDeletes() {
        assertTrue(VariableApplier.isListDelete(new VariableApplier.Change("settings::*", "null", "")));
        assertFalse(VariableApplier.isListDelete(new VariableApplier.Change("settings::bob", "null", "")));
        assertFalse(VariableApplier.isListDelete(new VariableApplier.Change("settings::*", "string", "x")));
    }

    @Test
    void deleteListRemovesEachDirectIndexThenTheList() {
        FakeStore store = new FakeStore();
        Map<String, Object> list = new LinkedHashMap<>();
        list.put(null, "own value");
        list.put("bob", "a");
        list.put("amy", new HashMap<>());
        store.values.put("settings::*", list);

        int removed = VariableApplier.deleteList("settings::*", store);

        assertEquals(2, removed);
        assertEquals(List.of("settings::bob=null", "settings::amy=null", "settings::*=null"), store.sets);
    }

    @Test
    void deleteListDoesNothingWhenTheListIsEmpty() {
        FakeStore store = new FakeStore();

        int removed = VariableApplier.deleteList("settings::*", store);

        assertEquals(0, removed);
        assertTrue(store.sets.isEmpty());
    }

    @Test
    void applyRoutesListAndPlainDeletes() {
        FakeStore store = new FakeStore();
        Map<String, Object> list = new LinkedHashMap<>();
        list.put("bob", "a");
        store.values.put("settings::*", list);

        VariableApplier.ApplyResult result = VariableApplier.apply(List.of(
            new VariableApplier.Change("settings::*", "null", ""),
            new VariableApplier.Change("bank::amy", "null", "")
        ), store);

        assertEquals(2, result.applied());
        assertEquals(0, result.skipped());
        assertEquals(List.of("settings::bob=null", "settings::*=null", "bank::amy=null"), store.sets);
    }
}
