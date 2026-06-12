/*
 * (C) Copyright 2023 Hyland (https://www.hyland.com/) and others.
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
 *     Gildas Lefevre
 */
package com.hyland.hxpr.async.tasks;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;

/**
 * A task status or a change of status (delta).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskStatus {

    private String commandId;

    private String username;

    private State state;

    private Long submitTime;

    private String task;

    private long errorCount;

    private String errorMessage;

    private Integer errorCode;

    private long batchCount;

    private Long processed;

    private Long lastExecutionTime;

    private boolean publishedToTopic;

    public TaskStatus() {

    }

    public TaskStatus(@NotNull String commandId) {
        this.commandId = commandId;
    }

    /**
     * Gets the task id.
     */
    public String getId() {
        return commandId;
    }

    public void setId(String id) {
        this.commandId = id;
    }

    /**
     * Gets the state of the bulk taskName.
     */
    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Gets the taskName name of the command.
     */
    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
    }

    /**
     * Gets the time when the command was submitted to the Bulk service.
     */
    public Instant getSubmitTime() {
        return submitTime == null ? null : Instant.ofEpochMilli(submitTime);
    }

    public void setSubmitTime(Instant submitTime) {
        this.submitTime = submitTime == null ? null : submitTime.toEpochMilli();
    }

    public long getBatchCount() {
        return batchCount;
    }

    public void setBatchCount(long batchCount) {
        this.batchCount = batchCount;
    }

    public void addProcessed(long processed) {
        if (this.processed == null) {
            this.processed = processed;
        } else {
            this.processed += processed;
        }
    }

    /**
     * For a full status returns the number of documents where the task has been applied so far.
     */
    public long getProcessed() {
        if (processed == null) {
            return 0;
        }
        return processed;
    }

    /**
     * Returns the epoch-millisecond timestamp when this task was last set to RUNNING, or {@code null} if not yet set.
     */
    public Long getLastExecutionTime() {
        return lastExecutionTime;
    }

    public void setLastExecutionTime(Long lastExecutionTime) {
        this.lastExecutionTime = lastExecutionTime;
    }

    /**
     * Returns true if command is completed.
     */
    @JsonIgnore
    public boolean isCompleted() {
        return getState() == State.COMPLETED;
    }

    /**
     * Checks if there is any error.
     *
     * @return {@code true} if there is any error, {@code false} otherwise
     */
    boolean hasError() {
        return getErrorMessage() != null;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(long errorCount) {
        this.errorCount = errorCount;
    }

    /**
     * Gets the error message if any.
     *
     * @return the error message if any, {@code null} otherwise
     * @since 11.5
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Gets the error code if any.
     *
     * @return the error code if any, {@code 0} otherwise
     */
    public int getErrorCode() {
        if (errorCode == null) {
            return 0;
        }
        return errorCode;
    }

    public void setErrorCode(Integer errorCode) {
        this.errorCode = errorCode;
    }

    public boolean isPublishedToTopic() {
        return publishedToTopic;
    }

    public void setPublishedToTopic(boolean publishedToTopic) {
        this.publishedToTopic = publishedToTopic;
    }

    /**
     * Possible states of a bulk taskName:
     */
    public enum State {
        /** The bulk taskName or the status is unknown. */
        UNKNOWN,

        /** The bulk taskName has been submitted to the service. */
        SCHEDULED,

        /** The scroller is running and materialize the document set. */
        SCROLLING_RUNNING,

        /** The scroller has terminated, the size of the document set is known and taskName is applied. */
        RUNNING,

        /** The taskName has been applied to the document set, the bulk taskName is completed. */
        COMPLETED,

        /** The bulk taskName has been aborted, the taskName might have been partially applied on the document set. */
        ABORTED
    }
}
