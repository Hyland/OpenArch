/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/) and others.
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
package org.nuxeo.common.httpclient;

import org.apache.http.client.HttpRequestRetryHandler;

import java.io.IOException;
import java.time.Duration;

public class ExponentialBackoffRetryHandler implements HttpRequestRetryHandler {

    public static final Duration INITIAL_DELAY = Duration.ofMillis(100);

    public static final int MAX_TRY = 3;

    @Override
    public boolean retryRequest(IOException exception, int executionCount, org.apache.http.protocol.HttpContext context) {
        if (executionCount > MAX_TRY) {
            return false;
        }
        try {
            Thread.sleep(INITIAL_DELAY.multipliedBy(1 << (executionCount - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        return true;
    }
}
