/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.rest.auth;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.ADMINISTRATORS_GROUP;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.POWER_USERS_GROUP;

import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.rest.auth.config.RolesAllowedInterceptorTestConfig;
import com.hyland.openarch.rest.auth.config.RolesAllowedInterceptorTestConfig.TestBean;
import com.hyland.openarch.rest.auth.config.RolesAllowedInterceptorTestConfig.TestBeanWithAdministratorAccess;
import com.hyland.openarch.rest.auth.config.RolesAllowedInterceptorTestConfig.TestBeanWithRolesAllowedAdminAndPowerUser;
import com.hyland.openarch.rest.auth.config.RolesAllowedInterceptorTestConfig.TestBeanWithRolesAllowedPowerUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(classes = { RolesAllowedInterceptorTest.TestConfig.class })
public class RolesAllowedInterceptorTest {

    // proxyTargetClass = true forces CGLIB subclass proxying instead of JDK interface-based proxying.
    // This is required because RolesAllowedInterceptor is a concrete @Aspect class with no interface,
    // so @MockitoSpyBean cannot wrap it with a spy proxy without CGLIB.
    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import({ RolesAllowedInterceptor.class, RolesAllowedInterceptorTestConfig.class })
    static class TestConfig {
    }

    @MockitoSpyBean
    RolesAllowedInterceptor rolesAllowedInterceptor;

    @Mock
    RepositoryPrincipal currentUser;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(rolesAllowedInterceptor.getCurrentUser()).thenReturn(currentUser);
    }

    @Test
    public void whenNoAnnotationPresentShouldNotThrowError(@Autowired TestBean testBean) {
        testBean.testMethod();
        verifyNoInteractions(rolesAllowedInterceptor);
    }

    @Test
    public void currentUserHasRequiredRoleAndAnnotationPresentOnMethodShouldNotThrowError(
            @Autowired TestBean testBean) {
        when(currentUser.isAdministrator()).thenReturn(true);

        testBean.testMethodWithRolesAllowedAdmin();
        verify(rolesAllowedInterceptor).checkRolesAllowed(any(JoinPoint.class));
    }

    @Test
    public void currentUserDoesNotHaveRequiredRoleAndAnnotationPresentOnMethodShouldThrowError(
            @Autowired TestBean testBean) {
        when(currentUser.isAdministrator()).thenReturn(false);

        assertThrows(AccessDeniedException.class, testBean::testMethodWithRolesAllowedAdmin);
        verify(rolesAllowedInterceptor).checkRolesAllowed(any(JoinPoint.class));
    }

    @Test
    public void currentUserHasOneOfManyRequiredRoleAndAnnotationPresentOnMethodShouldNotThrowError(
            @Autowired TestBean testBean) {
        when(currentUser.isAdministrator()).thenReturn(false);
        when(currentUser.isMemberOf(POWER_USERS_GROUP)).thenReturn(true);

        testBean.testMethodWithRolesAllowedAdminAndPowerUser();
        verify(rolesAllowedInterceptor).checkRolesAllowed(any(JoinPoint.class));
    }

    @Test
    public void currentUserPowerUserIsOneOfManyRolesAndAnnotationPresentOnClassShouldNotThrowError(
            @Autowired TestBeanWithRolesAllowedAdminAndPowerUser testBean) {
        when(currentUser.isMemberOf(POWER_USERS_GROUP)).thenReturn(true);

        testBean.testMethod();
        verify(rolesAllowedInterceptor).checkRolesAllowed(any(JoinPoint.class));
    }

    @Test
    public void currentUserAdminIsOneOfManyRolesAndAnnotationPresentOnClassShouldNotThrowError(
            @Autowired TestBeanWithRolesAllowedAdminAndPowerUser testBean) {
        when(currentUser.isAdministrator()).thenReturn(true);

        testBean.testMethod();
        verify(rolesAllowedInterceptor).checkRolesAllowed(any(JoinPoint.class));
    }

    @Test
    public void currentUserIsMemberOfAdministratorsGroupAndAdministratorAccessAnnotationPresentOnMethodShouldNotThrowError(
            @Autowired TestBean testBean) {
        when(currentUser.isAdministrator()).thenReturn(true);

        testBean.testMethodWithAdministratorAccess();
        verify(rolesAllowedInterceptor).checkRolesAllowed(any(JoinPoint.class));
    }

    @Test
    public void currentUserIsMemberOfAdministratorsGroupAndAdministratorAccessAnnotationPresentOnClassShouldNotThrowError(
            @Autowired TestBeanWithAdministratorAccess testBean) {
        when(currentUser.isAdministrator()).thenReturn(true);

        testBean.testMethod();
        verify(rolesAllowedInterceptor).checkRolesAllowed(any(JoinPoint.class));
    }

    @Test
    public void currentUserIsMemberOfPowerUserGroupAndPowerUserRoleAnnotationIsOnClassAndAdminAnnotationIsOnMethodShouldNotThrowError(
            @Autowired TestBeanWithRolesAllowedPowerUser testbean) {
        // Class Level Annotation: @RolesAllowed("powerusers"), Method Level Annotation: @AdministratorAccess
        // Method should allow access to both powerusers and admins.

        when(currentUser.isMemberOf(POWER_USERS_GROUP)).thenReturn(true);
        when(currentUser.isMemberOf(ADMINISTRATORS_GROUP)).thenReturn(false);

        testbean.testMethodWithRolesAllowedAdmin();
        verify(rolesAllowedInterceptor).checkRolesAllowed(any(JoinPoint.class));
    }

    @Test
    public void currentUserIsMemberOfAdministratorGroupAndPowerUserRoleAnnotationIsOnClassAndAdminAnnotationIsOnMethodShouldNotThrowError(
            @Autowired TestBeanWithRolesAllowedPowerUser testbean) {
        // Class Level Annotation: @RolesAllowed("powerusers"), Method Level Annotation: @AdministratorAccess
        // Method should allow access to both powerusers and admins.

        when(currentUser.isMemberOf(POWER_USERS_GROUP)).thenReturn(false);
        when(currentUser.isAdministrator()).thenReturn(true);

        testbean.testMethodWithRolesAllowedAdmin();
        verify(rolesAllowedInterceptor).checkRolesAllowed(any(JoinPoint.class));
    }

}
