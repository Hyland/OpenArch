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
package com.hyland.hxpr.async.tasks.mem;

import static com.hyland.hxpr.async.tasks.TaskStatus.State.COMPLETED;
import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

import tools.jackson.core.JacksonException;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.hyland.hxpr.async.tasks.TaskService;
import com.hyland.hxpr.async.tasks.TaskServiceImpl;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;
import com.hyland.hxpr.async.tasks.TaskProcessor;
import com.hyland.hxpr.async.tasks.message.Task;
import com.hyland.openarch.runtime.api.Framework;

public class MemTaskQueue<T extends Task> implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(MemTaskQueue.class);

    private static final ObjectMapper MAPPER = TaskServiceImpl.MAPPER;

    private static final long INACTIVITY_BREAK_MS = 100;

    private final Queue<Message> partition = new ConcurrentLinkedQueue<>();

    private final TaskProcessor<T> processor;

    protected volatile boolean stop;

    private boolean recordActivity;

    private long counter;

    public MemTaskQueue(TaskProcessor<T> processor) {
        this.processor = processor;
    }

    @Override
    public void run() {
        var interrupted = false;
        try {
            processLoop();
        } catch (InterruptedException e) {
            interrupted = true; // Thread.currentThread().interrupt() in finally
            var msg = "Queue of task " + processor.getClass().getName() + " interrupted";
            // this is expected when the pool is shutdownNow
            if (log.isTraceEnabled()) {
                log.debug(msg, e);
            } else {
                log.debug(msg);
            }
        } catch (Exception e) {
            if (Thread.interrupted()) {

            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void processLoop() throws InterruptedException {
        boolean timerActivity = false;
        while (continueLoop()) {
            recordActivity = processRecord();
            counter++;
            if (!timerActivity && !recordActivity) {
                // no activity take a break
                Thread.sleep(INACTIVITY_BREAK_MS);
            }
        }
    }

    private boolean continueLoop() {
        if (stop || Thread.currentThread().isInterrupted()) {
            log.debug("{}: Stop processing interrupted", processor.getClass().getName());
            return false;
        }
        return true;
    }

    private boolean processRecord() {
        Message message;
        if (!partition.isEmpty()) {
            message = partition.peek();
            String json = message.message();
            T task;
            try {
                task = MAPPER.readValue(json, processor.getTaskClass());
            } catch (JacksonException e) {
                log.error("Error deserializing message: {}", json, e);
                return true;
            }
            // Call the taskName compute method
            processTask(task);
            partition.remove(message);
            return true;
        }
        return false;
    }

    private void processTask(T task) {
        var retryCount = task.getRetryCount();
        RuntimeException suppressed = null;
        for (int i = 0; i <= retryCount; i++) {
            if (i != 0) {
                log.debug("Retry executing task {} {} (number of retries {})",task.getTaskName(), task.getTaskId(), i);
            }
            try {
                this.processor.process(task);
                break;
            } catch (RuntimeException e) {
                log.debug("Exception thrown when executing task {} {}", task.getTaskName(), task.getTaskId(), e);
                if (suppressed == null) {
                    suppressed = e;
                } else if (e != suppressed) {
                    suppressed.addSuppressed(e);
                }
            }
        }
        if (suppressed != null) {
            log.error("Error when processing the task {} after {} retries ", processor.getName(), retryCount);
            // Update the status of the task
            var asyncTasksService = Framework.getService(TaskService.class);
            var status = asyncTasksService.getStatus(task.getTaskId());
            status.setState(COMPLETED);
            status.setErrorMessage(suppressed.getMessage());
            status.setErrorCode(SC_INTERNAL_SERVER_ERROR);
            asyncTasksService.setStatus(status);
        }
    }

    public void publishMessageToQueue(String key, String message) {
        var newMessage = new Message(key, message);
        partition.add(newMessage);
    }

    public void stop() {
        log.debug("{}: Receives Stop signal", processor.getName());
        stop = true;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MemTaskQueue<?> other = (MemTaskQueue<?>) obj;
        if (!processor.equals(other.processor)) {
            return false;
        }
        return partition.equals(other.partition);
    }

    @Override
    public String toString() {
        return "MemTaskQueue{" + "taskName=" + processor.toString() + ", partition=" + ArrayUtils.toString(partition)
                + "}";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        var result = 1;
        result = prime * result + ((processor == null) ? 0 : processor.hashCode());
        result = prime * result + ((partition.isEmpty()) ? 0 : ArrayUtils.hashCode(partition));
        return result;
    }

    private record Message(String key, String message) {

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Message other = (Message) o;
            return StringUtils.equals(key, other.key) && StringUtils.equals(message, other.message);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(key);
            result = 31 * result + Objects.hash(message);
            return result;
        }

        @Override
        public String toString() {
            return "Message{" + "key=" + key + ", message=" + message + '}';
        }
    }
}
