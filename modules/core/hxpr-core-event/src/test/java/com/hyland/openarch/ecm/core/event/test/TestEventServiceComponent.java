/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.event.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import jakarta.inject.Inject;

import com.hyland.hxpr.async.tasks.AsyncTasksFeature;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.ConcurrentUpdateException;
import com.hyland.openarch.ecm.core.event.Event;
import com.hyland.openarch.ecm.core.event.EventService;
import com.hyland.openarch.ecm.core.event.impl.EventContextImpl;
import com.hyland.openarch.ecm.core.event.impl.EventImpl;
import com.hyland.openarch.ecm.core.event.impl.PostCommitEventExecutor;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.cluster.BasicClusterFeature;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.TransactionalFeature;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

@ExtendWith(FeaturesExtension.class)
@Features({ TransactionalFeature.class, AsyncTasksFeature.class, BasicClusterFeature.class })
@Deploy("com.hyland.openarch.runtime.jtajca")
@Deploy("org.nuxeo.ecm.core.event")
public class TestEventServiceComponent {

    @Inject
    protected TransactionalFeature txFeature;

    @Inject
    protected EventService eventService;

    protected int initialThreadCount;

    @BeforeEach
    public void setUp() throws InterruptedException {
        Framework.getProperties().setProperty(PostCommitEventExecutor.TIMEOUT_MS_PROP, "100"); // 0.1s
        Thread.sleep(100);
        initialThreadCount = Thread.activeCount();
        DummyPostCommitEventListener.handledCountReset();
        DummyPostCommitEventListener.eventCountReset();
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.event:test-async-listeners.xml")
    public void testAsync() throws InterruptedException {
        // send two events, only one of which is recognized by the listener
        // (the other is filtered out of the bundle passed to this listener)
        Event test1 = new EventImpl("testasync", new EventContextImpl()); // NOSONAR
        eventService.fireEvent(test1);
        assertEquals(0, DummyPostCommitEventListener.handledCount());
        Event test2 = new EventImpl("testnotmached", new EventContextImpl()); // NOSONAR
        test2.setIsCommitEvent(true);
        eventService.fireEvent(test2);
        txFeature.nextTransaction();

        assertEquals(1, DummyPostCommitEventListener.handledCount());
        assertEquals(1, DummyPostCommitEventListener.eventCount());

        // check new information from sync event are retrieved in postcommit
        // listener
        assertEquals("bar", DummyPostCommitEventListener.properties.get("foo"));
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.event:test-sync-postcommit-listeners.xml")
    public void testSyncPostCommitError() throws Exception {
        // second handler done even though there's an error
        doTestSyncPostCommit(false, true, 0, 2, 4);
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.event:test-sync-postcommit-listeners.xml")
    public void testSyncPostCommitTimeout() throws Exception {
        // returned after timeout (100ms), so only one listener done at this time
        doTestSyncPostCommit(false, false, 150, 1, 2);
        txFeature.nextTransaction();

        assertEquals(2, DummyPostCommitEventListener.handledCount());
        assertEquals(4, DummyPostCommitEventListener.eventCount());
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.event:test-sync-postcommit-listeners.xml")
    public void testSyncPostCommitBulk() throws Exception {
        doTestSyncPostCommit(true, false, 0, 2, 4);
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.event:test-sync-postcommit-listeners.xml")
    public void testSyncPostCommitErrorBulk() throws Exception {
        // second handler not done because error in first one (bulk)
        doTestSyncPostCommit(true, true, 0, 1, 2);
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.event:test-sync-postcommit-listeners.xml")
    public void testSyncPostCommitTimeoutBulk() throws Exception {
        // returned after timeout (100ms), so only one listener done
        doTestSyncPostCommit(true, false, 1100, 1, 2); // 1s timeout on bulk
        txFeature.nextTransaction();

        // other listener was never run due to interrupt
        assertEquals(1, DummyPostCommitEventListener.handledCount());
        assertEquals(2, DummyPostCommitEventListener.eventCount());
    }

    protected void doTestSyncPostCommit(boolean bulk, boolean error, long timeoutMS, int expectedHandled,
            int expectedEvents) throws Exception {
        EventService eventService = Framework.getService(EventService.class);
        try {
            eventService.setBulkModeEnabled(bulk);
            if (timeoutMS > 0) {
                Framework.getProperties().setProperty(PostCommitEventExecutor.BULK_TIMEOUT_PROP, "1"); // 1s
            }
            doTestSyncPostCommit(error, timeoutMS, expectedHandled, expectedEvents);
        } finally {
            eventService.setBulkModeEnabled(false);
            Framework.getProperties().remove(PostCommitEventExecutor.BULK_TIMEOUT_PROP);
        }
    }

    private void doTestSyncPostCommit(boolean error, long timeoutMS, int expectedHandled, int expectedEvents)
            throws InterruptedException {
        // Send 4 events, only 2 of which are recognized by the listeners
        // (the last one is filtered out of the bundle passed to the listeners
        // and also servers as a trigger for the event bundle execution).
        Event event1 = new EventImpl("testnotmached", new EventContextImpl());
        eventService.fireEvent(event1);
        EventContextImpl context = new EventContextImpl();
        if (error) {
            // Provoke an error in the first listener, to check whether the
            // second one is done or not (depending on bulk mode)
            context.setProperty("throw", Boolean.TRUE);
        }
        if (timeoutMS > 0) {
            // Provoke a sleep that makes the work time out
            context.setProperty("sleep", timeoutMS);
        }
        Event event2 = new EventImpl("testsyncpostcommit", context);
        eventService.fireEvent(event2);
        Event event3 = new EventImpl("testsyncpostcommit", new EventContextImpl());
        eventService.fireEvent(event3);
        Event event4 = new EventImpl("testnotmached", new EventContextImpl());
        event4.setIsCommitEvent(true);
        assertEquals(0, DummyPostCommitEventListener.handledCount());
        eventService.fireEvent(event4);
        txFeature.nextTransaction();

        assertEquals(expectedHandled, DummyPostCommitEventListener.handledCount());
        assertEquals(expectedEvents, DummyPostCommitEventListener.eventCount());
        if (timeoutMS > 0) {
            // wait for listeners to finish
            Thread.sleep(timeoutMS + 10);
        }
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.event:test-async-listeners.xml") // contains a sync listener too
    public void testConcurrentUpdateExceptionNotSwallowed() {
        Event event = new EventImpl("testasync", new EventContextImpl());
        event.getContext().setProperty("throw-concurrent", "yes");
        try {
            eventService.fireEvent(event);
            fail("should throw ConcurrentUpdateException");
        } catch (ConcurrentUpdateException e) {
            assertEquals("too fast bro", e.getMessage());
        }
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.event:test-rollback-listener.xml")
    public void testMarkRollbackWithRepositoryException() {
        Event event = new EventImpl("markRollback", new EventContextImpl());
        try {
            eventService.fireEvent(event);
            fail("should throw RepositoryException");
        } catch (RepositoryException e) {
            assertEquals("RepositoryException", e.getMessage());
            assertEquals(400, e.getStatusCode());
            assertTrue(TransactionHelper.isTransactionMarkedRollback());
        }
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.event:test-rollback-listener.xml")
    public void testMarkRollbackWithRuntimeException() {
        Event event = new EventImpl("markRollback", new EventContextImpl());
        event.getContext().setProperty("RuntimeException", true);
        try {
            eventService.fireEvent(event);
            fail("should throw RepositoryException");
        } catch (RepositoryException e) {
            assertEquals(
                    "Exception during testRollbackListener sync listener execution, transaction will be rolled back",
                    e.getMessage());
            assertEquals(500, e.getStatusCode());
            assertTrue(TransactionHelper.isTransactionMarkedRollback());
            Throwable cause = e.getCause();
            assertTrue(cause instanceof RuntimeException);
            assertEquals("RuntimeException", cause.getMessage());
        }
    }

}
