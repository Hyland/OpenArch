/*
 * (C) Copyright 2015-2026 Hyland (https://www.hyland.com/).
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

package org.nuxeo.ecm.core.security;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.local.DummyLoginFeature.TEST_ADMIN_USER;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FOLDER;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.runtime.mockito.RuntimeInject;
import org.nuxeo.runtime.springboot.junit.RuntimeTestState;
import org.springframework.boot.test.context.SpringBootTest;

import com.hyland.hxpr.async.tasks.AbstractTestAsyncTask;
import com.hyland.hxpr.async.tasks.TaskService;

/**
 * @since 7.4
 */
@SpringBootTest(classes = RuntimeTestState.class)
public class TestACEStatusUpdate extends AbstractTestAsyncTask {

    @RuntimeInject
    protected TaskService asyncTasksService;

    @Test
    public void shouldUpdateStatusToEffective() throws InterruptedException {
        DocumentModel doc = session.createDocumentModel("/", "folder", SYS_FOLDER);
        doc.create();

        Instant begin = Instant.now().plus(1, SECONDS);
        ACL acl = doc.getLocalACL();
        ACE ace = ACE.builder()
                     .user("leela")
                     .permission(READ)
                     .creator(TEST_ADMIN_USER)
                     .begin(begin)
                     .build();
        acl.add(ace);
        doc.setACL(acl, true);

        ACE leelaACE = getACEFor(acl, "leela");
        assertNotNull(leelaACE);
        assertTrue(leelaACE.isPending());

        // check that the ACE is pending
        doc = session.getDocument(doc.getRef());
        acl = doc.getLocalACL();
        leelaACE = getACEFor(acl, "leela");
        assertNotNull(leelaACE);
        assertTrue(leelaACE.isPending());

        // wait for the ACE to be effective
        runUpdateACEStatusScheduledJobAndWait();

        doc = session.getDocument(doc.getRef());
        acl = doc.getLocalACL();
        leelaACE = getACEFor(acl, "leela");
        assertNotNull(leelaACE);
        assertTrue(leelaACE.isEffective());
    }

    protected ACE getACEFor(ACL acl, String username) {
        ACE foundACE = null;
        for (ACE ace : acl) {
            if ((ACE.USER_PREFIX + username).equals(ace.getPrincipal())) {
                foundACE = ace;
            }
        }
        return foundACE;
    }

    protected void runUpdateACEStatusScheduledJobAndWait() throws InterruptedException {
        new UpdateACEStatusScheduledJob().run();
        await().atMost(Duration.ofSeconds(5L))
                .with()
                .pollInterval(Duration.ofSeconds(1L))
                .until(() -> asyncTasksService.awaitCompletion(Duration.ofMillis(200L)));
    }

    @Test
    public void shouldUpdateStatusToArchived() throws InterruptedException {
        DocumentModel doc = session.createDocumentModel("/", "folder", SYS_FOLDER);
        doc.create();

        Instant begin = Instant.now().minus(5, DAYS);
        Instant end = Instant.now().plus(1, SECONDS);
        ACL acl = doc.getLocalACL();
        ACE ace = ACE.builder()
                     .user("leela")
                     .permission(READ)
                     .creator(TEST_ADMIN_USER)
                     .begin(begin)
                     .end(end)
                     .build();
        acl.add(ace);
        doc.setACL(acl, true);

        ACE leelaACE = getACEFor(acl, "leela");
        assertNotNull(leelaACE);
        assertTrue(leelaACE.isEffective());

        // Before the ACE update, the ACE is still effective
        doc = session.getDocument(doc.getRef());
        acl = doc.getLocalACL();
        leelaACE = getACEFor(acl, "leela");
        assertNotNull(leelaACE);
        assertTrue(leelaACE.isEffective());

        // wait for the ACE to be effective
        runUpdateACEStatusScheduledJobAndWait();

        doc = session.getDocument(doc.getRef());
        acl = doc.getLocalACL();
        leelaACE = getACEFor(acl, "leela");
        assertNotNull(leelaACE);
        assertTrue(leelaACE.isArchived());
    }
}
