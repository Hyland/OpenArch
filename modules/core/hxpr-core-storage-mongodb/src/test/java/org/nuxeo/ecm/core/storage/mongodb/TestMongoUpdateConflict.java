/*
 * (C) Copyright 2016-2026 Hyland (https://www.hyland.com/).
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
import static org.nuxeo.ecm.core.storage.mongodb.MongoDBRepository.MONGODB_SET;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.storage.mongodb.MongoDBConverter.UpdateBuilder;

public class TestMongoUpdateConflict {

    protected UpdateBuilder newUpdateBuilder() {
        UpdateBuilder ub = new MongoDBConverter().new UpdateBuilder();
        ub.newUpdate();
        return ub;
    }

    @Test
    public void testEmpty() throws Exception {
        UpdateBuilder ulb = newUpdateBuilder();
        assertEquals(1, ulb.conditionsAndUpdates.updates.size());
    }

    @Test
    public void testComplexPropNoConflict() throws Exception {
        UpdateBuilder ub = newUpdateBuilder();
        ub.update(MONGODB_SET, "foo.0.bar", "val");
        ub.update(MONGODB_SET, "foo.0.zoo", "val");
        assertEquals(1, ub.conditionsAndUpdates.updates.size());
    }

    @Test
    public void testComplexPropNoConflict2() throws Exception {
        UpdateBuilder ub = newUpdateBuilder();
        ub.update(MONGODB_SET, "foo.0.bar.zoo.x", "val");
        ub.update(MONGODB_SET, "foo.0.bar.zoo.y", "val");
        assertEquals(1, ub.conditionsAndUpdates.updates.size());
    }

    @Test
    public void testComplexPropNoConflict3() throws Exception {
        UpdateBuilder ub = newUpdateBuilder();
        ub.update(MONGODB_SET, "foo.0.bar.zoo.x", "val");
        ub.update(MONGODB_SET, "foo.0.bar.z", "val");
        assertEquals(1, ub.conditionsAndUpdates.updates.size());
    }

    @Test
    public void testComplexPropNoConflict4() throws Exception {
        UpdateBuilder ub = newUpdateBuilder();
        ub.update(MONGODB_SET, "foo.0.bar.zoo", "val");
        ub.update(MONGODB_SET, "foo.00.bar", "val");
        ub.update(MONGODB_SET, "foobar", "val");
        ub.update(MONGODB_SET, "foobar1.0.bar.zoo", "val");
        ub.update(MONGODB_SET, "foo.1.0.bar.zoo", "val");
        ub.update(MONGODB_SET, "foo.bar", "val");
        ub.update(MONGODB_SET, "bar.foo", "val");
        ub.update(MONGODB_SET, "zz", "val");
        assertEquals(1, ub.conditionsAndUpdates.updates.size());
    }

    @Test
    public void testConflictEqual() throws Exception {
        UpdateBuilder ub = newUpdateBuilder();
        ub.update(MONGODB_SET, "foo.0.bar", "val");
        ub.update(MONGODB_SET, "foo.0.bar", "val2");
        assertEquals(2, ub.conditionsAndUpdates.updates.size());
    }

    @Test
    public void testConflictEqual2() throws Exception {
        UpdateBuilder ub = newUpdateBuilder();
        ub.update(MONGODB_SET, "foo", "val");
        ub.update(MONGODB_SET, "foo", "val2");
        assertEquals(2, ub.conditionsAndUpdates.updates.size());
    }

    @Test
    public void testConflict1() throws Exception {
        UpdateBuilder ub = newUpdateBuilder();
        ub.update(MONGODB_SET, "foo.0.bar", "val");
        ub.update(MONGODB_SET, "foo.0.bar.zoo", "val");
        assertEquals(2, ub.conditionsAndUpdates.updates.size());
    }

    @Test
    public void testConflict2() throws Exception {
        UpdateBuilder ub = newUpdateBuilder();
        ub.update(MONGODB_SET, "foo.0.bar.zoo", "val");
        ub.update(MONGODB_SET, "foo.0.bar", "val");
        assertEquals(2, ub.conditionsAndUpdates.updates.size());
    }

    @Test
    public void testConflict3() throws Exception {
        UpdateBuilder ub = newUpdateBuilder();
        ub.update(MONGODB_SET, "foo.0.bar.zoo", "val");
        ub.update(MONGODB_SET, "foo", "val");
        assertEquals(2, ub.conditionsAndUpdates.updates.size());
    }

    @Test
    public void testBigComplex() throws Exception {
        UpdateBuilder ub = newUpdateBuilder();
        for (int i = 0; i < 10000; i++) {
            ub.update(MONGODB_SET, "foo." + i + ".bar.zoo", "val");
        }
        assertEquals(1, ub.conditionsAndUpdates.updates.size());
    }


}
