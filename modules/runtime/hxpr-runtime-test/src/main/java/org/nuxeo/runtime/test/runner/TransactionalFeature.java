/*
 * (C) Copyright 2018-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.runtime.test.runner;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.runtime.management.jvm.ThreadDeadlocksDetector;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * The transactional feature is responsible of transaction management.
 * <p>
 * It brings some API to wait for transaction
 *
 * @since 10.2
 */
@Deploy("org.nuxeo.runtime.jtajca")
@Features(RuntimeFeature.class)
public class TransactionalFeature implements RunnerFeature {

    private static final Logger log = LogManager.getLogger(TransactionalFeature.class);

    protected boolean autoStartTransaction;

    protected boolean txStarted;

    protected final List<Waiter> waiters = new LinkedList<>();

    @FunctionalInterface
    public interface Waiter {

        /**
         * @since 10.3
         */
        boolean await(Duration duration) throws InterruptedException;

    }

    public void addWaiter(Waiter waiter) {
        waiters.add(waiter);
    }

    public void nextTransaction() {
        nextTransaction(Duration.ofMinutes(3));
    }

    public void nextTransaction(Duration duration) {
        boolean tx = TransactionHelper.isTransactionActive();
        boolean rb = TransactionHelper.isTransactionMarkedRollback();
        if (tx || rb) {
            // there may be tx synchronizer pending, so we
            // have to commit the transaction
            TransactionHelper.commitOrRollbackTransaction();
        }
        try {
            Duration remainingDuration = duration;
            log.trace("Waiter providers: {}", waiters);
            for (Waiter provider : waiters) {
                log.trace("Wait for provider: {}", provider);
                long start = System.currentTimeMillis();
                try {
                    await(provider, remainingDuration);
                } catch (InterruptedException cause) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("interrupted while awaiting for asynch completion", cause);
                }
                long end = System.currentTimeMillis();
                remainingDuration = remainingDuration.minusMillis(end - start);
            }
        } finally {
            if (tx || rb) {
                // restore previous tx status
                TransactionHelper.startTransaction();
                if (rb) {
                    TransactionHelper.setTransactionRollbackOnly();
                }
            }
        }
    }

    protected void await(Waiter waiter, Duration duration) throws InterruptedException {
        if (!waiter.await(duration)) {
            try {
                File file = new ThreadDeadlocksDetector().dump(new long[0]);
                log.warn("timed out in {}, thread dump available in {}", waiter.getClass(), file);
            } catch (IOException cause) {
                log.warn("timed out in {}, cannot take thread dump", waiter.getClass(), cause);
            }
        }
    }

    @Override
    public void initialize(FeaturesLoader loader) {
        autoStartTransaction = loader.getConfig(TransactionalConfig.class).autoStart();
    }

    @Override
    public void beforeSetup(Method method, Object test) {
        startTransactionBefore();
    }

    @Override
    public void afterTeardown(Method method, Object test) {
        commitOrRollbackTransactionAfter();
    }

    protected void startTransactionBefore() {
        if (autoStartTransaction) {
            txStarted = TransactionHelper.startTransaction();
        }
    }

    protected void commitOrRollbackTransactionAfter() {
        if (txStarted) {
            TransactionHelper.commitOrRollbackTransaction();
        } else {
            if (TransactionHelper.isTransactionActive()) {
                try {
                    TransactionHelper.setTransactionRollbackOnly();
                    TransactionHelper.commitOrRollbackTransaction();
                } finally {
                    log.warn("Committing a transaction for your, please do it yourself");
                }
            }
        }
    }

}
