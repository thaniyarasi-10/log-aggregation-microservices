package com.kovanlabs.logservice.mongo.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.kovanlabs.logservice.model.LogEvent;

public interface MongoLogEventRepository extends MongoRepository<LogEvent, String> {
}