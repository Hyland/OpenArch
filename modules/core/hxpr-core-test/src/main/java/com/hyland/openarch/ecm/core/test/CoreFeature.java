/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.test;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.VocabularyConstants.VOCABULARY_REPOSITORY_SUFFIX;
import static com.hyland.openarch.ecm.core.model.InternalSession.PROP_ALLOW_DELETE_UNDELETABLE_DOCUMENTS;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.IterableQueryResult;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.PathRef;
import com.hyland.openarch.ecm.core.api.local.DummyLoginFeature;
import com.hyland.openarch.ecm.core.api.security.ACL;
import com.hyland.openarch.ecm.core.api.validation.DocumentValidationException;
import com.hyland.openarch.ecm.core.repository.RepositoryService;
import com.hyland.openarch.ecm.core.test.annotations.Granularity;
import com.hyland.openarch.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.ecm.core.test.annotations.RepositoryInit;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.cluster.BasicClusterFeature;
import com.hyland.openarch.runtime.test.runner.Defaults;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesLoader;
import com.hyland.openarch.runtime.test.runner.RunnerFeature;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;
import com.hyland.openarch.runtime.test.runner.RuntimeHarness;
import com.hyland.openarch.runtime.test.runner.TransactionalFeature;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

import com.google.inject.Binder;
import com.hyland.openarch.async.tasks.TaskService;

/**
 * The core feature provides a default {@link CoreSession} that can be injected.
 * <p>
 * The provided {@link CoreSession} is opened for the principal logged in by the {@link DummyLoginFeature}.
 * <p>
 * In addition, by injecting the feature itself, some helper methods are available to open new sessions.
 */
@Deploy("com.hyland.openarch.runtime.management")
@Deploy("com.hyland.openarch.runtime.metrics")
@Deploy("com.hyland.openarch.runtime.pubsub")
@Deploy("com.hyland.openarch.config")
@Deploy("org.nuxeo.runtime.mongodb")
@Deploy("com.hyland.openarch.ecm.core.schema")
@Deploy("com.hyland.openarch.ecm.core.query")
@Deploy("com.hyland.openarch.ecm.core.api")
@Deploy("com.hyland.openarch.async.tasks")
@Deploy("org.nuxeo.ecm.core")
@Deploy("org.nuxeo.ecm.core.io")
@Deploy("org.nuxeo.ecm.core.cache")
@Deploy("org.nuxeo.ecm.core.test")
@Deploy("org.nuxeo.ecm.core.storage.mem")
@Deploy("com.hyland.openarch.runtime.kv") // for async tasks feature
@Deploy("org.nuxeo.ecm.core.event")
@Deploy("org.nuxeo.ecm.core.event.test")
@Deploy("org.nuxeo.ecm.core.storage.mongodb:OSGI-INF/mongodb-repository-service.xml")
// don't deploy mongodb-repository-config.xml containing default repo
@RepositoryConfig(cleanup = Granularity.METHOD)
@Features({ BasicClusterFeature.class, //
        DummyLoginFeature.class, //
        TransactionalFeature.class })
public class CoreFeature implements RunnerFeature {

    private static final Logger log = LogManager.getLogger(CoreFeature.class);

    protected ACL rootAcl;

    protected FeaturesLoader loader;

    protected StorageConfiguration storageConfiguration;

    protected RepositoryInit repositoryInit;

    protected Granularity granularity;

    // this value gets injected
    protected CoreSession session;

    protected boolean cleaned;

    // no injection on features because we need it during beforeRun (injection has not been done at this stage)
    protected TransactionalFeature txFeature;

    protected DummyLoginFeature loginFeature;

    public StorageConfiguration getStorageConfiguration() {
        return storageConfiguration;
    }

