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
package com.hyland.openarch.ecm.core.event;

import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;

import java.rmi.dgc.VMID;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.common.utils.Path;
import com.hyland.openarch.common.xmap.registry.Registry;
import com.hyland.openarch.ecm.core.api.ConcurrentUpdateException;
import com.hyland.openarch.ecm.core.api.NuxeoException;
import com.hyland.openarch.ecm.core.api.RecoverableClientException;
import com.hyland.openarch.ecm.core.event.impl.DocumentEventContext;
import com.hyland.openarch.ecm.core.event.impl.EventBundleImpl;
import com.hyland.openarch.ecm.core.event.impl.EventListenerDescriptor;
import com.hyland.openarch.ecm.core.event.impl.EventListenerList;
import com.hyland.openarch.ecm.core.event.impl.PostCommitEventExecutor;
import com.hyland.openarch.ecm.core.event.impl.ShallowEvent;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.model.DefaultComponent;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;

/**
 * Event Service Component, allowing registration of contributions and doing the event service shutdown upon
 * deactivation.
 */
public class EventServiceImpl extends DefaultComponent implements EventService, Synchronization {

    private static final Logger log = LogManager.getLogger(EventServiceImpl.class);

    public static final int APPLICATION_STARTED_ORDER = -500;

    public static final String NAME = "com.hyland.openarch.ecm.core.event.EventService";

    public static final String EVENT_LISTENER_XP = "listener";

    public static final long DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(5).toMillis();

    public static final VMID VMID = new VMID();

    protected static final ThreadLocal<CompositeEventBundle> threadBundles = ThreadLocal.withInitial(
            CompositeEventBundle::new);

    private static class CompositeEventBundle {

        boolean registeredSynchronization;

        final Map<String, EventBundle> byRepository = new HashMap<>();

        void push(Event event) {
            String repositoryName = event.getContext().getRepositoryName();
            byRepository.computeIfAbsent(repositoryName,  name -> new EventBundleImpl());
            byRepository.get(repositoryName).push(event);
        }

    }

    protected EventListenerList listenerDescriptors;

    protected PostCommitEventExecutor postCommitExec;

    protected boolean bulkModeEnabled = false;

    public EventServiceImpl() {
        postCommitExec = new PostCommitEventExecutor();
    }

    @Override
    public void start() {
        init();
    }

