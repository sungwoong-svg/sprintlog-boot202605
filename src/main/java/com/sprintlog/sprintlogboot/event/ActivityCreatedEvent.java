package com.sprintlog.sprintlogboot.event;

import java.time.LocalDate;

public record ActivityCreatedEvent(
    Long activityId,
    Long ownerId,
    String title,
    int minutes,
    LocalDate studiedOn
) {
}
