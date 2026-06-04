/*
 * (C) Copyright 2006-2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.storage.dbs.DBSHelper;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.mongodb.MongoDBConnectionConfig;
import org.nuxeo.runtime.mongodb.MongoDBConnectionHelper;
import org.nuxeo.runtime.test.runner.FeaturesLoader;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.RuntimeHarness;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;

/**
 * Description of the specific capabilities of a repository for tests, and helper methods.
 *
 * @since 7.3
 */
public class StorageConfiguration {

    private static final Logger log = LogManager.getLogger(StorageConfiguration.class);

    public static final String CORE_PROPERTY = "nuxeo.test.core";

    public static final String CORE_MEM = "mem";

    public static final String CORE_MONGODB = "mongodb";

    public static final String DEFAULT_CORE = CORE_MONGODB;

    private static final String REPOSITORY_NAME = "test";

    private String coreType;

    private DBSHelper dbsHelper;

    final CoreFeature feature;

    public StorageConfiguration(CoreFeature feature) {
        coreType = defaultSystemProperty(CORE_PROPERTY, DEFAULT_CORE);
        this.feature = feature;
    }

    public static String defaultSystemProperty(String name, String def) {
        String value = System.getProperty(name);
        if (value == null || value.equals("") || value.equals("${" + name + "}")) {
            value = def;
            System.setProperty(name, value);
        }
        return value;
    }

    protected static String defaultProperty(String name, String def) {
        String value = System.getProperty(name);
        if (value == null || value.equals("") || value.equals("${" + name + "}")) {
            value = def;
        }
        Framework.getProperties().setProperty(name, value);
        return value;
    }

    protected void init() {
        switch (coreType) {
        case CORE_MEM:
            break;
        case CORE_MONGODB:
            initMongoDB();
            break;
        default:
            initExternal();
        }
    }

    protected void initMongoDB() {
        runWithMongoDatabase(MongoDatabase::drop);
    }

    public void runWithMongoDatabase(Consumer<MongoDatabase> dbConsumer) {
        MongoDBConnectionConfig config = MongoDBConnectionHelper.getMongoClientConfig();
        try (MongoClient mongoClient = MongoDBConnectionHelper.newMongoClient(config)) {
            MongoDatabase db = mongoClient.getDatabase(config.dbname);
            dbConsumer.accept(db);
        }
    }

    protected void initExternal() {
        // Get DBSHelper by reflection
        String className = String.format("org.nuxeo.ecm.core.storage.%s.DBSHelperImpl", coreType);
        try {
            dbsHelper = (DBSHelper) Class.forName(className).getDeclaredConstructor().newInstance();
            dbsHelper.init();
        } catch (ReflectiveOperationException e) {
            throw new NuxeoException("DBSHelperImpl not found: " + className, e);
        }
    }

    public boolean isDBSMem() {
        return CORE_MEM.equals(coreType);
    }

    public boolean isDBSMongoDB() {
        return CORE_MONGODB.equals(coreType);
    }

    public boolean isDBSExternal() {
        return dbsHelper != null;
    }

    public String getRepositoryName() {
        return REPOSITORY_NAME;
    }

    /**
     * For databases that do asynchronous fulltext indexing, sleep a bit.
     */
    public void sleepForFulltext() {
        // nothing
    }

    /**
     * Sleep a bit to get to the next millisecond, to have different timestamps.
     */
    public void maybeSleepToNextSecond() {
        try {
            Thread.sleep(10); // 10 milliseconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // restore interrupted status
            throw new RuntimeException(e);
        }
    }

    public void waitForAsyncCompletion() {
        feature.waitForAsyncCompletion();
    }

    public void waitForFulltextIndexing() {
        waitForAsyncCompletion();
        sleepForFulltext();
    }

    public List<String> getExternalBundles() {
        if (isDBSExternal()) {
            return Arrays.asList(String.format("org.nuxeo.ecm.core.storage.%s", coreType),
                    String.format("org.nuxeo.ecm.core.storage.%s.test", coreType));
        }
        return Collections.emptyList();
    }

    public URL getBlobManagerContrib(FeaturesLoader loader) {
        String bundleName = "org.nuxeo.ecm.core.test";
        String contribPath = "OSGI-INF/test-storage-blob-contrib.xml";
        RuntimeHarness harness = loader.getFeature(RuntimeFeature.class).getHarness();
        URL contribURL = harness.getOSGiAdapter().getBundleEntry(bundleName, contribPath);
        assertNotNull(contribURL, "deployment contrib " + contribPath + " not found");
        return contribURL;
    }

    public URL getRepositoryContrib(FeaturesLoader loader) {
        String msg = "Deploying a DBS repository using " + coreType;
        // System.out.println(getClass().getSimpleName() + ": " + msg);
        log.debug(msg);

        String contribPath;
        String bundleName = "org.nuxeo.ecm.core.test";
        if (isDBSMem()) {
            contribPath = "OSGI-INF/test-storage-repo-mem-contrib.xml";
        } else if (isDBSMongoDB()) {
            contribPath = "OSGI-INF/test-storage-repo-mongodb-contrib.xml";
        } else if (isDBSExternal()) {
            bundleName = String.format("org.nuxeo.ecm.core.storage.%s.test", coreType);
            contribPath = "OSGI-INF/test-storage-repo-contrib.xml";
        } else {
            throw new NuxeoException("Unkown DBS test configuration (not mem/mongodb)");
        }
        RuntimeHarness harness = loader.getFeature(RuntimeFeature.class).getHarness();
        URL contribURL = harness.getOSGiAdapter().getBundleEntry(bundleName, contribPath);
        assertNotNull(contribURL, "deployment contrib " + contribPath + " not found");
        return contribURL;
    }

    /**
     * @since 9.2
     */
    public String getCoreType() {
        return coreType;
    }

}
