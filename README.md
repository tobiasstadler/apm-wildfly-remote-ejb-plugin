# apm-wildfly-remote-ejb-plugin

An Elastic APM agent plugin for instrumenting remote EJB invocations on a WildFly application server.

## Supported Versions

| Plugin | Elastic APM Agent | WildFly |
| :--- | :--- | :--- |
| 1.0+ | 1.25.0+ | 11.0.0 |

## Installation

Set the [`plugins_dir`](https://www.elastic.co/guide/en/apm/agent/java/current/config-core.html#config-plugins-dir) agent configuration option and copy the plugin to specified directory.

Remove `org.jboss.as.*` from the `classes_excluded_from_instrumentation_default` agent configuration option, e.g. set it to `(?-i)org.infinispan*,(?-i)org.apache.xerces*,(?-i)io.undertow.core*,(?-i)org.eclipse.jdt.ecj*,(?-i)org.wildfly.extension.*,(?-i)org.wildfly.security*`.
