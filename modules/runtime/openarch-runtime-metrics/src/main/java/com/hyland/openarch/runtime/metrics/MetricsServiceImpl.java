/*
 * (C) Copyright 2013-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.metrics;

import io.dropwizard.metrics5.Counter;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.SharedMetricRegistries;
import io.dropwizard.metrics5.jvm.BufferPoolMetricSet;
import io.dropwizard.metrics5.jvm.FileDescriptorRatioGauge;
import io.dropwizard.metrics5.jvm.GarbageCollectorMetricSet;
import io.dropwizard.metrics5.jvm.JmxAttributeGauge;
import io.dropwizard.metrics5.jvm.MemoryUsageGaugeSet;
import io.dropwizard.metrics5.jvm.ThreadStatesGaugeSet;

import java.lang.management.ManagementFactory;
import java.util.List;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.model.DefaultComponent;

public class MetricsServiceImpl extends DefaultComponent implements MetricsService {

    private static final Logger log = LogManager.getLogger(MetricsServiceImpl.class);

    protected static final String CONFIGURATION_EP = "configuration";

    protected static final String REPORTER_EP = "reporter";

    protected MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());

    protected final Counter instanceUp = registry.counter(MetricRegistry.name("nuxeo", "instance-up"));

    protected MetricsConfigurationDescriptor config;

    protected List<MetricsReporter> reporters;

    @Override
    public void activate() {
        log.debug("Activating component");
        SharedMetricRegistries.getOrCreate(MetricsService.class.getName());
    }

    @Override
    public void deactivate() {
        log.debug("Deactivating component");
        SharedMetricRegistries.remove(MetricsService.class.getName());
    }

    @Override
    public void start() {
        log.debug("Starting component");
        instanceUp.inc();
        config = (MetricsConfigurationDescriptor) getRegistryContribution(CONFIGURATION_EP).orElse(null);
        startReporters();
    }

    @Override
    public void stop() {
        log.debug("Stopping component");
        stopReporters();
        instanceUp.dec();
    }

    protected boolean metricEnabled() {
        return config != null && config.isEnabled();
    }

    @Override
    public void startReporters() {
        if (!metricEnabled() || reporters != null) {
            log.debug("Metrics disabled or already started.");
            return;
        }
        log.debug("Starting reporters");
        updateInstrumentation(config.getInstruments(), true);
        List<MetricsReporterDescriptor> descs = getRegistryContributions(REPORTER_EP);
        reporters = descs.stream().map(MetricsReporterDescriptor::newInstance).toList();
        reporters.forEach(reporter -> reporter.start(registry, config, config.getDeniedExpansions()));
    }

    @Override
    public void stopReporters() {
        if (!metricEnabled() || reporters == null) {
            log.debug("Metrics disabled or already stopped.");
            return;
        }
        log.debug("Stopping reporters");
        reporters.forEach(MetricsReporter::stop);
        updateInstrumentation(config.getInstruments(), false);
        reporters = null;
    }

    protected void updateInstrumentation(List<MetricsConfigurationDescriptor.InstrumentDescriptor> instruments,
            boolean activate) {
        for (String instrument : instruments.stream()
                                            .filter(MetricsConfigurationDescriptor.InstrumentDescriptor::isEnabled)
                                            .map(MetricsConfigurationDescriptor.InstrumentDescriptor::getId)
                                            .toList()) {
            switch (instrument) {
            case "tomcat":
                instrumentTomcat(activate);
                break;
            case "jvm":
                instrumentJvm(activate);
                break;
            default:
                log.warn("Ignoring unknown instrumentation: {}", instrument);
            }
        }
    }

    protected void registerTomcatGauge(String mbean, String attribute, MetricRegistry registry, String name) {
        try {
            registry.register(MetricRegistry.name("tomcat", name),
                    new JmxAttributeGauge(new ObjectName(mbean), attribute));
        } catch (MalformedObjectNameException | IllegalArgumentException e) {
            throw new UnsupportedOperationException("Cannot compute object name of " + mbean, e);
        }
    }

    protected void instrumentTomcat(boolean activate) {
        if (activate) {
            // TODO: do not hard code the common datasource
            String pool = "com.hyland.openarch.ecm.core.management.jtajca:type=ConnectionPoolMonitor,name=jdbc/nuxeo";
            String connector = String.format("Catalina:type=ThreadPool,name=\"http-nio-%s-%s\"",
                    Framework.getProperty("nuxeo.bind.address", "0.0.0.0"),
                    Framework.getProperty("nuxeo.bind.port", "8080"));
            String requestProcessor = String.format("Catalina:type=GlobalRequestProcessor,name=\"http-nio-%s-%s\"",
                    Framework.getProperty("nuxeo.bind.address", "0.0.0.0"),
                    Framework.getProperty("nuxeo.bind.port", "8080"));
            String manager = "Catalina:type=Manager,host=localhost,context=/nuxeo";
            registerTomcatGauge(pool, "ConnectionCount", registry, "jdbc-numActive");
            registerTomcatGauge(pool, "IdleConnectionCount", registry, "jdbc-numIdle");
            registerTomcatGauge(connector, "currentThreadCount", registry, "currentThreadCount");
            registerTomcatGauge(connector, "currentThreadsBusy", registry, "currentThreadBusy");
            registerTomcatGauge(requestProcessor, "errorCount", registry, "errorCount");
            registerTomcatGauge(requestProcessor, "requestCount", registry, "requestCount");
            registerTomcatGauge(requestProcessor, "processingTime", registry, "processingTime");
            registerTomcatGauge(requestProcessor, "bytesReceived", registry, "bytesReceived");
            registerTomcatGauge(requestProcessor, "bytesSent", registry, "bytesSent");
            registerTomcatGauge(manager, "activeSessions", registry, "activeSessions");
        } else {
            registry.removeMatching((name, metric) -> name.getKey().startsWith("tomcat."));
        }
    }

    protected void instrumentJvm(boolean activate) {
        if (activate) {
            registry.register(MetricRegistry.name("jvm.memory"), new MemoryUsageGaugeSet());
            registry.register(MetricRegistry.name("jvm.garbage"), new GarbageCollectorMetricSet());
            registry.register(MetricRegistry.name("jvm.threads"), new ThreadStatesGaugeSet());
            registry.register(MetricRegistry.name("jvm.files"), new FileDescriptorRatioGauge());
            registry.register(MetricRegistry.name("jvm.buffers"), new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer()));
        } else {
            registry.removeMatching((name, metric) -> name.getKey().startsWith("jvm."));
        }
    }
}
