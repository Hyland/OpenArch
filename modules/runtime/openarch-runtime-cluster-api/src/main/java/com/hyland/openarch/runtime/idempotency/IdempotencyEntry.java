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
package com.hyland.openarch.runtime.idempotency;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hyland.openarch.runtime.idempotency.IdempotencyHandler.Outcome;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cached outcome of an idempotent operation.
 * <p>
 * A single record shape is used for every state a cache entry can be in:
 * <ul>
 * <li><b>pending</b> — the key has been claimed and the guarded operation is still running. The
 * {@code claimId} uniquely identifies the claim so that only its owner may publish a result.</li>
 * <li><b>success</b> — the operation completed; {@code value} holds the JSON representation of the
 * result and {@code typename} the registered key used to recover its result type.</li>
 * <li><b>failure</b> — the operation threw; {@code exception} holds the replayable details.</li>
 * </ul>
 *
 * @param pending   whether the operation is still in progress
 * @param claimId   unique token identifying the claim while {@code pending}, {@code null} otherwise
 * @param typename  the registered result-type key of {@code value}, or {@code null}
 * @param value     the JSON representation of the operation result, or {@code null}
 * @param failure the replayable failure details, or {@code null}
 */
public record IdempotencyEntry(@JsonProperty(PENDING_FIELD) boolean pending,
                               @JsonProperty(CLAIM_ID_FIELD) String claimId,
                               @JsonProperty(TYPENAME_FIELD) String typename,
                               @JsonProperty(VALUE_FIELD) JsonNode value,
                               @JsonProperty(FAILURE_FIELD) FailedOutcome failure) {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    public static final String CLAIM_ID_FIELD = "claimId";

    public static final String FAILURE_FIELD = "failure";

    public static final String PENDING_FIELD = "pending";

    public static final String TYPENAME_FIELD = "__typename";

    public static final String VALUE_FIELD = "value";

    /**
     * Creates a result representing an in-progress (claimed but unresolved) operation.
     *
     * @param claimId unique token identifying the claim
     * @return a new pending IdempotencyEntry
     */
    public static IdempotencyEntry pending(String claimId) {
        return new IdempotencyEntry(true, claimId, null, null, null);
    }

    /**
     * Creates a result representing a successful operation outcome.
     *
     * @param value the operation result
     * @return a new IdempotencyEntry with the given value
     */
    public static IdempotencyEntry success(JsonNode value, String typename) {
        return new IdempotencyEntry(false, null, typename, value, null);
    }

    /**
     * Creates a result representing a failed operation outcome.
     *
     * @param failedOutcome the replayable failure details
     * @return a new IdempotencyEntry with the given failure
     */
    public static IdempotencyEntry failure(FailedOutcome failedOutcome) {
        return new IdempotencyEntry(false, null, null, null, failedOutcome);
    }

    /**
     * Creates a result representing a failed operation outcome.
     *
     * @param exception the exception that occurred
     * @return a new IdempotencyEntry with the given exception
     */
    public static IdempotencyEntry failure(Exception exception) {
        FailedOutcome failedOutCome;
        if (exception instanceof RepositoryException re) {
            failedOutCome = new FailedOutcome(re.getClass().getName(), re.getMessage(), re.getStatusCode());
        } else {
            failedOutCome = new FailedOutcome(exception.getClass().getName(), exception.getMessage());
        }
        return new IdempotencyEntry(false, null, null, null, failedOutCome);
    }

    /**
     * Returns whether this result represents a successful outcome.
     */
    @JsonIgnore
    public boolean isSuccess() {
        return !pending && failure == null;
    }

    /**
     * Returns whether this result represents a failed outcome.
     */
    @JsonIgnore
    public boolean isFailure() {
        return !pending && failure != null;
    }

    /**
     * Deserializes a successful cached entry into an Outcome object containing either the result or the failure details.
     * <p>
     * For deserializing the value, the registered type is a Jackson {@code JavaType}, so a generic result such as a
     * {@code List<DocumentDTO>} is replayed with its element type preserved.
     *
     * @return the Outcome, carrying both the result and whether it was replayed
     * @throws IllegalArgumentException when the result type key is not registered or its JSON
     *                                  value cannot be deserialized
     */
    public <T> Outcome<T> unwrap() {
        if (isFailure()) {
            FailedOutcome failedOutCome = new FailedOutcome(failure.type(), failure.message(), failure.statusCode());
            return new Outcome<>(null, failedOutCome, true);
        }
        if (value == null || value.isNull()) {
            return null;
        }
        JavaType resultType = IdempotencyResultTypeRegistry.getType(typename);
        if (resultType == null) {
            throw new IllegalArgumentException("No idempotency result type is registered for key: " + typename);
        }
        try {
            T value =  OBJECT_MAPPER.treeToValue(this.value, resultType);
            return new Outcome<>(value, null, true);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to deserialize idempotency result type: " + typename, e);
        }
    }
}
