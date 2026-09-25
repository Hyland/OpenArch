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
package com.hyland.openarch.ecm.core.management.jtajca;

import java.lang.reflect.Method;
import java.util.Set;

import jakarta.inject.Inject;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import jakarta.transaction.TransactionManager;

import org.apache.logging.log4j.LogManager;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.jtajca.RepositoryContainer;
import com.hyland.openarch.runtime.management.ManagementFeature;
import com.hyland.openarch.runtime.management.ServerLocator;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesLoader;
import com.hyland.openarch.runtime.test.runner.RunnerFeature;

import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

// ideally this feature should deploy/use TransactionalFeature but we can't depend on it because features order is
// important as feature is doing assertion. So we need to keep this order to ensure assertion is at good time:
// - CoreFeature (close and clean session - need a transaction)
// - TransactionalFeature (close the transaction)
// - JtajcaManagementFeature (assert there's no remaining transactions)
// we get this order by using features in tests like this @Features(JtajcaManagementFeature.class, CoreFeature.class)
@Features(ManagementFeature.class)
@Deploy("org.nuxeo.ecm.core.management.jtajca")
public class JtajcaManagementFeature implements RunnerFeature {

    protected static ObjectName nameOf(Class<?> itf) {
        try {
            return new ObjectName(Defaults.instance.name(itf, "*"));
        } catch (MalformedObjectNameException cause) {
            throw new AssertionError("Cannot name monitor", cause);
        }
    }

    protected <T> void bind(Binder binder, MBeanServer mbs, Class<T> type) {
        final Set<ObjectName> names = mbs.queryNames(nameOf(type), null);
        for (ObjectName name : names) {
            binder.bind(type)
                  .annotatedWith(Names.named(name.getKeyProperty("name")))
                  .toProvider(new MBeanProvider<>(type, name));
        }
    }

    class MBeanProvider<T> implements Provider<T> {
        protected Class<T> type;

        protected ObjectName name;

        public MBeanProvider(Class<T> type, ObjectName name) {
            this.type = type;
            this.name = name;
        }

        @Override
        public T get() {
            MBeanServer mbs = Framework.getService(ServerLocator.class).lookupServer();
            return type.cast(JMX.newMXBeanProxy(mbs, name, type));
        }
    }

    FeaturesLoader loader;

    CoreFeature core;

    Class<?> target;

    Injector injector;

    @Override
    public void initialize(FeaturesLoader loader) {
        this.loader = loader;
    }

    @Override
    public void start() {
        core = loader.getFeature(CoreFeature.class);
        target = loader.getTargetTestClass();
    }

    @Override
    public void testCreated(Injector injector, Object test) {
        this.injector = injector;
    }

    @Override
    public void configure(Binder binder) {
        if (core == null) {
            return;
        }

        MBeanServer mbs = Framework.getService(ServerLocator.class).lookupServer();
        bind(binder, mbs, ConnectionPoolMonitor.class);
        bind(binder, mbs, CoreSessionMonitor.class);
        TransactionManager tm = RepositoryContainer.getTransactionManager();
        if (tm != null) {
            bind(binder, mbs, TransactionMonitor.class);
            binder.bind(TransactionManager.class).toInstance(tm);
        }
    }

    class TxChecker {
        @Inject
        @Named("default")
        TransactionMonitor monitor;

        void assertNoTransactions() {
            long count = monitor.getActiveCount();
            if (count == 0) {
                LogManager.getLogger(JtajcaManagementFeature.class).debug(target + " was successful");
                return;
            }
            throw new AssertionError(
                    String.format("still have tx active (%d) %s", count, monitor.getActiveStatistics()));
        }

        public TxChecker(Injector injector) {
            injector.injectMembers(this);
        }
    }

    TxChecker txChecker;

    @Override
    public void beforeSetup(Method method, Object test) {
        if (core == null) {
            return;
        }
        txChecker = new TxChecker(injector);
    }

    @Override
    public void afterTeardown(Method method, Object test) {
        if (txChecker == null) {
            return;
        }
        try {
            txChecker.assertNoTransactions();
        } finally {
            txChecker = null;
        }
    }

}
