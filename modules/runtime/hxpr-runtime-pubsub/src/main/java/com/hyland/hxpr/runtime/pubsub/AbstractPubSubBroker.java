/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
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
package com.hyland.hxpr.runtime.pubsub;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.io.output.StringBuilderWriter;
import org.nuxeo.common.utils.TooLargeException;
import org.nuxeo.runtime.api.Framework;

/**
 * Encapsulates message sending and receiving through the {@link PubSubService}.
 * <p>
 * All nodes that use the same topic will receive the same messages. The discriminator is used to distinguish nodes
 * between one another, and to avoid that a node receives the messages it send itself.
 * <p>
 * An actual implementation must implement the method {@link #deserialize} (usually by delegating to a static method in
 * the {@link T} message class), and the {@link #receivedMessage} callback.
 * <p>
 * The public API is {@link #sendMessage}, and the {@link #receivedMessage} callback.
 *
 * @since 9.3
 */
public abstract class AbstractPubSubBroker<T extends SerializableMessage> {

    private static final Logger log = LoggerFactory.getLogger(AbstractPubSubBroker.class);

    protected String topic;

    protected String discriminatorAndSep;

    /** Deserializes a message, or returns {@code null}. */
    public abstract T deserialize(String message) throws IOException;

    /**
     * Initializes the broker.
     *
     * @param topic the topic
     * @param discriminator the discriminator
     */
    public void initialize(String topic, String discriminator) {
        this.topic = topic;
        if (discriminator.contains(DISCRIMINATOR_SEP)) {
            throw new IllegalArgumentException(
                    "Invalid discriminator, must not contain separator '" + DISCRIMINATOR_SEP + "': " + discriminator);
        }
        this.discriminatorAndSep = discriminator + DISCRIMINATOR_SEP;
        PubSubService pubSubService = Framework.getService(PubSubService.class);
        pubSubService.registerSubscriber(topic, this::subscriber);
    }

    /**
     * Closes this broker and releases resources.
     */
    public void close() {
        PubSubService pubSubService = Framework.getService(PubSubService.class);
        pubSubService.unregisterSubscriber(topic, this::subscriber);
    }

    protected static final String DISCRIMINATOR_SEP = ":";

    /**
     * Sends a message to other nodes.
     */
    public void sendMessage(T message) {
        String messageString = getMessageString(message);
        if (messageString == null) {
            return;
        }
        PubSubService pubSubService = Framework.getService(PubSubService.class);
        try {
            pubSubService.publish(topic, messageString);
        } catch (TooLargeException e) {
            if (!(message instanceof SerializableAccumulableInvalidations invalidations)) {
                throw e;
            }
            // attempt to minimize the message size (by sending "all" invalidations instead)
            SerializableMessage minimized = invalidations.minimize();
            // re-publish
            messageString = getMessageString(minimized);
            if (messageString == null) {
                return;
            }
            pubSubService.publish(topic, messageString);
        }
    }

    @SuppressWarnings("resource") // StringBuilderWriter doesn't need to be closed
    private String getMessageString(SerializableMessage message) {
        if (log.isTraceEnabled()) {
            log.trace("Sending message: {}", message);
        }
        StringBuilderWriter writer = new StringBuilderWriter();
        writer.append(discriminatorAndSep);
        try {
            message.serialize(writer);
            return writer.toString();
        } catch (IOException e) {
            log.error("Failed to serialize message", e);
            // don't crash for this
            return null;
        }
    }

    /**
     * PubSubService subscriber, called from a separate thread.
     */
    protected void subscriber(String topic, String messageString) {
        int start = scanDiscriminator(messageString);
        if (start == -1) {
            // same discriminator or invalid message
            return;
        }
        T message;
        try {
            message = deserialize(messageString.substring(start));
        } catch (IOException e) {
            log.error("Failed to deserialize message", e);
            // don't crash for this
            return;
        }
        if (message == null) {
            return;
        }
        if (log.isTraceEnabled()) {
            log.trace("Received message: {}", message);
        }
        receivedMessage(message);
    }

    /**
     * Callback implementing the delivery of a message from another node.
     *
     * @param message the received message
     */
    public abstract void receivedMessage(T message);

    /**
     * Scans for the discriminator and returns the payload start offset.
     *
     * @return payload start offset, or -1 if the discriminator is local or if the message is invalid
     */
    protected int scanDiscriminator(String message) {
        if (message == null || message.startsWith(discriminatorAndSep)) {
            return -1;
        }
        int index = message.indexOf(DISCRIMINATOR_SEP);
        if (index < 0) {
            return -1;
        }
        return index + DISCRIMINATOR_SEP.length();
    }

}
