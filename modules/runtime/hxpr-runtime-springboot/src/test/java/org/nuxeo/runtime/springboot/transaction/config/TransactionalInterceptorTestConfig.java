/*
 * (C) Copyright 2024 Hyland (https://hyland.com/) and others.
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
 *     dedwards
 */
package org.nuxeo.runtime.springboot.transaction.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.nuxeo.runtime.springboot.transaction.Transactional;
import org.nuxeo.runtime.transaction.TransactionHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

@EnableAspectJAutoProxy
@TestConfiguration
@TestPropertySource(locations = {"classpath:application.properties"})
public class TransactionalInterceptorTestConfig {

    @Value("${test.transaction.timeout}")
    private int timeout;

    protected void sleep2Seconds() throws InterruptedException {
        // sleep twice longer than timeout
        Thread.sleep(2000); // NOSONAR
    }

    @Component
    public class TestBean {

        public boolean nonTransactionalHasTransaction() {
            return TransactionHelper.isTransactionActive();
        }

        @Transactional
        public boolean transactionalHasTransaction() {
            return TransactionHelper.isTransactionActive();
        }

        @Transactional(timeout = 1)
        public void sleepMoreThanTransactionalTimeout() throws InterruptedException {
            assertWithSleep();
        }

        @Transactional(property = "test.transaction.timeout")
        public void sleepMoreThanTransactionalTimeoutConfig() throws InterruptedException {
            assertWithSleep();
        }

        private void assertWithSleep() throws InterruptedException {
            assertTrue(TransactionHelper.isTransactionActive());
            sleep2Seconds();
        }
    }

    @Component
    @Transactional
    public class TransactionalTestBean {
        public boolean hasTransaction() {
            return TransactionHelper.isTransactionActive();
        }
    }

    @Component
    @Transactional(timeout = 1)
    public class TransactionalTestBeanTimeout {
        public void sleepMoreThanTransactionalTimeout() throws InterruptedException {
            assertTrue(TransactionHelper.isTransactionActive());
            sleep2Seconds();
        }
    }

    @Component
    @Transactional(property = "test.transaction.timeout")
    public class TransactionalTestBeanTimeoutConfig {
        public void sleepMoreThanTransactionalTimeout() throws InterruptedException {
            assertTrue(TransactionHelper.isTransactionActive());
            sleep2Seconds();
        }
    }

}
