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
package org.nuxeo.ecm.core;

import org.nuxeo.ecm.core.test.FulltextDisabledFeature;
import com.hyland.openarch.runtime.springboot.junit.RuntimeTestState;
import com.hyland.openarch.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = RuntimeTestState.class)
@Features(FulltextDisabledFeature.class)
public class TestFulltextDisabledNoQuery extends TestFulltextAbstractNoQuery {

    @Override
    protected boolean expectBinaryText() {
        // no binary text available when fulltext is fully disabled
        return false;
    }

}
