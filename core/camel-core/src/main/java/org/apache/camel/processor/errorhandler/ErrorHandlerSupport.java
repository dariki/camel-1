/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor.errorhandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.processor.ErrorHandler;
import org.apache.camel.processor.exceptionpolicy.DefaultExceptionPolicyStrategy;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyKey;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyStrategy;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.support.ChildServiceSupport;

/**
 * Support class for {@link ErrorHandler} implementations.
 */
public abstract class ErrorHandlerSupport extends ChildServiceSupport implements ErrorHandler {

    protected final Map<ExceptionPolicyKey, OnExceptionDefinition> exceptionPolicies = new LinkedHashMap<>();
    protected ExceptionPolicyStrategy exceptionPolicy = createDefaultExceptionPolicyStrategy();

    public void addExceptionPolicy(RouteContext routeContext, OnExceptionDefinition exceptionType) {
        if (routeContext != null) {
            // add error handler as child service so they get lifecycle handled
            Processor errorHandler = routeContext.getOnException(exceptionType.getId());
            if (errorHandler != null) {
                addChildService(errorHandler);
            }

            // load exception classes
            List<Class<? extends Throwable>> list = null;
            if (exceptionType.getExceptions() != null && !exceptionType.getExceptions().isEmpty()) {
                list = createExceptionClasses(exceptionType, routeContext.getCamelContext().getClassResolver());
                for (Class<? extends Throwable> clazz : list) {
                    String routeId = null;
                    // only get the route id, if the exception type is route scoped
                    if (exceptionType.isRouteScoped()) {
                        RouteDefinition route = ProcessorDefinitionHelper.getRoute(exceptionType);
                        if (route != null) {
                            routeId = route.getId();
                        }
                    }
                    ExceptionPolicyKey key = new ExceptionPolicyKey(routeId, clazz, exceptionType.getOnWhen());
                    exceptionPolicies.put(key, exceptionType);
                }
            }
        }
    }

    protected List<Class<? extends Throwable>> createExceptionClasses(OnExceptionDefinition exceptionType, ClassResolver resolver) {
        List<String> list = exceptionType.getExceptions();
        List<Class<? extends Throwable>> answer = new ArrayList<>(list.size());
        for (String name : list) {
            try {
                Class<? extends Throwable> type = resolver.resolveMandatoryClass(name, Throwable.class);
                answer.add(type);
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
        return answer;
    }

    /**
     * Attempts to find the best suited {@link OnExceptionDefinition} to be used for handling the given thrown exception.
     *
     * @param exchange  the exchange
     * @param exception the exception that was thrown
     * @return the best exception type to handle this exception, <tt>null</tt> if none found.
     */
    protected OnExceptionDefinition getExceptionPolicy(Exchange exchange, Throwable exception) {
        if (exceptionPolicy == null) {
            throw new IllegalStateException("The exception policy has not been set");
        }

        return exceptionPolicy.getExceptionPolicy(exceptionPolicies, exchange, exception);
    }

    /**
     * Sets the strategy to use for resolving the {@link OnExceptionDefinition} to use
     * for handling thrown exceptions.
     */
    public void setExceptionPolicy(ExceptionPolicyStrategy exceptionPolicy) {
        if (exceptionPolicy != null) {
            this.exceptionPolicy = exceptionPolicy;
        }
    }

    /**
     * Creates the default exception policy strategy to use.
     */
    public static ExceptionPolicyStrategy createDefaultExceptionPolicyStrategy() {
        return new DefaultExceptionPolicyStrategy();
    }

    /**
     * Whether this error handler supports transacted exchanges or not.
     */
    public abstract boolean supportTransacted();

    /**
     * Whether this error handler handles exhausted errors by moving the exchange to a dead letter channel.
     */
    public boolean isDeadLetterChannel() {
        return false;
    }

    /**
     * Gets the output
     */
    public abstract Processor getOutput();

}
