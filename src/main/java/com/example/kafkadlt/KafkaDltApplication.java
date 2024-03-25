package com.example.kafkadlt;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;
import org.springframework.kafka.retrytopic.RetryTopicHeaders;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.serializer.SerializationUtils;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.EnableScheduling;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@EnableScheduling
@EnableKafka
@SpringBootApplication
@Slf4j
public class KafkaDltApplication {

	public static void main(String[] args) {
		SpringApplication.run(KafkaDltApplication.class, args);
	}

	@Bean
	public RetryTopicConfiguration retryTopicConfiguration(
		KafkaTemplate<String, Integer> template,
		@Value("${kafka.topic.in}") String topicToInclude,
		@Value("${spring.application.name}") String appName) {
		return RetryTopicConfigurationBuilder.newInstance()
			.retryTopicSuffix("-" + appName + "-retry")
			.maxAttempts(3) // original processing + 2x retry
			.fixedBackOff(1000L)
			.suffixTopicsWithIndexValues()
			.dltSuffix("-" + appName + ".dlt")
			.includeTopic(topicToInclude)
			.dltHandlerMethod("eventListener", "processDltEvent")
			.create(template);
	}

	@Bean
	public EventListener eventListener() {
		return new EventListener();
	}

	public class EventListener {

		@Getter
		private Integer received;

		@Getter
		private Tuple dltEvent;

		@KafkaListener(id = "myListener", topics = "${kafka.topic.in}", groupId = "${spring.kafka.consumer.group-id}", idIsGroup = false)
		public void processRopEvent(
			@Payload Integer event,
			@Header(name = KafkaHeaders.RECEIVED_KEY, required = false, defaultValue = "n/a") String key,
			@Header(name = SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER,
				required = false) RecordHeader deSerializationErrorHeader,
			@Header(name = RetryTopicHeaders.DEFAULT_HEADER_ATTEMPTS, required = false, defaultValue = "1") int attempt) {
			if (deSerializationErrorHeader != null) {
				log.info("Skipping event with key {} because of deSer issue.", key);
				received = event;
				return;
			}
			log.info("This is attempt <{}> to process event <{}>.", attempt, event);
			if (key.equalsIgnoreCase("baz")) {
				throw new RuntimeException("Something bad happened.");
			}
			log.info("Received  key {} and event: {}", key, event);
			this.received = event;
		}

		@DltHandler
		public void processDltEvent(ConsumerRecord<String, Integer> dltRecord) {
			log.info("DLT-processing: key/value {}/{}", dltRecord.key(), dltRecord.value());
			this.dltEvent = new Tuple(dltRecord.key(), dltRecord.value());
		}

		public void clear() {
			this.received = null;
			this.dltEvent = null;
		}
	}

	public static final record Tuple(String key, Integer value) { }

}
