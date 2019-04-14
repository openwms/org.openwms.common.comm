/*
 * Copyright 2018 Heiko Scherrer
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
package org.openwms.common.comm.osip.upd;

import org.ameba.annotation.Measured;
import org.openwms.common.comm.CommConstants;
import org.openwms.common.comm.RespondingServiceActivator;
import org.openwms.common.comm.osip.OSIP;
import org.openwms.common.comm.osip.OSIPHeader;
import org.openwms.common.comm.osip.ack.AckMessage;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A UpdateXServiceActivator implements the Service Activator pattern and delegates
 * incoming {@link UpdateXMessage}s to the appropriate handler function.
 *
 * @author <a href="mailto:hscherrer@openwms.org">Heiko Scherrer</a>
 */
@OSIP
@MessageEndpoint
class UpdateXServiceActivator implements RespondingServiceActivator<UpdateXMessage, AckMessage> {

    /** The name of the MessageChannel used as input-channel of this message processor. */
    static final String INPUT_CHANNEL_NAME = UpdateXMessage.IDENTIFIER + CommConstants.CHANNEL_SUFFIX;

    private final Function<GenericMessage<UpdateXMessage>, AckMessage> handler;
    private final ApplicationContext ctx;

    UpdateXServiceActivator(Function<GenericMessage<UpdateXMessage>, AckMessage> handler,
            ApplicationContext ctx) {
        this.handler = handler;
        this.ctx = ctx;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @OSIP
    @Measured
    @ServiceActivator(inputChannel = INPUT_CHANNEL_NAME, outputChannel = "outboundChannel")
    public Message<AckMessage> wakeUp(GenericMessage<UpdateXMessage> message) {
        AckMessage ackMessage = handler.apply(message);

        Map<String, Object> h = new HashMap<>(message.getHeaders());
        h.put(OSIPHeader.SENDER_FIELD_NAME, ackMessage.getHeader().getSender());
        h.put(OSIPHeader.RECEIVER_FIELD_NAME, ackMessage.getHeader().getReceiver());
        h.put(OSIPHeader.SEQUENCE_FIELD_NAME, ackMessage.getHeader().getSequenceNo());

        return new GenericMessage<>(ackMessage, new MessageHeaders(h));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MessageChannel getChannel() {
        return ctx.getBean(INPUT_CHANNEL_NAME, MessageChannel.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getChannelName() {
        return INPUT_CHANNEL_NAME;
    }
}
