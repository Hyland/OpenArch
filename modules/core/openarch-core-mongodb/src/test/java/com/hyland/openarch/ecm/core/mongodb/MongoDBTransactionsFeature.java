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
package com.hyland.openarch.ecm.core.mongodb;

import com.hyland.openarch.ecm.core.storage.mongodb.MongoDBRepository;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesLoader;
import com.hyland.openarch.runtime.test.runner.RunnerFeature;

/**
 * Test feature enabling MongoDB sessions and transactions for the repository, which are otherwise opt-in.
 * <p>
 * The property must be set before the runtime starts, as the repository detects its capabilities when it is created.
 */
@Features(CoreFeature.class)
public class MongoDBTransactionsFeature implements RunnerFeature {

    @Override
    public void initialize(FeaturesLoader loader) {
        System.setProperty(MongoDBRepository.TRANSACTIONS_ENABLED_PROP, "true");
    }

    @Override
    public void uninitialize() {
        System.clearProperty(MongoDBRepository.TRANSACTIONS_ENABLED_PROP);
    }
}
