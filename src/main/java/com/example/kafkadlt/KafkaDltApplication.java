package com.example.kafkadlt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;
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
			.suffixTopicsWithIndexValues()
			.dltSuffix("-" + appName + ".dlt")
			.includeTopic(topicToInclude)
			.create(template);
	}

	@Bean
	public EventListener eventListener() {
		return new EventListener();
	}

	public class EventListener {

		@Getter
		private Integer received;

		@KafkaListener(id = "myListener", topics = "${kafka.topic.in}", groupId = "${spring.kafka.consumer.group-id}", idIsGroup = false)
		public void processRopEvent(
			@Payload Integer event,
			@Header(name = KafkaHeaders.RECEIVED_KEY, required = false, defaultValue = "n/a") String key,
			@Header(name = SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER, required = false) byte[] deSerializationErrorHeader) {
			if (deSerializationErrorHeader != null) {
				log.info("Skipping event with key {} because of deSer issue.", key);
				received = event;
				return;
			}
			log.info("Received  key {} and event: {}", key, event);
			this.received = event;
		}

		public void clear(){
			this.received = null;
		}
	}

}
