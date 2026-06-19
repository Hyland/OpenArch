/*
 *  (C) Copyright 2026 Hyland (https://www.hyland.com/).
 *
 *  This is unpublished proprietary source code of Hyland. All rights reserved.
 *  Notice of copyright on this source code does not indicate publication.
 */

package org.nuxeo.rest.user;

import org.nuxeo.ecm.core.api.local.DummyLoginFeature;
import org.nuxeo.ecm.platform.login.filestore.UserManagerFileStoreFeature;
import org.nuxeo.ecm.platform.login.filestore.UserManagerFileStoreTestConfiguration;
import org.nuxeo.rest.exception.NuxeoExceptionController;
import org.nuxeo.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Community variant of {@link AbstractGroupAPITest} — backed by the file based usermanager via
 * {@link UserManagerFileStoreTestConfiguration}.
 */
@Features({ UserManagerFileStoreFeature.class, DummyLoginFeature.class })
@SpringBootTest(classes = { GroupAPI.class, NuxeoExceptionController.class,
        UserManagerFileStoreTestConfiguration.class })
class GroupAPIFileStoreTest extends AbstractGroupAPITest {

    @Override
    protected int minGroupSize() {
        return 5;
    }
}
