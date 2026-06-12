package com.kovanlabs.logservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.kovanlabs.logservice.model.LogEvent;
import com.kovanlabs.logservice.repository.ElasticRepository;

@Service
public class ElasticSearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchService.class);

    private final ElasticRepository elasticRepository;

    public ElasticSearchService(ElasticRepository elasticRepository) {
        this.elasticRepository = elasticRepository;
    }

    public boolean save(LogEvent logEvent) {
        if (logEvent == null) {
            LOGGER.warn("Attempt to save null LogEvent");
            return false;
        }

        try {
//            LOGGER.debug("Saving log to Elasticsearch via repository - service: {}, timestamp: {}",
//                    logEvent.getService(), logEvent.getTimestamp());
//            LOGGER.info("Saving to Elasticsearch");
            boolean saved = elasticRepository.save(logEvent);
//            LOGGER.info("Saved to Elasticsearch");
            return saved;
        } catch (Exception e) {
            LOGGER.error("Failed to save log to Elasticsearch: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean saveAll(java.util.List<LogEvent> logEvents) {
        if (logEvents == null || logEvents.isEmpty()) {
            return true;
        }
        try {
            return elasticRepository.saveAll(logEvents);
        } catch (Exception e) {
            LOGGER.error("Failed to bulk save logs to Elasticsearch: {}", e.getMessage(), e);
            return false;
        }
    }


}