    @Override
    public void deactivate() {
        String s = Framework.getProperty("org.nuxeo.ecm.core.event.shutdown.timeoutMillis");
        long timeout = s == null ? DEFAULT_SHUTDOWN_TIMEOUT : Long.parseLong(s);
        try {
            shutdown(timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getApplicationStartedOrder() {
        return APPLICATION_STARTED_ORDER;
    }

    @SuppressWarnings("unchecked")
    protected <T extends Registry> T getRegistry(String extensionPoint) {
        return (T) Framework.getRuntime()
                            .getComponentManager()
                            .getExtensionPointRegistry(NAME, extensionPoint)
                            .orElseThrow(() -> new IllegalArgumentException(String.format(
                                    "Unknown registry for extension point '%s--%s'", NAME, extensionPoint)));

    }

    public void init() {
        listenerDescriptors = getRegistry(EVENT_LISTENER_XP);
    }

    public void shutdown(long timeoutMillis) throws InterruptedException {
        postCommitExec.shutdown(timeoutMillis);
    }

    @Override
    public void addEventListener(EventListenerDescriptor listener) {
        listenerDescriptors.add(listener);
        log.debug("Registered event listener: {}" , listener.getName());
    }

    @Override
    public void removeEventListener(EventListenerDescriptor listener) {
        listenerDescriptors.removeDescriptor(listener);
        log.debug("Unregistered event listener: {}" , listener.getName());
    }

    @Override
    public void fireEvent(Event event) {

        String ename = event.getName();
        Tracer tracer = Tracing.getTracer();
        for (EventListenerDescriptor desc : listenerDescriptors.getInlineListenersDescriptors()) {
            if (!desc.acceptEvent(ename)) {
                continue;
            }
            try {
                long t0 = System.currentTimeMillis();
                desc.asEventListener().handleEvent(event);
                long elapsed = System.currentTimeMillis() - t0;
                traceAddAnnotation(event, tracer, elapsed, desc.getName());
                if (event.isCanceled()) {
                    // break loop
                    return;
                }
            } catch (ConcurrentUpdateException e) {
                // never swallow ConcurrentUpdateException
                throw e;
            } catch (RuntimeException e) {
                // get message
                String message = "Exception during " + desc.getName() + " sync listener execution, ";
                if (event.isBubbleException()) {
                    message += "other listeners will be ignored";
                } else if (event.isMarkedForRollBack()) {
                    message += "transaction will be rolled back";
                    if (event.getRollbackMessage() != null) {
                        message += " (" + event.getRollbackMessage() + ")";
                    }
                } else {
                    message += "continuing to run other listeners";
                }
                // log
                tracer.getCurrentSpan().addAnnotation("EventService#fireEvent " + event.getName() + ": " + message);
                if (e instanceof RecoverableClientException) {
                    log.info(String.format(message , "\n" , e.getMessage()));
                    log.debug(message, e);
                } else {
                    log.error(message, e);
                }
                // rethrow or swallow
                if (TransactionHelper.isTransactionMarkedRollback()) {
                    throw e;
                } else if (event.isBubbleException()) {
                    throw e;
                } else if (event.isMarkedForRollBack()) {
                    // make sure the transaction is marked rollback-only,
                    // even if some code later incorrectly swallows the rethrown exception
                    TransactionHelper.setTransactionRollbackOnly();

                    Exception ee;
                    if (event.getRollbackException() != null) {
                        ee = event.getRollbackException();
                    } else {
                        ee = e;
                    }

                    if (ee instanceof NuxeoException nuxeoException) {
                        throw nuxeoException;
                    } else {
                        throw new NuxeoException(message, ee);
                    }
                } else {
                    // swallow exception
                }
            }
        }

        if (!event.isInline()) { // record the event
            // don't record the complete event, only a shallow copy
            ShallowEvent shallowEvent = ShallowEvent.create(event);
            if (event.isImmediate()) {
                EventBundleImpl b = new EventBundleImpl();
                b.push(shallowEvent);
                tracer.getCurrentSpan().addAnnotation("EventService#fireEvent firing immediate: " + event.getName());
                fireEventBundle(b);
            } else {
                recordEvent(shallowEvent);
            }
        }
    }

    protected void traceAddAnnotation(Event event, Tracer tracer, long elapsed, String listener) {
        Map<String, AttributeValue> attributes = new HashMap<>();
        attributes.put("event", AttributeValue.stringAttributeValue(event.getName()));
        attributes.put("listener", AttributeValue.stringAttributeValue(listener));
        attributes.put("duration_ms", AttributeValue.longAttributeValue(elapsed));
        EventContext eventContext = event.getContext();
        if (eventContext instanceof DocumentEventContext docContext && docContext.getSourceDocument() != null) {
            Path docPath = docContext.getSourceDocument().getPath();
            if (docPath != null) {
                attributes.put("doc", AttributeValue.stringAttributeValue(docPath.toString()));
            }
            String id = docContext.getSourceDocument().getId();
            if (id != null) {
                attributes.put("doc_id", AttributeValue.stringAttributeValue(id));
            }
        }
        tracer.getCurrentSpan().addAnnotation("EventService#fireEvent Event fired", attributes);
    }

    @Override
    public void fireEventBundle(EventBundle event) {
        Span span = Tracing.getTracer().getCurrentSpan();
        span.addAnnotation("EventService#fireEventBundle");
        try {
            List<EventListenerDescriptor> postCommitSync = listenerDescriptors.getSyncPostCommitListenersDescriptors();

            if (bulkModeEnabled) {
                // run all listeners synchronously in one transaction
                List<EventListenerDescriptor> listeners = new ArrayList<>();
                listeners.addAll(postCommitSync);
                if (!listeners.isEmpty()) {
                    postCommitExec.runBulk(listeners, event);
                }
                return;
            }

            if (!postCommitSync.isEmpty()) {
                postCommitExec.run(postCommitSync, event);
            }
        } finally {
            span.addAnnotation("EventService#fireEventBundle.done");
        }
    }

    @Override
    public EventListenerDescriptor getEventListener(String name) {
        return (EventListenerDescriptor) listenerDescriptors.getContribution(name).orElse(null);
    }

    // methods for monitoring

    @Override
    public EventListenerList getListenerList() {
        return listenerDescriptors;
    }

    @Override
    public void setBulkModeEnabled(boolean bulkModeEnabled) {
        this.bulkModeEnabled = bulkModeEnabled;
    }

    protected void recordEvent(Event event) {
        CompositeEventBundle b = threadBundles.get();
        b.push(event);
        if (TransactionHelper.isTransactionActive()) {
            if (!b.registeredSynchronization) {
                // register as synchronization
                try {
                    TransactionHelper.lookupTransactionManager().getTransaction().registerSynchronization(this);
                } catch (SystemException | RollbackException e) {
                    throw new RuntimeException("Cannot register Synchronization", e);
                }
                b.registeredSynchronization = true;
            }
        } else if (event.isCommitEvent()) {
            handleTxCommited();
        }
    }

    @Override
    public void beforeCompletion() {
        Span span = Tracing.getTracer().getCurrentSpan();
        span.addAnnotation("EventService#beforeCompletion");
    }

    @Override
    public void afterCompletion(int status) {
        Span span = Tracing.getTracer().getCurrentSpan();
        if (status == Status.STATUS_COMMITTED) {
            span.addAnnotation("EventService#afterCompletion committed");
            handleTxCommited();
        } else if (status == Status.STATUS_ROLLEDBACK) {
            span.addAnnotation("EventService#afterCompletion ROLLBACK");
            handleTxRollbacked();
        } else {
            log.error("Unexpected afterCompletion status: {}" , status);
        }
        span.addAnnotation("EventService#afterCompletion.done");
    }

    protected void handleTxRollbacked() {
        threadBundles.remove();
    }

    protected void handleTxCommited() {
        CompositeEventBundle b = threadBundles.get();
        threadBundles.remove();

        // notify post commit event listeners
        for (EventBundle bundle : b.byRepository.values()) {
            try {
                fireEventBundle(bundle);
            } catch (NuxeoException e) {
                log.error("Error while processing {}" , bundle, e);
            }
        }
    }

}
