/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentModelList;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.local.DummyLoginFeature;
import com.hyland.openarch.ecm.core.api.security.ACE;
import com.hyland.openarch.ecm.core.api.security.ACL;
import com.hyland.openarch.ecm.core.api.security.SecurityConstants;
import com.hyland.openarch.ecm.core.schema.TypeConstants;
import com.hyland.openarch.ecm.core.security.expr.UserAttributeExpressionSecurityPolicy;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

import jakarta.inject.Inject;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
public class TestUserAttributeExpressionSecurityPolicy {

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected CoreSession session;

    protected DocumentModel doc;

    protected DocumentModel docWithoutACL;

    @BeforeEach
    public void setUp() {
        ACE grantAll = ACE.builder()
                          .principal(SecurityConstants.EVERYONE)
                          .permission(SecurityConstants.EVERYTHING)
                          .isGranted(true)
                          .build();
        // Create a document with permissive ACL so only the security policy affects access
        doc = session.createDocumentModel("/", "exprDoc", TypeConstants.SYS_FILE);
        ACL localACL = doc.getLocalACL();
        localACL.add(grantAll);
        doc.setACL(localACL, true);
        doc.create();

        // Create a document without ACL to check that security policy do not bypass access
        docWithoutACL = session.createDocumentModel("/", "exprDocWithoutACL", TypeConstants.SYS_FILE);
        docWithoutACL.create();

        session.save();
        doc.refresh();
        docWithoutACL.refresh();
    }

    @Test
    public void testAccessWhenExpressionTrue() {
        CoreSession testSession = coreFeature.getCoreSession(DummyLoginFeature.getTestPrincipal("userExprTrue", false));

        // Set external security expression on the principal to allow everything
        RepositoryPrincipal principal = testSession.getPrincipal();
        Map<String, Serializable> attrs = new HashMap<>();
        attrs.put(UserAttributeExpressionSecurityPolicy.SECURITY_EXPRESSION_ATTRIBUTE_NAME, "TRUE");
        principal.setUserAttributes(attrs);

        // Access by ID
        assertTrue(testSession.hasPermission(doc.getRef(), SecurityConstants.READ));

        // Access via query
        DocumentModelList docs = testSession.query("SELECT * FROM SysContent WHERE sys_id = '" + doc.getId() + "'");
        assertEquals(1, docs.size());
    }

    @Test
    public void testNoAccessWhenExpressionFalse() {
        CoreSession testSession = coreFeature.getCoreSession(
                DummyLoginFeature.getTestPrincipal("userExprFalse", false));

        // Set external security expression on the principal to deny everything
        RepositoryPrincipal principal = testSession.getPrincipal();
        Map<String, Serializable> attrs = new HashMap<>();
        attrs.put(UserAttributeExpressionSecurityPolicy.SECURITY_EXPRESSION_ATTRIBUTE_NAME, "FALSE");
        principal.setUserAttributes(attrs);

        // Access by ID
        assertFalse(testSession.hasPermission(doc.getRef(), SecurityConstants.READ));

        // Access via query
        DocumentModelList docs = testSession.query("SELECT * FROM SysContent WHERE sys_id = '" + doc.getId() + "'");
        assertEquals(0, docs.size());
    }

    @Test
    public void testAccessWhenPolicyNotApplied() {
        CoreSession testSession = coreFeature.getCoreSession(DummyLoginFeature.getTestPrincipal("userNoAttr", false));

        // No _SecurityExpression attribute set on principal -> policy not applied
        // Access by ID
        assertTrue(testSession.hasPermission(doc.getRef(), SecurityConstants.READ));

        // Access via query
        DocumentModelList docs = testSession.query("SELECT * FROM SysContent WHERE sys_id = '" + doc.getId() + "'");
        assertEquals(1, docs.size());
    }

    @Test
    public void testAccessWithDocumentPropertyValue() {
        CoreSession testSession = coreFeature.getCoreSession(DummyLoginFeature.getTestPrincipal("userDocProp", false));

        // Expression checks a specific document property value
        RepositoryPrincipal principal = testSession.getPrincipal();
        Map<String, Serializable> attrs = new HashMap<>();
        attrs.put(UserAttributeExpressionSecurityPolicy.SECURITY_EXPRESSION_ATTRIBUTE_NAME,
                "content.sys_title == \"exprDoc\"");
        principal.setUserAttributes(attrs);

        // Access by ID
        assertTrue(testSession.hasPermission(doc.getRef(), SecurityConstants.READ));

        // Access via query
        DocumentModelList docs = testSession.query("SELECT * FROM SysContent WHERE sys_id = '" + doc.getId() + "'");
        assertEquals(1, docs.size());
    }

    @Test
    public void testAccessWithUserAttributesValue() {
        CoreSession testSession = coreFeature.getCoreSession(DummyLoginFeature.getTestPrincipal("userAttr", false));

        // Expression checks a specific user attribute value
        RepositoryPrincipal principal = testSession.getPrincipal();
        Map<String, Serializable> attrs = new HashMap<>();
        attrs.put(UserAttributeExpressionSecurityPolicy.SECURITY_EXPRESSION_ATTRIBUTE_NAME,
                "user.custom_attribute == \"foo\"");
        attrs.put("custom_attribute", "foo");
        principal.setUserAttributes(attrs);

        // Access by ID
        assertTrue(testSession.hasPermission(doc.getRef(), SecurityConstants.READ));

        // Access via query
        DocumentModelList docs = testSession.query("SELECT * FROM SysContent WHERE sys_id = '" + doc.getId() + "'");
        assertEquals(1, docs.size());
    }

    @Test
    public void testAccessWithUserPropertiesValue() {
        CoreSession testSession = coreFeature.getCoreSession(DummyLoginFeature.getTestPrincipal("userProp", false));

        // Expression checks a specific user property value
        RepositoryPrincipal principal = testSession.getPrincipal();
        Map<String, Serializable> attrs = new HashMap<>();
        attrs.put(UserAttributeExpressionSecurityPolicy.SECURITY_EXPRESSION_ATTRIBUTE_NAME,
                "user.custom_property == \"bar\"");
        principal.setUserAttributes(attrs);
        Map<String, Serializable> userProps = new HashMap<>();
        userProps.put("custom_property", "bar");
        principal.setUserAttributes(userProps);

        // Access by ID
        assertTrue(testSession.hasPermission(doc.getRef(), SecurityConstants.READ));

        // Access via query
        DocumentModelList docs = testSession.query("SELECT * FROM SysContent WHERE sys_id = '" + doc.getId() + "'");
        assertEquals(1, docs.size());
    }

    @Test
    public void testNoAccessWithoutACL() {
        CoreSession testSession = coreFeature.getCoreSession(DummyLoginFeature.getTestPrincipal("userNoACL", false));

        // Set external security expression on the principal to allow everything and check it does not bypass ACL
        RepositoryPrincipal principal = testSession.getPrincipal();
        Map<String, Serializable> attrs = new HashMap<>();
        attrs.put(UserAttributeExpressionSecurityPolicy.SECURITY_EXPRESSION_ATTRIBUTE_NAME, "TRUE");
        principal.setUserAttributes(attrs);

        // Access by ID
        assertFalse(testSession.hasPermission(docWithoutACL.getRef(), SecurityConstants.READ));

        // Access via query
        DocumentModelList docs = testSession.query(
                "SELECT * FROM SysContent WHERE sys_id = '" + docWithoutACL.getId() + "'");
        assertEquals(0, docs.size());
    }
}
