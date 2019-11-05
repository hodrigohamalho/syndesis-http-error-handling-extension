/*
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.syndesis.extension.error;

import io.syndesis.extension.api.annotations.Action;
import io.syndesis.extension.api.annotations.ConfigurationProperty;
import io.syndesis.extension.api.annotations.DataShape;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.Message;
import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@Action(
    id = "error-handler",
    name = "Handle errors",
    description = "Perform error handling, when added any subsequent steps in a flow will be handled by this step",
    entrypoint = "direct:handle-errors",
    inputDataShape = @DataShape(kind = "none"),
    outputDataShape = @DataShape(kind = "none"))
public final class ErrorHandlingAction {

    private static Log log = LogFactory.getLog(ErrorHandlingAction.class);

    static final String STATUS_CODE = ErrorHandlingAction.class.getName() + ".statusCode";

    @ConfigurationProperty(
        name = "statusCode",
        description = "HTTP Status code to set",
        displayName = "Status code",
        type = "number",
        required = true,
        defaultValue = "400")
    private Integer statusCode;
    
    private final static Integer MAXIMUM_REDELIVERY=2;
    private final static Integer MAXIMUM_REDELIVERY_DELAY=2;
    private final static String DEAD_LETTER_ADDRESS="amqp-amqp-0-0://queue:DLQ";

    public RedeliveryPolicy redeliveryPolicy() {
        RedeliveryPolicy redeliveryPolicy = new RedeliveryPolicy();
        redeliveryPolicy.setMaximumRedeliveries(MAXIMUM_REDELIVERY);
	    redeliveryPolicy.setMaximumRedeliveryDelay(MAXIMUM_REDELIVERY_DELAY);
        redeliveryPolicy.setRedeliveryDelay(MAXIMUM_REDELIVERY_DELAY);
	    return redeliveryPolicy;
	}
	
    public ErrorHandlingAction(final CamelContext context) {
        System.out.println("ErrorHandlingAction --> Version 5.0");
        final OnExceptionDefinition onException = new OnExceptionDefinition(Throwable.class).handled(true);

        final DeadLetterChannelBuilder builder = new DeadLetterChannelBuilder();
        builder.setExceptionPolicyStrategy((exceptionPolicies, exchange, exception) -> onException);
        builder.setOnExceptionOccurred(ErrorHandlingAction::handleErrors);
        builder.logHandled(true);
        builder.setRedeliveryPolicy(redeliveryPolicy());
        builder.setDeadLetterUri(DEAD_LETTER_ADDRESS);
        
        context.getRouteDefinitions().forEach(route -> route.setErrorHandlerBuilder(builder));
    }

    public void setStatusCode(final Integer statusCode) {
        this.statusCode = statusCode;
    }

    @Handler
    public void setupErrorHandling(final Exchange exchange) {
        final Message in = exchange.getIn();
        in.setHeader(STATUS_CODE, statusCode);
    }

    static void handleErrors(final Exchange exchange) {
        System.out.println("Caneta azul, azul caneta 5.0");
        final Message in = exchange.getIn();
        log.info("Retry count: "+in.getHeader(Exchange.REDELIVERY_COUNTER));
        final Integer statusCode = in.getHeader(STATUS_CODE, Integer.class);
        if (statusCode == null) {
            exchange.setProperty(Exchange.ERRORHANDLER_HANDLED, Boolean.FALSE);
            return;
        }

        in.setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode);
        in.setBody(null);
    }

}
