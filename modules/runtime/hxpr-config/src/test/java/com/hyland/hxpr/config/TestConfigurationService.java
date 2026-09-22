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
package com.hyland.hxpr.config;

import static com.hyland.hxpr.config.DummyServiceImpl.CONFIG_DUMMY;
import static com.hyland.hxpr.config.DummyServiceImpl.DUMMY_REPO;
import static com.hyland.hxpr.config.DummyServiceImpl.SETTINGS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

import jakarta.inject.Inject;

@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
@Deploy("com.hyland.hxpr.config")
@Deploy("com.hyland.hxpr.config.test:OSGI-INF/dummy-service.xml")
public class TestConfigurationService {

    @Inject
    ConfigurationService configurationService;

    @Inject
    DummyService dummyService;

    private IO io = new IO();

    public static class IO implements ConfigurationServiceIO {

        public Map<String, Map<String, Object>> data = new HashMap<>();

        /** Called on every write, to observe when writes reach the persistence engine. */
        public Runnable onWrite;

        /** Called once a read has taken its value, to widen the window between it and what it feeds. */
        public Runnable onRead;

        /** When set, the next conditional write reports a conflict, as another node writing first would. */
        public boolean conflictOnce;

        /** Called when {@link #conflictOnce} fires, to store what that other writer wrote. */
        public Runnable onConflict;

        @Override
        public Long tryWriteConfiguration(String configName, Map<String, Object> config, long expectedVersion) {
            if (conflictOnce) {
                conflictOnce = false;
                if (onConflict != null) {
                    onConflict.run();
                }
                return null;
            }
            return writeConfiguration(configName, config, expectedVersion);
        }

        @Override
        public ConfigurationWithVersion readConfiguration(String configName) {
            Map<String, Object> config = data.get(configName);
            if (onRead != null) {
                onRead.run();
            }
            return new ConfigurationWithVersion(config, 0L);
        }

        @Override
        public void writeConfiguration(String configName, Map<String, Object> config) {
            if (onWrite != null) {
                onWrite.run();
            }
            Map<String, Object> map = new HashMap<>(config); // non-test code would use deep copy
            data.put(configName, map);
        }

        @Override
        public long writeConfiguration(String configName, Map<String, Object> config, long expectedVersion) {
            writeConfiguration(configName, config);
            return 0L; // version not supported in this dummy
        }
    }

    /**
     * How long a test waits for the other side to break in. Nothing should come through, so this elapses in full when the
     * code is correct; a violation, if it can happen at all, happens as soon as the other side is let go.
     */
    private static final Duration VIOLATION_WINDOW = Duration.ofMillis(250);

    /**
     * A configurable of the same configuration as the dummy service, with a hook run while its configuration is being
     * set. Two of these describe the same configuration, so they share its lock: one probes from inside the critical
     * section while the other tries to get in.
     */
    private static class HookedConfigurable implements Configurable<String> {

        private volatile String state = "";

        private final List<String> events;

        /** Run at the start of the set, i.e. inside the critical section. */
        private final Runnable whileSetting;

        HookedConfigurable(List<String> events, Runnable whileSetting) {
            this.events = events;
            this.whileSetting = whileSetting;
        }

        @Override
        public String getConfigurableConfigName() {
            return CONFIG_DUMMY;
        }

        @Override
        public String getLocalState(String repositoryName) {
            return state;
        }

        @Override
        public void setLocalState(String repositoryName, String newState) {
            if (whileSetting != null) {
                whileSetting.run();
            }
            state = newState;
            events.add("set");
        }

        @Override
        public Map<String, Object> toConfigMap(String state) {
            return Map.of(SETTINGS, state);
        }

        @Override
        public String fromConfigMap(String repositoryName, Map<String, Object> config) {
            return (String) config.get(SETTINGS);
        }
    }

    @BeforeEach
    public void before() {
        configurationService.registerConfigurationServiceIO(DUMMY_REPO, io);
    }

    @AfterEach
    public void after() {
        configurationService.unregisterConfigurationServiceIO(DUMMY_REPO);
    }