    @Override
    public void initialize(FeaturesLoader loader) {
        this.loader = loader;
        loader.getFeature(TransactionalFeature.class)
              .addWaiter(duration -> Framework.getService(TaskService.class).awaitCompletion(duration));

        storageConfiguration = new StorageConfiguration(this);
        txFeature = loader.getFeature(TransactionalFeature.class);
        loginFeature = loader.getFeature(DummyLoginFeature.class);
        // init from RepositoryConfig annotations
        RepositoryConfig repositoryConfig = loader.getConfig(RepositoryConfig.class);
        if (repositoryConfig == null) {
            repositoryConfig = Defaults.of(RepositoryConfig.class);
        }
        try {
            repositoryInit = repositoryConfig.init().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RepositoryException(e);
        }
        Granularity cleanup = repositoryConfig.cleanup();
        granularity = cleanup == Granularity.UNDEFINED ? Granularity.CLASS : cleanup;
    }

    public Granularity getGranularity() {
        return granularity;
    }

    @Override
    public void start() {
        try {
            RuntimeHarness harness = loader.getFeature(RuntimeFeature.class).getHarness();
            storageConfiguration.init();
            for (String bundle : storageConfiguration.getExternalBundles()) {
                try {
                    harness.deployBundle(bundle);
                } catch (Exception e) {
                    throw new RepositoryException(e);
                }
            }
            URL blobContribUrl = storageConfiguration.getBlobManagerContrib(loader);
            harness.getContext().deploy(blobContribUrl);
            URL repoContribUrl = storageConfiguration.getRepositoryContrib(loader);
            harness.getContext().deploy(repoContribUrl);
        } catch (IOException e) {
            throw new RepositoryException(e);
        }
    }

    @Override
    public void beforeRun() {
        // wait for async tasks that may have been triggered by
        // RuntimeFeature (typically repo initialization)
        txFeature.nextTransaction(Duration.ofSeconds(10));
        if (granularity != Granularity.METHOD) {
            // we need a transaction to properly initialize the session
            // but it hasn't been started yet by TransactionalFeature
            TransactionHelper.startTransaction();
            initializeSession(); // create the session for us
            TransactionHelper.commitOrRollbackTransaction();
        }
    }

    @Override
    public void configure(Binder binder) {
        binder.bind(CoreSession.class).toProvider(() -> session);
    }

    @Override
    public void afterRun() {
        waitForAsyncCompletion(); // fulltext and various async tasks
        if (granularity != Granularity.METHOD) {
            cleanupSession(); // close the session for us

        }
    }

    @Override
    public void beforeSetup(Method method, Object test) {
        if (granularity == Granularity.METHOD) {
            initializeSession(); // create the session for us
        }
    }

    @Override
    public void afterTeardown(Method method, Object test) {
        if (session != null && TransactionHelper.isTransactionActive()) {
            // an explicit session save must be done before transaction commit
            try {
                session.save();
            } catch (DocumentValidationException e) {
                // ignore
            }
        }
        waitForAsyncCompletion();
        if (granularity == Granularity.METHOD) {
            cleanupSession(); // close the session for us
        }
    }

    public void waitForAsyncCompletion() {
        txFeature.nextTransaction();
    }

    protected void cleanupSession() {
        releaseCoreSession(); // release session for tests
        cleanupRepository(getRepositoryName()); // Cleanup test repository
        cleanupRepository(getRepositoryName() + VOCABULARY_REPOSITORY_SUFFIX); // Cleanup test vocabularies repo
        cleaned = true;
    }

    protected void cleanupRepository(String repositoryName) {
        // open an administrator session to cleanup repository
        TransactionHelper.runInNewTransaction(() -> {
            try {
                CoreSession adminSession = getCoreSession(repositoryName, createAdministrator());
                log.trace("remove everything except root");
                // remove placeful docs (including versions)
                Framework.getProperties().put(PROP_ALLOW_DELETE_UNDELETABLE_DOCUMENTS, "true");
                try {
                    adminSession.removeChildren(new PathRef("/"));
                    adminSession.save();
                    waitForAsyncCompletion();
                } finally {
                    Framework.getProperties().remove(PROP_ALLOW_DELETE_UNDELETABLE_DOCUMENTS);
                }
                // remove remaining placeless documents
                try (IterableQueryResult results = adminSession.queryAndFetch("SELECT sys_id FROM SysContent, SysRelation")) {
                    batchRemoveDocuments(adminSession, results);
                }
                adminSession.save();
                waitForAsyncCompletion();

                // set original ACL on root
                DocumentModel root = adminSession.getRootDocument();
                root.setACL(rootAcl, true);

                adminSession.save();
                waitForAsyncCompletion();
                if (!adminSession.query("SELECT * FROM SysContent, SysRelation").isEmpty()) {
                    log.error("Fail to cleanupSession, repository will not be empty for the next test.");
                }
            } catch (RepositoryException e) {
                log.error("Unable to reset repository", e);
            } finally {
                CoreScope.INSTANCE.exit();
            }
        });
    }

