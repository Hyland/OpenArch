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
package org.nuxeo.ecm.core.security.expr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.nuxeo.ecm.core.security.expr.UserAttributeExpressionSecurityPolicy.SECURITY_EXPRESSION_ATTRIBUTE_NAME;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.impl.UserPrincipal;
import org.nuxeo.ecm.core.api.query.QueryParameters;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;

@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
@Deploy("org.nuxeo.ecm.core.schema")
@Deploy("org.nuxeo.ecm.core:OSGI-INF/SecurityService.xml")
@Deploy("org.nuxeo.ecm.core:OSGI-INF/permissions-contrib.xml")
@Deploy("org.nuxeo.ecm.core:OSGI-INF/CoreExtensions.xml")
public class TestUserAttributeExpressionSecurityPolicy {

    @Test
    public void testBasicBehavior() {
        InternalDocument idoc = mock(InternalDocument.class);
        doReturn("123").when(idoc).getValue("testSecKeyword");

        var config = new UserAttributeExpressionSecurityPolicyConfig();
        List<ValidationViolation> violations = new ArrayList<>();
        var policy = config.newSecurityPolicy("test", "myexpr", violations);
        assertEquals(List.of(), violations);

        String permission = null;
        Set<String> permissions = null;

        // alice can read due to the policy expression
        NuxeoPrincipal alice = new UserPrincipal("alice", null, false);
        alice.setUserAttributes(Map.of(SECURITY_EXPRESSION_ATTRIBUTE_NAME,
                "content.testSecKeyword == user.testSecKeyword", "testSecKeyword", "123"));
        Set<String> alices = Set.of(alice.getId());
        boolean check = policy.hasPermission(idoc, alice, alices, permission, permissions);
        assertTrue(check);

        // corresponding HXQL
        QueryParameters params = new QueryParameters(alice, 0, 0);
        Operand clause = policy.getQueryClause(params, alices);
        assertEquals("testSecKeyword = '123'", clause.toString());

        // bob cannot read due to the policy expression
        NuxeoPrincipal bob = new UserPrincipal("bob", null, false);
        bob.setUserAttributes(Map.of(SECURITY_EXPRESSION_ATTRIBUTE_NAME,
                "user.testSecKeyword == content.testSecKeyword", "testSecKeyword", "456"));
        Set<String> bobs = Set.of(bob.getId());
        check = policy.hasPermission(idoc, bob, bobs, permission, permissions);
        assertFalse(check);

        // corresponding HXQL
        params = new QueryParameters(bob, 0, 0);
        clause = policy.getQueryClause(params, bobs);
        assertEquals("'456' = testSecKeyword", clause.toString());

        // change test keyword on doc
        doReturn("456").when(idoc).getValue("testSecKeyword");

        // now bob can read
        check = policy.hasPermission(idoc, bob, bobs, permission, permissions);
        assertTrue(check);
    }


    @Test
    public void testOnBaseSecurityKeywordsEdgeCases() {
        InternalDocument idoc = mock(InternalDocument.class);
        doReturn("mySysID").when(idoc).getValue("testSystemId");
        doReturn("123").when(idoc).getValue("testSecKeyword");
        doReturn("456").when(idoc).getValue("testSecKeyword2");

        var config = new UserAttributeExpressionSecurityPolicyConfig();
        List<ValidationViolation> violations = new ArrayList<>();
        var policy = config.newSecurityPolicy("test", "myexpr", violations);
        assertEquals(List.of(), violations);

        String permission = null;
        Set<String> permissions = null;

        // alice can read due to the policy expression
        NuxeoPrincipal alice = new UserPrincipal("alice", null, false);
        alice.setUserAttributes(Map.of(SECURITY_EXPRESSION_ATTRIBUTE_NAME,
                "(content.testSystemId != \"mySysID\" || content.testSecKeyword == \"123\" && content.testSecKeyword2 == \"456\")"));
        Set<String> alices = Set.of(alice.getId());
        boolean check = policy.hasPermission(idoc, alice, alices, permission, permissions);
        assertTrue(check);

        // corresponding HXQL
        QueryParameters params = new QueryParameters(alice, 0, 0);
        Operand clause = policy.getQueryClause(params, alices);
        assertEquals("testSystemId <> 'mySysID' OR testSecKeyword = '123' AND testSecKeyword2 = '456'",
                clause.toString());

        // bob cannot read due to the policy expression
        NuxeoPrincipal bob = new UserPrincipal("bob", null, false);
        bob.setUserAttributes(Map.of(SECURITY_EXPRESSION_ATTRIBUTE_NAME,
                "(content.testSystemId != \"mySysID\" || content.testSecKeyword == \"123\" && content.testSecKeyword2 == \"789\")"));
        Set<String> bobs = Set.of(bob.getId());
        check = policy.hasPermission(idoc, bob, bobs, permission, permissions);
        assertFalse(check);

        // corresponding HXQL
        params = new QueryParameters(bob, 0, 0);
        clause = policy.getQueryClause(params, bobs);
        assertEquals("testSystemId <> 'mySysID' OR testSecKeyword = '123' AND testSecKeyword2 = '789'",
                clause.toString());

        // change test keyword on doc
        doReturn("789").when(idoc).getValue("testSecKeyword2");

        // now bob can read
        check = policy.hasPermission(idoc, bob, bobs, permission, permissions);
        assertTrue(check);

        // and alice cannot read
        check = policy.hasPermission(idoc, alice, alices, permission, permissions);
        assertFalse(check);

        // add another sysId expression for alice
        alice.setUserAttributes(Map.of(SECURITY_EXPRESSION_ATTRIBUTE_NAME,
                "(content.testSystemId != \"anotherSysId\" || content.testSecKeyword != \"123\" && content.testSecKeyword2 != \"456\") && (content.testSystemId != \"mySysID\" || content.testSecKeyword == \"123\" && content.testSecKeyword2 == \"456\")"));

        // and alice still cannot read
        check = policy.hasPermission(idoc, alice, alices, permission, permissions);
        assertFalse(check);

        //change testSystemId on doc
        doReturn("anotherSysId").when(idoc).getValue("testSystemId");

        // alice still cannot read
        check = policy.hasPermission(idoc, alice, alices, permission, permissions);
        assertFalse(check);

        // and bob can still read
        check = policy.hasPermission(idoc, bob, bobs, permission, permissions);
        assertTrue(check);

        // change testSecKeyword on doc
        doReturn("smthElse").when(idoc).getValue("testSecKeyword");

        // now alice can read
        check = policy.hasPermission(idoc, alice, alices, permission, permissions);
        assertTrue(check);

        // and bob can still read
        check = policy.hasPermission(idoc, bob, bobs, permission, permissions);
        assertTrue(check);
    }

}