    /**
     * A write and the local change that follows it are one operation, so two concurrent writers set their configuration
     * in the order they persisted. Were they not, a writer that persisted first could set its content after the other
     * had already set theirs, leaving this node with content older than what is stored — and no invalidation to repair
     * it, since those go to the other nodes.
     */
    @Test
    public void testSetConfigurationIfVersionMatchesIsOneOperation() throws Exception {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch proberInside = new CountDownLatch(1);
        CountDownLatch wroteWhileProberInside = new CountDownLatch(1);
        AtomicBoolean brokeIn = new AtomicBoolean();

        // The prober persists, then waits inside the critical section for the other writer to break in. It cannot wait
        // for that writer to finish, which is what it is meant to be prevented from doing, so it waits for as long as a
        // violation would take and reports whether one happened.
        Configurable<String> prober = new HookedConfigurable(events, () -> {
            proberInside.countDown();
            try {
                brokeIn.set(wroteWhileProberInside.await(VIOLATION_WINDOW.toMillis(), TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });
        // the other writer describes the same configuration, so it contends for the same lock
        Configurable<String> other = new HookedConfigurable(events, null);

        // record the writes as they reach the persistence engine, interleaved with the local changes. The prober's own
        // write comes before it is inside, so any write seen after that is the other writer breaking in.
        io.onWrite = () -> {
            events.add("write");
            if (proberInside.getCount() == 0) {
                wroteWhileProberInside.countDown();
            }
        };
        Callable<Void> proberWriter = () -> {
            configurationService.setConfigurationIfVersionMatches(DUMMY_REPO, prober, "prober", 0L);
            return null;
        };
        Callable<Void> otherWriter = () -> {
            proberInside.await(); // start only once the prober is between persisting and setting its state
            configurationService.setConfigurationIfVersionMatches(DUMMY_REPO, other, "other", 0L);
            return null;
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (Future<Void> future : executor.invokeAll(List.of(proberWriter, otherWriter))) {
                future.get(); // fails the test if a writer threw
            }
        } finally {
            io.onWrite = null;
        }

        assertFalse(brokeIn.get(), "another writer persisted while this one was between persisting and setting");
        // each write is immediately followed by its own local change, never by the other writer's write
        assertEquals(List.of("write", "set", "write", "set"), events);
    }

    /**
     * A configuration cannot have an entry whose name is reserved for the metadata of the document it is stored in.
     * Configurations keyed by entity name — the security policies, the renditions — take those names from the caller, and
     * such an entry would be overwritten by the metadata when written and dropped when read, so it is refused instead of
     * silently lost. The whole {@code _} prefix is reserved, not just the names used today.
     */
    @Test
    public void testWriteRejectsEntryNamedLikeDocumentMetadata() {
        for (String reserved : List.of("_id", "_version", "_anything")) {
            Map<String, Object> config = Map.of(SETTINGS, "gee", reserved, "mine");
            Map<String, Object> storedBefore = io.data.get(CONFIG_DUMMY);

            var withoutVersion = assertThrows(IllegalArgumentException.class,
                    () -> configurationService.writeConfiguration(DUMMY_REPO, CONFIG_DUMMY, config));
            assertTrue(withoutVersion.getMessage().contains(reserved), withoutVersion.getMessage());

            var withVersion = assertThrows(IllegalArgumentException.class,
                    () -> configurationService.writeConfiguration(DUMMY_REPO, CONFIG_DUMMY, config, 0L));
            assertTrue(withVersion.getMessage().contains(reserved), withVersion.getMessage());

            // nothing was persisted
            assertEquals(storedBefore, io.data.get(CONFIG_DUMMY));
        }
    }

    /**
     * A version-unaware write persists the configuration and sets it locally as one operation, so two concurrent writers
     * cannot persist in one order and set theirs in the other. Otherwise this node ends up holding a configuration it
     * did not store, and stays that way: the other nodes are invalidated and converge on what is stored, while the node
     * that wrote does not invalidate itself.
     */
    @Test
    public void testSetConfigurationIsOneOperation() throws Exception {
        CountDownLatch proberInside = new CountDownLatch(1);
        CountDownLatch otherWrote = new CountDownLatch(1);
        AtomicBoolean brokeIn = new AtomicBoolean();

        // One writer probes from between reading its state back and persisting it, which is where the engine is called;
        // the other reports that it got that far.
        ThreadLocal<Boolean> probing = new ThreadLocal<>();
        io.onWrite = () -> {
            if (Boolean.TRUE.equals(probing.get())) {
                proberInside.countDown();
                try {
                    brokeIn.set(otherWrote.await(VIOLATION_WINDOW.toMillis(), TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            } else {
                otherWrote.countDown();
            }
        };
        Callable<Void> prober = () -> {
            probing.set(Boolean.TRUE);
            try {
                dummyService.setSettings("prober");
            } finally {
                probing.remove();
            }
            return null;
        };
        Callable<Void> other = () -> {
            proberInside.await(); // start only once the prober is between reading its state back and persisting it
            dummyService.setSettings("other");
            return null;
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (Future<Void> future : executor.invokeAll(List.of(prober, other))) {
                future.get(); // fails the test if either threw
            }
        } finally {
            io.onWrite = null;
        }

        assertFalse(brokeIn.get(), "another writer persisted while this one was between setting and persisting");
        // whichever writer went second, this node holds exactly what it stored
        assertEquals(io.data.get(CONFIG_DUMMY).get(SETTINGS), dummyService.getSettings());
    }

    /**
     * The read-and-set a received invalidation performs shares the critical section of a write, so it cannot set
     * content it read before a write that has already set its own on this node. That would leave the local configuration
     * behind the stored one with nothing left to repair it: an invalidation carries no content, so the one that prompted
     * this read has already been consumed.
     */
    @Test
    public void testInvalidationCannotOvertakeAWrite() throws Exception {
        io.data.put(CONFIG_DUMMY, Map.of(SETTINGS, "old"));
        CountDownLatch readTaken = new CountDownLatch(1);
        CountDownLatch wroteWhileReading = new CountDownLatch(1);
        AtomicBoolean brokeIn = new AtomicBoolean();

        // The invalidation probes from between its read and what that read feeds; the write reports reaching the
        // engine, which it can only do if it was not kept out.
        ThreadLocal<Boolean> readingForInvalidation = new ThreadLocal<>();
        io.onRead = () -> {
            if (Boolean.TRUE.equals(readingForInvalidation.get())) {
                readTaken.countDown();
                try {
                    brokeIn.set(wroteWhileReading.await(VIOLATION_WINDOW.toMillis(), TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        };
        io.onWrite = wroteWhileReading::countDown;
        Callable<Void> invalidation = () -> {
            readingForInvalidation.set(Boolean.TRUE);
            try {
                // what receiving a cluster invalidation does
                ((ConfigurationServiceImpl) configurationService).readConfigurationAndSetLocally(DUMMY_REPO,
                        (DummyServiceImpl) dummyService);
            } finally {
                readingForInvalidation.remove();
            }
            return null;
        };
        Callable<Void> write = () -> {
            readTaken.await(); // start only once the invalidation has read, and before it sets what it read
            configurationService.setConfigurationIfVersionMatches(DUMMY_REPO, (DummyServiceImpl) dummyService, "new",
                    0L);
            return null;
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (Future<Void> future : executor.invokeAll(List.of(invalidation, write))) {
                future.get(); // fails the test if either threw
            }
        } finally {
            io.onRead = null;
            io.onWrite = null;
        }

        assertFalse(brokeIn.get(), "a write got through while an invalidation was between reading and setting");
        // whichever ran first, the local configuration ends up being what is stored, not something older
        assertEquals(io.data.get(CONFIG_DUMMY).get(SETTINGS), dummyService.getSettings());
    }

    /**
     * An update derives its result from the persisted configuration, not from the one this node holds. This node's copy
     * lags behind while a cluster invalidation is in flight, and the write that follows replaces the whole
     * configuration, so an update derived from the local copy would drop what this node has not seen yet.
     */
    @Test
    public void testUpdateDerivesFromThePersistedConfiguration() {
        dummyService.setSettings("mine");
        // another node has written since; this node still holds its own until the invalidation arrives
        io.data.put(CONFIG_DUMMY, Map.of(SETTINGS, "elsewhere"));

        List<String> derivedFrom = new ArrayList<>();
        configurationService.updateConfiguration(DUMMY_REPO, (DummyServiceImpl) dummyService, current -> {
            derivedFrom.add(current);
            return current + "+update";
        });

        assertEquals(List.of("elsewhere"), derivedFrom);
        assertEquals("elsewhere+update", io.data.get(CONFIG_DUMMY).get(SETTINGS));
        // the update also brings this node back in step with what it stored
        assertEquals("elsewhere+update", dummyService.getSettings());
    }

    /**
     * Nothing persisted yet — at bootstrap, or on a backend that does not persist configuration — is the one case where
     * the configuration this node holds is the current one.
     */
    @Test
    public void testUpdateDerivesFromTheLocalConfigurationWhenNothingPersisted() {
        dummyService.setSettings("mine");
        io.data.remove(CONFIG_DUMMY);

        configurationService.updateConfiguration(DUMMY_REPO, (DummyServiceImpl) dummyService,
                current -> current + "+update");

        assertEquals("mine+update", dummyService.getSettings());
        assertEquals("mine+update", io.data.get(CONFIG_DUMMY).get(SETTINGS));
    }

    /**
     * A write that fails leaves the configuration this node holds untouched, because it is set only once the write has
     * gone through. Set first, the node would serve a configuration it never stored, and no other node would ever hear
     * of it: invalidations are only sent for a write that happened.
     */
    @Test
    public void testFailedWriteLeavesTheLocalConfigurationUntouched() {
        dummyService.setSettings("kept");
        io.onWrite = () -> {
            throw new IllegalStateException("storage is down");
        };
        try {
            assertThrows(IllegalStateException.class, () -> dummyService.setSettings("lost"));
            assertEquals("kept", dummyService.getSettings());

            // the same holds for an update derived from the persisted configuration
            assertThrows(IllegalStateException.class, () -> configurationService.updateConfiguration(DUMMY_REPO,
                    (DummyServiceImpl) dummyService, current -> current + "+lost"));
            assertEquals("kept", dummyService.getSettings());
        } finally {
            io.onWrite = null;
        }
        // nothing was stored either, so this node and storage still agree
        assertEquals("kept", io.data.get(CONFIG_DUMMY).get(SETTINGS));
    }

    /**
     * Another node writing between the read and the write is a conflict, not a silent overwrite: the update is applied
     * again, to what that node wrote. The per-node lock cannot protect this, since that node has its own.
     */
    @Test
    public void testUpdateIsRecomputedWhenAnotherNodeWritesInBetween() {
        dummyService.setSettings("mine");
        io.conflictOnce = true;
        io.onConflict = () -> io.data.put(CONFIG_DUMMY, new HashMap<>(Map.of(SETTINGS, "elsewhere")));

        List<String> derivedFrom = new ArrayList<>();
        configurationService.updateConfiguration(DUMMY_REPO, (DummyServiceImpl) dummyService, current -> {
            derivedFrom.add(current);
            return current + "+update";
        });

        // the first attempt derived from what was stored, the second from what the other node left
        assertEquals(List.of("mine", "elsewhere"), derivedFrom);
        assertEquals("elsewhere+update", io.data.get(CONFIG_DUMMY).get(SETTINGS));
        assertEquals("elsewhere+update", dummyService.getSettings());
    }

    @Test
    public void testInitFromPersistedConfigWhenStarting() {
        // stop service as we'll need to restart it later
        ((DummyServiceImpl) dummyService).stop();

        // prepare initial persisted data
        io.data.put(CONFIG_DUMMY, Map.of(SETTINGS, "gee"));

        // restart service
        dummyService = new DummyServiceImpl();
        ((DummyServiceImpl) dummyService).start();

        // check that startup read the persisted config
        assertEquals("gee", dummyService.getSettings());
    }

}
