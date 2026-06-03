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

package org.nuxeo.common.xmap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class TestContext {

    @Test
    public void testEqualsAndHashCode() {
        Context context1 = new Context();
        context1.setProperty("k1", 1);
        context1.setProperty("k2", 2);
        assertNotNull(context1);
        Context context2 = new Context();
        context2.setProperty("k1", 1);
        context2.setProperty("k2", 2);
        assertEquals(context1, context2);

        assertEquals(6732, context1.hashCode());
    }

}
