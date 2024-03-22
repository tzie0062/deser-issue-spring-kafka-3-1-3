# Sample project to highlight possible deserialization issue with spring-kafka 3.1.3

## Issue

When using `spring-boot 3.2.3 / spring-kafka 3.1.2` the tests in this project work as expected: an invalid event is handled by
the `FailedDeserializationFunction`, however when upgrading to `3.2.4` this is no longer the case and a faulty event leads to an
endless loop.

## Testing

### spring-boot 3.2.3 / spring-kafka 3.1.2

Run the app's test e.g. by calling `./mvnw clean verify` or from within an IDE.

**Result**: both tests pass

### spring-boot 3.2.4 / spring-kafka 3.1.3

Update the `spring-boot-starter-parent` in the `pom.xml` to `3.2.4` and run the app's test e.g. by calling `./mvnw clean verify` or from within an IDE.

**Result**: 1 test passes / 1 test fails

The `deserIssue` test fails: `FailedDeserializationFunction` is still called, but a `DeadLetterPublishingRecoverer` tries to send
the faulty event to `test-topic-kafka-dlt-retry-0` which consistently fails and leads to an infinite loop.

## Fix
Question: https://stackoverflow.com/questions/78207314/possible-deserialization-issue-with-spring-kafka-3-1-3/
includes the fix: use `org.apache.kafka.common.header.internals.RecordHeader` instead of `byte[]` for the exception header.