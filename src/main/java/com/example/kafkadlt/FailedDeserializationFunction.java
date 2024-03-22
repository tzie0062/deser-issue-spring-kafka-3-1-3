package com.example.kafkadlt;

import java.util.function.Function;

import org.springframework.kafka.support.serializer.FailedDeserializationInfo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FailedDeserializationFunction implements Function<FailedDeserializationInfo, Integer> {

	@Override
	public Integer apply(final FailedDeserializationInfo failedDeserializationInfo) {
		log.info("Failed to deserialize event - returning default");
		return 0;
	}
}
