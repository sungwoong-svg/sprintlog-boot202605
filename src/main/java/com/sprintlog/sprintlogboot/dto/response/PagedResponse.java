package com.sprintlog.sprintlogboot.dto.response;

import java.util.List;

public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        int totalElements
) {
}
