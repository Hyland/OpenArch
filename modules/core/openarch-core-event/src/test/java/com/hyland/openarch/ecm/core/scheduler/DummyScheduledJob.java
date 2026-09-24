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

import java.util.concurrent.atomic.AtomicLong;

public class DummyScheduledJob implements ScheduledJob {

    protected static AtomicLong count = new AtomicLong(0);

    @Override
    public void run() {
        count.incrementAndGet();
    }

    protected static void setCount(long val) {
        count.set(val);
    }

    protected static long getCount() {
        return count.get();
    }

}
