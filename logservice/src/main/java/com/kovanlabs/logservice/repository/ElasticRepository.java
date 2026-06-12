package com.kovanlabs.logservice.repository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import com.kovanlabs.logservice.auth.AuthenticatedUserContext;
import com.kovanlabs.logservice.auth.UserRole;
import com.kovanlabs.logservice.model.LogEvent;
import com.kovanlabs.logservice.model.AlertItemView;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.aggregations.FieldDateMath;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;

@Repository
public class ElasticRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticRepository.class);

    private static final String LEVEL_FIELD       = "level";       // keyword, lowercase_normalizer
    private static final String SERVICE_FIELD     = "service";     // keyword
    private static final String ENVIRONMENT_FIELD = "environment"; // keyword
    private static final String PROJECT_FIELD     = "project";     // keyword

    // Error level value — must be lowercase to match the lowercase_normalizer on "level"
    private static final String ERROR_LEVEL_VALUE = "error";
    private static final String INDEX_PATTERN   = "app-logs-*";
    private static final String NO_ACCESS_SENTINEL = "__NO_ACCESS__";
    private static final String RESPONSE_TIME_FIELD = "responseTime";
    private static final String AGG_TOTAL_COUNT = "total_count";
    private static final String AGG_ERROR_COUNT = "error_count";
    private static final String AGG_AVG_RESPONSE_TIME = "avg_response_time";
    private static final String AGG_P95_LATENCY = "p95_latency";
    private static final String AGG_LEVEL_DISTRIBUTION = "level_distribution";
    private static final String AGG_THROUGHPUT_OVER_TIME = "throughput_over_time";
    private static final String AGG_BUCKET_ERROR_COUNT = "bucket_error_count";
    private static final String AGG_BUCKET_AVG_RESPONSE_TIME = "bucket_avg_response_time";
    private static final String DEFAULT_METRICS_INTERVAL = "1m";
    private static final int TARGET_BUCKET_COUNT = 80;
    private static final String PRESET_5M = "5m";
    private static final String PRESET_15M = "15m";
    private static final String PRESET_1H = "1h";
    private static final String PRESET_24H = "24h";
    private static final String PRESET_7D = "7d";
    private static final String PRESET_15D = "15d";
    private static final String PRESET_CUSTOM = "custom";
    private static final Duration RETENTION_PERIOD = Duration.ofDays(15);
    private static final long WRITE_BACKOFF_MS = 30_000L;
    private static final long ERROR_LOG_THROTTLE_MS = 30_000L;

    private volatile long writesMutedUntilMs = 0L;
    private volatile long nextErrorLogAtMs = 0L;
    private volatile Map<String, Object> lastStableMetrics = defaultMetricsPayload();

    @org.springframework.beans.factory.annotation.Value("${elasticsearch.search-debug:false}")
    private boolean searchDebug;

    private final ElasticsearchClient client;

    public ElasticRepository(ElasticsearchClient client) {
        this.client = client;
    }

    // SAVE - preserved existing write/indexing method from microservice
    public boolean save(LogEvent log) {
        if (log == null) {
            LOGGER.warn("ES SAVE — received null LogEvent, skipping");
            return false;
        }

        if (log.getTimestamp() == null || log.getTimestamp().isBlank()) {
            log.setTimestamp(Instant.now().toString());
        }

        long now = System.currentTimeMillis();

        // Mute check — visible, counted, never silent
        if (now < writesMutedUntilMs) {
            long remainingMs = writesMutedUntilMs - now;
            LOGGER.warn("ES WRITE MUTED — skipping index. Mute expires in {}ms. " +
                    "Check earlier 'ES SAVE FAILED' log for the root cause.", remainingMs);
            return false;
        }

        if (!isValidForIndexing(log)) {
            return false;
        }

        String index = "app-logs-" + resolveIndexDate(log.getTimestamp());
        String docId = generateDeterministicId(log);

        try {
            IndexRequest<LogEvent> request = IndexRequest.of(i -> i
                    .index(index)
                    .id(docId)
                    .document(log)
            );
            client.index(request);

            if (writesMutedUntilMs != 0L) {
                writesMutedUntilMs = 0L;
                LOGGER.info("ES SAVE — Elasticsearch connection restored. Resuming log persistence.");
            }
            return true;

        } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException esEx) {
            int status = esEx.status();
            if (status >= 400 && status < 500) {
                LOGGER.error("ES SAVE FAILED — non-retryable HTTP {} from Elasticsearch. " +
                        "Likely cause: mapping conflict or malformed field. " +
                        "index={} service={} timestamp={} error={}",
                        status, index, log.getService(), log.getTimestamp(), esEx.getMessage(), esEx);
                // Do NOT mute writes for client errors — the next document may be fine.
            } else {
                writesMutedUntilMs = now + WRITE_BACKOFF_MS;
                LOGGER.error("ES SAVE FAILED — HTTP {} from Elasticsearch. " +
                        "Writes muted for {}ms. index={} service={} error={}",
                        status, WRITE_BACKOFF_MS, index, log.getService(), esEx.getMessage(), esEx);
            }
            return false;
        } catch (Exception e) {
            // Covers connection failures, timeouts, serialization errors, etc.
            writesMutedUntilMs = now + WRITE_BACKOFF_MS;
            LOGGER.error("ES SAVE FAILED — unexpected exception. " +
                    "Writes muted for {}ms. index={} service={} timestamp={} exceptionType={} message={}",
                    WRITE_BACKOFF_MS, index, log.getService(), log.getTimestamp(),
                    e.getClass().getSimpleName(), e.getMessage(), e);
            return false;
        }
    }

    // preserved existing indexing validation from microservice
    private boolean isValidForIndexing(LogEvent log) {
        // @timestamp must be a valid ISO-8601 instant — ES will reject anything else
        if (log.getTimestamp() != null && !log.getTimestamp().isBlank()) {
            Instant parsed = parseInstantOrNull(log.getTimestamp());
            if (parsed == null) {
                LOGGER.error("ES SAVE SKIPPED — invalid @timestamp '{}' for service='{}'. " +
                        "Expected ISO-8601 format (e.g. 2026-05-12T10:00:00Z). " +
                        "This document would cause a mapping conflict and has been dropped.",
                        log.getTimestamp(), log.getService());
                return false;
            }
            // Normalize to UTC string for Elasticsearch compatibility
            log.setTimestamp(parsed.toString());
        }

        // service and message are the minimum fields needed for a useful log entry.
        if ((log.getService() == null || log.getService().isBlank()) &&
                (log.getMessage() == null || log.getMessage().isBlank())) {
            LOGGER.warn("ES SAVE SKIPPED — document has no service and no message. Dropping to avoid index pollution.");
            return false;
        }

        return true;
    }

    // preserved existing index date resolver from microservice
    private String resolveIndexDate(String rawTimestamp) {
        if (rawTimestamp != null && !rawTimestamp.isBlank()) {
            try {
                return Instant.parse(rawTimestamp)
                        .atZone(java.time.ZoneOffset.UTC)
                        .toLocalDate()
                        .toString();
            } catch (java.time.format.DateTimeParseException e) {
                LOGGER.warn("Unparseable @timestamp '{}' — falling back to current UTC date for index naming", rawTimestamp);
            }
        }
        return LocalDate.now(java.time.ZoneOffset.UTC).toString();
    }

    // preserved existing write mute reset scheduler from microservice
    @Scheduled(fixedDelay = 35_000)
    public void resetWriteMuteIfExpired() {
        long now = System.currentTimeMillis();
        if (writesMutedUntilMs != 0L && now >= writesMutedUntilMs) {
            writesMutedUntilMs = 0L;
            LOGGER.info("ES WRITE MUTE — backoff period expired. ES writes re-enabled. " +
                    "If indexing still fails, check ES cluster health and index mappings.");
        }
    }

    // preserved throttled error logger from microservice
    private void logErrorThrottled(String operation, Exception e) {
        long now = System.currentTimeMillis();
        if (now >= nextErrorLogAtMs) {
            nextErrorLogAtMs = now + ERROR_LOG_THROTTLE_MS;
            LOGGER.error("Elasticsearch {} failed (throttled — next log in {}ms): {} — {}",
                    operation, ERROR_LOG_THROTTLE_MS, e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    // preserved deterministic ID generation from microservice
    private String generateDeterministicId(LogEvent log) {
        String raw = Stream.of(
                log.getTimestamp(),
                log.getService(),
                log.getLevel(),
                log.getTraceId(),
                log.getMessage()
        )
        .map(s -> s == null ? "" : s.trim())
        .collect(Collectors.joining("|"));

        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(Objects.hash(log.getTimestamp(), log.getService(), log.getMessage()));
        }
    }

    // SEARCH - restored original LAS implementation
    public List<LogEvent> search(String service, String environment, String level,
                                 String traceId, String message,
                                 String from, String to,
                                 int page, int size,
                                 AuthenticatedUserContext accessContext) {
        try {
            if (!hasAnyLogIndexes()) {
                return new ArrayList<>();
            }
            LOGGER.debug("SEARCH INPUT → service=[{}] environment=[{}] level=[{}] traceId=[{}] message=[{}]",
                    service, environment, level, traceId, message);

            TimeBounds bounds = resolveTimeBounds(from, to);
            
            BoolQuery.Builder boolQueryBuilder = QueryBuilders.bool();

            if (service != null && !service.isBlank() && !"All Services".equalsIgnoreCase(service)) {
                boolQueryBuilder.must(
                    QueryBuilders.term(t -> t
                        .field(SERVICE_FIELD)
                        .value(service.trim().toLowerCase(java.util.Locale.ROOT))
                    )
                );
            }

            if (environment != null && !environment.isBlank()) {
                boolQueryBuilder.must(
                    QueryBuilders.term(t -> t
                        .field(ENVIRONMENT_FIELD)
                        .value(environment.trim().toLowerCase(java.util.Locale.ROOT))
                    )
                );
            }

            if (level != null && !level.isBlank()) {
                boolQueryBuilder.must(
                    QueryBuilders.term(t -> t
                        .field(LEVEL_FIELD)
                        .value(level.trim().toLowerCase(java.util.Locale.ROOT))
                    )
                );
            }
            if (traceId != null && !traceId.isBlank()) {
                boolQueryBuilder.must(
                    QueryBuilders.bool(b -> b
                        .should(s -> s.term(t -> t.field("traceId").value(traceId.trim())))
                        .should(s -> s.term(t -> t.field("trace_id").value(traceId.trim())))
                        .minimumShouldMatch("1")
                    )
                );
            }

            if (message != null && !message.isBlank()) {
                boolQueryBuilder.must(buildMessageQuery(message.trim()));
            }

            if (from != null || to != null) {
                boolQueryBuilder.filter(rangeQuery(bounds));
            }

            buildAccessFilter(accessContext).ifPresent(boolQueryBuilder::filter);

            BoolQuery boolQuery = boolQueryBuilder.build();

            SearchRequest request = SearchRequest.of(s -> s
                    .index(INDEX_PATTERN)
                    .query(boolQuery._toQuery())
                    .from(page * size)
                    .size(Math.min(size, 500))
                    .sort(sort -> sort
                            .field(f -> f
                                    .field("@timestamp")
                                            .order(SortOrder.Desc)))
            );

            SearchResponse<LogEvent> response = executeSearch(request, LogEvent.class, "search", boolQuery._toQuery(), accessContext, service != null ? List.of(service) : null);

            return response.hits().hits()
                    .stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            logErrorThrottled("search", e);
            return new ArrayList<>();
        }
    }

    // SEARCH MULTI - restored original LAS implementation
    public List<LogEvent> searchMulti(
            List<String> services,
            String environment,
            List<String> levels,
            String traceId,
            String message,
            String from,
            String to,
            int page,
            int size,
            AuthenticatedUserContext accessContext) {
        try {
            if (!hasAnyLogIndexes()) {
                return new ArrayList<>();
            }
//            LOGGER.debug("SEARCH MULTI → services={} levels={} environment={} traceId={} message={}",
//                    services, levels, environment, traceId, message);

            TimeBounds bounds = resolveTimeBounds(from, to);
            BoolQuery.Builder boolQueryBuilder = QueryBuilders.bool();

            if (services != null && !services.isEmpty()) {
                List<FieldValue> serviceValues = services.stream()
                        .filter(this::hasText)
                        .map(s -> s.trim().toLowerCase(java.util.Locale.ROOT))
                        .distinct()
                        .map(FieldValue::of)
                        .toList();
                if (!serviceValues.isEmpty()) {
                    boolQueryBuilder.must(
                        QueryBuilders.terms()
                            .field(SERVICE_FIELD)
                            .terms(v -> v.value(serviceValues))
                            .build()._toQuery()
                    );
                }
            }

            if (environment != null && !environment.isBlank()) {
                boolQueryBuilder.must(
                    QueryBuilders.term(t -> t
                        .field(ENVIRONMENT_FIELD)
                        .value(environment.trim().toLowerCase(java.util.Locale.ROOT))
                    )
                );
            }

            if (levels != null && !levels.isEmpty()) {
                List<FieldValue> levelValues = levels.stream()
                        .filter(this::hasText)
                        .map(l -> l.trim().toLowerCase(java.util.Locale.ROOT))
                        .distinct()
                        .map(FieldValue::of)
                        .toList();
                if (!levelValues.isEmpty()) {
                    boolQueryBuilder.must(
                        QueryBuilders.terms()
                            .field(LEVEL_FIELD)
                            .terms(v -> v.value(levelValues))
                            .build()._toQuery()
                    );
                }
            }

            if (traceId != null && !traceId.isBlank()) {
                boolQueryBuilder.must(
                    QueryBuilders.bool(b -> b
                        .should(s -> s.term(t -> t.field("traceId").value(traceId.trim())))
                        .should(s -> s.term(t -> t.field("trace_id").value(traceId.trim())))
                        .minimumShouldMatch("1")
                    )
                );
            }

            if (message != null && !message.isBlank()) {
                boolQueryBuilder.must(buildMessageQuery(message.trim()));
            }

            if (from != null || to != null) {
                boolQueryBuilder.filter(rangeQuery(bounds));
            }

            buildAccessFilter(accessContext).ifPresent(boolQueryBuilder::filter);

            BoolQuery boolQuery = boolQueryBuilder.build();

            SearchRequest request = SearchRequest.of(s -> s
                    .index(INDEX_PATTERN)
                    .query(boolQuery._toQuery())
                    .from(page * size)
                    .size(Math.min(size, 500))
                    .sort(sort -> sort
                            .field(f -> f
                                    .field("@timestamp")
                                    .order(SortOrder.Desc)))
            );

            SearchResponse<LogEvent> response = executeSearch(request, LogEvent.class, "searchMulti", boolQuery._toQuery(), accessContext, services);
            return response.hits().hits()
                    .stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .toList();

        } catch (Exception e) {
            logErrorThrottled("searchMulti", e);
            return new ArrayList<>();
        }
    }

    // DISTINCT SERVICES - restored original LAS implementation
    public List<String> getDistinctServices(String from, String to, int maxServices, AuthenticatedUserContext accessContext) {
        try {
            if (!hasAnyLogIndexes()) {
                return new ArrayList<>();
            }
            boolean hasTimeFilter = hasText(from) || hasText(to);
            List<Query> filters = new ArrayList<>();

            if (hasTimeFilter) {
                TimeBounds bounds = resolveTimeBounds(from, to);
                filters.add(rangeQuery(bounds));
            }

            buildAccessFilter(accessContext).ifPresent(filters::add);

            Query query = filters.isEmpty()
                    ? QueryBuilders.matchAll().build()._toQuery()
                    : BoolQuery.of(b -> b.filter(filters))._toQuery();

            SearchRequest request = SearchRequest.of(s -> s
                            .index(INDEX_PATTERN)
                            .query(query)
                            .size(0)
                            .aggregations("services", a -> a
                                            .terms(t -> t
                                                            .field(SERVICE_FIELD)
                                                            .size(Math.max(1, maxServices))
                                            ))
                            .aggregations("projects", a -> a
                                            .terms(t -> t
                                                            .field(PROJECT_FIELD)
                                                            .size(Math.max(1, maxServices))
                                            ))
            );

            SearchResponse<Void> response = executeSearch(request, Void.class, "getDistinctServices", query, accessContext, null);

            List<String> services = response.aggregations()
                            .get("services")
                            .sterms()
                            .buckets()
                            .array()
                            .stream()
                            .map(bucket -> bucket.key().stringValue())
                            .filter(Objects::nonNull)
                            .filter(svc -> !svc.isBlank())
                            .toList();

            List<String> projects = response.aggregations()
                            .get("projects")
                            .sterms()
                            .buckets()
                            .array()
                            .stream()
                            .map(bucket -> bucket.key().stringValue())
                            .filter(Objects::nonNull)
                            .filter(project -> !project.isBlank())
                            .toList();

            return Stream.concat(services.stream(), projects.stream())
                            .map(String::trim)
                            .filter(name -> !name.isBlank())
                            .distinct()
                            .sorted()
                            .toList();

        } catch (Exception e) {
            logErrorThrottled("getDistinctServices", e);
            return new ArrayList<>();
        }
    }

    // GET METRICS - restored original LAS implementation
    public Map<String, Object> getMetrics(String services, String environment, String levels, String message, String from, String to, String timePreset, AuthenticatedUserContext accessContext) {
        try {
            if (!hasAnyLogIndexes()) {
                return defaultMetricsPayload();
            }
            TimeBounds bounds = resolveTimeBounds(from, to);
            String interval = resolveMetricsInterval(bounds, timePreset);
            int intervalSeconds = toIntervalSeconds(interval);
            SearchRequest request = buildMetricsRequest(services, environment, levels, message, bounds, interval, accessContext);

            SearchResponse<Void> response = executeSearch(request, Void.class, "getMetrics", BoolQuery.of(b -> b.filter(buildMetricFilters(services, environment, levels, message, bounds, accessContext)))._toQuery(), accessContext, services != null ? Arrays.asList(services.split(",")) : null);
            Map<String, Object> payload = toMetricsPayload(response, interval, intervalSeconds);
            lastStableMetrics = payload;
            return payload;

        } catch (Exception e) {
            logErrorThrottled("metrics", e);
            return lastStableMetrics == null || lastStableMetrics.isEmpty() ? defaultMetricsPayload() : lastStableMetrics;
        }
    }

    private Map<String, Object> defaultMetricsPayload() {
        return Stream.of(
                Map.entry("totalLogs", 0L),
                Map.entry("errorCount", 0L),
                Map.entry("warningCount", 0L),
                Map.entry("errorRate", 0.0),
                Map.entry("avgResponseTime", 0.0),
                Map.entry("p95Latency", 0.0),
                Map.entry("bucketInterval", DEFAULT_METRICS_INTERVAL),
                Map.entry("throughputOverTime", List.<Map<String, Object>>of()),
                Map.entry("levelDistribution", List.<Map<String, Object>>of()),
                Map.entry("logsPerService", List.<Map<String, Object>>of()),
                Map.entry("topServices", List.<String>of())
        ).collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
        ));
    }

    private SearchRequest buildMetricsRequest(String services, String environment, String levels, String message, TimeBounds bounds, String interval, AuthenticatedUserContext accessContext) {
        BoolQuery boolQuery = BoolQuery.of(b -> b.filter(buildMetricFilters(services, environment, levels, message, bounds, accessContext)));
//        LOGGER.debug("ES METRICS QUERY → {}", boolQuery._toQuery());

        return SearchRequest.of(s -> s
                .index(INDEX_PATTERN)
                .query(boolQuery._toQuery())
                .size(0)
                .aggregations(AGG_TOTAL_COUNT, a -> a
                        .valueCount(v -> v.field("@timestamp")))
                .aggregations(AGG_ERROR_COUNT, a -> a
                        .filter(f -> f
                                .term(t -> t
                                        .field(LEVEL_FIELD)
                                        .value(ERROR_LEVEL_VALUE))))
                .aggregations("warning_count", a -> a
                        .filter(f -> f
                                .term(t -> t
                                        .field(LEVEL_FIELD)
                                        .value("warn"))))
                .aggregations(AGG_AVG_RESPONSE_TIME, a -> a
                        .avg(avg -> avg.field(RESPONSE_TIME_FIELD)))
                .aggregations(AGG_P95_LATENCY, a -> a
                        .percentiles(p -> p
                                .field(RESPONSE_TIME_FIELD)
                                .percents(95.0)))
                .aggregations(AGG_LEVEL_DISTRIBUTION, a -> a
                        .terms(t -> t
                                .field(LEVEL_FIELD)
                                .size(10)))
                .aggregations("logs_per_service", a -> a
                        .terms(t -> t
                                .field(SERVICE_FIELD)
                                .size(20)))
                .aggregations(AGG_THROUGHPUT_OVER_TIME, a -> a
                        .dateHistogram(dh -> dh
                                .field("@timestamp")
                                .fixedInterval(Time.of(t -> t.time(interval)))
                                .minDocCount(0)
                                .extendedBounds(eb -> eb
                                        .min(FieldDateMath.of(f -> f.expr(bounds.getFromInstant().toString())))
                                        .max(FieldDateMath.of(f -> f.expr(bounds.getToInstant().toString()))))
                        )
                        .aggregations(AGG_BUCKET_ERROR_COUNT, sub -> sub
                                .filter(f -> f
                                        .term(t -> t
                                                .field(LEVEL_FIELD)
                                                .value(ERROR_LEVEL_VALUE))))
                        .aggregations(AGG_BUCKET_AVG_RESPONSE_TIME, sub -> sub
                                .avg(avg -> avg.field(RESPONSE_TIME_FIELD))))
        );
    }

    private List<Query> buildMetricFilters(String servicesStr, String environment, String levelsStr, String message, TimeBounds bounds, AuthenticatedUserContext accessContext) {
        List<Query> filters = new ArrayList<>();

        if (servicesStr != null && !servicesStr.isBlank() && !"All Services".equalsIgnoreCase(servicesStr)) {
            List<FieldValue> serviceValues = Arrays.stream(servicesStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(s -> s.toLowerCase(java.util.Locale.ROOT))
                    .distinct()
                    .map(FieldValue::of)
                    .toList();
            if (!serviceValues.isEmpty()) {
                filters.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q
                        .terms(t -> t
                                .field(SERVICE_FIELD)
                                .terms(v -> v.value(serviceValues)))));
            }
        }

        if (environment != null && !environment.isBlank()) {
            filters.add(QueryBuilders.term(t -> t
                    .field(ENVIRONMENT_FIELD)
                    .value(environment.trim().toLowerCase(java.util.Locale.ROOT))
            ));
        }

        if (levelsStr != null && !levelsStr.isBlank()) {
            List<FieldValue> levelValues = Arrays.stream(levelsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(s -> s.toLowerCase(java.util.Locale.ROOT))
                    .distinct()
                    .map(FieldValue::of)
                    .toList();
            if (!levelValues.isEmpty()) {
                filters.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q
                        .terms(t -> t
                                .field(LEVEL_FIELD)
                                .terms(v -> v.value(levelValues)))));
            }
        }

        if (message != null && !message.isBlank()) {
            filters.add(buildMessageQuery(message.trim()));
        }

        buildAccessFilter(accessContext).ifPresent(filters::add);

        final String metricFromStr = bounds.getFromInstant().truncatedTo(ChronoUnit.SECONDS).toString();
        final String metricToStr = bounds.getToInstant().truncatedTo(ChronoUnit.SECONDS).toString();
        filters.add(
                RangeQuery.of(r -> r
                        .field("@timestamp")
                        .gte(JsonData.of(metricFromStr))
                        .lte(JsonData.of(metricToStr)))._toQuery()
        );

        return filters;
    }

    private Map<String, Object> toMetricsPayload(SearchResponse<Void> response, String interval, int intervalSeconds) {
        List<Map<String, Object>> throughputOverTime = response.aggregations()
                .get(AGG_THROUGHPUT_OVER_TIME)
                .dateHistogram()
                .buckets()
                .array()
                .stream()
                .sorted(Comparator.comparingLong(bucket -> bucket.key()))
                .map(bucket -> {
                    long bucketErrors = bucket.aggregations().get(AGG_BUCKET_ERROR_COUNT).filter().docCount();
                    long bucketCount = bucket.docCount();
                    double throughputPerSecond = intervalSeconds > 0 ? (bucketCount * 1.0 / intervalSeconds) : 0.0;
                    return Map.<String, Object>of(
                            "time", Objects.toString(bucket.keyAsString(), ""),
                            "count", bucketCount,
                            "intervalSeconds", intervalSeconds,
                            "throughputPerSecond", normalizeDouble(throughputPerSecond),
                            "errorCount", bucketErrors,
                            "errorRate", bucketCount > 0 ? (bucketErrors * 100.0 / bucketCount) : 0.0,
                            "avgResponseTime", normalizeDouble(bucket.aggregations().get(AGG_BUCKET_AVG_RESPONSE_TIME).avg().value())
                    );
                })
                .toList();

        long total = (long) response.aggregations()
                .get(AGG_TOTAL_COUNT).valueCount().value();

        long errors = response.aggregations()
                .get(AGG_ERROR_COUNT).filter().docCount();

        long warnings = response.aggregations()
                .get("warning_count") != null ? response.aggregations().get("warning_count").filter().docCount() : 0L;

        double avgRt = normalizeDouble(response.aggregations()
                .get(AGG_AVG_RESPONSE_TIME).avg().value());

        double p95 = extractP95(response);

        List<Map<String, Object>> levelDistribution = response.aggregations()
                .get(AGG_LEVEL_DISTRIBUTION)
                .sterms()
                .buckets()
                .array()
                .stream()
                .map(bucket -> Map.<String, Object>of(
                        "level", bucket.key().stringValue(),
                        "count", bucket.docCount()
                ))
                .toList();

        List<Map<String, Object>> logsPerService = response.aggregations().get("logs_per_service") != null
                ? response.aggregations().get("logs_per_service").sterms().buckets().array().stream()
                        .map(bucket -> Map.<String, Object>of(
                                "service", bucket.key().stringValue(),
                                "count", bucket.docCount()
                        )).toList()
                : List.of();

        List<String> topServices = logsPerService.stream()
                .map(item -> (String) item.get("service"))
                .toList();

        double errorRate = total > 0 ? (errors * 100.0 / total) : 0.0;

        return Stream.of(
                Map.entry("totalLogs",       total),
                Map.entry("errorCount",      errors),
                Map.entry("warningCount",    warnings),
                Map.entry("errorRate",       Math.round(errorRate * 100.0) / 100.0),
                Map.entry("avgResponseTime", avgRt),
                Map.entry("p95Latency",      p95),
                Map.entry("bucketInterval", interval),
                Map.entry("throughputOverTime", throughputOverTime),
                Map.entry("levelDistribution", levelDistribution),
                Map.entry("logsPerService", logsPerService),
                Map.entry("topServices", topServices)
        ).collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
        ));
    }

    private int toIntervalSeconds(String interval) {
        if (interval == null || interval.isBlank()) {
            return 60;
        }
        if (interval.endsWith("s")) {
            return Integer.parseInt(interval.substring(0, interval.length() - 1));
        }
        if (interval.endsWith("m")) {
            return Integer.parseInt(interval.substring(0, interval.length() - 1)) * 60;
        }
        if (interval.endsWith("h")) {
            return Integer.parseInt(interval.substring(0, interval.length() - 1)) * 3600;
        }
        return 60;
    }

    private String resolveMetricsInterval(TimeBounds bounds, String timePreset) {
        if (timePreset != null && !timePreset.isBlank() && !PRESET_CUSTOM.equalsIgnoreCase(timePreset)) {
            return switch (timePreset) {
                case PRESET_5M -> "30s";
                case PRESET_15M -> "1m";
                case PRESET_1H -> "5m";
                case PRESET_24H -> "2h";
                case PRESET_7D -> "6h";
                case PRESET_15D -> "1d";
                default -> DEFAULT_METRICS_INTERVAL;
            };
        }

        long rangeMs = resolveRangeMillis(bounds.getFromInstant(), bounds.getToInstant());
        if (rangeMs <= 0) {
            return DEFAULT_METRICS_INTERVAL;
        }

        long rawIntervalMs = Math.max(1L, rangeMs / TARGET_BUCKET_COUNT);
        return roundIntervalToStandard(rawIntervalMs);
    }

    private String roundIntervalToStandard(long intervalMs) {
        if (intervalMs <= 30_000L) {
            return "30s";
        }
        if (intervalMs <= 60_000L) {
            return "1m";
        }
        if (intervalMs <= 2 * 60_000L) {
            return "2m";
        }
        if (intervalMs <= 5 * 60_000L) {
            return "5m";
        }
        if (intervalMs <= 10 * 60_000L) {
            return "10m";
        }
        if (intervalMs <= 15 * 60_000L) {
            return "15m";
        }
        if (intervalMs <= 30 * 60_000L) {
            return "30m";
        }
        if (intervalMs <= 60 * 60_000L) {
            return "1h";
        }
        if (intervalMs <= 2 * 60 * 60_000L) {
            return "2h";
        }
        if (intervalMs <= 6 * 60 * 60_000L) {
            return "6h";
        }
        if (intervalMs <= 12 * 60 * 60_000L) {
            return "12h";
        }
        return "1d";
    }

    private long resolveRangeMillis(Instant from, Instant to) {
        try {
            if (from == null || to == null) {
                return -1L;
            }
            return Math.max(0L, Duration.between(from, to).toMillis());
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private double extractP95(SearchResponse<Void> response) {
        try {
            if (response.aggregations() == null || response.aggregations().get(AGG_P95_LATENCY) == null) {
                return 0.0;
            }

            Map<String, String> keyed = response.aggregations()
                    .get(AGG_P95_LATENCY)
                    .tdigestPercentiles()
                    .values()
                    .keyed();

            if (keyed == null) {
                return 0.0;
            }

            String rawP95 = keyed.get("95.0");
            if (rawP95 == null || rawP95.isBlank()) {
                return 0.0;
            }

            return normalizeDouble(Double.parseDouble(rawP95));
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private double normalizeDouble(double value) {
        return Double.isFinite(value) ? Math.round(value * 100.0) / 100.0 : 0.0;
    }

    public long countErrorsInWindow(String windowExpression) {
        try {
            if (!hasAnyLogIndexes()) {
                return 0L;
            }

            BoolQuery boolQuery = BoolQuery.of(b -> b
                            .filter(buildErrorWindowFilters(windowExpression)));

            CountRequest request = CountRequest.of(c -> c
                            .index(INDEX_PATTERN)
                            .query(boolQuery._toQuery()));

            CountResponse response = client.count(request);
            return response.count();
        } catch (Exception e) {
            logErrorThrottled("countErrorsInWindow", e);
            return 0L;
        }
    }

    public Map<String, Long> countErrorsByServiceInWindow(String windowExpression, int maxServices) {
        try {
            if (!hasAnyLogIndexes()) {
                return new HashMap<>();
            }

            BoolQuery boolQuery = BoolQuery.of(b -> b
                            .filter(buildErrorWindowFilters(windowExpression)));

            SearchRequest request = SearchRequest.of(s -> s
                            .index(INDEX_PATTERN)
                            .query(boolQuery._toQuery())
                            .size(0)
                            .aggregations("errors_by_service", a -> a
                                            .terms(t -> t
                                                            .field(SERVICE_FIELD)
                                                            .size(maxServices))));

            SearchResponse<Void> response = executeSearch(request, Void.class, "countErrorsByServiceInWindow", boolQuery._toQuery(), null, null);

            return response.aggregations()
                            .get("errors_by_service")
                            .sterms()
                            .buckets()
                            .array()
                            .stream()
                            .collect(Collectors.toMap(
                                            bucket -> bucket.key().stringValue(),
                                            bucket -> bucket.docCount()
                            ));
        } catch (Exception e) {
            logErrorThrottled("countErrorsByServiceInWindow", e);
            return new HashMap<>();
        }
    }

    // SHARED FILTER BUILDER
    private Optional<Query> buildAccessFilter(AuthenticatedUserContext accessContext) {
        if (accessContext == null) {
            return Optional.of(noAccessQuery());
        }

        String orgId = accessContext.organizationId();
        if (orgId == null || orgId.isBlank()) {
            return Optional.of(noAccessQuery());
        }

        Query orgFilter = QueryBuilders.term(t -> t
                .field("organizationId")
                .value(orgId)
        );

        if (accessContext.isAdmin()) {
            return Optional.of(orgFilter);
        }

        List<String> allowedServices = accessContext.allowedServices();
        if (allowedServices == null || allowedServices.isEmpty()) {
            return Optional.of(BoolQuery.of(b -> b
                    .filter(orgFilter)
                    .filter(noAccessQuery())
            )._toQuery());
        }

        boolean hasWildcardAccess = allowedServices.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .anyMatch(value -> "*".equals(value));

        if (hasWildcardAccess) {
            return Optional.of(orgFilter);
        }

        List<FieldValue> fieldValues = allowedServices.stream()
                .filter(this::hasText)
                .map(String::trim)
                .map(s -> s.toLowerCase(java.util.Locale.ROOT))
                .map(FieldValue::of)
                .toList();

        if (fieldValues.isEmpty()) {
            return Optional.of(BoolQuery.of(b -> b
                    .filter(orgFilter)
                    .filter(noAccessQuery())
            )._toQuery());
        }

        Query servicesQuery = QueryBuilders.terms()
                .field(SERVICE_FIELD)
                .terms(v -> v.value(fieldValues))
                .build()._toQuery();

        return Optional.of(BoolQuery.of(b -> b
                .filter(orgFilter)
                .filter(servicesQuery)
        )._toQuery());
    }

    private Query noAccessQuery() {
        return TermQuery.of(t -> t
                        .field(SERVICE_FIELD)
                        .value(NO_ACCESS_SENTINEL)
        )._toQuery();
    }

    private Query rangeQuery(TimeBounds bounds) {
        Instant from = bounds.getFromInstant().truncatedTo(ChronoUnit.SECONDS);
        Instant to = bounds.getToInstant()
                .truncatedTo(ChronoUnit.SECONDS)
                .plusSeconds(2);

        return RangeQuery.of(r -> r
                        .field("@timestamp")
                        .gte(JsonData.of(from.toString()))
                        .lte(JsonData.of(to.toString())))
                ._toQuery();
    }

    private TimeBounds resolveTimeBounds(String requestedFrom, String requestedTo) {
        Instant now = Instant.now();
        Instant retentionStart = now.minus(RETENTION_PERIOD);

        Instant to = parseInstantOrNull(requestedTo);
        if (to == null) {
            to = now.plus(Duration.ofMinutes(5)); // Add future clock drift buffer
        }

        Instant from = parseInstantOrNull(requestedFrom);
        if (from == null) {
            from = retentionStart;
        }

        return new TimeBounds(from, to);
    }

    private Instant parseInstantOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            try {
                // Normalize space separator to T separator for ISO format
                String normalized = value.replace(' ', 'T');
                if (!normalized.endsWith("Z") && !normalized.contains("+") && normalized.lastIndexOf('-') <= 7) {
                    normalized = normalized + "Z";
                }
                return Instant.parse(normalized);
            } catch (Exception ex) {
                try {
                    return java.time.OffsetDateTime.parse(value.replace(' ', 'T')).toInstant();
                } catch (Exception exc) {
                    try {
                        return java.time.LocalDateTime.parse(value.replace(' ', 'T'))
                                .atZone(java.time.ZoneId.systemDefault()).toInstant();
                    } catch (Exception exx) {
                        return null;
                    }
                }
            }
        }
    }

    private boolean hasAnyLogIndexes() {
        try {
            return client.indices().exists(e -> e.index(INDEX_PATTERN)).value();
        } catch (Exception e) {
            logErrorThrottled("checkIndexes", e);
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record TimeBounds(Instant fromInstant, Instant toInstant) {
        public Instant getFromInstant() {
            return fromInstant;
        }

        public Instant getToInstant() {
            return toInstant;
        }
    }

    private List<Query> buildErrorWindowFilters(String windowExpression) {
        List<Query> filters = Stream.of(
                        TermQuery.of(t -> t
                                        .field(LEVEL_FIELD)
                                        .value(ERROR_LEVEL_VALUE))._toQuery(),
                        RangeQuery.of(r -> r
                                        .field("@timestamp")
                                        .gte(JsonData.of("now-" + windowExpression))
                                        .lte(JsonData.of("now")))._toQuery()
                        )
                        .toList();
        LOGGER.debug("ES ALERT WINDOW FILTERS → level={} window={}", ERROR_LEVEL_VALUE, windowExpression);
        return filters;
    }

    public Map<String, List<AlertItemView>> calculateAlerts(AuthenticatedUserContext context) {
        try {
            if (!hasAnyLogIndexes()) {
                return new HashMap<>();
            }

            List<LogEvent> errorLogs = searchMulti(
                    null, // services
                    null, // environment
                    List.of("error", "critical", "fatal"), // levels
                    null, // traceId
                    null, // message
                    "now-24h", // from
                    null, // to
                    0, // page
                    1000, // size
                    context
            );

            Map<String, Map<String, List<LogEvent>>> grouped = errorLogs.stream()
                    .filter(log -> log.getService() != null && log.getMessage() != null)
                    .collect(Collectors.groupingBy(
                            LogEvent::getService,
                            Collectors.groupingBy(LogEvent::getMessage)
                    ));

            Map<String, List<AlertItemView>> alertsMap = new HashMap<>();

            for (Map.Entry<String, Map<String, List<LogEvent>>> serviceEntry : grouped.entrySet()) {
                String serviceName = serviceEntry.getKey();
                List<AlertItemView> serviceAlerts = new ArrayList<>();

                for (Map.Entry<String, List<LogEvent>> messageEntry : serviceEntry.getValue().entrySet()) {
                    String message = messageEntry.getKey();
                    List<LogEvent> logs = messageEntry.getValue();

                    long count = logs.size();
                    String latestTimestamp = logs.stream()
                            .map(LogEvent::getTimestamp)
                            .filter(Objects::nonNull)
                            .max(Comparator.naturalOrder())
                            .orElse(null);

                    boolean isCritical = logs.stream().anyMatch(l -> "critical".equalsIgnoreCase(l.getLevel()) || "fatal".equalsIgnoreCase(l.getLevel())) || count >= 5;
                    String severity = isCritical ? "CRITICAL" : "WARNING";

                    serviceAlerts.add(new AlertItemView(
                            serviceName,
                            message,
                            count,
                            severity,
                            latestTimestamp
                    ));
                }
                alertsMap.put(serviceName, serviceAlerts);
            }

            return alertsMap;
        } catch (Exception e) {
            LOGGER.error("Failed to calculate alerts from Elasticsearch: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    Query buildMessageQuery(String message) {
        String[] words = message.split("\\s+");
        List<Query> wordQueries = new ArrayList<>();
        for (String word : words) {
            if (!word.isBlank()) {
                final String searchWord = word.toLowerCase(java.util.Locale.ROOT);
                Query q = QueryBuilders.bool(b -> b
                        .should(s1 -> s1.match(m -> m.field("message").query(searchWord)))
                        .should(s2 -> s2.wildcard(w -> w.field("message").value("*" + searchWord + "*").caseInsensitive(true)))
                        .minimumShouldMatch("1")
                );
                wordQueries.add(q);
            }
        }
        if (wordQueries.isEmpty()) {
            return QueryBuilders.matchAll().build()._toQuery();
        }
        return QueryBuilders.bool(b -> b.must(wordQueries));
    }

    private <T> SearchResponse<T> executeSearch(SearchRequest request, Class<T> clazz, String operation, Query generatedQuery, AuthenticatedUserContext accessContext, List<String> requestedServices) {
        if (accessContext != null && !accessContext.isAdmin() && requestedServices != null) {
            List<String> allowed = accessContext.allowedServices();
            for (String svc : requestedServices) {
                if (svc != null && !svc.isBlank() && (allowed == null || !allowed.contains(svc))) {
                    LOGGER.warn("RBAC access failure: User {} (role: DEV) attempted to access unauthorized service '{}'. Allowed: {}",
                            accessContext.email(), svc, allowed);
                }
            }
        }

        if (searchDebug) {
            LOGGER.info("SEARCH-DEBUG [{}]: Incoming filters: services={}, rbac_services={}",
                    operation, requestedServices, accessContext != null ? accessContext.allowedServices() : "none");
            LOGGER.info("SEARCH-DEBUG [{}]: Generated Elasticsearch query: {}", operation, generatedQuery);
        }

        long startTime = System.currentTimeMillis();
        try {
            SearchResponse<T> response = client.search(request, clazz);
            long duration = System.currentTimeMillis() - startTime;

            if (duration > 2000) {
                LOGGER.warn("Slow Elasticsearch query detected [{}]: took {}ms. Query: {}", operation, duration, generatedQuery);
            }

            long totalHits = (response.hits() != null && response.hits().total() != null) ? response.hits().total().value() : 0;
            if (searchDebug) {
                LOGGER.info("SEARCH-DEBUG [{}]: Total Elasticsearch hits: {}", operation, totalHits);
            }
            if (totalHits == 0) {
                LOGGER.info("Unexpected empty-result scenario [{}]: query returned 0 hits. Query: {}", operation, generatedQuery);
            }

            return response;
        } catch (Exception e) {
            LOGGER.error("Elasticsearch query failed [{}]: {}", operation, e.getMessage(), e);
            throw new RuntimeException("Elasticsearch query failure", e);
        }
    }

    public List<com.kovanlabs.logservice.model.ServiceLogMetrics> getServiceHealthMetrics(int windowMinutes, String organizationId) {
        try {
            if (!hasAnyLogIndexes()) {
                LOGGER.info("No app-logs-* indexes exist in Elasticsearch. Returning empty metrics.");
                return new ArrayList<>();
            }

            Instant now = Instant.now();
            Instant from = now.minus(windowMinutes, ChronoUnit.MINUTES);

            Query timeFilter = RangeQuery.of(r -> r
                    .field("@timestamp")
                    .gte(JsonData.of(from.toString()))
                    .lte(JsonData.of(now.toString())))._toQuery();

            Query orgFilter = QueryBuilders.term(t -> t
                    .field("organizationId")
                    .value(organizationId != null && !organizationId.isBlank() ? organizationId : "__NO_ORG__")
            );

            Query boolQuery = BoolQuery.of(b -> b
                    .filter(timeFilter)
                    .filter(orgFilter)
            )._toQuery();

            SearchRequest request = SearchRequest.of(s -> s
                    .index(INDEX_PATTERN)
                    .query(boolQuery)
                    .size(0)
                    .aggregations("services", a -> a
                            .terms(t -> t
                                    .field(SERVICE_FIELD)
                                    .size(200)
                            )
                            .aggregations("error_count", sub -> sub
                                    .filter(f -> f
                                            .term(term -> term
                                                    .field(LEVEL_FIELD)
                                                    .value("error"))))
                            .aggregations("warn_count", sub -> sub
                                    .filter(f -> f
                                            .term(term -> term
                                                    .field(LEVEL_FIELD)
                                                    .value("warn"))))
                            .aggregations("latest_timestamp", sub -> sub
                                    .max(m -> m
                                            .field("@timestamp")))));

            SearchResponse<Void> response = client.search(request, Void.class);

            if (response.aggregations() == null || response.aggregations().get("services") == null) {
                return new ArrayList<>();
            }

            return response.aggregations()
                    .get("services")
                    .sterms()
                    .buckets()
                    .array()
                    .stream()
                    .map(bucket -> {
                        String serviceName = bucket.key().stringValue();
                        long errorCount = 0;
                        long warnCount = 0;
                        Instant lastSeen = null;

                        if (bucket.aggregations() != null) {
                            if (bucket.aggregations().get("error_count") != null) {
                                errorCount = bucket.aggregations().get("error_count").filter().docCount();
                            }
                            if (bucket.aggregations().get("warn_count") != null) {
                                warnCount = bucket.aggregations().get("warn_count").filter().docCount();
                            }
                            if (bucket.aggregations().get("latest_timestamp") != null) {
                                double maxVal = bucket.aggregations().get("latest_timestamp").max().value();
                                if (Double.isFinite(maxVal) && maxVal > 0) {
                                    lastSeen = Instant.ofEpochMilli((long) maxVal);
                                }
                            }
                        }

                        return new com.kovanlabs.logservice.model.ServiceLogMetrics(serviceName, errorCount, warnCount, lastSeen);
                    })
                    .filter(Objects::nonNull)
                    .toList();

        } catch (Exception e) {
            LOGGER.error("Failed to fetch service health metrics from Elasticsearch: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
}