## Distribured Tracing
1. Add dependencies to the build files to bring Micrometer Tracing with a tracer 
    implementation and a reporter. 

```groovy
implementation 'io.micrometer:micrometer-tracing-bridge-otel'
implementation 'io.opentelemetry:opentelemetry-exporter-zipkin'

// for relational DB also add the library that can create spans for the SQL operations
implementation 'net.ttddyy.observation:datasource-micrometer-spring-boot:1.0.0'
```
2. Add a Zipkin server to the Docker Compose files.
```yaml
zipkin:
    image: openzipkin/zipkin:2.24.0
    restart: always
    mem_limit: 1024m
    environment:
      - STORAGE_TYPE=mem
    ports:
      - 9411:9411
```
3. Configure the microservices to send trace information to Zipkin.
   - add configuration for micrometer Tracing and Zipkin
   - Configuration for using Micrometer Tracing and Zipkin is added to the common configuration file,
     config-repo/application.yml In the default profile, it is specified that trace information will be
     sent to Zipkin using the following URL: **management.zipkin.tracing.endpoint: http://zipkin:9411/api/v2/spans**
   - By default, Micrometer Tracing only sends 10% of the traces to Zipkin. To ensure that all traces are
     sent to Zipkin, the following property is added to the default profile: **management.tracing.sampling.probability: 1.0**
   - We also want trace and span IDs to be written to logs: **logging.pattern.level: "%5p [${spring.application.name:},%X{traceId:-
     },%X{spanId:-}]"**
   - To reduce log output, change log level from DEBUG to INFO for each microservice configuration
   - For the product-composite microservice change the log level for HttpWebHandlerAdapter from TRACE to INFO
4. Add workarounds for the lacking support of reactive clients.
    - Add `Hooks.enableAutomaticContextPropagation();` in the main method.
    - To ensure that a WebClient instance is correctly instrumented for observation, for example, to be able to propagate the current
      trace and span IDs as headers in an outgoing request, the WebClient.Builder instance is expected to
      be injected using auto-wiring. Unfortunately, when using Eureka for service discovery, the WebClient.
      Builder instance is recommended to be created as a bean annotated with @LoadBalanced. So, there is a conflict in how to create a WebClient instance when used with both Eureka and Microme-
      ter Tracing. To resolve this conflict, the @LoadBalanced bean can be replaced by a load-balancer-aware
      exchange-filter function, ReactorLoadBalancerExchangeFilterFunction. An exchange-filter function
      can be set on an auto-wired WebClient.Builder instance like:
```java
@Autowired
private ReactorLoadBalancerExchangeFilterFunction lbFunction;
@Bean
public WebClient webClient(WebClient.Builder builder) {
return builder.filter(lbFunction).build();
}
```
5. Add code for creating custom spans and custom tags in existing spans. 

Before calling the API you need an access token. To acquire the token, run the following command:
```bash
unset ACCESS_TOKEN
ACCESS_TOKEN=$(curl -k https://writer:secret-writer@localhost:8443/oauth2/token
-d grant_type=client_credentials -d scope="product:read product:write" -s | jq
-r .access_token)
echo $ACCESS_TOKEN
```
### Send a successful API request:
```bash
curl -H "Authorization: Bearer $ACCESS_TOKEN" -k https://localhost:8443/
product-composite/1 -w "%{http_code}\n" -o /dev/null -s
```

To better understand how trace and span IDs are propagated between microservices, we can change
the logging configuration of the product-composite service so that HTTP headers in outgoing requests
are written to its log. This can be achieved by taking the following steps:
1. Add the following two lines to the configuration file config-repo/product-composite.yml:
```yaml
spring.codec.log-request-details: true
logging.level.org.springframework.web.reactive.function.client.ExchangeFunctions: TRACE
```
2. restart the product-composite service:
```bash
docker-compose restart product-composite
```
3. Display the log output from the product-composite service:
```bash
 docker-compose log -f --tail 0 product-composite
```
### Sending an unsuccessful API request
```bash
curl -H "Authorization: Bearer $ACCESS_TOKEN" -k https://localhost:8443/
product-composite/1?delay=3 -w "%{http_code}\n" -o /dev/null -s
```

