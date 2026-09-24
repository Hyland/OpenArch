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
package com.hyland.openarch.ecm.core.scheduler;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.runtime.cluster.BasicClusterFeature;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(FeaturesExtension.class)
@Features(BasicClusterFeature.class)
@Deploy("org.nuxeo.ecm.core.event")
@Deploy("org.nuxeo.ecm.core.event.test:test-jobfactory.xml") // jobs
public class EventJobFactoryTest {
    @Inject
    private SchedulerService schedulerService;

    @Test
    public void test() throws SchedulerException {
        Scheduler scheduler = ((SchedulerServiceImpl) schedulerService).scheduler;

        // job with the DisallowConcurrentExecution annotation
        JobKey jobKey = new JobKey("testScheduler", "nuxeo");
        JobDetail jobDetail = scheduler.getJobDetail(jobKey);
        assertThat(jobDetail).isNotNull();
        assertThat(jobDetail.getJobClass().isAnnotationPresent(DisallowConcurrentExecution.class)).isTrue();
    }
}
