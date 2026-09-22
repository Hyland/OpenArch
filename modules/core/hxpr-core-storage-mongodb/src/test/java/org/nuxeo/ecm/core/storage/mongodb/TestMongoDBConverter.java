/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.storage.mongodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.nuxeo.ecm.core.storage.mongodb.MongoDBConverter.DEFAULT_CONVERTER;
import static com.hyland.openarch.runtime.mongodb.MongoDBOperators.AND;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.storage.State;

public class TestMongoDBConverter {

    private Document doc() {
        return new Document();
    }

    private Document doc(String a, Object b) {
        return new Document(Map.of(a, b));
    }

    @Test
    public void testAddToFilter() {
        MongoDBConverter conv = DEFAULT_CONVERTER;
        Document filter;

        // add to empty filter
        filter = doc();
        conv.addToFilter(filter, doc("A", "B"));
        assertEquals(Map.of("A", "B"), filter);

        // no collision
        filter = doc("A", "B");
        conv.addToFilter(filter, doc("C", "D"));
        assertEquals(Map.of("A", "B", "C", "D"), filter);

        // collision of keys -> $and
        filter = doc("A", "B");
        conv.addToFilter(filter, doc("A", "C"));
        assertEquals(Map.of(AND, List.of(doc("A", "B"), doc("A", "C"))), filter);

        // add to existing $and
        filter = doc(AND, List.of(doc("A", "B")));
        conv.addToFilter(filter, doc("C", "D"));
        assertEquals(Map.of(AND, List.of(doc("A", "B")), "C", "D"), filter);

        // add $and to existing $and
        filter = doc(AND, List.of(doc("A", "B")));
        conv.addToFilter(filter, doc(AND, List.of(doc("C", "D"))));
        assertEquals(Map.of(AND, List.of(doc("A", "B"), doc("C", "D"))), filter);
    }

    @Test
    public void testBsonToState() {
        MongoDBConverter conv = DEFAULT_CONVERTER;

        Document doc = new Document();
        doc.put("string", "value");
        doc.put("int", 42);
        doc.put("long", 42L);
        Date date = new Date();
        doc.put("date", date);
        Decimal128 decimal = new Decimal128(42L);
        doc.put("decimal", decimal);

        State state = conv.bsonToState(doc);
        assertEquals("value", state.get("string"));
        assertEquals(42L, state.get("int"));
        assertEquals(42L, state.get("long"));
        assertEquals(date.toInstant(), state.get("date"));
        assertEquals(decimal.bigDecimalValue(), state.get("decimal"));
    }

}
