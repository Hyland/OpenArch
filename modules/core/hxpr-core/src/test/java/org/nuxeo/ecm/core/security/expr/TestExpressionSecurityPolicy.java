/*
 * (C) Copyright 2024 Hyland (https://hyland.com/) and others.
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
package org.nuxeo.ecm.core.security.expr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATOR;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.impl.UserPrincipal;
import org.nuxeo.ecm.core.api.query.QueryParameters;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
@Deploy("org.nuxeo.ecm.core.schema")
@Deploy("org.nuxeo.ecm.core:OSGI-INF/SecurityService.xml")
@Deploy("org.nuxeo.ecm.core:OSGI-INF/permissions-contrib.xml")
@Deploy("org.nuxeo.ecm.core:OSGI-INF/CoreExtensions.xml")
@Deploy("org.nuxeo.ecm.core.tests:OSGI-INF/test-expression-security-policy.xml")
public class TestExpressionSecurityPolicy {

    private static final String MIXIN = "Clearance";

    private static final String CLEARANCE_LEVEL = "clearance_level";

    @Test
    public void testBasicBehavior() {
        InternalDocument idoc = mock(InternalDocument.class);
        when(idoc.getValue(SYS_CREATOR)).thenReturn("somebody");
        when(idoc.getValue(CLEARANCE_LEVEL)).thenReturn(42);
        when(idoc.getEffectiveMixins()).thenReturn(Set.of(MIXIN));

        String expression = "user.id == \"alice\" || content.clearance_level < 10";
        var config = new ExpressionSecurityPolicyConfig(null, Set.of(SYS_FILE), Set.of(READ), MIXIN, expression);
        List<ValidationViolation> violations = new ArrayList<>();
        var policy = config.newSecurityPolicy("test", "myexpr", violations);
        assertEquals(List.of(), violations);

        String permission = null;
        Set<String> permissions = null;

        // alice can read due to the policy expression
        NuxeoPrincipal alice = new UserPrincipal("alice", null, false);
        Set<String> alices = Set.of(alice.getId());
        boolean check = policy.hasPermission(idoc, alice, alices, permission, permissions);
        assertTrue(check);

        // corresponding HXQL
        QueryParameters params = new QueryParameters(alice, 0, 0);
        Operand clause = policy.getQueryClause(params, alices);
        assertEquals("true", clause.toString());

        // bob cannot read due to document high clearance level
        NuxeoPrincipal bob = new UserPrincipal("bob", null, false);
        Set<String> bobs = Set.of(bob.getId());
        check = policy.hasPermission(idoc, bob, bobs, permission, permissions);
        assertFalse(check);

        // corresponding HXQL
        params = new QueryParameters(bob, 0, 0);
        clause = policy.getQueryClause(params, bobs);
        assertEquals("(clearance_level < 10)", clause.toString());

        // lower clearance level on document
        when(idoc.getValue(CLEARANCE_LEVEL)).thenReturn(3L);

        // now bob can read
        check = policy.hasPermission(idoc, bob, bobs, permission, permissions);
        assertTrue(check);
    }

}
