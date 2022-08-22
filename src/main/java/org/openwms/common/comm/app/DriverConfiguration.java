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
package org.openwms.common.comm.app;

import org.openwms.common.comm.TelegramResolver;
import org.openwms.common.comm.config.Connections;
import org.openwms.common.comm.config.Subsystem;
import org.openwms.common.comm.tcp.TelegramDeserializer;
import org.openwms.common.comm.transformer.tcp.TelegramTransformer;
import org.openwms.common.comm.transformer.tcp.Transformable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.serializer.Deserializer;
import org.springframework.core.serializer.Serializer;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.handler.AbstractMessageHandler;
import org.springframework.integration.ip.tcp.TcpReceivingChannelAdapter;
import org.springframework.integration.ip.tcp.TcpSendingMessageHandler;
import org.springframework.integration.ip.tcp.connection.AbstractClientConnectionFactory;
import org.springframework.integration.ip.tcp.connection.AbstractConnectionFactory;
import org.springframework.integration.ip.tcp.connection.AbstractServerConnectionFactory;
import org.springframework.integration.ip.tcp.connection.TcpConnectionInterceptorFactory;
import org.springframework.integration.ip.tcp.connection.TcpConnectionInterceptorFactoryChain;
import org.springframework.integration.ip.tcp.connection.TcpMessageMapper;
import org.springframework.integration.ip.tcp.connection.TcpNetClientConnectionFactory;
import org.springframework.integration.ip.tcp.connection.TcpNetServerConnectionFactory;
import org.springframework.integration.ip.tcp.serializer.ByteArrayLfSerializer;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

import static java.lang.String.format;
import static org.ameba.LoggingCategories.BOOT;
import static org.openwms.common.comm.CommConstants.PREFIX_CONNECTION_FACTORY;
import static org.openwms.common.comm.CommConstants.SUFFIX_OUTBOUND;

/**
 * A DriverConfiguration is the main Spring configuration class to setup all TCP/IP connections like configured in project specific
 * configuration files. Multiple driver instances can be bootstrapped each in a different mode, like Duplex, Client or Server.
 *
 * @author Heiko Scherrer
 */
