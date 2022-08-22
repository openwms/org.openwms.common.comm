/*
 * Copyright 2005-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openwms.common.comm.transformer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.Transformer;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Headers;

import java.util.Map;

/**
 * A MessageTransformer.
 *
 * @author Heiko Scherrer
 */
@MessageEndpoint("messageTransformer")
public class MessageTransformer<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageTransformer.class);

    /**
     * Transformer method to transform a message into a telegram String.
     *
     * @param message The incoming message
     * @param headers The message headers
     * @return The {@code Payload} is transformable
     */
    @Transformer
    public Message<T> transform(Message<T> message, @Headers Map<String, Object> headers) {
        if (message == null) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Received message to transform was null, just skip");
            }
            return null;
        }
        return message;
    }
}
