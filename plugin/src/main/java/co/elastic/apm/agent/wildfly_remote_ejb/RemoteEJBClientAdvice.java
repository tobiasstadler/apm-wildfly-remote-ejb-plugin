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
import co.elastic.apm.api.Outcome;
import co.elastic.apm.api.Scope;
import co.elastic.apm.api.Span;
import net.bytebuddy.asm.Advice;
import org.jboss.ejb.client.EJBLocator;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

public class RemoteEJBClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnterInvoke(@Advice.FieldValue("locatorRef") AtomicReference<EJBLocator<?>> locatorRef, @Advice.Argument(1) Method method) {
        Span parent = ElasticApm.currentSpan();
        if (parent.getId().isEmpty()) {
            return null;
        }

        Span span = parent.startSpan("external", "ejb", "call")
                .setName("Call " + locatorRef.get().getViewType().getSimpleName() + "#" + method.getName());

        return span.activate();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExitInvoke(@Advice.Enter Object scopeOrNull, @Advice.Thrown Throwable t) {
        if (scopeOrNull == null) {
            return;
        }

        try {
            Span span = ElasticApm.currentSpan();
            if (t != null) {
                span.captureException(t);
                span.setOutcome(Outcome.FAILURE);
            } else {
                span.setOutcome(Outcome.SUCCESS);
            }
            span.end();
        } finally {
            ((Scope) scopeOrNull).close();
        }
    }
}