@Configuration
@IntegrationComponentScan
@EnableIntegration
public class DriverConfiguration implements ApplicationEventPublisherAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(DriverConfiguration.class);
    private static final Logger BOOT_LOGGER = LoggerFactory.getLogger(BOOT);
    private static final String SUFFIX_INBOUND = "_inbound";
    private static final String PREFIX_CHANNEL_ADAPTER = "channelAdapter_";
    private static final String PREFIX_SENDING_MESSAGE_HANDLER = "sendingMessageHandler_";
    public static final String PREFIX_ENRICHED_OUTBOUND_CHANNEL = "enrichedOutboundChannel_";
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    private GenericApplicationContext applicationContext;

    private void registerBean(String beanName, Object singletonObject) {
        applicationContext.getBeanFactory().registerSingleton(beanName, singletonObject);
    }

    private TcpReceivingChannelAdapter tcpReceivingChannelAdapter(
            AbstractConnectionFactory connectionFactory, MessageChannel inboundChannel) {
        TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
        adapter.setConnectionFactory(connectionFactory);
        adapter.setOutputChannel(inboundChannel);
        return adapter;
    }

    private void attachReceivingChannelAdapter(
            Subsystem subsystem, MessageChannel inboundChannel,
            AbstractConnectionFactory connectionFactory) {
        TcpReceivingChannelAdapter channelAdapter = tcpReceivingChannelAdapter(connectionFactory, inboundChannel);
        registerBean(PREFIX_CONNECTION_FACTORY + subsystem.getName() + SUFFIX_INBOUND, connectionFactory);
        registerBean(PREFIX_CHANNEL_ADAPTER + subsystem.getName() + SUFFIX_INBOUND, channelAdapter);
    }

    private TcpSendingMessageHandler createSendingMessageHandler(
            AbstractConnectionFactory connectionFactory) {
        TcpSendingMessageHandler handler = new TcpSendingMessageHandler();
        handler.setConnectionFactory(connectionFactory);
        handler.setClientMode(true);
        return handler;
    }

    private AbstractClientConnectionFactory createClientConnectionFactory(
            String clientHostname, int clientPort, Serializer serializer,
            Deserializer deserializer, Integer soTimeout, Integer soReceiveBufferSize, Integer soSendBufferSize) {

        TcpNetClientConnectionFactory connectionFactory = new TcpNetClientConnectionFactory(clientHostname, clientPort);
        connectionFactory.setSerializer(serializer);
        connectionFactory.setDeserializer(deserializer);
        if (soTimeout != null) {
            connectionFactory.setSoTimeout(soTimeout);
        }
        if (soReceiveBufferSize != null) {
            connectionFactory.setSoReceiveBufferSize(soReceiveBufferSize);
        }
        if (soSendBufferSize != null) {
            connectionFactory.setSoSendBufferSize(soSendBufferSize);
        }
        LOGGER.debug("Hostname [{}], Port [{}], SoTimeout [{}], SoReceiveBufferSize [{}], SoSendBufferSize [{}]",
                clientHostname, clientPort, soTimeout, soReceiveBufferSize, soSendBufferSize);
        connectionFactory.start();
        return connectionFactory;
    }

    private AbstractServerConnectionFactory createInboundServerConnectionFactory(
            Connections connections,
            Subsystem subsystem,
            TcpMessageMapper tcpMessageMapper, Serializer serializer,
            Deserializer deserializer) {

        Subsystem.Inbound inbound = subsystem.getInbound();
        int port = Optional.ofNullable(inbound.getPort()).orElseThrow(() -> new ConfigurationException(format("Port not configured for outbound connection server [%s]", subsystem.getName())));

        TcpNetServerConnectionFactory connectionFactory = new TcpNetServerConnectionFactory(port);
        connectionFactory.setHost(inbound.getHostname());
        connectionFactory.setLeaveOpen(true);
        connectionFactory.setSerializer(serializer);
        connectionFactory.setDeserializer(deserializer);
        connectionFactory.setMapper(tcpMessageMapper);
        connectionFactory.setApplicationEventPublisher(applicationEventPublisher);

        Integer soTimeout = inbound.getSoTimeout() == null ? connections.getSoTimeout() : inbound.getSoTimeout();
        if (soTimeout != null) {
            connectionFactory.setSoTimeout(soTimeout);
        }

        Integer soReceiveBufferSize = inbound.getSoReceiveBufferSize() == null ? connections.getSoReceiveBufferSize() : inbound.getSoReceiveBufferSize();
        if (soReceiveBufferSize != null) {
            connectionFactory.setSoReceiveBufferSize(soReceiveBufferSize);
        }
        LOGGER.debug("Hostname [{}], Port [{}], SoTimeout [{}], SoReceiveBufferSize [{}]",
                inbound.getHostname(), port, soTimeout, soReceiveBufferSize);
        return connectionFactory;
    }

    private AbstractServerConnectionFactory createOutboundServerConnectionFactory (
            Connections connections,
            String subsystemName,
            Subsystem.Outbound outbound,
            TcpMessageMapper tcpMessageMapper, Serializer serializer,
            Deserializer deserializer) {

        int port = Optional.ofNullable(outbound.getPort()).orElseThrow(() -> new ConfigurationException(format("Port not configured for outbound connection server [%s]", subsystemName)));

        TcpNetServerConnectionFactory connectionFactory = new TcpNetServerConnectionFactory(port);
        if (outbound.getHostname() != null) {
            connectionFactory.setHost(outbound.getHostname());
        }
        connectionFactory.setLeaveOpen(true);
        connectionFactory.setSerializer(serializer);
        connectionFactory.setDeserializer(deserializer);
        connectionFactory.setMapper(tcpMessageMapper);
        connectionFactory.setApplicationEventPublisher(applicationEventPublisher);

        Integer soTimeout = outbound.getSoTimeout() == null ? connections.getSoTimeout() : outbound.getSoTimeout();
        if (soTimeout != null) {
            connectionFactory.setSoTimeout(soTimeout);
        }

        Integer soSendBufferSize = outbound.getSoSendBufferSize() == null ? connections.getSoSendBufferSize() : outbound.getSoSendBufferSize();
        if (soSendBufferSize != null) {
            connectionFactory.setSoSendBufferSize(soSendBufferSize);
        }
        LOGGER.debug("Hostname [{}], Port [{}], SoTimeout [{}], SoSendBufferSize [{}]",
                outbound.getHostname(), port, soTimeout, soSendBufferSize);
        return connectionFactory;
    }

    private AbstractConnectionFactory createOutboundConnectionFactory (
            Subsystem.MODE mode,
            String subsystemName,
            Connections connections,
            Subsystem.Duplex duplex,
            TcpMessageMapper tcpMessageMapper, Serializer serializer,
            Deserializer deserializer) {

        String host = duplex.getHostname() == null ? connections.getHostname() : duplex.getHostname();
        int port = Optional.ofNullable(duplex.getPort()).orElseThrow(() -> new ConfigurationException(format("Port not configured for duplex connection server [%s]", subsystemName)));
        AbstractConnectionFactory connectionFactory = mode == Subsystem.MODE.server
                ? new TcpNetServerConnectionFactory(port)
                : new TcpNetClientConnectionFactory(host, port);

        connectionFactory.setHost(
                duplex.getHostname() == null ? connections.getHostname() : duplex.getHostname()
        );

        connectionFactory.setSoTimeout(
                duplex.getSoTimeout() == null ? connections.getSoTimeout() : duplex.getSoTimeout()
        );

        connectionFactory.setSoSendBufferSize(
                duplex.getSoSendBufferSize() == null ? connections.getSoSendBufferSize() : duplex.getSoSendBufferSize()
        );

        connectionFactory.setSoReceiveBufferSize(
                duplex.getSoReceiveBufferSize() == null ? connections.getSoReceiveBufferSize() : duplex.getSoReceiveBufferSize()
        );

        connectionFactory.setSerializer(serializer);
        connectionFactory.setDeserializer(deserializer);
        connectionFactory.setMapper(tcpMessageMapper);
        connectionFactory.setApplicationEventPublisher(applicationEventPublisher);
        LOGGER.debug("Hostname [{}], Port [{}], SoTimeout [{}], SoReceiveBufferSize [{}], SoSendBufferSize [{}]",
                host, port, connectionFactory.getSoTimeout(), connectionFactory.getSoReceiveBufferSize(), connectionFactory.getSoSendBufferSize()
        );
        return connectionFactory;
    }

    private void setupInbound(
            Connections connections, Subsystem subsystem,
            TcpMessageMapper tcpMessageMapper, MessageChannel inboundChannel,
            TaskScheduler taskScheduler, Serializer serializer,
            Deserializer deserializer) {

        Subsystem.Inbound inbound = subsystem.getInbound();
        if (inbound.getMode() == Subsystem.MODE.server) {

            AbstractServerConnectionFactory connectionFactory = createInboundServerConnectionFactory(connections, subsystem, tcpMessageMapper, serializer, deserializer);
            connectionFactory.setBeanName(PREFIX_CONNECTION_FACTORY + subsystem.getName() + SUFFIX_INBOUND);
            connectionFactory.setInterceptorFactoryChain(createInterceptorChain());
            attachReceivingChannelAdapter(subsystem, inboundChannel, connectionFactory);
            BOOT_LOGGER.info("[{}] Inbound  TCP/IP connection configured as server: Port [{}]", subsystem.getName(), inbound.getPort());
        } else if (inbound.getMode() == Subsystem.MODE.client) {

            String hostname = inbound.getHostname() == null ? connections.getHostname() : inbound.getHostname();
            Integer soTimeout = inbound.getSoTimeout() == null ? connections.getSoTimeout() : inbound.getSoTimeout();
            Integer soReceiveBufferSize = inbound.getSoReceiveBufferSize() == null ? connections.getSoReceiveBufferSize() : inbound.getSoReceiveBufferSize();
            AbstractClientConnectionFactory connectionFactory = createClientConnectionFactory(
                    hostname, inbound.getPort(), serializer, deserializer, soTimeout, soReceiveBufferSize, null
            );
            connectionFactory.setBeanName(PREFIX_CONNECTION_FACTORY + subsystem.getName() + SUFFIX_INBOUND);
            connectionFactory.setComponentName(PREFIX_CONNECTION_FACTORY + subsystem.getName() + SUFFIX_INBOUND);
            connectionFactory.setSingleUse(false);
            connectionFactory.setApplicationEventPublisher(applicationEventPublisher);
            connectionFactory.setInterceptorFactoryChain(createInterceptorChain());
            registerBean(PREFIX_CONNECTION_FACTORY + subsystem.getName() + SUFFIX_INBOUND, connectionFactory);

            TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
            adapter.setOutputChannel(inboundChannel);
            adapter.setConnectionFactory(connectionFactory);
            adapter.setTaskScheduler(taskScheduler);
            adapter.setClientMode(true);
            adapter.setRetryInterval(10000);
            adapter.start();
            registerBean(PREFIX_CHANNEL_ADAPTER + subsystem.getName() + SUFFIX_INBOUND, adapter);

            BOOT_LOGGER.info("[{}] Inbound  TCP/IP connection configured as client: Hostname [{}], port [{}]", subsystem.getName(), inbound.getHostname(), inbound.getPort());
        } else {
            throw new ConfigurationException(format("Mode [%s] for subsystem [%s] inbound not supported. Please use [server] or [client]", inbound.getMode(), subsystem.getName()));
        }
    }

    private TcpConnectionInterceptorFactoryChain createInterceptorChain() {
        TcpConnectionInterceptorFactoryChain chain = new TcpConnectionInterceptorFactoryChain();
        chain.setInterceptors(new TcpConnectionInterceptorFactory[]{() -> new TenantInterception(applicationEventPublisher)});
        return chain;
    }

    private void setupOutbound(
            Connections connections, Subsystem subsystem,
            TcpMessageMapper tcpMessageMapper, Channels channels,
            TaskScheduler taskScheduler, Serializer serializer,
            Deserializer deserializer) {

        Subsystem.Outbound outbound = subsystem.getOutbound();
        if (outbound.getMode() == Subsystem.MODE.server) {

            AbstractServerConnectionFactory connectionFactory =
                    createOutboundServerConnectionFactory(
                            connections, subsystem.getName(), outbound, tcpMessageMapper,
                            serializer, deserializer);
            connectionFactory.setBeanName(PREFIX_CONNECTION_FACTORY + outbound.getIdentifiedByValue() + SUFFIX_OUTBOUND);
            connectionFactory.setComponentName(PREFIX_CONNECTION_FACTORY + outbound.getIdentifiedByValue() + SUFFIX_OUTBOUND);

            TcpSendingMessageHandler sendingMessageHandler = createSendingMessageHandler(connectionFactory);
            sendingMessageHandler.setTaskScheduler(taskScheduler);
            DirectChannel channel = createEnrichedOutboundChannel(sendingMessageHandler);

            // This adapter is only required to let the SCF work as a CCF!!
            TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
            adapter.setConnectionFactory(connectionFactory);
            adapter.setOutputChannel(channel);
            adapter.setClientMode(false);
            adapter.setTaskScheduler(taskScheduler);
            adapter.setRetryInterval(10000);
            adapter.start();

            channels.addOutboundChannel(PREFIX_ENRICHED_OUTBOUND_CHANNEL + outbound.getIdentifiedByValue(), channel);
            registerBean(PREFIX_CONNECTION_FACTORY + outbound.getIdentifiedByValue() + SUFFIX_OUTBOUND, connectionFactory);
            registerBean(PREFIX_CHANNEL_ADAPTER + subsystem.getName() + SUFFIX_OUTBOUND, adapter);
            registerBean(PREFIX_SENDING_MESSAGE_HANDLER + subsystem.getName() + SUFFIX_OUTBOUND, sendingMessageHandler);
            registerBean(PREFIX_ENRICHED_OUTBOUND_CHANNEL + outbound.getIdentifiedByValue(), channel);

            BOOT_LOGGER.info("[{}] Outbound TCP/IP connection configured as server: Port [{}]", subsystem.getName(), outbound.getPort());
        } else if (outbound.getMode() == Subsystem.MODE.client) {

            String hostname = outbound.getHostname() == null ? connections.getHostname() : outbound.getHostname();
            Integer soTimeout = outbound.getSoTimeout() == null ? connections.getSoTimeout() : outbound.getSoTimeout();
            Integer soSendBufferSize = outbound.getSoSendBufferSize() == null ? connections.getSoSendBufferSize() : outbound.getSoSendBufferSize();
            AbstractClientConnectionFactory connectionFactory = createClientConnectionFactory(
                    hostname, outbound.getPort(), serializer, deserializer, soTimeout, null, soSendBufferSize
            );
            connectionFactory.setBeanName(PREFIX_CONNECTION_FACTORY + outbound.getIdentifiedByValue() + SUFFIX_OUTBOUND);
            connectionFactory.setComponentName(PREFIX_CONNECTION_FACTORY + outbound.getIdentifiedByValue() + SUFFIX_OUTBOUND);
            connectionFactory.setSingleUse(false);
            connectionFactory.setApplicationEventPublisher(applicationEventPublisher);
            registerBean(PREFIX_CONNECTION_FACTORY + outbound.getIdentifiedByValue() + SUFFIX_OUTBOUND, connectionFactory);

            TcpSendingMessageHandler sendingMessageHandler = createSendingMessageHandler(connectionFactory);
            sendingMessageHandler.setClientMode(true);
            sendingMessageHandler.setTaskScheduler(taskScheduler);
            registerBean(PREFIX_SENDING_MESSAGE_HANDLER + outbound.getIdentifiedByValue() + SUFFIX_OUTBOUND, sendingMessageHandler);

            DirectChannel channel = createEnrichedOutboundChannel(sendingMessageHandler);
            registerBean(PREFIX_ENRICHED_OUTBOUND_CHANNEL + outbound.getIdentifiedByValue(), channel);

            channels.addOutboundChannel(PREFIX_ENRICHED_OUTBOUND_CHANNEL + outbound.getIdentifiedByValue(), channel);

            TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
            adapter.setConnectionFactory(connectionFactory);
            adapter.setClientMode(true);
            adapter.setOutputChannel(channel);
            adapter.setTaskScheduler(taskScheduler);
            adapter.setRetryInterval(10000);
            adapter.start();
            registerBean(PREFIX_CHANNEL_ADAPTER + subsystem.getName() + SUFFIX_OUTBOUND, adapter);

            BOOT_LOGGER.info("[{}] Outbound TCP/IP connection configured as client: Hostname [{}], port [{}]", subsystem.getName(), hostname, outbound.getPort());
        } else {
            throw new ConfigurationException(format("Mode [%s] for outbound not supported. Please use [server] or [client]", outbound.getMode()));
        }
    }

    @Bean
    @DependsOn("connections")
    Channels channels(
            Connections connections, TcpMessageMapper tcpMessageMapper,
            MessageChannel inboundChannel,
            Serializer serializer,
            Deserializer deserializer,
            TaskScheduler taskScheduler,
            @Value("${owms.driver.type:NA}") String type
            ) {
        BOOT_LOGGER.info("Driver instantiated in [{}] mode", "NA".equals(type) ? "N/A" : type);
        Channels channels =  new Channels();
        for(Subsystem subsystem : connections.getSubsystems()) {
            if (subsystem.getDuplex() != null) {
                setupDuplex(connections, subsystem, tcpMessageMapper, inboundChannel, channels, taskScheduler, serializer, deserializer);
            } else {
                setupInbound(connections, subsystem, tcpMessageMapper, inboundChannel, taskScheduler, serializer, deserializer);
                setupOutbound(connections, subsystem, tcpMessageMapper, channels, taskScheduler, serializer, deserializer);
            }
        }
        return channels;
    }

    @Bean
    public ThreadPoolTaskScheduler threadPoolTaskScheduler(){
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(5);
        threadPoolTaskScheduler.setThreadNamePrefix("ThreadPoolTaskScheduler");
        return threadPoolTaskScheduler;
    }

    private void setupDuplex(Connections connections, Subsystem subsystem,
            TcpMessageMapper tcpMessageMapper, MessageChannel inboundChannel,
            Channels channels, TaskScheduler taskScheduler,
            Serializer serializer, Deserializer deserializer) {

        Subsystem.Duplex duplex = subsystem.getDuplex();

        if (subsystem.getDuplex().getMode() == Subsystem.MODE.client) {

            // Duplex mode !!!
            AbstractConnectionFactory clientConnectionFactory =
                    createOutboundConnectionFactory(
                            Subsystem.MODE.client,
                            subsystem.getName(),
                            connections, subsystem.getDuplex(),
                            tcpMessageMapper, serializer, deserializer);

            clientConnectionFactory.setBeanName(PREFIX_CONNECTION_FACTORY + subsystem.getDuplex().getIdentifiedByValue() + SUFFIX_OUTBOUND);
            clientConnectionFactory.setComponentName(PREFIX_CONNECTION_FACTORY + subsystem.getDuplex().getIdentifiedByValue() + SUFFIX_OUTBOUND);
            clientConnectionFactory.setSingleUse(false);
            clientConnectionFactory.setInterceptorFactoryChain(createInterceptorChain());
            registerBean(PREFIX_CONNECTION_FACTORY + subsystem.getName() + SUFFIX_OUTBOUND, clientConnectionFactory);

            // This adapter is only required to let the SCF work as a CCF!!
            TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
            adapter.setConnectionFactory(clientConnectionFactory);
            adapter.setOutputChannel(inboundChannel);
            adapter.setTaskScheduler(taskScheduler);
            adapter.setClientMode(true);
            adapter.setRetryInterval(10000);
            adapter.start();
            registerBean(PREFIX_CHANNEL_ADAPTER + subsystem.getName() + SUFFIX_OUTBOUND, adapter);

            // ---- Sending part
            TcpSendingMessageHandler sendingMessageHandler = createSendingMessageHandler(clientConnectionFactory);
            sendingMessageHandler.setClientMode(true);
            sendingMessageHandler.setRetryInterval(10000);
            sendingMessageHandler.setLoggingEnabled(true);
            sendingMessageHandler.setTaskScheduler(taskScheduler);
            registerBean(PREFIX_SENDING_MESSAGE_HANDLER + subsystem.getName() + SUFFIX_OUTBOUND, sendingMessageHandler);
            sendingMessageHandler.start();

            DirectChannel channel = createEnrichedOutboundChannel(sendingMessageHandler);
            registerBean(PREFIX_ENRICHED_OUTBOUND_CHANNEL + duplex.getIdentifiedByValue(), channel);

            channels.addOutboundChannel(PREFIX_ENRICHED_OUTBOUND_CHANNEL + duplex.getIdentifiedByValue(), channel);
            String host = duplex.getHostname() == null ? connections.getHostname() : duplex.getHostname();
            BOOT_LOGGER.info("[{}] Duplex   TCP/IP connection configured with client: Hostname [{}], port [{}]", subsystem.getName(), host, duplex.getPort());
        } else {
            AbstractConnectionFactory connectionFactory =
                    createOutboundConnectionFactory(
                            Subsystem.MODE.server,
                            subsystem.getName(),
                            connections, subsystem.getDuplex(),
                            tcpMessageMapper, serializer, deserializer);
            connectionFactory.setInterceptorFactoryChain(createInterceptorChain());
            connectionFactory.setBeanName(PREFIX_CONNECTION_FACTORY + subsystem.getDuplex().getIdentifiedByValue() + SUFFIX_OUTBOUND);
            connectionFactory.setComponentName(PREFIX_CONNECTION_FACTORY + subsystem.getDuplex().getIdentifiedByValue() + SUFFIX_OUTBOUND);
            registerBean(PREFIX_CONNECTION_FACTORY + subsystem.getName() + SUFFIX_OUTBOUND, connectionFactory);

            // This adapter is only required to let the SCF work as a CCF!!
            TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
            adapter.setConnectionFactory(connectionFactory);
            adapter.setOutputChannel(inboundChannel);
            adapter.setTaskScheduler(taskScheduler);
            adapter.setClientMode(false);
            adapter.setRetryInterval(10000);
            registerBean(PREFIX_CHANNEL_ADAPTER + subsystem.getName() + SUFFIX_OUTBOUND, adapter);
            adapter.start();

            TcpSendingMessageHandler sendingMessageHandler = new TcpSendingMessageHandler();
            sendingMessageHandler.setConnectionFactory(connectionFactory);
            sendingMessageHandler.setClientMode(false);
            registerBean(PREFIX_SENDING_MESSAGE_HANDLER + subsystem.getName() + SUFFIX_OUTBOUND, sendingMessageHandler);
            //TcpSendingMessageHandler sendingMessageHandler = createSendingMessageHandler(connectionFactory);
            sendingMessageHandler.start();

            DirectChannel channel = createEnrichedOutboundChannel(sendingMessageHandler);
            registerBean(PREFIX_ENRICHED_OUTBOUND_CHANNEL + duplex.getIdentifiedByValue(), channel);

            channels.addOutboundChannel(PREFIX_ENRICHED_OUTBOUND_CHANNEL + duplex.getIdentifiedByValue(), channel);
            BOOT_LOGGER.info("[{}] Outbound TCP/IP connection configured as server: Port [{}]", subsystem.getName(), duplex.getPort());
        }
    }

    /*~ --------------- MessageChannels ------------ */
    @Bean
    MessageChannel commonExceptionChannel() {
        return MessageChannels.executor(Executors.newCachedThreadPool()).get();
    }

    @Bean
    MessageChannel transformerOutputChannel() {
        return MessageChannels.executor(Executors.newCachedThreadPool()).get();
    }

    @Bean
    MessageChannel inboundChannel() {
        return MessageChannels.executor(Executors.newCachedThreadPool()).get();
    }

    @Bean
    MessageChannel outboundChannel() {
        return MessageChannels.direct().get();
    }

    private DirectChannel createEnrichedOutboundChannel(
            AbstractMessageHandler messageHandler) {
        DirectChannel channel = MessageChannels.direct().get();
        channel.subscribe(messageHandler);
        return channel;
    }

    /*~ --------- Serializer / Deserializer -------- */
    @Bean
    Serializer serializer() {
        return new ByteArrayLfSerializer();
    }

    @Bean
    Deserializer deserializer() {
        return new ByteArrayLfSerializer();
    }

    /*~ ----------------   Converter---------------- */
    @Bean
    TcpMessageMapper tcpMessageMapper() {
        return new TcpMessageMapper();
    }

    @Bean
    ByteArrayMessageConverter byteArrayMessageConverter() {
        return new ByteArrayMessageConverter();
    }

    @Bean
    @ConditionalOnMissingBean(Transformable.class)
    Transformable telegramTransformer(List<TelegramDeserializer> deserializers, TelegramResolver telegramResolver) {
        return new TelegramTransformer(telegramResolver, deserializers);
    }

    /*~ -------------------- Flows ----------------- */
    @Bean
    IntegrationFlow inboundFlow(
            MessageChannel inboundChannel,
            MessageChannel transformerOutputChannel,
            Transformable telegramTransformer) {
        return IntegrationFlows
                .from(inboundChannel)
                .transform(telegramTransformer)
                .channel(transformerOutputChannel)
                .route("messageRouter")
                .get();
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }
}
