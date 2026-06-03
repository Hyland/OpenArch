/*
 * (C) Copyright 2022 Hyland SA (http://hyland.com/) and others.
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
 *
 * Contributors:
 *     Mikolaj Garbula
 */
package org.nuxeo.ecm.core.api;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestPartialList {

    @Test
    public void testEqualsAndHash() {
        List<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        list.add("c");
        PartialList<String> partialList1 = new PartialList<>(list, list.size());
        assertNotNull(partialList1);
        assertEquals(new PartialList<>(list, 3), partialList1);
        PartialList<String> partialList2 = new PartialList<>(list, list.size());
        assertEquals(partialList1, partialList2);

        assertEquals(2144757, partialList1.hashCode());
    }

}
