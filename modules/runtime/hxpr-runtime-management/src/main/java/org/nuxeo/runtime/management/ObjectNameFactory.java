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
package org.nuxeo.runtime.management;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.JMException;
import javax.management.ObjectName;

/**
 * @author Stephane Lacoin (Nuxeo EP Software Engineer)
 */
public class ObjectNameFactory {

    public static final String NUXEO_DOMAIN_NAME = "org.nuxeo";

    private ObjectNameFactory() {
    }

    public static String formatMetricQualifiedName(String instanceName, String metricName) {
        StringBuilder sb = new StringBuilder(NUXEO_DOMAIN_NAME).append(":");
        int lastDotIndex = instanceName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            String packageName = instanceName.substring(0, lastDotIndex);
            instanceName = instanceName.substring(lastDotIndex + 1);
            sb.append("package=").append(packageName).append(",");
        }
        sb.append("name=")
          .append(instanceName)
          .append(",type=service,metric=")
          .append(metricName)
          .append(",management=metric");
        return sb.toString();
    }

    public static String formatTypeQuery(String typeName) {
        return String.format("%s:type=%s,*", NUXEO_DOMAIN_NAME, typeName);
    }

    private static final Pattern namePattern = Pattern.compile(".*:.*");

    public static boolean hasDomain(String value) {
        if (value == null) {
            return false;
        }
        Matcher matcher = namePattern.matcher(value);
        return matcher.matches();
    }

    private static final Pattern avaPattern = Pattern.compile(".*=.*");

    public static boolean hasAttributeValueAssertion(String value) {
        if (value == null) {
            return false;
        }
        Matcher matcher = avaPattern.matcher(value);
        return matcher.matches();
    }

    public static String getQualifiedName(String name) {
        String qualifiedName = name;
        if (!hasAttributeValueAssertion(qualifiedName)) {
            qualifiedName = NUXEO_DOMAIN_NAME + ":name=" + name + ",type=service";
        } else if (!hasDomain(qualifiedName)) {
            qualifiedName = NUXEO_DOMAIN_NAME + ":" + qualifiedName;
        }
        return qualifiedName;
    }

    public static ObjectName getObjectName(String name) {
        String qualifiedName = getQualifiedName(name);
        try {
            return new ObjectName(qualifiedName);
        } catch (JMException e) {
            throw ManagementRuntimeException.wrap(name + " is not correct", e);
        }
    }

    public static String formatMetricShortName(String name) {
        return name + "-metric";
    }

}
