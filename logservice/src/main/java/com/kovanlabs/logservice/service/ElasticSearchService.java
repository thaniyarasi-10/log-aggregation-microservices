package com.kovanlabs.logservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.kovanlabs.logservice.model.LogEvent;


@Service
public class ElasticSearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchService.class);

    @Value("${elasticsearch.host:localhost}")
    private String esHost;

    @Value("${elasticsearch.port:9200}")
    private int esPort;

    public void save(LogEvent logEvent) {
        if (logEvent == null) {
            LOGGER.warn("Attempt to save null LogEvent");
            return;
        }

//        try {
//            LOGGER.debug("Saving log to Elasticsearch - service: {}, timestamp: {}",
//                    logEvent.getService(), logEvent.getTimestamp());
//        } catch (Exception e) {
//            LOGGER.error("Failed to save log to Elasticsearch: {}", e.getMessage(), e);
//        }
    }


    public void search(String query) {
        try {
            LOGGER.debug("Searching logs with query: {}", query);
        } catch (Exception e) {
            LOGGER.error("Failed to search logs: {}", e.getMessage(), e);
        }
    }
}