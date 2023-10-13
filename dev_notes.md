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

