    protected void batchRemoveDocuments(CoreSession adminSession, IterableQueryResult results) {
        String rootDocumentId = adminSession.getRootDocument().getId();
        List<DocumentRef> ids = new ArrayList<>();
        for (Map<String, Object> result : results) {
            String id = (String) result.get(SYS_ID);
            if (id.equals(rootDocumentId)) {
                continue;
            }
            ids.add(new IdRef(id));
            if (ids.size() >= 100) {
                batchRemoveDocuments(adminSession, ids);
                ids.clear();
            }
        }
        if (!ids.isEmpty()) {
            batchRemoveDocuments(adminSession, ids);
        }
    }

    protected void batchRemoveDocuments(CoreSession adminSession, List<DocumentRef> ids) {
        List<DocumentRef> deferredIds = new ArrayList<>();
        for (DocumentRef id : ids) {
            if (!adminSession.exists(id)) {
                continue;
            }
            if (adminSession.canRemoveDocument(id)) {
                adminSession.removeDocument(id);
            } else {
                deferredIds.add(id);
            }
        }
        adminSession.removeDocuments(deferredIds.toArray(new DocumentRef[0]));
    }

    protected void initializeSession() {
        if (cleaned) {
            // reinitialize repositories content
            RepositoryService repositoryService = Framework.getService(RepositoryService.class);
            repositoryService.initRepositories();
            cleaned = false;
        }
        CoreScope.INSTANCE.enter();
        // populate repository and get root ACL
        Framework.doPrivileged(() -> {
            CoreSession adminSession = getCoreSession(createAdministrator());
            if (repositoryInit != null) {
                repositoryInit.populate(adminSession);
                adminSession.save();
                waitForAsyncCompletion();
            }
            // save current root ACL
            rootAcl = adminSession.getRootDocument().getLocalACL();
        });
        // open session for test
        createCoreSession();
    }

    public String getRepositoryName() {
        return getStorageConfiguration().getRepositoryName();
    }

    public CoreSession getCoreSession(String username) {
        return getCoreSession(getRepositoryName(), DummyLoginFeature.getTestPrincipal(username, false));
    }

    public CoreSession getCoreSession(RepositoryPrincipal principal) {
        return getCoreSession(getRepositoryName(), principal);
    }

    public CoreSession getCoreSession(String repositoryName, RepositoryPrincipal principal) {
        return CoreInstance.getCoreSession(repositoryName, principal);
    }

    public CoreSession getCoreSessionCurrentUser() {
        return CoreInstance.getCoreSession(getRepositoryName());
    }

    public CoreSession getCoreSessionSystem() {
        return CoreInstance.getCoreSessionSystem(getRepositoryName());
    }

    public CoreSession createCoreSession() {
        RepositoryPrincipal principal = loginFeature.getPrincipal();
        session = CoreInstance.getCoreSession(getRepositoryName(), principal);
        return session;
    }

    public CoreSession getCoreSession() {
        return session;
    }

    public void releaseCoreSession() {
        if (session != null && TransactionHelper.isTransactionActive()) {
            // an explicit session save must be done before transaction commit
            session.save();
        }
        session = null;
    }

    public CoreSession reopenCoreSession() {
        releaseCoreSession();
        waitForAsyncCompletion();
        Framework.getService(RepositoryService.class).resetPool();
        createCoreSession();
        return session;
    }

    protected RepositoryPrincipal createAdministrator() {
        return DummyLoginFeature.getTestAdminPrincipal();
    }

}
