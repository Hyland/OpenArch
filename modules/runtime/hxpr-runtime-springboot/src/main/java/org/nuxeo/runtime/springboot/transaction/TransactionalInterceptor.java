/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.runtime.springboot.transaction;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.nuxeo.runtime.transaction.TransactionHelper;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Interceptor to handle transactions.
 */
@Aspect
@Component
public class TransactionalInterceptor implements ApplicationContextAware {

    ApplicationContext applicationContext;

    @Pointcut("@annotation(transactional)")
    public void callAtMethodWith(Transactional transactional) {}

    @Pointcut("@within(transactional)")
    public void callAtDeclaringTypeWith(Transactional transactional) {}

    @Around("callAtDeclaringTypeWith(transactional)")
    public Object handleTypeLevelAnnotation(ProceedingJoinPoint proceedingJoinPoint, Transactional transactional)
            throws Throwable {
        // Class level moved to own method. Combined pointcuts caused transactional to occasionally be null.
        return handleTransaction(proceedingJoinPoint, transactional);
    }

    @Around("callAtMethodWith(transactional)")
    private Object handleTransaction(ProceedingJoinPoint proceedingJoinPoint, Transactional transactional)
            throws Throwable {
        boolean txActive = TransactionHelper.isTransactionActive();
        boolean txStarted = false;
        try {
            if (txActive) {
                TransactionHelper.commitOrRollbackTransaction();
            }
            txStarted = TransactionHelper.startTransaction(getTimeout(transactional));
            return proceedingJoinPoint.proceed();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt(); // restore the interrupted status
            }
            TransactionHelper.setTransactionRollbackOnly();
            throw e;
        } finally {
            if (txStarted) {
                TransactionHelper.commitOrRollbackTransaction();
            }
            if (txActive) {
                // go back to default transaction timeout
                TransactionHelper.startTransaction();
            }
        }
    }

    private int getTimeout(Transactional transactional) {
        return applicationContext.getEnvironment().getProperty(transactional.property(), Integer.class, transactional.timeout());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

}
