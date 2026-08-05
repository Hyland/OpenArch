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
package org.nuxeo.rest.upload;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.rest.document.DocumentManager;
import org.nuxeo.runtime.springboot.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class UploadHandlerCleanerInterceptor implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    private final String DOCUMENT_MANAGER_CLASS_NAME = DocumentManager.class.getName();

    Logger log = LoggerFactory.getLogger(UploadHandlerCleanerInterceptor.class);

    @Pointcut("@annotation(transactional)")
    public void callAtMethodWith(Transactional transactional) {
    }

    @Around("callAtMethodWith(transactional)")
    private Object handleTransaction(ProceedingJoinPoint proceedingJoinPoint, Transactional transactional)
            throws Throwable {
        try {
            return proceedingJoinPoint.proceed();
        } catch (NuxeoException e) {
            cleanupUploadIds(e);
            throw e;
        }
    }

    private void cleanupUploadIds(NuxeoException ne) {
        for (StackTraceElement element : ne.getStackTrace()) {
            if (DOCUMENT_MANAGER_CLASS_NAME.equalsIgnoreCase(element.getClassName())) {
                UploadHandlerCleaner cleaner = applicationContext.getBean(UploadHandlerCleaner.class);
                if (cleaner.get().size() > 0) {
                    log.info("Upload ids : {}", cleaner.get());
                    cleaner.cleanAll();
                }
                break;
            }
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
