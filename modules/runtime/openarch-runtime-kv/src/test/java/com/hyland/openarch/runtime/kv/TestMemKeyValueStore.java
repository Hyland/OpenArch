/*
 * (C) Copyright 2017-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.kv;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.hyland.openarch.runtime.test.runner.Deploy;

/**
 * @since 9.1
 */
public class TestMemKeyValueStore extends AbstractKeyValueStoreTest {

    @Test
    public void testClass() {
        assertTrue(store instanceof MemKeyValueStore);
    }

    @Test
    @Deploy("com.hyland.openarch.runtime.kv.tests:OSGI-INF/test-keyvalueservice-transactional.xml")
    public void testTransactionalStoreNotSupported() {
        assertSame(store, keyValueService.getKeyValueStore("default"));
        // this store implementation cannot take part in a transaction
        assertThrows(UnsupportedOperationException.class, () -> keyValueService.getKeyValueStore("transactional"));
    }

    @Override
    protected void sleepForTTLExpiration(long sleepMS) {
        try {
            Thread.sleep(sleepMS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

}
