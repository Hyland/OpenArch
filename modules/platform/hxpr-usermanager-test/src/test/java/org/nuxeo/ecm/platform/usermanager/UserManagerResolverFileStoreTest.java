/*
 *  (C) Copyright 2026 Hyland (https://www.hyland.com/).
 *
 *  This is unpublished proprietary source code of Hyland. All rights reserved.
 *  Notice of copyright on this source code does not indicate publication.
 */

package org.nuxeo.ecm.platform.usermanager;

import org.nuxeo.ecm.platform.login.filestore.UserManagerFileStoreFeature;
import org.nuxeo.ecm.platform.login.filestore.UserManagerFileStoreTestConfiguration;
import org.nuxeo.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests for {@link UserManagerResolver} backed by the community file-based
 * {@link org.nuxeo.ecm.platform.login.filestore.UserManagerFileStore}.
 */
@Features({ UserManagerFileStoreFeature.class })
@SpringBootTest(classes = { UserManagerFileStoreTestConfiguration.class })
public class UserManagerResolverFileStoreTest extends AbstractUserManagerResolverTestCase {

}
