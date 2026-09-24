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
package com.hyland.openarch.ecm.core.event.impl;

import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.common.xmap.Context;
import com.hyland.openarch.common.xmap.annotation.XNode;
import com.hyland.openarch.common.xmap.annotation.XNodeList;
import com.hyland.openarch.common.xmap.annotation.XObject;
import com.hyland.openarch.common.xmap.registry.XEnable;
import com.hyland.openarch.common.xmap.registry.XRegistry;
import com.hyland.openarch.ecm.core.api.NuxeoException;
import com.hyland.openarch.ecm.core.event.Event;
import com.hyland.openarch.ecm.core.event.EventBundle;
import com.hyland.openarch.ecm.core.event.EventListener;
import com.hyland.openarch.ecm.core.event.PostCommitEventListener;
import com.hyland.openarch.ecm.core.event.PostCommitFilteringEventListener;
import com.hyland.openarch.runtime.model.RuntimeContext;

/**
 * XObject descriptor to declare event listeners
 */
@XObject("listener")
@XRegistry(enable = false)
public class EventListenerDescriptor {

    public static final Logger log = LogManager.getLogger(EventListenerDescriptor.class);

    @XNode("@name")
    protected String name;

    /**
     * The event listener class.
     */
    @XNode("@class")
    protected String className;

    @XNode("@transactionTimeOut")
    protected Integer transactionTimeOut;

    /**
     * The priority to be used to order listeners.
     */
    @XNode("@priority")
    protected Integer priority;

    @XNode(value = XEnable.ENABLE, fallback = "@enabled", defaultAssignment = "true")
    @XEnable
    protected boolean isEnabled = true;

    @XNode("@retryCount")
    protected Integer retryCount;

    @XNode("@singlethread")
    protected boolean singleThreaded = false;

    @XNodeList(value = "event", componentType = String.class, type = HashSet.class, nullByDefault = true)
    protected Set<String> events;

    protected RuntimeContext context;

    protected EventListener inLineListener;

    protected PostCommitEventListener postCommitEventListener;

    public int getPriority() {
        return priority == null ? 0 : priority.intValue();
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public Set<String> getEvents() {
        return events;
    }

    public void initListener(Context context) {
        try {
            if (className != null) {
                Class<?> klass = context.loadClass(className);
                if (EventListener.class.isAssignableFrom(klass)) {
                    inLineListener = (EventListener) klass.getDeclaredConstructor().newInstance();
                } else if (PostCommitEventListener.class.isAssignableFrom(klass)) {
                    postCommitEventListener = (PostCommitEventListener) klass.getDeclaredConstructor().newInstance();
                } else {
                    throw new IllegalArgumentException(
                            "Listener extension must define a class extending EventListener or PostCommitEventListener: '"
                                    + className + "'.");
                }
            } else {
                throw new IllegalArgumentException("Listener extension must define either a class or a script");
            }
        } catch (ReflectiveOperationException | NoClassDefFoundError e) {
            throw new NuxeoException(e);
        }
    }

    public EventListener asEventListener() {
        return inLineListener;
    }

    public PostCommitEventListener asPostCommitListener() {
        return postCommitEventListener;
    }

    public String getName() {
        if (name == null) {
            name = className;
        }
        return name;
    }

    public Integer getTransactionTimeout() {
        return transactionTimeOut;
    }

    public final boolean acceptEvent(String eventName) {
        return events == null || events.contains(eventName);
    }

    public boolean isSingleThreaded() {
        return singleThreaded;
    }

    /**
     * Filters the event bundle to only keep events of interest to this listener.
     *
     * @since 5.7
     */
    public EventBundle filterBundle(EventBundle bundle) {
        EventBundle filtered = new EventBundleImpl();

        for (Event event : bundle) {
            if (!acceptEvent(event.getName())) {
                continue;
            }
            PostCommitEventListener pcl = asPostCommitListener();
            if (pcl instanceof PostCommitFilteringEventListener pclCasted
                    && !pclCasted.acceptEvent(event)) {
                continue;
            }
            filtered.push(event);
        }
        return filtered;
    }

    /**
     * Checks if there's at least one event of interest in the bundle.
     *
     * @since 5.7
     */
    public boolean acceptBundle(EventBundle bundle) {
        for (Event event : bundle) {
            if (!acceptEvent(event.getName())) {
                continue;
            }
            PostCommitEventListener pcl = asPostCommitListener();
            if (pcl instanceof PostCommitFilteringEventListener pclCasted
                    && !pclCasted.acceptEvent(event)) {
                continue;
            }
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "EventListenerDescriptor [name=" + name + ", className=" + className + "]";
    }

}
