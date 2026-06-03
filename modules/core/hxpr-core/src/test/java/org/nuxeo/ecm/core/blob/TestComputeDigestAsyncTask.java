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

package org.nuxeo.ecm.core.blob;

import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.async.tasks.ComputeDigestTask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
public class TestComputeDigestAsyncTask {

    @Test
    public void testEqualsAndHashCode() {
        ComputeDigestTask task = new ComputeDigestTask("id", "key");
        assertNotNull(task);
        assertEquals(new ComputeDigestTask("id", "key"), task);

        assertEquals(163403, task.hashCode());
        assertNull(task.getTimestamp());
    }

}
