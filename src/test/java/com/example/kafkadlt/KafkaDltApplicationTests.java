package com.example.kafkadlt;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class KafkaDltApplicationTests {
	@Autowired
	private KafkaTemplate<String, Integer> kafkaTemplateInt;
	@Value("${kafka.topic.in}")
	private String topic;
	@Autowired
	private KafkaDltApplication.EventListener eventListener;
	@Autowired
	private KafkaConnectionDetails connectionDetails;

	@Container
	@ServiceConnection
	static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
		.withStartupTimeout(Duration.ofSeconds(20L));

	@BeforeEach
	public void resetListener() {
		eventListener.clear();
	}

	@Test
	void happyPath() {
		Integer value = 42;
		kafkaTemplateInt.send(topic, "key", value);
		await()
			.atLeast(1L, TimeUnit.MILLISECONDS)
			.atMost(10L, TimeUnit.SECONDS)
			.pollInterval(100L, TimeUnit.MILLISECONDS)
			.until(() -> eventListener.getReceived() != null && value.compareTo(eventListener.getReceived()) == 0);
	}

	@Test
	void deserIssue() {
		ProducerFactory<String, String> pf = new DefaultKafkaProducerFactory<>(Map.of(
			ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getBootstrapServers(),
			"key.serializer", StringSerializer.class.getName(),
			"value.serializer", StringSerializer.class.getName()
		));
		KafkaTemplate<String, String> stringTemplate = new KafkaTemplate<>(pf);
		stringTemplate.send(topic, "key", "foo-bar-baz");
		await()
			.atLeast(1L, TimeUnit.MILLISECONDS)
			.atMost(10L, TimeUnit.SECONDS)
			.pollInterval(100L, TimeUnit.MILLISECONDS)
			.until(() -> eventListener.getReceived() != null && Integer.valueOf(0).compareTo(eventListener.getReceived()) == 0);
	}

	@Test
	void retryAndDlt(){
		kafkaTemplateInt.send(topic, "baz", 42);
		await()
			.atLeast(1L, TimeUnit.MILLISECONDS)
			.atMost(10L, TimeUnit.SECONDS)
			.pollInterval(100L, TimeUnit.MILLISECONDS)
			.until(() -> eventListener.getDltEvent() != null);
		KafkaDltApplication.Tuple dltEvent = eventListener.getDltEvent();
		assertEquals("baz", dltEvent.key());
		assertEquals(42, dltEvent.value());
	}
}
