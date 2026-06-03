/*
 * (C) Copyright 2007-2017 Nuxeo (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.format.DateTimeFormatter;

import jakarta.inject.Inject;

import com.hyland.hxpr.async.tasks.AsyncTasksFeature;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.runtime.cluster.BasicClusterFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features({ BasicClusterFeature.class, AsyncTasksFeature.class })
@Deploy("org.nuxeo.ecm.core.event")
public class TestSchedulerService {

    public static final DateTimeFormatter CRON_EXPRESSION_FORMATTER = DateTimeFormatter.ofPattern("s m H d M ? yyyy");

    protected void waitUntilDummyEventListenerIsCalled(int maxRetry) throws Exception {
        waitUntilDummyEventListenerIsCalled(maxRetry, 1);
    }

    protected void waitUntilDummyEventListenerIsCalled(int maxRetry, int minCountValue) throws Exception {
        long count = DummyScheduledJob.getCount();
        int retry = 0;
        while (count < minCountValue && retry < maxRetry * 2) {
            Thread.sleep(50);
            count = DummyScheduledJob.getCount();
            retry++;
        }
    }

    @Inject
    protected SchedulerService scheduler;

    @BeforeEach
    public void setUp() throws Exception {
        DummyScheduledJob.setCount(0);
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.event.test:OSGI-INF/test-scheduler-config.xml")
    public void testScheduleRegistration() throws Exception {

        waitUntilDummyEventListenerIsCalled(10); // so that job is called at least once
        long count = DummyScheduledJob.getCount();
        assertTrue(count >= 1, "count " + count);
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.event.test:OSGI-INF/test-scheduler-config.xml")
    public void testConfig() throws Exception {

        waitUntilDummyEventListenerIsCalled(10); // so that job is called at least once
        long count = DummyScheduledJob.getCount();
        assertTrue(count >= 1);
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.event.test:OSGI-INF/test-scheduler-config.xml")
    @Deploy("org.nuxeo.ecm.core.event.test:OSGI-INF/test-scheduler-disabled-config.xml")
    public void testDisableSchedule() throws Exception {

        long count = DummyScheduledJob.getCount();
        waitUntilDummyEventListenerIsCalled(10); // wait a bit, with timeout
        long newCount = DummyScheduledJob.getCount();
        assertEquals(count, newCount);
    }

}
