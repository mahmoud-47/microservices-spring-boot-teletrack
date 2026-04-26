package com.teletrack.reportingservice.listener;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class DltListener {

    @KafkaListener(
            topics = {
                    "incident.created.DLT",
                    "incident.assigned.DLT",
                    "incident.status.changed.DLT",
                    "incident.resolved.DLT",
                    "incident.closed.DLT"
            },
            groupId = "reporting-service-dlt"
    )
    public void handleDlt(ConsumerRecord<String, String> record) {
        String originalTopic = header(record, "kafka_dlt-original-topic");
        String exceptionClass = header(record, "kafka_dlt-exception-fqcn");
        String exceptionMessage = header(record, "kafka_dlt-exception-message");
        log.error("DLT | topic={} partition={} offset={} exception={}: {} | payload={}",
                originalTopic,
                record.partition(),
                record.offset(),
                exceptionClass,
                exceptionMessage,
                record.value());
    }

    private String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : "unknown";
    }
}
