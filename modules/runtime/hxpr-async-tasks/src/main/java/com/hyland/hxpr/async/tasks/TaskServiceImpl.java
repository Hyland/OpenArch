/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.hxpr.async.tasks;

import static com.hyland.hxpr.async.tasks.TaskStatus.State.ABORTED;
import static com.hyland.hxpr.async.tasks.TaskStatus.State.COMPLETED;
import static com.hyland.hxpr.async.tasks.TaskStatus.State.SCHEDULED;
import static com.hyland.hxpr.async.tasks.TaskStatus.State.UNKNOWN;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.nuxeo.common.utils.TooLargeException;
import org.nuxeo.common.xmap.registry.MapRegistry;
import org.nuxeo.common.xmap.registry.SingleRegistry;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.cluster.ClusterService;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;
import org.nuxeo.runtime.kv.KeyValueStoreProvider;
import org.nuxeo.runtime.model.ComponentManager;
import org.nuxeo.runtime.model.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hyland.hxpr.async.tasks.message.AbstractTask;
import com.hyland.hxpr.async.tasks.message.ScrollerTask;
import com.hyland.hxpr.async.tasks.message.Task;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public class TaskServiceImpl extends DefaultComponent implements TaskService, ComponentManager.Listener {

    private static final Logger log = LoggerFactory.getLogger(TaskServiceImpl.class);

    private static final String NAME = "com.hyland.hxpr.async.tasks.AsyncTasksService";

    private static final String XP_TASKS = "asyncTasks";

    private static final String XP_TASKS_RUNNER = "runner";

    private static final String ASYNC_TASKS_KV_STORE_NAME = "asyncTasks";

    private static final String ASYNC_STATUS_PREFIX = "asyncStatus:";

    // How long we keep the command and its status in the kv store once completed
    private static final long COMPLETED_TTL_SECONDS = 3_600;

    // How long we keep the command and its status in the kv store once aborted
    private static final long ABORTED_TTL_SECONDS = 43_200;

    // How long we keep the command and its status in the kv store once completed with an error
    private static final long COMPLETED_IN_ERROR_TTL_SECONDS = 86_400;

    // A pod can have a couple dozen runners (one per queue); each runner's stopRunner() already bounds
    // its own wait, but stopping them one after another would still multiply that bound by the runner
    // count and can exceed the pod's termination grace period. Stop them concurrently instead, with this
    // as an overall safety net.
    private static final long STOP_RUNNERS_TIMEOUT_MS = Duration.ofSeconds(15).toMillis();

    private static final Pattern UUID_REGEX =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    public static final ObjectMapper MAPPER = JsonMapper.builder()
            .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .build();

    private final Map<String, TaskRunner<?>> runners = new HashMap<>();

    private TaskRunnerDescriptor runnerDescriptor;

    private final Map<String, TaskProcessorDescriptor> descriptors = new HashMap<>();

    @Override
    public void start() {
        // Load the runner contribution
        runnerDescriptor = getTasksRunnerContribution();

        // Load the contributions for the async tasks
        getEnabledDescriptors().forEach(this::initTaskProcessor);
    }

    private TaskRunnerDescriptor getTasksRunnerContribution() {
        SingleRegistry registry = (SingleRegistry) Framework.getRuntime()
                .getComponentManager()
                .getExtensionPointRegistry(NAME, XP_TASKS_RUNNER)
                .orElseThrow(() -> new IllegalArgumentException("Unknown registry for extension point '%s--%s'".formatted(NAME, XP_TASKS_RUNNER)));
        Optional<TaskRunnerDescriptor> descriptor = registry.getContribution();
        return descriptor.orElseThrow(() -> new NuxeoException("No Async Tasks runner has been defined"));
    }

    /**
     * Stops all task runners in parallel, bounding the total wait to {@link #STOP_RUNNERS_TIMEOUT_MS} so the shutdown
     * time doesn't scale with the number of runners.
     */
    @Override
    public void beforeRuntimeStop(ComponentManager mgr) {
        List<Thread> stoppers = runners.entrySet().stream().map(this::startStopperThread).toList();
        joinAllWithDeadline(stoppers, System.currentTimeMillis() + STOP_RUNNERS_TIMEOUT_MS);
    }

    /**
     * Starts a daemon thread that stops the given runner. Any failure is logged rather than propagated, so one runner's
     * error doesn't stop the others from being processed.
     *
     * @param entry the queue name and runner to stop
     * @return the started, not-yet-joined thread
     */
    private Thread startStopperThread(Map.Entry<String, TaskRunner<?>> entry) {
        Thread thread = new Thread(() -> {
            try {
                entry.getValue().stopRunner();
            } catch (RuntimeException e) {
                log.warn("Error stopping task runner for queue {}", entry.getKey(), e);
            }
        }, "task-runner-stopper-" + entry.getKey());
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * Joins each thread in turn, without letting the total wait exceed {@code deadline}. Returns early, leaving any
     * remaining threads running as daemons, if the deadline is reached or the wait is interrupted.
     *
     * @param stoppers the threads to join, in order
     * @param deadline the {@link System#currentTimeMillis()} time after which to stop waiting
     */
    private void joinAllWithDeadline(List<Thread> stoppers, long deadline) {
        for (Thread thread : stoppers) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                log.warn("Timed out waiting for all task runners to stop, {} may still be shutting down",
                        thread.getName());
                return;
            }
            try {
                thread.join(remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private List<TaskProcessorDescriptor> getEnabledDescriptors() {
        MapRegistry registry = (MapRegistry) Framework.getRuntime()
                .getComponentManager()
                .getExtensionPointRegistry(NAME, XP_TASKS)
                .orElseThrow(() -> new IllegalArgumentException("Unknown registry for extension point '%s--%s'".formatted(NAME, XP_TASKS)));

        List<TaskProcessorDescriptor> contributions = registry.getContributionValues();
        return contributions.stream().filter(TaskProcessorDescriptor::isEnabled).toList();
    }

    @Override
    public String submitTask(Task task, boolean throwOnError) {
        String taskName = task.getTaskName();
        String repositoryName = task.getRepository();
        String taskId = task.getTaskId();

        // Check that the command exists
        if (!isTaskAvailable(taskName)) {
            throw new IllegalArgumentException("Invalid task name: " + taskName);
        }

        // Check that the repository exists
        RepositoryManager repoManager = Framework.getService(RepositoryManager.class);
        if (repoManager != null) {
            // It is possible to have a task triggered with a null repository because it's not related to documents
            if (repositoryName != null && repoManager.getRepository(repositoryName) == null) {
                throw new IllegalArgumentException("Unknown repository: " + repositoryName);
            }
        }

        // Add the extra information if defined by the implementation
        addExtraInfoToTaskMessage(task);

        String statusTaskName;
        if (task instanceof ScrollerTask scrollerTask //
                && (statusTaskName = scrollerTask.getBulkTaskName()) != null) {
            if (!isTaskAvailable(statusTaskName)) {
                throw new IllegalArgumentException("Invalid bulk task name: " + statusTaskName);
            }
            if (scrollerTask.getBatchSize() == 0) {
                scrollerTask.setBatchSize(getBatchSize(statusTaskName));
            }
        } else {
            statusTaskName = taskName;
        }

        // Store the async taskName status and the taskName itself in the K/V store if there isn't an entry yet
        TaskStatus status = getStatus(taskId);
        if (status.getState() == UNKNOWN) {
            status = new TaskStatus(taskId);
            status.setUsername(task.getUsername());
            status.setState(SCHEDULED);
            status.setTask(statusTaskName);
            status.setSubmitTime(Instant.now());
            setStatus(status);
        }
        // else ScrollerTask subtasks will already have a (shared) status

        try {
            // Submit the task to the runner
            getRunner(taskName).submitTask(taskId, serializeTask(task), throwOnError);
            status.setPublishedToTopic(true);
            setStatus(status);
        } catch (TooLargeException e) {
            // Rethrow as-is (not wrapped) so that callers such as BatchUtils#tryWithSplit can catch it by type
            // and retry with a smaller batch instead of aborting the whole task.
            abortTask(status, taskId, taskName, e);
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) { // NOSONAR
                Thread.currentThread().interrupt();
            }
            abortTask(status, taskId, taskName, e);
            throw new NuxeoException(e);
        }
        return taskId;
    }

    private void abortTask(TaskStatus status, String taskId, String taskName, Exception e) {
        log.error("Failed to submit task {} to runner {}: {}", taskId, taskName, e.getMessage(), e);
        status.setErrorMessage(e.getMessage());
        status.setState(ABORTED);
        setStatus(status);
    }

    @Override
    public String submitTask(Task task) {
        return submitTask(task, false);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void initTaskProcessor(TaskProcessorDescriptor descriptor) {
        var runner = runnerDescriptor.createTaskRunner();
        runners.put(descriptor.getName(), runner);
        runner.initTaskProcessor((TaskProcessor) descriptor.createTaskProcessor());
        descriptors.put(descriptor.getName(), descriptor);
    }

    // public for tests
    public TaskRunner<?> getRunner(String taskId) {
        if (!runners.containsKey(taskId)) {
            throw new IllegalArgumentException("The actionId %s does not have a registered runner.".formatted(taskId));
        }
        return runners.get(taskId);
    }

    // public for tests
    public boolean isTaskAvailable(String taskId) {
        return runners.containsKey(taskId);
    }

    private String getAsyncStatusKey(TaskStatus status) {
        return status.getId();
    }

    /**
     * Stores the status in the kv store returns the encoded status
     */
    @Override
    public String setStatus(TaskStatus status) {
        KeyValueStore kvStore = getKvStore();
        var statusSerialized = serializeStatus(status);
        var key = getAsyncStatusKey(status);
        switch (status.getState()) {
        case ABORTED:
            kvStore.put(key, statusSerialized, ABORTED_TTL_SECONDS);
            break;
        case COMPLETED:
            long ttl = status.hasError() ? COMPLETED_IN_ERROR_TTL_SECONDS : COMPLETED_TTL_SECONDS;
            kvStore.put(key, statusSerialized, ttl);
            break;
        default:
            kvStore.put(key, statusSerialized);
        }
        return key;
    }

    @Override
    public TaskStatus getStatus(String taskId) {
        var kvStore = getKvStore();
        // Handle backward compatibility in task id
        var taskKey = UUID_REGEX.matcher(taskId).matches() ?  ASYNC_STATUS_PREFIX + taskId : taskId;
        var taskStatus = kvStore.getString(taskKey);
        if (taskStatus == null) {
            TaskStatus status = new TaskStatus(taskId);
            status.setState(UNKNOWN);
            return status;
        }
        return deserializeStatus(taskStatus);
    }

    // public for tests
    public List<TaskStatus> getStatuses(String username) {
        KeyValueStoreProvider kv = (KeyValueStoreProvider) getKvStore();
        return kv.keyStream()
                .map(kv::getString)
                .map(this::deserializeStatus)
                .filter(status -> username.equals(status.getUsername()))
                .toList();
    }

    // public for tests
    public List<TaskStatus> getStatusesByTaskIdPrefix(String prefix) {
        KeyValueStoreProvider kv = (KeyValueStoreProvider) getKvStore();
        return kv.keyStream()
                .map(kv::getString)
                .map(this::deserializeStatus)
                .filter(status -> status.getId().startsWith(prefix))
                .toList();
    }

    private int getBatchSize(String action) {
        return descriptors.get(action).getBatchSize();
    }

    private TaskStatus deserializeStatus(String serializedStatus) {
        if (serializedStatus != null) {
            try {
                return MAPPER.readValue(serializedStatus, TaskStatus.class);
            } catch (JacksonException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    private KeyValueStore getKvStore() {
        return Framework.getService(KeyValueService.class).getKeyValueStore(ASYNC_TASKS_KV_STORE_NAME);
    }

    private String serializeStatus(TaskStatus status) {
        try {
            return MAPPER.writeValueAsString(status);
        } catch (JacksonException e) {
            log.error("Error when serializing status: {}", status, e);
            throw new RuntimeException(e);
        }
    }

    private String serializeTask(Task task) {
        try {
            return MAPPER.writeValueAsString(task);
        } catch (JacksonException e) {
            log.error("Error when serializing task: {}", task, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean awaitCompletion(Duration duration, String... taskNames) throws InterruptedException {
        long deadline = System.nanoTime() + duration.toNanos();
        KeyValueStoreProvider kv = (KeyValueStoreProvider) getKvStore();
        var nowMinus5min = Instant.now().minus(5, ChronoUnit.MINUTES);
        // Loop: each iteration waits for the current set of active tasks, then rescans to catch any new
        // child tasks (e.g. batches) that were submitted while the previous layer was running.
        // This handles arbitrarily deep chains: trigger → scroller → batches, without hard-coding phases.
        // Re-scanning completed IDs is not a concern: setStatus() assigns a TTL when a task reaches
        // COMPLETED or ABORTED, so they are absent from keyStreamWithoutTTL on the next iteration.
        while (true) {
            Set<String> active = new HashSet<>();
            if (taskNames != null && taskNames.length > 0) {
                Arrays.stream(taskNames).forEach(name -> kv.keyStreamWithoutTTL(name).forEach(active::add));
            } else {
                kv.keyStreamWithoutTTL().forEach(active::add);
            }
            // Filter out tasks that are older than 5 minutes
            List<TaskStatus> activeStatuses = active.stream()
                    .map(this::getStatus)
                    .filter(status -> !status.getSubmitTime().isBefore(nowMinus5min))
                    .toList();
            if (activeStatuses.isEmpty()) {
                return true; // no active tasks — the full pipeline is done
            }
            log.debug("Wait for task ids: {}", activeStatuses.stream().map(TaskStatus::getId).toList());
            if (!isTasksCompleted(activeStatuses, deadline)) {
                return false;
            }
            // Tasks completed — rescan to catch child tasks submitted during the wait
        }
    }

    // protected for tests
    protected boolean isTasksCompleted(List<TaskStatus> statuses, long deadline) throws InterruptedException {
        for (TaskStatus status : statuses) {
            String id = status.getId();
            log.debug("Wait for command id: {}", id);
            while (true) {
                TaskStatus currentStatus = getStatus(id);
                TaskStatus.State state = currentStatus.getState();
                log.debug("Status of command: {} = {} -> {}", id, currentStatus, state);
                if (state == COMPLETED || state == ABORTED || state == UNKNOWN) {
                    break;
                }
                if (deadline < System.nanoTime()) {
                    log.debug("await timeout, at least one uncompleted command: {} = {}", id, currentStatus);
                    return false;
                }
                Thread.sleep(200);
            }
        }
        return true;
    }

    @Override
    public void addExtraInfoToTaskMessage(Task task) {
        if (task.getNodeId() == null && task instanceof AbstractTask abstractTask) {
            var clusterService = Framework.getService(ClusterService.class);
            if (clusterService != null) {
                abstractTask.setNodeId(clusterService.getNodeId());
            }
        }
    }
}
