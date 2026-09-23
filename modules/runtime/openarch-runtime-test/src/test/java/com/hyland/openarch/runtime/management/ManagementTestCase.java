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
package com.hyland.openarch.runtime.management;

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.RestartFeature;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;

/**
 * @author matic
 */
@ExtendWith(FeaturesExtension.class)
@Features({ RuntimeFeature.class, RestartFeature.class })
@Deploy("com.hyland.openarch.runtime.management:OSGI-INF/management-server-locator-service.xml")
@Deploy("com.hyland.openarch.runtime.management:OSGI-INF/management-resource-publisher-service.xml")
@Deploy("org.nuxeo.runtime.test:isolated-server.xml")
public abstract class ManagementTestCase {

    protected ResourcePublisherService publisherService;

    protected ServerLocatorService locatorService;

    @BeforeEach
    public void setUp() throws Exception {
        locatorService = (ServerLocatorService) Framework.getService(ServerLocator.class);
        publisherService = (ResourcePublisherService) Framework.getService(ResourcePublisher.class);
    }

    protected Set<ObjectName> doQuery(String name) {
        String qualifiedName = ObjectNameFactory.getQualifiedName(name);
        ObjectName objectName = ObjectNameFactory.getObjectName(qualifiedName);
        MBeanServer server = locatorService.lookupServer(objectName.getDomain());
        return server.queryNames(objectName, null);
    }

}
