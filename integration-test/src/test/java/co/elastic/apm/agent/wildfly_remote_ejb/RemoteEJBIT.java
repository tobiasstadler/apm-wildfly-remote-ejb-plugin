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
import co.elastic.apm.api.Scope;
import co.elastic.apm.api.Transaction;
import co.elastic.apm.attach.ElasticApmAttacher;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.ClearType;
import org.mockserver.model.Format;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

@Testcontainers
class RemoteEJBIT {

    private static final Network NETWORK = Network.newNetwork();

    @Container
    private static final GenericContainer<?> MOCK_SERVER = new GenericContainer<>(DockerImageName.parse("mockserver/mockserver:mockserver-5.13.0"))
            .withNetwork(NETWORK)
            .withNetworkAliases("apm-server")
            .withExposedPorts(1080)
            .waitingFor(Wait.forHttp("/mockserver/status").withMethod("PUT").forStatusCode(200));

    @Container
    private static final GenericContainer<?> WILDFLY = new GenericContainer<>(DockerImageName.parse("quay.io/wildfly/wildfly:26.0.1.Final"))
            .withNetwork(NETWORK)
            .withExposedPorts(8080)
            .withFileSystemBind("target/test-classes/application-users.properties", "/opt/jboss/wildfly/standalone/configuration/application-users.properties")
            .withFileSystemBind("target/apm-wildfly-remote-ejb-plugin-it.war", "/opt/jboss/wildfly/standalone/deployments/apm-wildfly-remote-ejb-plugin-it.war")
            .withFileSystemBind("target/apm-agent-attach-cli.jar", "/opt/jboss/wildfly/apm-agent-attach-cli.jar")
            .withFileSystemBind("target/apm-plugins/apm-wildfly-remote-ejb-plugin.jar", "/opt/jboss/wildfly/apm-plugins/apm-wildfly-remote-ejb-plugin.jar")
            .waitingFor(Wait.forLogMessage(".*WFLYSRV0025.*", 1))
            .dependsOn(MOCK_SERVER);

    private static MockServerClient MOCK_SERVER_CLIENT;

    @BeforeAll
    static void setUp() throws IOException, InterruptedException {
        MOCK_SERVER_CLIENT = new MockServerClient(MOCK_SERVER.getContainerIpAddress(), MOCK_SERVER.getMappedPort(1080));
        MOCK_SERVER_CLIENT.when(request("/")).respond(response().withStatusCode(200).withBody(json("{\"version\": \"7.13.0\"}")));
        MOCK_SERVER_CLIENT.when(request("/config/v1/agents")).respond(response().withStatusCode(403));
        MOCK_SERVER_CLIENT.when(request("/intake/v2/events")).respond(response().withStatusCode(200));

        WILDFLY.execInContainer(
                "java", "-jar", "/opt/jboss/wildfly/apm-agent-attach-cli.jar",
                "--include-all",
                "--config", "server_url=http://apm-server:1080",
                "--config", "report_sync=true",
                "--config", "disable_metrics=*",
                "--config", "plugins_dir=/opt/jboss/wildfly/apm-plugins",
                "--config", "application_packages=co.elastic.apm.agent.wildfly_remote_ejb",
                "--config", "classes_excluded_from_instrumentation_default=(?-i)org.infinispan*,(?-i)org.apache.xerces*,(?-i)io.undertow.core*,(?-i)org.eclipse.jdt.ecj*,(?-i)org.wildfly.extension.*,(?-i)org.wildfly.security*"
        );

        Map<String, String> configuration = new HashMap<>();
        configuration.put("server_url", "http://" + MOCK_SERVER.getContainerIpAddress() + ":" + MOCK_SERVER.getMappedPort(1080));
        configuration.put("report_sync", "true");
        configuration.put("disable_metrics", "*");
        configuration.put("plugins_dir", "target/apm-plugins");
        configuration.put("application_packages", "co.elastic.apm.agent.wildfly_remote_ejb");

        ElasticApmAttacher.attach(configuration);
    }

    @BeforeEach
    void clear() {
        MOCK_SERVER_CLIENT.clear(request("/intake/v2/events"), ClearType.LOG);
    }

    @Test
    void testSuccess() throws NamingException {
        Transaction transaction = ElasticApm.startTransaction();
        try (Scope scope = transaction.activate()) {
            GreetingManager greeterManager = (GreetingManager) new InitialContext(getContextProperties()).lookup("ejb:/apm-wildfly-remote-ejb-plugin-it/GreetingManagerImpl!co.elastic.apm.agent.wildfly_remote_ejb.GreetingManager");
            assertEquals("Hello World!", greeterManager.greet(false));
        } catch (RuntimeException ignored) {
        } finally {
            transaction.end();
        }

        Map<String, Object> span = getClientSpan();

        assertAll(
                () -> assertClientSpan(span, "success"),
                () -> assertServerTransaction(getServerTransaction(), (String) span.get("trace_id"), (String) span.get("id"), "success")
        );
    }

