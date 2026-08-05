/*
 * (C) Copyright 2020-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.test;

import org.nuxeo.runtime.test.runner.FeaturesLoader;
import org.nuxeo.runtime.test.runner.RunnerFeature;

public class FulltextStoredInBlobFeature implements RunnerFeature {

    private static final String KEY = "nuxeo.test.fulltext.storedInBlob";

    private String flag;

    @Override
    public void initialize(FeaturesLoader loader) {
        flag = System.setProperty(KEY, "true");
    }

    @Override
    public void uninitialize() {
        if (flag == null) {
            System.clearProperty(KEY);
        } else {
            System.setProperty(KEY, flag);
        }
    }

}
