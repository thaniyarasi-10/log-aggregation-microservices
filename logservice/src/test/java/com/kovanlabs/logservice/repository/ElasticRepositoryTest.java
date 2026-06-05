package com.kovanlabs.logservice.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ElasticRepositoryTest {

    private ElasticRepository repository;

    @BeforeEach
    void setUp() {
        ElasticsearchClient mockClient = mock(ElasticsearchClient.class);
        repository = new ElasticRepository(mockClient);
    }

    @Test
    void testBuildMessageQuery_SingleWord() {
        Query query = repository.buildMessageQuery("Connection");
        assertNotNull(query);
        assertTrue(query.isBool());
        assertEquals(1, query.bool().must().size());
        
        Query firstWordQuery = query.bool().must().get(0);
        assertTrue(firstWordQuery.isBool());
        assertEquals(2, firstWordQuery.bool().should().size());
        
        Query matchQuery = firstWordQuery.bool().should().get(0);
        assertTrue(matchQuery.isMatch());
        assertEquals("message", matchQuery.match().field());
        assertEquals("connection", matchQuery.match().query().stringValue());
        
        Query wildcardQuery = firstWordQuery.bool().should().get(1);
        assertTrue(wildcardQuery.isWildcard());
        assertEquals("message", wildcardQuery.wildcard().field());
        assertEquals("*connection*", wildcardQuery.wildcard().value());
        assertTrue(wildcardQuery.wildcard().caseInsensitive());
    }

    @Test
    void testBuildMessageQuery_MultipleWords() {
        Query query = repository.buildMessageQuery("connection failed");
        assertNotNull(query);
        assertTrue(query.isBool());
        assertEquals(2, query.bool().must().size());

        // First word: connection
        Query firstWord = query.bool().must().get(0);
        assertTrue(firstWord.isBool());
        assertEquals("*connection*", firstWord.bool().should().get(1).wildcard().value());

        // Second word: failed
        Query secondWord = query.bool().must().get(1);
        assertTrue(secondWord.isBool());
        assertEquals("*failed*", secondWord.bool().should().get(1).wildcard().value());
    }

    @Test
    void testBuildMessageQuery_EmptyOrBlank() {
        Query query = repository.buildMessageQuery("   ");
        assertNotNull(query);
        assertTrue(query.isMatchAll());
    }
}
