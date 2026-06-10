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

    @Bean
    public ApplicationRunner indexTemplateInitializer(ElasticsearchClient client) {
        return args -> {
            try {
                LOGGER.info("Initializing Elasticsearch index template 'app-logs-template' for patterns 'app-logs-*'...");
                client.indices().putIndexTemplate(pit -> pit
                        .name("app-logs-template")
                        .indexPatterns(List.of("app-logs-*"))
                        .template(t -> t
                                .settings(s -> s
                                        .analysis(a -> a
                                                .normalizer("lowercase_normalizer", n -> n
                                                        .custom(c -> c
                                                                .filter(List.of("lowercase"))
                                                        )
                                                )
                                        )
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
                LOGGER.info("Elasticsearch index template 'app-logs-template' initialized successfully.");

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
