/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.transport.jms;

import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.naming.Context;

import org.apache.cxf.Bus;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.configuration.ConfiguredBeanLocator;
import org.apache.cxf.service.model.BindingInfo;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.service.model.ServiceInfo;
import org.apache.cxf.transport.jms.uri.JMSEndpoint;
import org.apache.cxf.transport.jms.util.JMSDestinationResolver;
import org.apache.cxf.transport.jms.util.JndiHelper;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

public final class JMSConfigFactory {
    private static final Logger LOG = LogUtils.getL7dLogger(JMSConfigFactory.class);
    
    private JMSConfigFactory() {
    }
    
    public static JMSConfiguration createFromEndpointInfo(Bus bus, EndpointInfo endpointInfo,
                                                          EndpointReferenceType target) {
        JMSEndpoint jmsEndpoint = new JMSEndpoint(endpointInfo, target);
        return createFromEndpoint(bus, jmsEndpoint);
    }

    /**
     * @param bus
     * @param endpointInfo
     * @return
     */
    public static JMSConfiguration createFromEndpoint(Bus bus, JMSEndpoint endpoint) {
        JMSConfiguration jmsConfig = new JMSConfiguration();
        
        int deliveryMode = endpoint.getDeliveryMode() 
            == org.apache.cxf.transport.jms.uri.JMSEndpoint.DeliveryModeType.PERSISTENT
            ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT;
        jmsConfig.setDeliveryMode(deliveryMode);
        
        jmsConfig.setPriority(endpoint.getPriority());
        
        jmsConfig.setReconnectOnException(endpoint.isReconnectOnException());
        
        jmsConfig.setExplicitQosEnabled(true);
        jmsConfig.setMessageType(endpoint.getMessageType().value());
        boolean pubSubDomain = endpoint.getJmsVariant().contains(JMSEndpoint.TOPIC);
        jmsConfig.setPubSubDomain(pubSubDomain);

        jmsConfig.setDurableSubscriptionName(endpoint.getDurableSubscriptionName());
        // TODO We might need a separate config here
        jmsConfig.setDurableSubscriptionClientId(endpoint.getDurableSubscriptionName());

        jmsConfig.setReceiveTimeout(endpoint.getReceiveTimeout());
        jmsConfig.setTimeToLive(endpoint.getTimeToLive());
        jmsConfig.setSessionTransacted(endpoint.isSessionTransacted());
        if (!endpoint.isUseConduitIdSelector()) {
            jmsConfig.setUseConduitIdSelector(endpoint.isUseConduitIdSelector());
        }
        jmsConfig.setConduitSelectorPrefix(endpoint.getConduitIdSelectorPrefix());
        jmsConfig.setUserName(endpoint.getUsername());
        jmsConfig.setPassword(endpoint.getPassword());
        if (endpoint.getJndiURL() != null) {
            // Configure Connection Factory using jndi
            jmsConfig.setJndiEnvironment(JMSConfigFactory.getInitialContextEnv(endpoint));
            jmsConfig.setConnectionFactoryName(endpoint.getJndiConnectionFactoryName());
        } else {
            ConfiguredBeanLocator locator = bus.getExtension(ConfiguredBeanLocator.class);
            if (endpoint.getConnectionFactory() != null) {
                jmsConfig.setConnectionFactory(endpoint.getConnectionFactory());
            } else if (locator != null) {
                // Configure ConnectionFactory using locator
                // Lookup connectionFactory in context like blueprint
                ConnectionFactory cf = locator.getBeanOfType(endpoint.getJndiConnectionFactoryName(),
                                                             ConnectionFactory.class);
                if (cf != null) {
                    jmsConfig.setConnectionFactory(cf);
                }
            }
        }

        boolean resolveUsingJndi = endpoint.getJmsVariant().contains(JMSEndpoint.JNDI);
        if (resolveUsingJndi) {
            // Setup Destination jndi destination resolver
            JndiHelper jt = new JndiHelper(JMSConfigFactory.getInitialContextEnv(endpoint));
            final JMSDestinationResolver jndiDestinationResolver = new JMSDestinationResolver();
            jndiDestinationResolver.setJndiTemplate(jt);
            jmsConfig.setDestinationResolver(jndiDestinationResolver);
            jmsConfig.setTargetDestination(endpoint.getDestinationName());
            setReplyDestination(jmsConfig, endpoint);
        } else {
            // Use the default dynamic destination resolver
            jmsConfig.setTargetDestination(endpoint.getDestinationName());
            setReplyDestination(jmsConfig, endpoint);
        }
        
        String requestURI = endpoint.getRequestURI();
        jmsConfig.setRequestURI(requestURI);
        
        String targetService = endpoint.getTargetService();
        jmsConfig.setTargetService(targetService);
        return jmsConfig;
    }

    private static void setReplyDestination(JMSConfiguration jmsConfig, JMSEndpoint endpoint) {
        if (endpoint.getReplyToName() != null)  {
            jmsConfig.setReplyDestination(endpoint.getReplyToName());
            jmsConfig.setReplyPubSubDomain(false);
        } else if (endpoint.getTopicReplyToName() != null) {
            jmsConfig.setReplyDestination(endpoint.getTopicReplyToName());
            jmsConfig.setReplyPubSubDomain(true);
        }
    }

    public static <T> T getWSDLExtensor(EndpointInfo ei, Class<T> cls) {
        ServiceInfo si = ei.getService();
        BindingInfo bi = ei.getBinding();
        
        Object o = ei.getExtensor(cls);
        if (o == null && si != null) {
            o = si.getExtensor(cls);
        }
        if (o == null && bi != null) {
            o = bi.getExtensor(cls);
        }
        
        if (o == null) {
            return null;
        }
        if (cls.isInstance(o)) {
            return cls.cast(o);
        }
        return null;
    }
    
    public static Properties getInitialContextEnv(JMSEndpoint endpoint) {
        Properties env = new Properties();
        if (endpoint.getJndiInitialContextFactory() != null) {
            env.put(Context.INITIAL_CONTEXT_FACTORY, endpoint.getJndiInitialContextFactory());
        }
        if (endpoint.getJndiURL() != null) {
            env.put(Context.PROVIDER_URL, endpoint.getJndiURL());
        }
        for (Map.Entry<String, String> ent : endpoint.getJndiParameters().entrySet()) {
            env.put(ent.getKey(), ent.getValue());
        }
        if (LOG.isLoggable(Level.FINE)) {
            Enumeration<?> props = env.propertyNames();
            while (props.hasMoreElements()) {
                String name = (String)props.nextElement();
                String value = env.getProperty(name);
                LOG.log(Level.FINE, "Context property: " + name + " | " + value);
            }
        }
        return env;
    }
}