### Sending an API request that triggers asynchronous processing
Let’s try a delete request, where the delete process in the core services is done asynchronously.

The product-composite service sends a delete event to each of the three core services over the mes-
sage broker and each core service picks up the delete event and processes it asynchronously. Thanks
to Micrometer Tracing, trace information is added to the events that are sent to the message broker,
resulting in a coherent view of the total processing of the delete request.

```bash
curl -X DELETE -H "Authorization: Bearer $ACCESS_TOKEN" -k https://
localhost:8443/product-composite/12345 -w "%{http_code}\n" -o /dev/null
-s
```

## Deploy to Kubernetes

Kubernetes comes with its own service discovery based on Kubernetes Service objects and 
`kube-proxy`. This makes it unnecessary to use Netflix Eureka. 

Apply the following changes:
1. Netflix Eureka and the Spring Cloud LoadBalancer-specific configuration (client and server) have been removed from the configuration repository, config-repo.
2. Routing rules in the gateway Service to the Eureka server have been removed from the config- repo/gateway.yml file.
3. The Eureka server project, in the spring-cloud/eureka-server folder, has been removed.
4. The Eureka server has been removed from the Docker Compose files and the settings.gradle Gradle file.
5. The dependency on `spring-cloud-starter-netflix-eureka-client` has been removed in all of Eureka’s client build files, build.gradle.
6. The property setting `eureka.client.enabled=false` has been removed from all integration tests of former Eureka clients.
7. The gateway Service no longer uses routing based on the client-side load balancer in Spring Cloud LoadBalancer, using the lb protocol. For example, the lb://product-composite routing destination has been replaced with http://product-composite in the config-repo/gateway. yml file.
8. The HTTP port used by the microservices and the authorization server has been changed from port 8080 (9999 in the case of the authorization server) to the default HTTP port, 80. This has been configured in config-repo for each affected Service, like so:
```yaml
spring.config.activate.on-profile: docker
server.port: 80
```
9. To enable graceful shutdown, the following properties are added to the common application.yml
```yaml
server.shutdown: graceful
spring.lifecycle.timeout-per-shutdown-phase: 10s
```
10. To enable liveness and readiness probes, the following configurations are added to the common application.yml:
```yaml
management.endpoint.health.probes.enabled: true
management.endpoint.health.group.readiness.include: readinessState, rabbit, db, mongo
```

## Introducing Helm 
Helm is a package manager for Kubernetes.
A package is known as a **chart** in Helm. A chart contains templates, default values for the 
templates, and optional dependencies on definitions on other charts. 
To extract boilerplate definitions from the components' charts, a special type of chart,
a **library chart** will be used. It doesn't contain any deployable definitions but only 
templates expected to be used by other charts for Kubernetes manifests.
Finally, to be able to describe how to deploy all components into different types of
environments, for example, for development and testing or staging and production, the
concept of parent charts and subcharts will be used. We will define two types of
environments, dev-env and prod-env. Each environment will be implemented as a parent 
chart that depends on different sets of subcharts, for example, the microservice charts. 
The environment charts will also provide environment-specific default values, such as for
the requested number of Pods, Docker image versions, credentials, and resource requests 
and limits.

### Running Helm Commands

To make Helm do something for us, we will use its CLI tool, helm. Some of the most frequently used Helm commands are:

- **create**: Used to create new charts.
- **dependency update** (dep up for short): Resolves dependencies on other charts. Charts are placed in the charts folder and the file Chart.lock is updated.
- **dependency build**: Rebuilds the dependencies based on the content in the file Chart.lock. 
- **template**: Renders the definition files created by the templates.
- **install**: Installs a chart. This command can override the values supplied by a chart, either using the --set flag to override a single value or using the --values flag to supply its own yaml file with values.
- **install --Dry-run**: simulates a Deployment without performing it; it’s useful for verifying a Deployment before executing it.
- **list**: Lists installations in the current n amespace. upgrade: Updates an existing installation.
- **uninstall**: Removes an installation.

