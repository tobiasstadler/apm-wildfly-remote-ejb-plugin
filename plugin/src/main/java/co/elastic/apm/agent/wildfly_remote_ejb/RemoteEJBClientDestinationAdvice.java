/*
   Copyright 2021 Tobias Stadler

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package co.elastic.apm.agent.wildfly_remote_ejb;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Span;
import net.bytebuddy.asm.Advice;
import org.jboss.ejb.client.EJBClientInvocationContext;

import java.net.URI;

public class RemoteEJBClientDestinationAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExitSendRequestInitial(@Advice.This EJBClientInvocationContext ejbClientInvocationContext) {
        Span span = ElasticApm.currentSpan();
        if (span.getId().isEmpty()) {
            return;
        }

        URI destination = ejbClientInvocationContext.getDestination();
        if (destination == null) {
            return;
        }

        span.setDestinationAddress(destination.getHost(), destination.getPort());

        if (destination.getPort() > 0) {
            span.setDestinationService(destination.getHost() + ":" + destination.getPort());
        } else {
            span.setDestinationService(destination.getHost());
        }
    }
}
