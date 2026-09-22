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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.common.utils.DurationUtils;
import org.nuxeo.ecm.core.api.NuxeoException;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.cluster.ClusterService;
import com.hyland.openarch.runtime.model.ComponentManager;
import com.hyland.openarch.runtime.model.DefaultComponent;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.ScheduleBuilder;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.jdbcjobstore.LockException;
import org.quartz.impl.matchers.GroupMatcher;

/**
 * Schedule service implementation.
 * <p>
 * Since the cleanup of the quartz job is done when service is activated in cluster mode (see
 * https://jira.nuxeo.com/browse/NXP-7303), the schedules contributions MUST be the same on all nodes.
 * <p>
 * Due the fact that all jobs are removed when service starts on a node it may be a short period with no schedules in
 * quartz table even other node is running.
 */
public class SchedulerServiceImpl extends DefaultComponent implements SchedulerService, ComponentManager.Listener {

    private static final Logger log = LogManager.getLogger(SchedulerServiceImpl.class);

    /** @since 11.1 */
    public static final String CLUSTER_START_DURATION_PROP = "org.nuxeo.scheduler.cluster.start.duration";

    /** @since 11.1 */
    public static final Duration CLUSTER_START_DURATION_DEFAULT = Duration.ofMinutes(1);

    protected static final String XP = "schedule";

    protected Scheduler scheduler;

    /**
     * @since 7.10
     */
    private Map<String, JobKey> jobKeys = new HashMap<>();

