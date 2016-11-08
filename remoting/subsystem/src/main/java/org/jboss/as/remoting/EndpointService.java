/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.remoting;

import java.io.IOException;

import org.jboss.as.remoting.logging.RemotingLogger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.EndpointBuilder;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;
/**
 * An MSC service for Remoting endpoints.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class EndpointService implements Service<Endpoint> {

    protected final String endpointName;
    protected volatile Endpoint endpoint;
    protected final OptionMap optionMap;
    private final InjectedValue<XnioWorker> worker = new InjectedValue<>();

    public EndpointService(String nodeName, EndpointType type, final OptionMap optionMap) {
        if (nodeName == null) {
            nodeName = "remote";
        }
        endpointName = type == EndpointType.SUBSYSTEM ? nodeName : nodeName + ":" + type;
        this.optionMap = optionMap;
    }

    InjectedValue<XnioWorker> getWorker() {
        return worker;
    }

    /** {@inheritDoc} */
    public void start(final StartContext context) throws StartException {
        final Endpoint endpoint;
        final EndpointBuilder builder = Endpoint.builder();
        builder.setEndpointName(endpointName);
        builder.setXnioWorker(worker.getValue());
        try {
            endpoint = builder.build();
        } catch (IOException e) {
            throw RemotingLogger.ROOT_LOGGER.couldNotStart(e);
        }
        // TODO: this is not really how we want to do this though; we want to set a context-sensitive default
        Endpoint.ENDPOINT_CONTEXT_MANAGER.setGlobalDefault(endpoint);
        // Reuse the options for the remote connection factory for now
        this.endpoint = endpoint;
    }

    /** {@inheritDoc} */
    public void stop(final StopContext context) {
        context.asynchronous();
        final Endpoint endpoint = this.endpoint;
        this.endpoint = null;
        try {
            endpoint.closeAsync();
        } finally {
            endpoint.addCloseHandler((closed, exception) -> {
                context.complete();
                // TODO fix this later
                Endpoint.ENDPOINT_CONTEXT_MANAGER.setGlobalDefault(null);
            });
        }
    }

    /** {@inheritDoc} */
    public Endpoint getValue() throws IllegalStateException {
        final Endpoint endpoint = this.endpoint;
        if (endpoint == null) throw RemotingLogger.ROOT_LOGGER.endpointEmpty();
        return endpoint;
    }

    public enum EndpointType {
        MANAGEMENT,
        SUBSYSTEM
    }
}