    @Test
    void testError() throws NamingException {
        Transaction transaction = ElasticApm.startTransaction();
        try (Scope scope = transaction.setName("Client").activate()) {
            GreetingManager greeterManager = (GreetingManager) new InitialContext(getContextProperties()).lookup("ejb:/apm-wildfly-remote-ejb-plugin-it/GreetingManagerImpl!co.elastic.apm.agent.wildfly_remote_ejb.GreetingManager");
            assertEquals("Hello World!", greeterManager.greet(true));
        } catch (RuntimeException ignored) {
        } finally {
            transaction.end();
        }

        Map<String, Object> clientSpan = getClientSpan();
        Map<String, Object> serverTransaction = getServerTransaction();

        assertAll(
                () -> assertClientSpan(clientSpan, "failure"),
                () -> assertServerTransaction(serverTransaction, (String) clientSpan.get("trace_id"), (String) clientSpan.get("id"), "failure"),
                () -> assertErrors(getErrors(), (String) clientSpan.get("id"), (String) serverTransaction.get("id"))
        );
    }

    private static Properties getContextProperties() {
        Properties contextProperties = new Properties();
        contextProperties.put(Context.INITIAL_CONTEXT_FACTORY, "org.wildfly.naming.client.WildFlyInitialContextFactory");
        contextProperties.put(Context.PROVIDER_URL, "remote+http://" + WILDFLY.getContainerIpAddress() + ":" + WILDFLY.getMappedPort(8080));
        contextProperties.put(Context.SECURITY_PRINCIPAL, "ejb-user");
        contextProperties.put(Context.SECURITY_CREDENTIALS, "passw0rd");
        return contextProperties;
    }

    private static Map<String, Object> getServerTransaction() {
        return getEvents()
                .flatMap(dc -> ((List<Map<String, Object>>) dc.read("$[?(@.transaction)].transaction")).stream())
                .filter(t -> "GreetingManager#greet".equals(t.get("name")))
                .findAny()
                .get();
    }

    private static Map<String, Object> getClientSpan() {
        return getEvents()
                .flatMap(dc -> ((List<Map<String, Object>>) dc.read("$[?(@.span)].span")).stream())
                .filter(s -> "Call GreetingManager#greet".equals(s.get("name")))
                .findAny()
                .get();
    }

    private static List<Map<String, Object>> getErrors() {
        return getEvents()
                .flatMap(dc -> ((List<Map<String, Object>>) dc.read("$[?(@.error)].error")).stream())
                .collect(Collectors.toList());
    }

    private static Stream<DocumentContext> getEvents() {
        return ((List<String>) JsonPath.read(MOCK_SERVER_CLIENT.retrieveRecordedRequests(request("/intake/v2/events"), Format.JAVA), "$..body.rawBytes"))
                .stream()
                .map(Base64.getDecoder()::decode)
                .map(String::new)
                .flatMap(s -> Arrays.stream(s.split("\r?\n")))
                .map(JsonPath::parse);
    }

    private static void assertServerTransaction(Map<String, Object> transaction, String traceId, String parentId, String outcome) {
        assertAll(
                () -> assertEquals("trace_id", traceId, JsonPath.read(transaction, "$.trace_id")),
                () -> assertEquals("parent_id", parentId, JsonPath.read(transaction, "$.parent_id")),
                () -> assertEquals("type", "request", JsonPath.read(transaction, "$.type")),
                () -> assertEquals("context.service.framework.name", "EJB", JsonPath.read(transaction, "$.context.service.framework.name")),
                () -> assertEquals("outcome", outcome, JsonPath.read(transaction, "$.outcome"))
        );
    }

    private static void assertClientSpan(Map<String, Object> span, String outcome) {
        assertAll(
                () -> assertEquals("type", "external.ejb.call", JsonPath.read(span, "$.type")),
                () -> assertEquals("context.destination.address", WILDFLY.getContainerIpAddress(), JsonPath.read(span, "$.context.destination.address")),
                () -> assertEquals("context.destination.port", WILDFLY.getMappedPort(8080), JsonPath.read(span, "$.context.destination.port")),
                () -> assertEquals("context.destination.service.resource", WILDFLY.getContainerIpAddress() + ":" + WILDFLY.getMappedPort(8080), JsonPath.read(span, "$.context.destination.service.resource")),
                () -> assertEquals("outcome", outcome, JsonPath.read(span, "$.outcome"))
        );
    }

    private static void assertErrors(List<Map<String, Object>> errors, String clientSpanId, String serverTransactionId) {
        boolean clientErrorFound = false;
        boolean serverErrorFound = false;
        for (Map<String, Object> error : errors) {
            if (clientSpanId.equals(error.get("parent_id"))) {
                clientErrorFound = true;
            } else if (serverTransactionId.equals(error.get("parent_id"))) {
                serverErrorFound = true;
            }
        }

        assertTrue("client error not found", clientErrorFound);
        assertTrue("server error not found", serverErrorFound);
    }
}
