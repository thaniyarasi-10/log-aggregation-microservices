package com.kovanlabs.logservice.config;

import java.util.List;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;

@Configuration
public class ElasticsearchConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchConfig.class);

    @Value("${elasticsearch.host:localhost}")
    private String host;

    @Value("${elasticsearch.port:9200}")
    private int port;

    @Value("${elasticsearch.scheme:http}")
    private String scheme;

    @Value("${elasticsearch.socket-timeout-ms:60000}")
    private int socketTimeout;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        RestClient restClient = RestClient.builder(
                new HttpHost(host, port, scheme)
        ).setRequestConfigCallback(requestConfigBuilder ->
                requestConfigBuilder.setSocketTimeout(socketTimeout)
        ).build();

        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper()
        );

        return new ElasticsearchClient(transport);
    }

    private void createIlmPolicy(ElasticsearchClient client, String policyName, String minAge) {
        try {
            LOGGER.info("Checking/Creating Elasticsearch ILM policy '{}' with retention of {}...", policyName, minAge);
            java.util.Map<String, Object> actionsMap = new java.util.HashMap<>();
            actionsMap.put("delete", new java.util.HashMap<String, Object>());

            client.ilm().putLifecycle(p -> p
                    .name(policyName)
                    .policy(pol -> pol
                            .phases(ph -> ph
                                    .delete(del -> del
                                            .minAge(co.elastic.clients.elasticsearch._types.Time.of(t -> t.time(minAge)))
                                            .actions(co.elastic.clients.json.JsonData.of(actionsMap))
                                    )
                            )
                    )
            );
            LOGGER.info("Elasticsearch ILM policy '{}' created/updated successfully.", policyName);
        } catch (Exception e) {
            LOGGER.warn("Failed to create ILM policy '{}'. This is normal if ILM is disabled or not supported by the cluster. Error: {}", policyName, e.getMessage());
        }
    }

    private void putTemplate(ElasticsearchClient client, String templateName, String pattern, String lifecyclePolicy) throws Exception {
        LOGGER.info("Initializing Elasticsearch index template '{}' for patterns '{}'...", templateName, pattern);
        client.indices().putIndexTemplate(pit -> pit
                .name(templateName)
                .indexPatterns(List.of(pattern))
                .priority(10)
                .template(t -> t
                        .settings(s -> s
                                .analysis(a -> a
                                        .normalizer("lowercase_normalizer", n -> n
                                                .custom(c -> c
                                                        .filter(List.of("lowercase"))
                                                )
                                        )
                                )
                                .lifecycle(l -> l.name(lifecyclePolicy))
                        )
                        .mappings(m -> m
                                .properties("@timestamp", pr -> pr.date(d -> d))
                                .properties("service", pr -> pr.keyword(k -> k.normalizer("lowercase_normalizer")))
                                .properties("level", pr -> pr.keyword(k -> k.normalizer("lowercase_normalizer")))
                                .properties("environment", pr -> pr.keyword(k -> k.normalizer("lowercase_normalizer")))
                                .properties("project", pr -> pr.keyword(k -> k.normalizer("lowercase_normalizer")))
                                .properties("traceId", pr -> pr.keyword(k -> k))
                                .properties("message", pr -> pr.text(tx -> tx))
                                .properties("responseTime", pr -> pr.double_(db -> db))
                                .properties("errorType", pr -> pr.keyword(k -> k.normalizer("lowercase_normalizer")))
                                .properties("possibleCauses", pr -> pr.keyword(k -> k))
                                .properties("suggestedFixes", pr -> pr.keyword(k -> k))
                                .properties("severity", pr -> pr.keyword(k -> k.normalizer("lowercase_normalizer")))
                                .properties("suggestionGeneratedAt", pr -> pr.date(d -> d))
                                .properties("rootCause", pr -> pr.text(tx -> tx.fields("keyword", f -> f.keyword(k -> k))))
                                .properties("confidence", pr -> pr.integer(i -> i))
                                .properties("suggestionSource", pr -> pr.keyword(k -> k.normalizer("lowercase_normalizer")))
                        )
                )
        );
        LOGGER.info("Elasticsearch index template '{}' initialized successfully.", templateName);
    }

    @Bean
    public org.springframework.boot.ApplicationRunner indexTemplateInitializer(ElasticsearchClient client) {
        return args -> {
            try {
                // Initialize ILM Policies
                createIlmPolicy(client, "app-logs-errors-policy", "90d");
                createIlmPolicy(client, "app-logs-general-policy", "14d");

                // Initialize Templates
                putTemplate(client, "app-logs-errors-template", "app-logs-errors-*", "app-logs-errors-policy");
                putTemplate(client, "app-logs-general-template", "app-logs-general-*", "app-logs-general-policy");

                LOGGER.info("Checking/Initializing Elasticsearch index 'error-knowledge-base'...");
                boolean indexExists = client.indices().exists(e -> e.index("error-knowledge-base")).value();
                if (!indexExists) {
                    client.indices().create(c -> c
                            .index("error-knowledge-base")
                            .mappings(m -> m
                                    .properties("errorPattern", pr -> pr.text(t -> t))
                                    .properties("errorType", pr -> pr.keyword(k -> k))
                                    .properties("rootCause", pr -> pr.text(tx -> tx.fields("keyword", f -> f.keyword(k -> k))))
                                    .properties("possibleCauses", pr -> pr.keyword(k -> k))
                                    .properties("suggestedFixes", pr -> pr.keyword(k -> k))
                                    .properties("severity", pr -> pr.keyword(k -> k))
                                    .properties("confidence", pr -> pr.integer(i -> i))
                                    .properties("source", pr -> pr.keyword(k -> k))
                                    .properties("createdAt", pr -> pr.date(d -> d))
                            )
                    );
                    LOGGER.info("Elasticsearch index 'error-knowledge-base' created successfully.");
                } else {
                    LOGGER.info("Elasticsearch index 'error-knowledge-base' already exists.");
                }
            } catch (Exception e) {
                LOGGER.error("Failed to initialize Elasticsearch templates and indexes", e);
            }
        };
    }
}
