/*
 * (C) Copyright 2011-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.management.jtajca;

import static java.lang.Boolean.TRUE;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.TransactionManager;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.test.CoreFeature;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.LogCaptureFeature;

/**
 * @author matic
 */
@ExtendWith(FeaturesExtension.class)
@Features({ JtajcaManagementFeature.class, CoreFeature.class, LogCaptureFeature.class })
public class CanMonitorTransactionsTest {

    @Inject
    @Named("default")
    protected TransactionMonitor monitor;

    @Inject
    protected TransactionManager tm;

    protected ExecutorService executor;

    @BeforeEach
    public void injectExectutor() {
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    public void shudownExecutor() {
        executor.shutdownNow();
    }

    protected void begin() {
        try {
            tm.begin();
        } catch (Exception cause) {
            throw new RuntimeException("Cannot start new transacton", cause);
        }
    }

    protected void rollback() {
        try {
            tm.rollback();
        } catch (Exception cause) {
            throw new RuntimeException("Cannot rollback transaction", cause);
        }
    }

    protected void commit() {
        try {
            tm.commit();
        } catch (Exception cause) {
            throw new RuntimeException("Cannot commit transaction", cause);
        }
    }

    @Test
    public void isMonitorInstalled() {
        assertThat(monitor, notNullValue());
        monitor.getTotalCommits(); // throw exception is monitor not present
    }

    protected class TestTotalRollbacks implements Callable<Boolean> {

        @Override
        public Boolean call() {
            long activeCount = monitor.getActiveCount();
            long totalRollbacks = monitor.getTotalRollbacks();
            begin();
            assertThat(monitor.getActiveCount(), is(activeCount + 1));
            rollback();
            assertThat(monitor.getActiveCount(), is(activeCount));
            assertThat(monitor.getTotalRollbacks(), is(totalRollbacks + 1));

            return TRUE;
        }

    }

    @Test
    public void isTotalRollbacksCorrect() throws InterruptedException, ExecutionException {
        assertThat(monitor.getActiveCount(), is(1L));
        FutureTask<Boolean> rollback = new FutureTask<>(new TestTotalRollbacks());
        executor.execute(rollback);
        assertThat(rollback.get(), is(TRUE));
    }

    protected class TestTotalCommits implements Callable<Boolean> {

        @Override
        public Boolean call() {
            long totalCommits = monitor.getTotalCommits();
            begin();
            commit();
            assertThat(monitor.getTotalCommits(), is(totalCommits + 1));
            return TRUE;
        }

    }

    @Test
    public void isTotalCommitsCorrect() throws InterruptedException, ExecutionException {
        FutureTask<Boolean> commit = new FutureTask<>(new TestTotalCommits());
        executor.execute(commit);
        assertThat(commit.get(), is(TRUE));
    }

    protected class TestCollectStatistics implements Callable<Boolean> {

        @Override
        public Boolean call() {
            try {
                List<TransactionStatistics> stats = monitor.getActiveStatistics();
                long count = stats.size();
                begin();
                stats = monitor.getActiveStatistics();
                assertThat((long) stats.size(), is(count + 1));
                commit();
                stats = monitor.getActiveStatistics();
                assertThat((long) stats.size(), is(count));
            } catch (Exception cause) {
                LogManager.getLogger(CanMonitorTransactionsTest.class).error("Caught error while collecting statistics",
                        cause);
            }

            return TRUE;
        }

    }

    @Test
    public void isActiveStatisticsCollected() throws InterruptedException, ExecutionException {
        FutureTask<Boolean> task = new FutureTask<>(new TestCollectStatistics());
        executor.execute(task);
        assertThat(task.get(), is(TRUE));
    }

}
