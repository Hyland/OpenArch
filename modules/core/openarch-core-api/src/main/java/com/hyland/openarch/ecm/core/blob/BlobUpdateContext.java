/*
 * (C) Copyright 2019-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.blob;

import java.time.Duration;
import java.time.Instant;

/**
 * Context available when a blob is updated in a blob provider.
 *
 * @since 11.1
 */
public class BlobUpdateContext {

    /** The blob key, without blob provider prefix. */
    public final String key;

    private UpdateRetainUntil updateRetainUntil;

    private UpdateLegalHold updateLegalHold;

    private RestoreForDuration restoreForDuration;

    public BlobUpdateContext(String key) {
        this.key = key;
    }

    public BlobUpdateContext with(BlobUpdateContext other) {
        if (other.updateRetainUntil != null) {
            updateRetainUntil = other.updateRetainUntil;
        }
        if (other.updateLegalHold != null) {
            updateLegalHold = other.updateLegalHold;
        }
        if (other.restoreForDuration != null) {
            restoreForDuration = other.restoreForDuration;
        }
        return this;
    }

    public BlobUpdateContext withUpdateRetainUntil(Instant retainUntil) {
        updateRetainUntil = new UpdateRetainUntil(retainUntil);
        return this;
    }

    public BlobUpdateContext withUpdateLegalHold(boolean hold) {
        updateLegalHold = new UpdateLegalHold(hold);
        return this;
    }

    public BlobUpdateContext withRestoreForDuration(Duration duration) {
        restoreForDuration = new RestoreForDuration(duration);
        return this;
    }

    public UpdateRetainUntil getUpdateRetainUntil() {
        return updateRetainUntil;
    }

    public UpdateLegalHold getUpdateLegalHold() {
        return updateLegalHold;
    }

    public RestoreForDuration getRestoreForDuration() {
        return restoreForDuration;
    }

    public static class UpdateRetainUntil {

        public final Instant retainUntil;

        public UpdateRetainUntil(Instant retainUntil) {
            this.retainUntil = retainUntil;
        }

    }

    public static class UpdateLegalHold {

        public final boolean hold;

        public UpdateLegalHold(boolean hold) {
            this.hold = hold;
        }

    }

    public static class RestoreForDuration {

        public final Duration duration;

        public RestoreForDuration(Duration duration) {
            this.duration = duration;
        }

    }

}
