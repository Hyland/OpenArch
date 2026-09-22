/*
 * (C) Copyright 2022-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import org.nuxeo.ecm.core.api.ConcurrentUpdateException;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.SchemaManagerDTO;
import org.nuxeo.ecm.core.schema.TypeService;
import org.nuxeo.ecm.core.schema.TypeService.ModelWithVersion;
import org.nuxeo.ecm.core.schema.dto.ContentModelDTO;
import org.nuxeo.ecm.core.schema.dto.ModelMapCodec;
import org.nuxeo.ecm.core.schema.validation.SchemaValidationUtils;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.runtime.api.Framework;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.Jackson3JsonPatch;

import jakarta.validation.constraints.NotNull;

@Component
public class ModelManager {

    private static final ObjectMapper mapper = new ObjectMapper();

    /** Maximum attempts for the atomic patch read-modify-write before surfacing the conflict. */
    private static final int MAX_PATCH_ATTEMPTS = 5;

    /** Initial backoff before the first patch retry; doubled on each subsequent attempt (with jitter). */
    private static final Duration PATCH_RETRY_INITIAL_DELAY = Duration.ofMillis(50);

    /**
     * Applies a JSON patch to the current content model atomically and persists it.
     * <p>
     * The model is read together with its version, the patch is applied to that snapshot, and the result is written
     * back conditionally on that version. If a concurrent write moved the version in between, the operation backs off
     * then re-reads and re-applies the patch (up to {@value #MAX_PATCH_ATTEMPTS} times), so a patch never silently
     * loses a concurrent update. This is entirely transparent to the caller: no version is sent in the patch or
     * returned. After exhausting the retries the last {@link ConcurrentUpdateException} (HTTP 409) is surfaced.
     *
     * @param repoId the repository id
     * @param patchBody the JSON patch
     * @param validateOnly whether to only validate (no persist)
     */
    public void applyPatch(String repoId, String patchBody, boolean validateOnly) {
        JsonNode patch = mapper.readTree(patchBody);
        ConcurrentUpdateException lastConflict = null;
        for (int attempt = 1; attempt <= MAX_PATCH_ATTEMPTS; attempt++) {
            ModelWithVersion snapshot = getModelWithVersion(repoId);
            // the patch applies to the model as the API renders it, i.e. to what GET returns
            JsonNode patched = Jackson3JsonPatch.apply(patch, mapper.valueToTree(snapshot.model()));
            // read the patch result ourselves rather than letting Jackson bind it, so that a value of
            // the wrong shape is reported as a validation violation instead of a binding failure
            List<ValidationViolation> violations = new ArrayList<>();
            Map<String, Object> patchedMap = mapper.convertValue(patched, Map.class);
            ContentModelDTO model = ModelMapCodec.read(patchedMap, violations);
            // diff against the patch result as it stands, not against the model read out of it: an entry the reader had
            // to drop still counts as a modification, not as a removal. The base is the same snapshot the patch was
            // applied to, not the possibly-stale local model.
            SchemaManager schemaManager = createSchemaManager(snapshot.model(), model, patchedMap, violations,
                    validateOnly);
            if (validateOnly) {
                return;
            }
            try {
                persistSchemaManager(repoId, schemaManager, snapshot.version());
                return;
            } catch (ConcurrentUpdateException e) {
                // a concurrent write moved the version between our read and write; back off, then re-read and re-apply
                lastConflict = e;
                if (attempt < MAX_PATCH_ATTEMPTS) {
                    backoffBeforePatchRetry(attempt);
                }
            }
        }
        throw lastConflict;
    }

    /**
     * Waits before the next patch retry using exponential backoff with jitter. The base delay follows the algorithm of
     * ExponentialBackoffRetryHandler (doubling from {@link #PATCH_RETRY_INITIAL_DELAY} each attempt); the jitter
     * decorrelates competing writers so two pods that just collided don't retry in lockstep and collide again.
     *
     * @param attempt the 1-based number of the attempt that just failed
     */
    private static void backoffBeforePatchRetry(int attempt) {
        long base = PATCH_RETRY_INITIAL_DELAY.toMillis() << (attempt - 1);
        // equal jitter: half the base plus a random share of the other half
        long delay = base / 2 + ThreadLocalRandom.current().nextLong(base / 2 + 1);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    ContentModelDTO getContentModel(String repoId) {
        return getTypeService().getSchemaManager(repoId).toContentModel();
    }

    ContentModelDTO getContentModel(String repoId, @NotNull String projectId) {
        return getContentModel(repoId).filterByProject(projectId);
    }

    /**
     * Persists the schema manager for a v1 (version-unaware) write: last-write-wins on content. The storage layer
     * atomically bumps the version on every write, so the counter stays monotonic across v1 and v2 writes (a concurrent
     * v2 writer holding an older version then still gets a 409); no version is sent or returned, so this is transparent
     * to v1 callers.
     */
    void persistSchemaManager(String repoId, SchemaManager schemaManager) {
        getTypeService().setSchemaManager(repoId, schemaManager);
    }

    /**
     * Persists the schema manager using optimistic locking. The resulting version is read back with
     * {@link #getModelWithVersion}, which reports it together with the model it describes.
     *
     * @param repoId the repository id
     * @param schemaManager the schema manager to persist
     * @param expectedVersion the version the caller last read
     */
    void persistSchemaManager(String repoId, SchemaManager schemaManager, long expectedVersion) {
        getTypeService().setSchemaManager(repoId, schemaManager, expectedVersion);
    }

    /**
     * Returns the persisted model together with its optimistic-locking version (a consistent snapshot).
     */
    ModelWithVersion getModelWithVersion(String repoId) {
        return getTypeService().getModelWithVersion(repoId);
    }

    private TypeService getTypeService() {
        return Framework.getService(TypeService.class);
    }

    /**
     * Returns a consolidated content model for the proposed update specific to a project: every
     * item currently belonging to {@code projectId} is dropped, and the contributed ones are added
     * in its place, stamped with that project.
     *
     * @param current the existing content model
     * @param contributed the content model from the request made for a project
     * @param projectId the one associated with the proposed update
     * @return the consolidated content model for update
     */
    public ContentModelDTO createConsolidatedContentModel(ContentModelDTO current, ContentModelDTO contributed,
            String projectId) {
        Objects.requireNonNull(projectId);
        return current.withProjectItems(projectId, contributed);
    }

    /**
     * Builds a {@link SchemaManager} from {@code model}, validating the diff against the given persisted {@code base}
     * instead of the node-local model. Used by the optimistic-locking writes, which hold the snapshot their write is
     * conditioned on: the local model can lag behind the persisted one while a cluster invalidation is in flight, and
     * diffing against it would describe a removal or an incompatible change as an addition and let it through.
     */
    SchemaManager createSchemaManager(ContentModelDTO base, ContentModelDTO model, boolean validateOnly) {
        return createSchemaManager(base, model, ModelMapCodec.write(model), new ArrayList<>(), validateOnly);
    }

    /**
     * Views an already-persisted model as a {@link SchemaManager}, to check a request against that exact state rather
     * than against the node-local model. Lenient about the model itself: it is existing content, not a proposed update.
     */
    SchemaManager asSchemaManager(ContentModelDTO model) {
        return new SchemaManagerDTO(model, new ArrayList<>(), true);
    }

    /**
     * Builds a {@link SchemaManager} from {@code model}, validating the diff against {@code base} — the exact model the
     * update was computed from. Callers that read a model snapshot (e.g. the atomic patch) pass that same snapshot, so
     * validation and application share one base rather than re-reading a possibly-stale local model.
     *
     * @param base the model to diff against
     * @param model the requested model
     * @param requestedMap the request as it stands, which is what the diff reports values from
     */
    private SchemaManager createSchemaManager(ContentModelDTO base, ContentModelDTO model,
            Map<String, Object> requestedMap, List<ValidationViolation> violations, boolean validateOnly) {
        SchemaValidationUtils.validateDTODiff(ModelMapCodec.write(base), requestedMap, violations);
        return new SchemaManagerDTO(model, violations, validateOnly);
    }
}
