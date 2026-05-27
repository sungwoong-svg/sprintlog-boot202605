package com.sprintlog.sprintlogboot.lifecycle;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ImportBatch {

    private final String batchId;
    private final LocalDateTime startedAt;

    public ImportBatch() {
        this.batchId = UUID.randomUUID().toString().substring(0, 8);
        this.startedAt = LocalDateTime.now();
    }

    public String getBatchId() {
        return batchId;
    }
    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    @Override
    public String toString() {
        return "ImportBatch{" +
                "batchId='" + batchId + '\'' +
                ", startedAt=" + startedAt +
                '}';
    }
}
