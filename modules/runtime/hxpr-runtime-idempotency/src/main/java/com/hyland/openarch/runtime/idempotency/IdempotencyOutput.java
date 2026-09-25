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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
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
 * result and {@code typename} the concrete class it must be converted back to.</li>
 * <li><b>failure</b> — the operation threw; {@code exception} holds the replayable details.</li>
 * </ul>
 *
 * @param pending   whether the operation is still in progress
 * @param claimId   unique token identifying the claim while {@code pending}, {@code null} otherwise
 * @param typename  the concrete class name of {@code value}, or {@code null}
 * @param value     the JSON representation of the operation result, or {@code null}
 * @param exception the replayable failure details, or {@code null}
 */
public record IdempotencyOutput(boolean pending, String claimId, @JsonProperty("__typename") String typename,
        JsonNode value, IdempotencyException exception) {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyOutput.class);

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    /**
     * Creates a result representing an in-progress (claimed but unresolved) operation.
     *
     * @param claimId unique token identifying the claim
     * @return a new pending IdempotencyOutput
     */
    public static IdempotencyOutput pending(String claimId) {
        return new IdempotencyOutput(true, claimId, null, null, null);
    }

    /**
     * Creates a result representing a successful operation outcome.
     *
     * @param value the operation result
     * @return a new IdempotencyOutput with the given value
     */
    public static IdempotencyOutput success(JsonNode value, String typename) {
        return new IdempotencyOutput(false, null, typename, value, null);
    }

    /**
     * Creates a result representing a failed operation outcome.
     *
     * @param exception the exception that occurred
     * @return a new IdempotencyOutput with the given exception
     */
    public static IdempotencyOutput failure(Exception exception) {
        IdempotencyException idempotencyException;
        if (exception instanceof NuxeoException nuxeoException) {
            idempotencyException = new IdempotencyException(nuxeoException.getClass().getName(), nuxeoException.getMessage(), nuxeoException.getStatusCode());
        } else {
            idempotencyException = new IdempotencyException(exception.getClass().getName(), exception.getMessage());
        }
        return new IdempotencyOutput(false, null, null, null, idempotencyException);
    }

    /**
     * Returns whether this result represents a successful outcome.
     */
    public boolean isSuccess() {
        return !pending && exception == null;
    }

    /**
     * Returns whether this result represents a failed outcome.
     */
    public boolean isFailure() {
        return !pending && exception != null;
    }

    public Object unwrap() {
        if (isFailure()) {
            return new IdempotencyException(exception.key(), exception.message(), exception.httpErrorCode());
        } else {
            if (value == null || value.isNull()) {
                return null;
            }

            JsonNode typenameNode = value.get("__typename");
            if (typenameNode == null) {
                log.debug("No __typename found in node, returning raw data");
                return value;
            }

            String typename = typenameNode.asString();
            if (StringUtils.isBlank(typename)) {
                log.debug("Empty __typename found in node, returning raw data");
                return value;
            }
            try {
                return OBJECT_MAPPER.treeToValue(value, Class.forName(typename));
            } catch (JacksonException | ClassNotFoundException e) {
                log.debug("Failed to deserialize value {}, returning raw data", typename, e);
                return value;
            }
        }
    }
}
