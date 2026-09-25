/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hyland.openarch.ecm.core.storage.mongodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MongoDBRepository#migrateKgConfig(Map)} — covers the one-time
 * legacy {@code domains[]} → {@code graphDBs[]} rewrite.
 */
public class TestKgConfigMigration {

    @Test
    public void migratesLegacyDomainsToGraphDBs() {
        Map<String, Object> domain1 = new LinkedHashMap<>();
        domain1.put("active_ontology", "");
        domain1.put("domain", "contractsTesting");
        domain1.put("domainId", "b8a130f4-8896-4fae-81c4-1344fd1ee023");
        domain1.put("connectionConfig",
                "{\"namespaceId\":\"10004\",\"namespaceUserId\":\"fc006a81-bb4b-493f-b888-58cb16fd78d0\"}");

        Map<String, Object> domain2 = new LinkedHashMap<>();
        domain2.put("namespaceId", "10006");
        domain2.put("active_ontology", "");
        domain2.put("domain", "testDomain");
        domain2.put("namespaceUserId", "cc58c252-e87d-45a2-adc9-fa5c6fab1193");
        domain2.put("domainId", "bbadb6e2-1e53-4961-88f6-2d88162b63c1");

        Map<String, Object> config = new HashMap<>();
        config.put("domains", new ArrayList<>(List.of(domain1, domain2)));

        boolean migrated = MongoDBRepository.migrateKgConfig(config);

        assertTrue(migrated);
        assertFalse(config.containsKey("domains"), "legacy 'domains' field should be removed");
        assertTrue(config.containsKey("ontologies"), "ontologies array must be present (empty)");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> graphDBs = (List<Map<String, Object>>) config.get("graphDBs");
        assertEquals(2, graphDBs.size());

        Map<String, Object> entry1 = graphDBs.get(0);
        assertEquals("b8a130f4-8896-4fae-81c4-1344fd1ee023", entry1.get("graphDBId"));
        assertEquals("contractsTesting", entry1.get("graphDBName"));
        assertEquals("v1", entry1.get("version"), "version must default to v1 when absent on legacy entry");
        assertEquals("{\"namespaceId\":\"10004\",\"namespaceUserId\":\"fc006a81-bb4b-493f-b888-58cb16fd78d0\"}",
                entry1.get("connectionConfig"));
        assertNull(entry1.get("ontologyRoutes"));
        // legacy stray fields must not leak into the new entry
        assertFalse(entry1.containsKey("active_ontology"));
        assertFalse(entry1.containsKey("domainId"));
        assertFalse(entry1.containsKey("domain"));

        Map<String, Object> entry2 = graphDBs.get(1);
        assertEquals("bbadb6e2-1e53-4961-88f6-2d88162b63c1", entry2.get("graphDBId"));
        assertEquals("testDomain", entry2.get("graphDBName"));
        assertFalse(entry2.containsKey("namespaceId"));
        assertFalse(entry2.containsKey("namespaceUserId"));
    }

    @Test
    public void preservesExistingVersionWhenPresent() {
        Map<String, Object> domain = new HashMap<>();
        domain.put("domainId", "id-1");
        domain.put("domain", "name-1");
        domain.put("version", "v2");

        Map<String, Object> config = new HashMap<>();
        config.put("domains", new ArrayList<>(List.of(domain)));

        MongoDBRepository.migrateKgConfig(config);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> graphDBs = (List<Map<String, Object>>) config.get("graphDBs");
        assertEquals("v2", graphDBs.get(0).get("version"));
    }

