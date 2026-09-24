/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.transientstore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import com.hyland.openarch.async.tasks.message.RepositoryTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import com.hyland.openarch.ecm.core.blob.GCAlreadyStartedException;
import com.hyland.openarch.ecm.core.transientstore.api.TransientStoreProvider;

/**
 * Unit tests for TransientStorageComponent business logic that prevents concurrent GC execution.
 * Tests focus on our application behavior, not Java library functionality.
 */
public class TestTransientStorageGCTaskProcessor {

    @Mock
    private TransientStoreProvider mockStoreProvider1;

    @Mock
    private TransientStoreProvider mockStoreProvider2;

    private TransientStorageComponent component;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        component = new TransientStorageComponent();

        // Inject mock store providers using reflection
        Field storesField = TransientStorageComponent.class.getDeclaredField("stores");
        storesField.setAccessible(true);
        ConcurrentHashMap<String, TransientStoreProvider> stores = new ConcurrentHashMap<>();
        stores.put("store1", mockStoreProvider1);
        stores.put("store2", mockStoreProvider2);
        storesField.set(component, stores);
    }

    @Test
    void testNormalGCExecution() throws Exception {
        // Test normal GC execution
        component.doGC();

        // Verify both providers were called
        verify(mockStoreProvider1, times(1)).doGC();
        verify(mockStoreProvider2, times(1)).doGC();
    }

    @Test
    void testGCAlreadyStartedExceptionHandling() throws Exception {
        // Mock first provider to throw GCAlreadyStartedException
        doThrow(new GCAlreadyStartedException()).when(mockStoreProvider1).doGC();

        // Execute GC
        component.doGC();

        // Verify first provider was called (and threw exception)
        verify(mockStoreProvider1, times(1)).doGC();
        // Verify second provider was still called despite first one's exception
        verify(mockStoreProvider2, times(1)).doGC();
    }

    @Test
    void testMixedExceptionHandling() throws Exception {
        // Mock first provider to throw GCAlreadyStartedException
        doThrow(new GCAlreadyStartedException()).when(mockStoreProvider1).doGC();
        // Mock second provider to throw regular exception
        doThrow(new RuntimeException("Other error")).when(mockStoreProvider2).doGC();

        // Execute GC - should not fail despite exceptions
        component.doGC();

        // Verify both providers were called
        verify(mockStoreProvider1, times(1)).doGC();
        verify(mockStoreProvider2, times(1)).doGC();
    }

    @Test
    void testMultipleGCCallsWithIntermittentConflicts() throws Exception {
        // Mock provider1 to throw GCAlreadyStartedException on every other call
        doThrow(new GCAlreadyStartedException()).doNothing().doThrow(new GCAlreadyStartedException())
                .when(mockStoreProvider1).doGC();

        // Execute GC multiple times
        component.doGC(); // provider1 throws, provider2 succeeds
        component.doGC(); // both succeed
        component.doGC(); // provider1 throws, provider2 succeeds

        // Verify all calls were made
        verify(mockStoreProvider1, times(3)).doGC();
        verify(mockStoreProvider2, times(3)).doGC();
    }

    @Test
    void testTaskProcessorBasicFunctionality() {
        // Test the task processor methods
        TransientStorageGCTaskProcessor processor = new TransientStorageGCTaskProcessor();

        assertEquals("transientStorageGC", processor.getName());
        assertEquals(RepositoryTask.class, processor.getTaskClass());
    }
}
