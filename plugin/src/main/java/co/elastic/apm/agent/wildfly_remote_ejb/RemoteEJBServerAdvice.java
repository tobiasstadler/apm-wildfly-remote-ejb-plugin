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
import co.elastic.apm.api.Transaction;
import net.bytebuddy.asm.Advice;
import org.jboss.as.ee.component.ComponentView;
import org.jboss.ejb.server.InvocationRequest;

import java.lang.reflect.Method;

public class RemoteEJBServerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnterInvokeMethod(@Advice.Argument(0) ComponentView componentView, @Advice.Argument(1) Method method, @Advice.Argument(3) InvocationRequest.Resolved invocationRequest) {
        Transaction transaction = ElasticApm.startTransactionWithRemoteParent(new MapTextHeaderAccessor(invocationRequest.getAttachments()))
                .setType(Transaction.TYPE_REQUEST)
                .setName(componentView.getViewClass().getSimpleName() + "#" + method.getName())
                .setFrameworkName("EJB");

        System.out.println("Set ");

        return transaction.activate();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExitInvokeMethod(@Advice.Enter Object scope, @Advice.Thrown Throwable t) {
        try {
            Transaction transaction = ElasticApm.currentTransaction();
            if (t != null) {
                transaction.captureException(t);
                transaction.setOutcome(Outcome.FAILURE);
            } else {
                transaction.setOutcome(Outcome.SUCCESS);
            }
            transaction.end();
        } finally {
            ((Scope) scope).close();
        }
    }
}
