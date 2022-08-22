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
package org.openwms.common.comm.tcp;

import org.springframework.messaging.Message;

import java.util.Map;

/**
 * A TelegramDeserializer is able to deserialize an incoming telegram String to a known type of {@link Message}.
 *
 * @author Heiko Scherrer
 * @see Message
 */
public interface TelegramDeserializer<T> {

    /**
     * Try to deserialize the {@code telegram} String into a valid {@link Message}.
     * Implementations may throw some kind of RuntimeException if the type of telegram is
     * not known and cannot be deserialized.
     *
     * @param telegram The telegram String to deserialize
     * @param headers A map with the headers passed along the telegram
     * @return The deserialized telegram as Message
     */
    Message<T> deserialize(String telegram, Map<String, Object> headers);

    /**
     * Return the telegram type this mapper is responsible for.
     *
     * @return the telegram type as String
     */
    String forType();
}