    protected void setupScheduler() throws IOException, SchedulerException {
        Properties props = new Properties();
        if (!Framework.isTestModeSet()) {
            String filename = "quartz-config/quartz.properties";
            ClassLoader cl = getClass().getClassLoader();
            String content;
            try (InputStream stream = cl.getResourceAsStream(filename)) {
                content = IOUtils.toString(stream, UTF_8);
                content = Framework.expandVars(content);
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot load resource file: " + filename, e);
            }
            try (InputStream stream = IOUtils.toInputStream(content, UTF_8)) {
                try {
                    props.load(stream);
                } catch (IOException e) {
                    throw new NuxeoException("Failed to load properties from: " + filename, e);
                }
                patchProps(props);
            }
        } else {
            // use default config (unit tests)
            props.put("org.quartz.scheduler.instanceName", "Quartz");
            props.put("org.quartz.scheduler.threadName", "Quartz_Scheduler");
            props.put("org.quartz.scheduler.instanceId", "NON_CLUSTERED");
            props.put("org.quartz.scheduler.makeSchedulerThreadDaemon", "true");
            props.put("org.quartz.scheduler.skipUpdateCheck", "true");
            props.put("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
            props.put("org.quartz.threadPool.threadCount", "1");
            props.put("org.quartz.threadPool.threadPriority", "4");
            props.put("org.quartz.threadPool.makeThreadsDaemons", "true");
        }
        StdSchedulerFactory schedulerFactory = new StdSchedulerFactory();
        schedulerFactory.initialize(props);
        scheduler = schedulerFactory.getScheduler();
        scheduler.start();
        // server = MBeanServerFactory.createMBeanServer();
        // server.createMBean("org.quartz.ee.jmx.jboss.QuartzService",
        // quartzObjectName);

        // clean up all nuxeo jobs and triggers
        // https://jira.nuxeo.com/browse/NXP-7303
        GroupMatcher<JobKey> matcher = GroupMatcher.jobGroupEquals("nuxeo");
        Set<JobKey> jobs = scheduler.getJobKeys(matcher);
        GroupMatcher<TriggerKey> triggerMatcher = GroupMatcher.triggerGroupEquals("nuxeo");
        Set<TriggerKey> triggerKeys = scheduler.getTriggerKeys(triggerMatcher);
        try {
            scheduler.deleteJobs(new ArrayList<>(jobs)); // raise a lock error in case of concurrencies
            // quartz-mongodb may leave orphaned triggers after deleteJobs, clean them up explicitly
            scheduler.unscheduleJobs(new ArrayList<>(triggerKeys));
            this.<ScheduleDescriptor> getRegistryContributions(XP).forEach(this::registerSchedule);
        } catch (LockException cause) {
            log.warn("scheduler already re-initializing, another cluster node concurrent startup ?", cause);
        }
        log.debug("scheduler started");
    }

    /**
     * Patch some properties using code to avoid doing some outside preprocessing.
     *
     * @since 12.0
     */
    protected void patchProps(Properties props) {
        // backward compat convenience to allow local devs to use host:port as MongoDB URI
        String key = "org.quartz.jobStore.mongoUri";
        String mongoUri = (String) props.get(key);
        if (mongoUri != null && !mongoUri.startsWith("mongodb://") && !mongoUri.startsWith("mongodb+srv://")) {
            mongoUri = "mongodb://" + mongoUri;
            props.setProperty(key, mongoUri);
        }
    }

    protected void shutdownScheduler() {
        if (scheduler == null) {
            return;
        }
        try {
            scheduler.shutdown();
        } catch (SchedulerException cause) {
            log.error("Cannot shutdown scheduler", cause);
        } finally {
            scheduler = null;
        }
    }

    @Override
    public void deactivate() {
        log.debug("Deactivate");
        shutdownScheduler();
    }

    @Override
    public void afterRuntimeStart(ComponentManager mgr) {
        startScheduler();
    }

    protected void startScheduler() {
        ClusterService clusterService = Framework.getService(ClusterService.class);
        String prop = Framework.getProperty(CLUSTER_START_DURATION_PROP);
        Duration duration = DurationUtils.parsePositive(prop, CLUSTER_START_DURATION_DEFAULT);
        Duration pollDelay = Duration.ofSeconds(1);
        clusterService.runAtomically("start-scheduler", duration, pollDelay, () -> {
            try {
                setupScheduler();
            } catch (IOException | SchedulerException e) {
                throw new NuxeoException(e);
            }
        });
    }

    @Override
    public void stop() {
        if (scheduler == null) {
            return;
        }
        try {
            scheduler.standby();
        } catch (SchedulerException cause) {
            log.error("Cannot put scheduler in stand by mode", cause);
        }
    }

    @Override
    public boolean hasApplicationStarted() {
        return scheduler != null;
    }

    protected void registerSchedule(ScheduleDescriptor schedule) {
        registerSchedule(schedule, null);
    }

    protected void registerSchedule(ScheduleDescriptor schedule, Map<String, Serializable> parameters) {
        if (scheduler == null || schedule == null) {
            return;
        }
        String id = schedule.getId();
        this.<ScheduleDescriptor> getRegistryContribution(XP, id)
            .ifPresentOrElse(c -> schedule(c, parameters), () -> unschedule(id));
    }

    protected void schedule(ScheduleDescriptor schedule, Map<String, Serializable> parameters) {
        log.debug("Registering {}" , schedule);

        JobDetail job = buildJob(schedule, parameters);
        Trigger trigger = buildTrigger(schedule).build();

        // This is useful when testing to avoid multiple threads:
        // trigger = new SimpleTrigger(schedule.getId(), "nuxeo");

        try {
            try {
                schedule(schedule.getId(), job, trigger);
            } catch (ObjectAlreadyExistsException e) {
                // when jobs are persisted in a database, the job should already be there
                // remove existing job/trigger and re-schedule
                log.trace("Overriding scheduler with id: {}" , schedule.getId());
                unschedule(schedule.getId(), job.getKey());
                // quartz-mongodb may not cascade job deletion to triggers, remove explicitly
                scheduler.unscheduleJob(trigger.getKey());
                schedule(schedule.getId(), job, trigger);
            }
        } catch (SchedulerException e) {
            log.error(String.format("failed to schedule job with id '%s': %s", schedule.getId(), e.getMessage()), e);
        }
    }

    private JobDetail buildJob(ScheduleDescriptor schedule, Map<String, Serializable> parameters) {
        JobDataMap map = new JobDataMap();
        if (parameters != null) {
            map.putAll(parameters);
        }
        return JobBuilder.newJob(ScheduledJobRunner.class)
                         .withIdentity(schedule.getId(), "nuxeo")
                         .usingJobData(map)
                         .usingJobData(ScheduledJobRunner.SCHEDULE_ID, schedule.getId())
                         .usingJobData(ScheduledJobRunner.CLASS_NAME, schedule.getClassName())
                         .usingJobData(ScheduledJobRunner.USER_ID, schedule.getUserId())
                         .build();
    }

    private TriggerBuilder<?> buildTrigger(ScheduleDescriptor schedule) {
        return TriggerBuilder.newTrigger()
                             .withIdentity(schedule.getId(), "nuxeo")
                             .withSchedule(buildSchedule(schedule));
    }

    private ScheduleBuilder<?> buildSchedule(ScheduleDescriptor schedule) {
        CronScheduleBuilder builder = CronScheduleBuilder.cronSchedule(schedule.getCronExpression());
        if (schedule.getTimeZone() != null) {
            builder.inTimeZone(TimeZone.getTimeZone(schedule.getTimeZone()));
        }
        return builder;
    }

    protected void schedule(String id, JobDetail job, Trigger trigger) throws SchedulerException {
        scheduler.scheduleJob(job, trigger);
        // record the jobKey only if scheduleJob didn't throw
        jobKeys.put(id, job.getKey());
    }

    protected boolean unschedule(String id) {
        return unschedule(id, jobKeys.remove(id));
    }

    protected boolean unschedule(String id, JobKey jobKey) {
        if (jobKey == null) {
            // shouldn't happen but let's be robust
            log.warn("Unscheduling null jobKey for id: {}" , id);
            return false;
        }
        try {
            return scheduler.deleteJob(jobKey);
        } catch (SchedulerException e) {
            log.error(String.format("failed to unschedule job with '%s': %s", id, e.getMessage()), e);
            return true; // didn't exist so consider it removed (maybe concurrency)
        }
    }

}
