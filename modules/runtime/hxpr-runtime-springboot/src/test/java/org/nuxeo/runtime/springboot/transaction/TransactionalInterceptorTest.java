/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nelson Silva
 */
package org.nuxeo.runtime.springboot.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.nuxeo.runtime.springboot.config.HxprRuntimeSpringbootAutoConfiguration;
import org.nuxeo.runtime.springboot.transaction.config.TransactionalInterceptorTestConfig;
import org.nuxeo.runtime.springboot.transaction.config.TransactionalInterceptorTestConfig.TestBean;
import org.nuxeo.runtime.springboot.transaction.config.TransactionalInterceptorTestConfig.TransactionalTestBean;
import org.nuxeo.runtime.springboot.transaction.config.TransactionalInterceptorTestConfig.TransactionalTestBeanTimeout;
import org.nuxeo.runtime.springboot.transaction.config.TransactionalInterceptorTestConfig.TransactionalTestBeanTimeoutConfig;
import org.nuxeo.runtime.transaction.TransactionHelper;
import org.nuxeo.runtime.transaction.TransactionRuntimeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Unit tests for the {@link Transactional} annotation and the {@link TransactionalInterceptor}.
 */
@SpringBootTest(classes = { HxprRuntimeSpringbootAutoConfiguration.class, TransactionalInterceptorTestConfig.class })
class TransactionalInterceptorTest {

    @Test
    void testNonTransactional(@Autowired TestBean bean) {
        assertFalse(bean.nonTransactionalHasTransaction());

        // explicitly start transaction
        TransactionHelper.startTransaction();
        assertTrue(bean.nonTransactionalHasTransaction());
        TransactionHelper.commitOrRollbackTransaction();
        assertFalse(TransactionHelper.isTransactionActive());
    }

    @Test
    void testTransactional(@Autowired TestBean bean, @Autowired TransactionalTestBean transactionalBean) {
        assertTrue(bean.transactionalHasTransaction());
        assertFalse(TransactionHelper.isTransactionActive());

        assertTrue(transactionalBean.hasTransaction());
        assertFalse(TransactionHelper.isTransactionActive());

        // explicitly start transaction
        TransactionHelper.startTransaction();
        assertTrue(transactionalBean.hasTransaction());
        TransactionHelper.commitOrRollbackTransaction();
        assertFalse(TransactionHelper.isTransactionActive());
    }

    @Test
    void testTransactionalTimeoutMethod(@Autowired TestBean bean) throws InterruptedException {
        checkTimeout(bean::sleepMoreThanTransactionalTimeout);
    }

    @Test
    void testTransactionalTimeoutConfigMethod(@Autowired TestBean bean) throws InterruptedException {
        checkTimeout(bean::sleepMoreThanTransactionalTimeoutConfig);
    }

    @Test
    void testTransactionalTimeoutClass(@Autowired TransactionalTestBeanTimeout bean) throws InterruptedException {
        checkTimeout(bean::sleepMoreThanTransactionalTimeout);
    }

    @Test
    void testTransactionalTimeoutConfigClass(@Autowired TransactionalTestBeanTimeoutConfig bean)
            throws InterruptedException {
        checkTimeout(bean::sleepMoreThanTransactionalTimeout);
    }

    public interface TimeoutProducer {
        void timeout() throws InterruptedException;
    }

    protected void checkTimeout(TimeoutProducer tp) throws InterruptedException {
        assertFalse(TransactionHelper.isTransactionActive());
        try {
            tp.timeout();
            fail("Transaction should have timed out");
        } catch (TransactionRuntimeException e) {
            assertEquals("Unable to commit: Transaction timeout", e.getMessage());
        }
        assertFalse(TransactionHelper.isTransactionActive());
    }

}
