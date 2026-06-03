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
            return elasticRepository.save(logEvent);
        } catch (Exception e) {
            LOGGER.error("Failed to save log to Elasticsearch: {}", e.getMessage(), e);
            return false;
        }
    }


}