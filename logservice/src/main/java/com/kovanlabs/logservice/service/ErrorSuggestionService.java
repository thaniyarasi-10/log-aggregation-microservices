package com.kovanlabs.logservice.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.logservice.model.ErrorSuggestion;
import com.kovanlabs.logservice.model.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.kovanlabs.logservice.repository.ElasticRepository;
import com.kovanlabs.logservice.util.ErrorNormalizer;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service that operates the suggestion engine to analyze log events
 * and attach error resolution metadata based on matched exception patterns.
 */
@Service
public class ErrorSuggestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorSuggestionService.class);

    private final ElasticRepository elasticRepository;
    private final GeminiAnalysisService geminiAnalysisService;
    private final java.util.Map<String, ErrorSuggestion> aiCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Object> inFlightGeminiCalls = new java.util.concurrent.ConcurrentHashMap<>();

    private List<ErrorPatternMapping> mappings = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ErrorSuggestionService() {
        this.elasticRepository = null;
        this.geminiAnalysisService = null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ErrorSuggestionService(ElasticRepository elasticRepository, GeminiAnalysisService geminiAnalysisService) {
        this.elasticRepository = elasticRepository;
        this.geminiAnalysisService = geminiAnalysisService;
    }

    /**
     * Initializes the service by loading mapping rules from classpath.
     */
    @PostConstruct
    public void init() {
        try {
            InputStream is = new ClassPathResource("error-suggestions.json").getInputStream();
            List<ErrorPatternMapping> loaded = objectMapper.readValue(is, new TypeReference<List<ErrorPatternMapping>>() {});
            loaded.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
            this.mappings = loaded;
            LOGGER.info("Successfully loaded {} error suggestion pattern mappings.", mappings.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load error suggestion mappings from JSON. Using hardcoded fallbacks. Error: {}", e.getMessage());
            initializeFallbacks();
        }
    }

    private void initializeFallbacks() {
        this.mappings = new ArrayList<>();

        // NullPointerException
        mappings.add(new ErrorPatternMapping("NullPointerException|java\\.lang\\.NullPointerException", true, "NullPointerException",
                List.of("Attempted to invoke a method on a null object reference", "Attempted to access or modify a field of a null object reference", "Passed a null argument to a method that rejects null"),
                List.of("Add null check guards (e.g., Objects.requireNonNull or if (obj != null)) before accessing methods or fields", "Verify that instance variables are properly initialized before usage", "Use Optional or default values to safely handle potentially missing data"),
                "MEDIUM", 10));

        // OutOfMemoryError
        mappings.add(new ErrorPatternMapping("OutOfMemoryError|java\\.lang\\.OutOfMemoryError", true, "OutOfMemoryError",
                List.of("Java Heap space is exhausted due to large objects or high object volume", "Metaspace limits exceeded because too many classes were loaded", "Memory leak where objects are referenced indefinitely, preventing garbage collection"),
                List.of("Increase JVM heap memory limit (e.g., use -Xmx2g or -Xmx4g)", "Analyze a heap dump using memory profiling tools (like VisualVM, Eclipse MAT) to find leaks", "Optimize code to process data in streams or chunks instead of loading entire tables/files into memory"),
                "CRITICAL", 10));

        // SQLTimeoutException
        mappings.add(new ErrorPatternMapping("SQLTimeoutException|java\\.sql\\.SQLTimeoutException|query timeout", true, "SQLTimeoutException",
                List.of("Database query took longer than the configured timeout limit", "Heavy load or lock contention on the database server", "Slow database operations due to missing indexes or unoptimized SQL queries"),
                List.of("Optimize the SQL query and verify that proper indexes are applied", "Increase query/transaction timeout parameters in application database configuration if long execution is expected", "Monitor database locks and optimize transaction boundaries to reduce contention"),
                "HIGH", 10));

        // Communications link failure
        mappings.add(new ErrorPatternMapping("Communications link failure|CommunicationsException", true, "Communications link failure",
                List.of("Database server is offline or restarting", "Network disruption between the application service and the database host", "Incorrect JDBC URL connection string, host IP, or port configuration"),
                List.of("Verify that the database server process is active and running", "Check firewall rules and security groups to allow communication between application and database hosts", "Validate JDBC connection details in application properties (e.g., host, port, username)"),
                "HIGH", 10));

        // RedisConnectionFailureException
        mappings.add(new ErrorPatternMapping("RedisConnectionFailureException|Unable to connect to Redis|Redis connection failed|RedisConnectionException", true, "RedisConnectionFailureException",
                List.of("Redis server is not running", "Redis host or port is incorrect", "Redis container is unavailable", "Network connectivity issue"),
                List.of("Start Redis container", "Verify spring.redis.host", "Verify spring.redis.port", "Check Docker networking"),
                "HIGH", 10));

        // ConnectException
        mappings.add(new ErrorPatternMapping("ConnectException|java\\.net\\.ConnectException|Connection refused", true, "ConnectException",
                List.of("Target microservice or external API is down or not listening on the specified port", "Network/firewall blocks access to the target host", "Service discovery issue (e.g., Eureka registered a stale or incorrect instance IP/port)"),
                List.of("Check the health and running status of the target microservice", "Verify connectivity to the target port using telnet/nc commands", "Confirm Eureka service registry status and ensure instance hostname/port are resolved correctly"),
                "HIGH", 10));

        // SocketTimeoutException
        mappings.add(new ErrorPatternMapping("SocketTimeoutException|java\\.net\\.SocketTimeoutException|Read timed out", true, "SocketTimeoutException",
                List.of("Target service responded too slowly, exceeding the HTTP read/socket timeout limit", "Heavy resource utilization or slow database calls on the downstream service", "Network latency or packet loss causing delayed response delivery"),
                List.of("Analyze the logs of the downstream service to determine why request handling was slow", "Increase the read-timeout or connection-timeout configuration in RestTemplate/WebClient/FeignClient", "Implement circuit breakers (Resilience4j) or retry logic to handle transient timeouts gracefully"),
                "HIGH", 10));

        // Kafka TimeoutException
        mappings.add(new ErrorPatternMapping("org\\.apache\\.kafka\\.common\\.errors\\.TimeoutException|Kafka TimeoutException|TimeoutException", true, "Kafka TimeoutException",
                List.of("Kafka broker cluster is offline, unreachable, or in an unhealthy state", "Network delay or misconfigured bootstrap.servers configuration", "Producer request buffer filled up because brokers cannot keep up with write rate"),
                List.of("Check Kafka broker logs and ensure the brokers are running and cluster metadata is healthy", "Verify network routing and validate connection properties in application properties", "Increase delivery.timeout.ms or request.timeout.ms configurations in producer settings"),
                "HIGH", 10));

        // LeaderNotAvailableException
        mappings.add(new ErrorPatternMapping("LeaderNotAvailableException|org\\.apache\\.kafka\\.common\\.errors\\.LeaderNotAvailableException", true, "LeaderNotAvailableException",
                List.of("Partition leader election is actively occurring for the requested topic/partition", "All replica brokers for the partition have crashed or are offline", "Zookeeper or KRaft metadata sync lag across brokers"),
                List.of("Verify that the Kafka broker cluster has a quorum and all nodes are online", "Implement automatic retries (retries > 0) in the producer configuration to wait for election to finish", "Run kafka-topics.sh to check the under-replicated partition counts and rebalance partitions if needed"),
                "HIGH", 10));

        // NoSuchBeanDefinitionException
        mappings.add(new ErrorPatternMapping("NoSuchBeanDefinitionException|org\\.springframework\\.beans\\.factory\\.NoSuchBeanDefinitionException", true, "NoSuchBeanDefinitionException",
                List.of("Spring container cannot find a bean of the required type or with the required name", "The package containing the class is not included in Spring's scan packages", "The bean definition was disabled due to conditional annotations (e.g. @ConditionalOnProperty)"),
                List.of("Ensure the missing bean class is annotated with @Component, @Service, @Repository, or declared via @Bean", "Check @ComponentScan or @SpringBootApplication scan packages to verify the package is covered", "Verify that all required conditional properties are correctly specified in the application profile"),
                "HIGH", 15));

        // BeanCreationException
        mappings.add(new ErrorPatternMapping("BeanCreationException|org\\.springframework\\.beans\\.factory\\.BeanCreationException", true, "BeanCreationException",
                List.of("Error during dependency injection (autowiring) due to missing dependencies", "Initialization method (@PostConstruct or AfterPropertiesSet) threw an exception", "Circular dependency detected between configured Spring beans"),
                List.of("Inspect the 'caused by' stack traces nested inside the exception to find the root initialization error", "Break circular dependencies using lazy initialization (@Lazy) or structural refactoring", "Verify that all autowired dependencies are correctly registered in the Spring ApplicationContext"),
                "HIGH", 10));

        // Sort by priority desc
        this.mappings.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
    }

    /**
     * Scans the log message and error details to return the best matched suggestion.
     *
     * @param message      the log message
     * @param errorDetails the detailed stack trace or error info
     * @return the matched suggestion, or a default generic suggestion if none match
     */
    public ErrorSuggestion getSuggestionForLog(String message, String errorDetails) {
        String searchText = (message != null ? message : "") + " " + (errorDetails != null ? errorDetails : "");
        if (searchText.trim().isEmpty()) {
            return getGenericSuggestion();
        }

        for (ErrorPatternMapping mapping : mappings) {
            if (isMatch(searchText, mapping)) {
                return new ErrorSuggestion(
                        mapping.getErrorType(),
                        mapping.getPossibleCauses(),
                        mapping.getSuggestedFixes(),
                        mapping.getSeverity()
                );
            }
        }

        return getGenericSuggestion();
    }

    private boolean isMatch(String text, ErrorPatternMapping mapping) {
        if (mapping.getPattern() == null || mapping.getPattern().isBlank()) {
            return false;
        }

        if (mapping.isRegex()) {
            try {
                Pattern pattern = Pattern.compile(mapping.getPattern(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
                return pattern.matcher(text).find();
            } catch (Exception e) {
                LOGGER.error("Invalid regex pattern defined: {}", mapping.getPattern(), e);
                return false;
            }
        } else {
            return text.toLowerCase().contains(mapping.getPattern().toLowerCase());
        }
    }

    private ErrorSuggestion getGenericSuggestion() {
        return new ErrorSuggestion(
                "UnknownException",
                List.of("An unexpected exception occurred during application execution"),
                List.of("Examine the log message and stack trace in errorDetails for clues", "Verify system health and third-party service availability"),
                "HIGH"
        );
    }

    /**
     * Analyzes an incoming ERROR log and attaches the suggestion parameters directly.
     *
     * @param logEvent the log event to modify
     */
    public void attachSuggestion(LogEvent logEvent) {
        if (logEvent == null) {
            return;
        }

        String message = ErrorNormalizer.normalize(logEvent.getMessage());
        String errorDetails = ErrorNormalizer.normalize(logEvent.getErrorDetails());
        LOGGER.info("[Learning Pipeline] Starting suggestion attachment for service '{}'. Message: '{}'", 
                logEvent.getService(), message != null && message.length() > 60 ? message.substring(0, 60) + "..." : message);

        String searchText = (message != null ? message : "") + " " + (errorDetails != null ? errorDetails : "");

        // 1. Evaluate Rule Engine patterns
        LOGGER.debug("[Learning Pipeline] Step 1: Checking Rule Engine mappings (count={})", mappings.size());
        for (ErrorPatternMapping mapping : mappings) {
            if (isMatch(searchText, mapping)) {
                LOGGER.info("[Learning Pipeline] Match found in Rule Engine mappings. Type: '{}'", mapping.getErrorType());
                logEvent.setErrorType(mapping.getErrorType());
                logEvent.setPossibleCauses(mapping.getPossibleCauses());
                logEvent.setSuggestedFixes(mapping.getSuggestedFixes());
                logEvent.setSeverity(mapping.getSeverity());
                logEvent.setSuggestionSource("RULE_ENGINE");
                logEvent.setConfidence(100);
                logEvent.setSuggestionGeneratedAt(Instant.now().toString());
                return;
            }
        }

        // 2. Check local in-memory cache using signature hash
        String rawTextForHash = (message != null ? message : "") + "|" + (errorDetails != null ? errorDetails : "");
        String signatureHash = ErrorNormalizer.hashSignature(rawTextForHash);
        LOGGER.debug("[Learning Pipeline] Step 2: Checking local in-memory cache with signatureHash: {}", signatureHash);
        ErrorSuggestion cached = aiCache.get(signatureHash);
        if (cached != null) {
            LOGGER.info("[Learning Pipeline] Match found in local in-memory cache. Source: '{}', Type: '{}'", 
                    cached.getSuggestionSource(), cached.getErrorType());
            populateLogEvent(logEvent, cached);
            return;
        }

        // 3. Query error-knowledge-base in Elasticsearch
        LOGGER.debug("[Learning Pipeline] Step 3: Querying Elasticsearch error-knowledge-base");
        if (elasticRepository != null) {
            java.util.Optional<com.kovanlabs.logservice.model.ErrorKnowledgeBaseEntry> similar =
                    elasticRepository.findSimilarKnowledgeBaseEntry(message, errorDetails);
            if (similar.isPresent()) {
                com.kovanlabs.logservice.model.ErrorKnowledgeBaseEntry entry = similar.get();
                LOGGER.info("[Learning Pipeline] Match found in error-knowledge-base. Type: '{}', Source: '{}'", 
                        entry.getErrorType(), entry.getSource());
                logEvent.setErrorType(entry.getErrorType());
                logEvent.setPossibleCauses(entry.getPossibleCauses());
                logEvent.setSuggestedFixes(entry.getSuggestedFixes());
                logEvent.setSeverity(entry.getSeverity());
                logEvent.setRootCause(entry.getRootCause());
                logEvent.setConfidence(entry.getConfidence());
                logEvent.setSuggestionSource("KNOWLEDGE_BASE");
                logEvent.setSuggestionGeneratedAt(Instant.now().toString());

                // Cache in memory
                ErrorSuggestion cachedSuggestion = new ErrorSuggestion(
                        entry.getErrorType(),
                        entry.getPossibleCauses(),
                        entry.getSuggestedFixes(),
                        entry.getSeverity()
                );
                cachedSuggestion.setRootCause(entry.getRootCause());
                cachedSuggestion.setConfidence(entry.getConfidence());
                cachedSuggestion.setSuggestionSource("KNOWLEDGE_BASE");
                aiCache.put(signatureHash, cachedSuggestion);
                return;
            } else {
                LOGGER.debug("[Learning Pipeline] No similar entry found in error-knowledge-base");
            }
        } else {
            LOGGER.warn("[Learning Pipeline] ElasticRepository is null; skipping Step 3 (Knowledge Base)");
        }

        // 4. Call Gemini AI with in-flight request deduplication
        LOGGER.debug("[Learning Pipeline] Step 4: Calling Gemini AI with in-flight deduplication");
        if (geminiAnalysisService != null) {
            Object lock = inFlightGeminiCalls.computeIfAbsent(signatureHash, k -> new Object());
            synchronized (lock) {
                // Double check in-memory cache inside synchronized block
                ErrorSuggestion secondCheck = aiCache.get(signatureHash);
                if (secondCheck != null) {
                    LOGGER.info("[Learning Pipeline] Concurrent request resolved from cache for signatureHash: {}", signatureHash);
                    populateLogEvent(logEvent, secondCheck);
                    return;
                }

                com.kovanlabs.logservice.model.GeminiResponse geminiResponse =
                        geminiAnalysisService.analyze(message, errorDetails, logEvent.getService(), logEvent.getLevel(), logEvent.getTimestamp());
                if (geminiResponse != null) {
                    LOGGER.info("[Learning Pipeline] Gemini AI response returned. Type: '{}', Confidence: {}%", 
                            geminiResponse.getErrorType(), geminiResponse.getConfidence());
                    logEvent.setErrorType(geminiResponse.getErrorType());
                    logEvent.setPossibleCauses(geminiResponse.getPossibleCauses());
                    logEvent.setSuggestedFixes(geminiResponse.getSuggestedFixes());
                    logEvent.setSeverity(geminiResponse.getSeverity());
                    logEvent.setRootCause(geminiResponse.getRootCause());
                    logEvent.setConfidence(geminiResponse.getConfidence());
                    logEvent.setSuggestionSource("GEMINI");
                    logEvent.setSuggestionGeneratedAt(Instant.now().toString());

                    // Cache in memory
                    ErrorSuggestion cachedSuggestion = new ErrorSuggestion(
                            geminiResponse.getErrorType(),
                            geminiResponse.getPossibleCauses(),
                            geminiResponse.getSuggestedFixes(),
                            geminiResponse.getSeverity()
                    );
                    cachedSuggestion.setRootCause(geminiResponse.getRootCause());
                    cachedSuggestion.setConfidence(geminiResponse.getConfidence());
                    cachedSuggestion.setSuggestionSource("GEMINI");
                    aiCache.put(signatureHash, cachedSuggestion);

                    // Save to Elasticsearch Knowledge Base
                    if (elasticRepository != null) {
                        LOGGER.info("[Learning Pipeline] Persisting Gemini suggestion to error-knowledge-base index");
                        com.kovanlabs.logservice.model.ErrorKnowledgeBaseEntry entry = new com.kovanlabs.logservice.model.ErrorKnowledgeBaseEntry(
                                message,
                                geminiResponse.getErrorType(),
                                geminiResponse.getRootCause(),
                                geminiResponse.getPossibleCauses(),
                                geminiResponse.getSuggestedFixes(),
                                geminiResponse.getSeverity(),
                                geminiResponse.getConfidence(),
                                "GEMINI",
                                Instant.now().toString()
                        );
                        entry.setSignatureHash(signatureHash);
                        elasticRepository.saveKnowledgeBaseEntry(entry);
                    } else {
                        LOGGER.warn("[Learning Pipeline] ElasticRepository is null; cannot persist learned solution");
                    }
                    inFlightGeminiCalls.remove(signatureHash);
                    return;
                } else {
                    LOGGER.warn("[Learning Pipeline] Gemini AI analysis returned null");
                }
            }
            inFlightGeminiCalls.remove(signatureHash);
        } else {
            LOGGER.warn("[Learning Pipeline] GeminiAnalysisService is null; skipping Step 4 (Gemini AI)");
        }

        // Fallback: rule-engine generic suggestion
        LOGGER.info("[Learning Pipeline] Falling back to generic Rule Engine suggestion");
        ErrorSuggestion fallback = getGenericSuggestion();
        populateLogEvent(logEvent, fallback);
    }

    private void populateLogEvent(LogEvent logEvent, ErrorSuggestion suggestion) {
        logEvent.setErrorType(suggestion.getErrorType());
        logEvent.setPossibleCauses(suggestion.getPossibleCauses());
        logEvent.setSuggestedFixes(suggestion.getSuggestedFixes());
        logEvent.setSeverity(suggestion.getSeverity());
        logEvent.setRootCause(suggestion.getRootCause());
        logEvent.setConfidence(suggestion.getConfidence() == 0 ? 100 : suggestion.getConfidence());
        logEvent.setSuggestionSource(suggestion.getSuggestionSource() != null ? suggestion.getSuggestionSource() : "RULE_ENGINE");
        logEvent.setSuggestionGeneratedAt(Instant.now().toString());
    }

    public List<ErrorPatternMapping> getMappings() {
        return Collections.unmodifiableList(mappings);
    }

    /**
     * Inner class representing configuration matching pattern.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorPatternMapping {
        private String pattern;
        @JsonProperty("isRegex")
        private boolean isRegex;
        private String errorType;
        private List<String> possibleCauses;
        private List<String> suggestedFixes;
        private String severity;
        private int priority;

        public ErrorPatternMapping() {}

        public ErrorPatternMapping(String pattern, boolean isRegex, String errorType,
                                    List<String> possibleCauses, List<String> suggestedFixes,
                                    String severity, int priority) {
            this.pattern = pattern;
            this.isRegex = isRegex;
            this.errorType = errorType;
            this.possibleCauses = possibleCauses;
            this.suggestedFixes = suggestedFixes;
            this.severity = severity;
            this.priority = priority;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        @JsonProperty("isRegex")
        public boolean isRegex() {
            return isRegex;
        }

        @JsonProperty("isRegex")
        public void setIsRegex(boolean isRegex) {
            this.isRegex = isRegex;
        }

        public String getErrorType() {
            return errorType;
        }

        public void setErrorType(String errorType) {
            this.errorType = errorType;
        }

        public List<String> getPossibleCauses() {
            return possibleCauses;
        }

        public void setPossibleCauses(List<String> possibleCauses) {
            this.possibleCauses = possibleCauses;
        }

        public List<String> getSuggestedFixes() {
            return suggestedFixes;
        }

        public void setSuggestedFixes(List<String> suggestedFixes) {
            this.suggestedFixes = suggestedFixes;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }
    }
}
