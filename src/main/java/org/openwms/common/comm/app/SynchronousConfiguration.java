/*
 * Copyright 2005-2022 the original author or authors.
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
package org.openwms.common.comm.app;

import org.openwms.core.SpringProfiles;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

/**
 * A SynchronousConfiguration is a Spring configuration class used when NO ASYNCHRONOUS profile is active and synchronous RESTful
 * communication is used instead.
 *
 * @author Heiko Scherrer
 * @deprecated use ameba-lib instead
 */
@Deprecated
@Profile("!"+ SpringProfiles.ASYNCHRONOUS_PROFILE)
@Configuration
class SynchronousConfiguration {

    @LoadBalanced
    @Bean
    RestTemplate aLoadBalanced() {
        var restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new RestTemplateInterceptor());
        return restTemplate;
    }
}
