/*
 * (C) Copyright 2017-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.blob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.impl.blob.StringBlob;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
@ExtendWith(FeaturesExtension.class)
@Features(BlobManagerFeature.class)
@Deploy("com.hyland.openarch.ecm.core.api.tests:OSGI-INF/dummy-blob-provider.xml")
public class TestBlobManager {

    private static final String DUMMY = "dummy";

    @Inject
    protected BlobService blobService;

    @Test
    @Order(1)
    public void testDummyRegistration() throws Exception {
        BlobProvider dummyBlobProvider = blobService.getBlobProvider(DUMMY);
        assertNotNull(dummyBlobProvider);
    }

    @Test
    @Order(2)
    public void testGetBlobProviders() throws Exception {
        Map<String, BlobProvider> providers = blobService.getBlobProviders();
        assertEquals(6, providers.size());
    }

    @Test
    @Order(3)
    public void testDefaultAndNamespace() throws Exception {
        BlobProvider def = blobService.getBlobProvider("default");
        BlobProvider other1 = blobService.getBlobProviderWithNamespace("providerNotRegisteredInXML", "default");
        BlobProvider other2 = blobService.getBlobProviderWithNamespace("otherProviderNotRegisteredInXML", "otherdefault");
        assertNotNull(def);
        assertEquals(def.getClass(), other1.getClass());
        assertEquals(def.getClass(), other2.getClass());
        // check that the blob providers come from two different defaults
        assertEquals("main", other1.getProperties().get("kind"));
        assertEquals("other", other2.getProperties().get("kind"));
        // put a blob in the default one
        String key = def.writeBlob(new StringBlob("foo"));
        assertEquals("foo", readBlob(def, key));
        assertNull(readBlob(other1, key));
        assertNull(readBlob(other2, key));
        // put a blob in the first namespaced one
        String key2 = other1.writeBlob(new StringBlob("bar"));
        // make sure there's no key collision
        assertEquals("foo", readBlob(def, key));
        assertEquals("bar", readBlob(other1, key2));
        assertNull(readBlob(other2, key));
        assertNull(readBlob(other2, key2));
    }

    protected static String readBlob(BlobProvider blobProvider, String key) {
        try {
            BlobInfo blobInfo = new BlobInfo();
            blobInfo.setKey(key);
            Blob blob = blobProvider.readBlob(blobInfo);
            return blob.getString();
        } catch (IOException e) {
            assertEquals(e.getMessage(), "Unknown blob: " + key);
            return null;
        }
    }

}
