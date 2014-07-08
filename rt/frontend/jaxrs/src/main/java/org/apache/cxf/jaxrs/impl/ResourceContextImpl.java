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
package org.apache.cxf.jaxrs.impl;

import javax.ws.rs.container.ResourceContext;

import org.apache.cxf.jaxrs.lifecycle.PerRequestResourceProvider;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.apache.cxf.jaxrs.provider.ServerProviderFactory;
import org.apache.cxf.message.Message;

public class ResourceContextImpl implements ResourceContext {

    private ClassResourceInfo cri;
    private Class<?> subClass;
    private Message m;
    public ResourceContextImpl(Message m, OperationResourceInfo ori) {
        this.m = m;
        this.cri = ori.getClassResourceInfo();
        this.subClass = ori.getMethodToInvoke().getReturnType();
    }
    
    @Override
    public <T> T getResource(Class<T> cls) {
        T resource = cls.cast(new PerRequestResourceProvider(cls).getInstance(m));
        return doInitResource(cls, resource);
    }
    
    public <T> T initResource(T resource) {
        return doInitResource(resource.getClass(), resource);
    }

    private <T> T doInitResource(Class<?> cls, T resource) {
        ClassResourceInfo sub = cri.getSubResource(subClass, cls, resource, true, m);
        sub.initBeanParamInfo(ServerProviderFactory.getInstance(m));
        sub.injectContexts(resource, m.getExchange().get(OperationResourceInfo.class), m);
        return resource;
    }
}