### Looking into a Helm Chart

A Helm chart has a predefined structure of files. We will use the following files:
- **Chart.yaml**, which contains general information about the chart and a list of other charts it might depend on.
- **templates**, a folder that contains the templates that will be used to deploy the chart.
- **values.yaml**, which contains default values for the variables used by the templates.
- **Chart.lock**, a file created by Helm when resolving the dependencies described in the Chart. yaml file. This information describes in more detail what dependencies are actually used. It is used by Helm to track the entire dependency tree, making it possible to recreate the depen- dency tree exactly as it looked the last time the chart worked.
- **charts,** a folder that will contain the charts this chart depends on after Helm has resolved the dependencies.
- **.helmignore**, an ignore file similar to .gitignore. It can be used to list files that should be excluded when building the chart.


### Most frequently used Helm built-in objects:

1. **Values**: Used to refer to values in the chart’s values.yaml file or values supplied when running a Helm command like install.
2. **Release**: Used to provide metadata regarding the current release that is installed. It contains fields like:
   1. **Name**: The name of the release
   2. **Namespace**: The name of the namespace where the installation is performed
   3. **Service**: The name of the installation Service, always returning Helm
3. **Chart**: Used to access information from the Chart.yaml file. Examples of fields that can be useful for providing metadata for a Deployment are:
   1. **Name**: The name of the chart
   2. **Version**: The chart’s version number
4. **Files**: Containing functions for accessing chart-specific files. In this chapter we will use the following two functions in the Files object:
   1. **Glob**: Returns files in a chart based on a glob pattern. For example, the pattern "config- repo/*" will return all files found in the folder config-repo
   2. **AsConfig**: Returns the content of files as a YAML map appropriate for declaring values in a ConfigMap
5. **Capabilities**: Can be used to find information regarding the capabilities of the Kubernetes cluster that the installation is performed on. For example, a template can use information in this object to adopt a manifest based on what API versions the actual Kubernetes cluster supports. We will not use this object in this chapter, but I think it is in our interest to be aware of it for more advanced use cases.

#### Example of using the ConfigMap template
```bash
cd kubernetes/helm/components/config-server
helm dependency update .
helm template . -s templates/configmap_from_file.yaml
```

#### Example of using the Secrets template:
```bash
cd kubernetes/helm
for f in components/*; do helm dependency update $f; done
helm dependency update environments/dev-env 
helm template environments/dev-env -s templates/secrets.yaml
```

### Example of using the Service template

```bash
cd kubernetes/helm
helm dependency update components/product
helm template components/product -s templates/service.yaml
```
### Example of using the Deployment template
```bash
cd kubernetes/helm
helm dependency update components/product
helm template components/product -s templates/deployment.yaml
```

### Example of using the Deployment template for mongodb
```bash
cd kubernetes/helm
helm dependency update components/mongodb
helm template components/mongodb -s templates/deployment.yaml
```

#### Readiness and Liveness probes

Finding optimal settings for the probes can be challenging, that is, finding a proper
balance between getting a swift reaction from Kubernetes when the availability of a Pod
changes and not overloading the Pods with probe requests.

Specifically, configuring a liveness probe with values that are too low can result in
Ku- bernetes restarting Pods that don’t need to be restarted; they just need some extra 
time to start up. Starting a large number of Pods at the same time, also resulting in 
extra-long startup times, can similarly result in a lot of unnecessary restarts.

Setting the configuration values too high on the probes (except for the successThreshold 
value) makes Kubernetes react more slowly, which can be annoying in a development 
environment. Proper values also depend on the available hardware, which affects the 
startup times for the Pods. For the scope of this book, failureThreshold for the 
liveness probes is set to a high value, 20, to avoid unnecessary restarts on computers 
with limited hardware resources.















