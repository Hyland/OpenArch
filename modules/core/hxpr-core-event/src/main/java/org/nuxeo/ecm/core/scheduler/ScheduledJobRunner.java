/*
 * (C) Copyright 2007-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.scheduler;

import java.time.Duration;

import javax.security.auth.login.LoginException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.api.login.LoginContext;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * A Quartz job whose execution runs a configured {@link ScheduledJob}.
 */
@DisallowConcurrentExecution
public class ScheduledJobRunner implements Job {

    private static final Logger log = LogManager.getLogger(ScheduledJobRunner.class);

    public static final String SCHEDULE_ID = "id";

    public static final String CLASS_NAME = "class";

    public static final String USER_ID = "userid";

    private static final int TIMEOUT = (int) Duration.ofMinutes(30).toSeconds();

    /**
     * Job execution to execute the configured code.
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        SchedulerService scheduler = Framework.getService(SchedulerService.class);
        if (scheduler == null || !scheduler.hasApplicationStarted()) {
            // too early
            return;
        }

        // switch to the proper context classloader
        ClassLoader oldCL = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
        try {
            execute(context.getJobDetail().getJobDataMap());
        } finally {
            Thread.currentThread().setContextClassLoader(oldCL);
        }
    }

    private void execute(JobDataMap dataMap) {
        String scheduleId = dataMap.getString(SCHEDULE_ID);
        String className = dataMap.getString(CLASS_NAME);
        String userId = dataMap.getString(USER_ID);
        Object object;
        try {
            object = Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            log.error("Failed to instantiate scheduled job: {} with class: {}", scheduleId, className, e);
            return;
        }
        if (!(object instanceof ScheduledJob scheduledJob)) {
            log.error("Failed to instantiate scheduled job: {} with invalid class: {}", scheduleId, className);
            return;
        }

        TransactionHelper.runInTransaction(TIMEOUT, () -> {
            try (var __ = loginUserOrSystem(userId)) {
                log.debug("Executing scheduled job: {} (user {})", scheduleId, userId);
                scheduledJob.run();
            } catch (LoginException e) {
                log.error("Error while processing scheduled job: {}", scheduleId, e);
            }
        });
    }

    private LoginContext loginUserOrSystem(String userid) throws LoginException {
        try {
            return Framework.loginUserOrSystem(userid);
        } catch (NullPointerException e) {
            // no PrincipalFactory registered
            // TODO set up tests properly for this to not happen
            return null;
        }
    }

}
