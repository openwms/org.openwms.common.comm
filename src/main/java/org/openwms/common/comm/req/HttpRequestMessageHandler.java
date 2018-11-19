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
package org.openwms.common.comm.req;


import org.ameba.annotation.Measured;
import org.openwms.common.comm.MessageProcessingException;
import org.openwms.core.SecurityUtils;
import org.openwms.core.SpringProfiles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.function.Function;

import static org.openwms.common.comm.req.RequestHelper.getRequest;

/**
 * A HttpRequestMessageHandler forwards the request to the routing service.
 *
 * @author <a href="mailto:scherrer@openwms.org">Heiko Scherrer</a>
 */
@Profile("!"+ SpringProfiles.ASYNCHRONOUS_PROFILE)
@Component
@RefreshScope
class HttpRequestMessageHandler implements Function<GenericMessage<RequestMessage>, Void> {

    private final RestTemplate restTemplate;
    private final String routingServiceName;
    private final String routingServiceProtocol;
    private final String routingServiceUsername;
    private final String routingServicePassword;

    HttpRequestMessageHandler(RestTemplate restTemplate,
                              @Value("${owms.driver.server.routing-service.name:routing-service}") String routingServiceName,
                              @Value("${owms.driver.server.routing-service.protocol:http}") String routingServiceProtocol,
                              @Value("${owms.driver.server.routing-service.username:user}") String routingServiceUsername,
                              @Value("${owms.driver.server.routing-service.password:sa}") String routingServicePassword) {
        this.restTemplate = restTemplate;
        this.routingServiceName = routingServiceName;
        this.routingServiceProtocol = routingServiceProtocol;
        this.routingServiceUsername = routingServiceUsername;
        this.routingServicePassword = routingServicePassword;
    }

    /**
     * {@inheritDoc}
     */
    @Measured
    @Override
    public Void apply(GenericMessage<RequestMessage> msg) {
        try {
            restTemplate.exchange(routingServiceProtocol + "://" + routingServiceName + "/req", HttpMethod.POST, new HttpEntity<>(getRequest(msg), SecurityUtils.createHeaders(routingServiceUsername, routingServicePassword)), Void.class);
            return null;
        } catch (Exception e) {
            throw new MessageProcessingException(e.getMessage(), e);
        }
    }
}
