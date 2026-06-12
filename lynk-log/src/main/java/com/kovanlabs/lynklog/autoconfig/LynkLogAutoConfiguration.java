package com.kovanlabs.lynklog.autoconfig;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import com.kovanlabs.lynklog.client.VerificationClient;
import com.kovanlabs.lynklog.config.LynkLogProperties;
import com.kovanlabs.lynklog.config.KafkaProducerConfig;
import com.kovanlabs.lynklog.context.ServiceContext;
import com.kovanlabs.lynklog.producer.LynkKafkaProducer;
import com.kovanlabs.lynklog.parser.LogParser;
import com.kovanlabs.lynklog.queue.LynkLogQueue;
import com.kovanlabs.lynklog.worker.KafkaPublisherWorker;
import com.kovanlabs.lynklog.startup.LynkLogStartupVerifier;
import com.kovanlabs.lynklog.watcher.LynkLogFileWatcher;

@AutoConfiguration
@EnableConfigurationProperties(LynkLogProperties.class)
@Import(KafkaProducerConfig.class)
public class LynkLogAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ServiceContext serviceContext() {
        return new ServiceContext();
    }

    @Bean
    @ConditionalOnMissingBean
    public VerificationClient verificationClient() {
        return new VerificationClient();
    }

    @Bean
    @ConditionalOnMissingBean
    public LynkLogQueue lynkLogQueue() {
        return new LynkLogQueue();
    }

    @Bean
    @ConditionalOnMissingBean
    public LogParser logParser() {
        return new LogParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public LynkKafkaProducer lynkKafkaProducer(KafkaTemplate<String, String> kafkaTemplate,
                                               ServiceContext serviceContext,
                                               LynkLogProperties properties) {
        return new LynkKafkaProducer(kafkaTemplate, serviceContext, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public KafkaPublisherWorker kafkaPublisherWorker(LynkLogQueue queue, LynkKafkaProducer producer, LogParser logParser) {
        return new KafkaPublisherWorker(queue, producer, logParser);
    }

    @Bean
    @ConditionalOnMissingBean
    public LynkLogFileWatcher lynkLogFileWatcher(LynkLogProperties properties, LynkLogQueue queue) {
        return new LynkLogFileWatcher(properties, queue);
    }

    @Bean
    @ConditionalOnMissingBean
    public LynkLogStartupVerifier lynkLogStartupVerifier(LynkLogProperties properties,
                                                           VerificationClient verificationClient,
                                                           ServiceContext serviceContext,
                                                           LynkLogFileWatcher fileWatcher) {
        return new LynkLogStartupVerifier(properties, verificationClient, serviceContext, fileWatcher);
    }
}