    @Test
    public void isIdempotentOnAlreadyMigratedDocument() {
        Map<String, Object> graphDB = new HashMap<>();
        graphDB.put("graphDBId", "id");
        graphDB.put("graphDBName", "name");
        graphDB.put("version", "v2");

        Map<String, Object> config = new HashMap<>();
        config.put("graphDBs", new ArrayList<>(List.of(graphDB)));
        config.put("ontologies", new ArrayList<>());

        boolean migrated = MongoDBRepository.migrateKgConfig(config);

        assertFalse(migrated, "already-migrated doc must not be reported as changed");
        assertFalse(config.containsKey("domains"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> graphDBs = (List<Map<String, Object>>) config.get("graphDBs");
        assertEquals(1, graphDBs.size());
        assertEquals("id", graphDBs.get(0).get("graphDBId"));
    }

    @Test
    public void emptyDomainsListSeedsTargetShape() {
        Map<String, Object> config = new HashMap<>();
        config.put("domains", new ArrayList<>());

        boolean migrated = MongoDBRepository.migrateKgConfig(config);

        assertTrue(migrated);
        assertFalse(config.containsKey("domains"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> graphDBs = (List<Map<String, Object>>) config.get("graphDBs");
        assertTrue(graphDBs.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ontologies = (List<Map<String, Object>>) config.get("ontologies");
        assertTrue(ontologies.isEmpty());
    }

    @Test
    public void absentDomainsKeySeedsTargetShape() {
        Map<String, Object> config = new HashMap<>();   // no "domains", no "graphDBs"

        boolean migrated = MongoDBRepository.migrateKgConfig(config);

        assertTrue(migrated);
        assertFalse(config.containsKey("domains"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> graphDBs = (List<Map<String, Object>>) config.get("graphDBs");
        assertTrue(graphDBs.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ontologies = (List<Map<String, Object>>) config.get("ontologies");
        assertTrue(ontologies.isEmpty());
    }

    @Test
    public void seedsMissingOntologiesOnAlreadyMigratedDoc() {
        Map<String, Object> graphDB = new HashMap<>();
        graphDB.put("graphDBId", "id");
        graphDB.put("graphDBName", "name");
        graphDB.put("version", "v2");

        Map<String, Object> config = new HashMap<>();
        config.put("graphDBs", new ArrayList<>(List.of(graphDB)));
        // intentionally no "ontologies" — the new graph service expects it to exist

        boolean migrated = MongoDBRepository.migrateKgConfig(config);

        assertTrue(migrated, "missing ontologies[] must be seeded and reported as changed");
        assertTrue(config.containsKey("ontologies"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ontologies = (List<Map<String, Object>>) config.get("ontologies");
        assertTrue(ontologies.isEmpty());
    }

    @Test
    public void refusesMalformedDomainsValue() {
        Map<String, Object> config = new HashMap<>();
        config.put("domains", "this-should-be-a-list-but-isnt");

        boolean migrated = MongoDBRepository.migrateKgConfig(config);

        assertFalse(migrated, "non-List 'domains' must not be silently overwritten");
        // Original (corrupt) value must remain so manual cleanup is possible.
        assertEquals("this-should-be-a-list-but-isnt", config.get("domains"));
        assertFalse(config.containsKey("graphDBs"));
        assertFalse(config.containsKey("ontologies"));
    }

    @Test
    public void treatsNullDomainsAsAbsent() {
        Map<String, Object> config = new HashMap<>();
        config.put("domains", null);   // value is explicitly null

        boolean migrated = MongoDBRepository.migrateKgConfig(config);

        assertTrue(migrated);
        // We treat null-valued legacy 'domains' as effectively absent: the migration runs the
        // empty-domains path, which removes the stray key and seeds the target shape.
        assertFalse(config.containsKey("domains"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> graphDBs = (List<Map<String, Object>>) config.get("graphDBs");
        assertTrue(graphDBs.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ontologies = (List<Map<String, Object>>) config.get("ontologies");
        assertTrue(ontologies.isEmpty());
    }

    @Test
    public void replacesNullOrNonListGraphDBsWithEmptyList() {
        Map<String, Object> config = new HashMap<>();
        config.put("graphDBs", null);
        config.put("ontologies", "not-a-list");

        boolean migrated = MongoDBRepository.migrateKgConfig(config);

        assertTrue(migrated);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> graphDBs = (List<Map<String, Object>>) config.get("graphDBs");
        assertTrue(graphDBs.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ontologies = (List<Map<String, Object>>) config.get("ontologies");
        assertTrue(ontologies.isEmpty());
    }

    @Test
    public void skipsCorruptNonMapDomainEntries() {
        Map<String, Object> goodDomain = new HashMap<>();
        goodDomain.put("domainId", "good-id");
        goodDomain.put("domain", "good-name");
        goodDomain.put("version", "v2");

        Map<String, Object> config = new HashMap<>();
        // List with a corrupt element (a String) interleaved with a valid one — must not
        // throw ClassCastException out of readConfiguration().
        List<Object> domains = new ArrayList<>();
        domains.add("not-a-map");
        domains.add(goodDomain);
        domains.add(42);
        config.put("domains", domains);

        boolean migrated = MongoDBRepository.migrateKgConfig(config);

        assertTrue(migrated);
        assertFalse(config.containsKey("domains"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> graphDBs = (List<Map<String, Object>>) config.get("graphDBs");
        assertEquals(1, graphDBs.size(), "only the valid Map entry should be migrated");
        assertEquals("good-id", graphDBs.get(0).get("graphDBId"));
        assertEquals("good-name", graphDBs.get(0).get("graphDBName"));
    }

    @Test
    public void refusesPartiallyMigratedDocWithBothKeys() {
        Map<String, Object> domain = new HashMap<>();
        domain.put("domainId", "legacy-id");
        domain.put("domain", "legacy-name");

        Map<String, Object> graphDB = new HashMap<>();
        graphDB.put("graphDBId", "new-id");
        graphDB.put("graphDBName", "new-name");

        Map<String, Object> config = new HashMap<>();
        config.put("domains", new ArrayList<>(List.of(domain)));
        config.put("graphDBs", new ArrayList<>(List.of(graphDB)));

        boolean migrated = MongoDBRepository.migrateKgConfig(config);

        assertFalse(migrated, "doc with both keys must not be touched");
        // Both keys must remain exactly as they were — corruption stays visible.
        assertTrue(config.containsKey("domains"));
        assertTrue(config.containsKey("graphDBs"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> domains = (List<Map<String, Object>>) config.get("domains");
        assertEquals(1, domains.size());
        assertEquals("legacy-id", domains.get(0).get("domainId"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> graphDBs = (List<Map<String, Object>>) config.get("graphDBs");
        assertEquals(1, graphDBs.size());
        assertEquals("new-id", graphDBs.get(0).get("graphDBId"));
    }
}
