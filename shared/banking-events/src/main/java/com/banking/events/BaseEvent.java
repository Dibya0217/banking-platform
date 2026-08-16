package com.banking.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class BaseEvent {

    private String eventId;
    private String eventType;
    private String eventVersion;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant producedAt;

    private String correlationId;
    private String producerService;

    @SuppressWarnings("unchecked")
    protected static <T extends BaseEventBuilder<?, ?>> T baseFields(T builder, String eventType, String service) {
        return (T) builder
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .eventVersion("1.0")
                .producedAt(Instant.now())
                .producerService(service);
    }
}
