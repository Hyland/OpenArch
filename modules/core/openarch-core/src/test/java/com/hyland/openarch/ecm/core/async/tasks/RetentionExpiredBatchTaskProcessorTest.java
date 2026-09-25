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
package com.hyland.openarch.ecm.core.async.tasks;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.RepositoryException;
import com.hyland.openarch.ecm.core.event.EventService;
import com.hyland.openarch.runtime.api.Framework;

import com.hyland.openarch.async.tasks.message.BatchTask;

/**
 * Unit tests for {@link RetentionExpiredBatchTaskProcessor#process}, driven with a mocked {@link CoreSession}.
 */
class RetentionExpiredBatchTaskProcessorTest {

    private final RetentionExpiredBatchTaskProcessor processor = new RetentionExpiredBatchTaskProcessor();

    @Test
    void name() {
        assertEquals(RetentionExpiredBatchTaskProcessor.BATCH_RETENTION_EXPIRED_TASK_NAME, processor.getName());
    }

    @Test
    void resetsExpiredRetentionAndFiresEvent() {
        CoreSession session = mock(CoreSession.class);
        IdRef ref = new IdRef("doc1");
        when(session.exists(ref)).thenReturn(true);
        when(session.isRecord(ref)).thenReturn(true);
        when(session.getRetainUntil(ref)).thenReturn(Instant.now().minusSeconds(3600));
        when(session.getDocument(ref)).thenReturn(mock(DocumentModel.class));
        when(session.getRepositoryName()).thenReturn("repo");

        EventService eventService = mock(EventService.class);
        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            fw.when(() -> Framework.getService(EventService.class)).thenReturn(eventService);
            processor.process(mock(BatchTask.class), List.of("doc1"), session);
        }

        verify(session).setRetainUntil(ref, null);
        verify(eventService).fireEvent(any());
        verify(session).save();
    }

    @Test
    void skipsNonEligibleDocuments() {
        CoreSession session = mock(CoreSession.class);
        // missing
        when(session.exists(new IdRef("missing"))).thenReturn(false);
        // not a record
        when(session.exists(new IdRef("notRecord"))).thenReturn(true);
        when(session.isRecord(new IdRef("notRecord"))).thenReturn(false);
        // no retention
        when(session.exists(new IdRef("noRetention"))).thenReturn(true);
        when(session.isRecord(new IdRef("noRetention"))).thenReturn(true);
        when(session.getRetainUntil(new IdRef("noRetention"))).thenReturn(null);
        // still under retention
        when(session.exists(new IdRef("underRetention"))).thenReturn(true);
        when(session.isRecord(new IdRef("underRetention"))).thenReturn(true);
        when(session.getRetainUntil(new IdRef("underRetention"))).thenReturn(Instant.now().plusSeconds(3600));

        processor.process(mock(BatchTask.class), List.of("missing", "notRecord", "noRetention", "underRetention"),
                session);

        verify(session, never()).setRetainUntil(any(), any());
        verify(session).save();
    }

    @Test
    void swallowsResetFailure() {
        CoreSession session = mock(CoreSession.class);
        IdRef ref = new IdRef("doc1");
        when(session.exists(ref)).thenReturn(true);
        when(session.isRecord(ref)).thenReturn(true);
        when(session.getRetainUntil(ref)).thenReturn(Instant.now().minusSeconds(3600));
        doThrow(new RepositoryException("boom")).when(session).setRetainUntil(ref, null);

        assertDoesNotThrow(() -> processor.process(mock(BatchTask.class), List.of("doc1"), session));
        verify(session).save();
    }
}
