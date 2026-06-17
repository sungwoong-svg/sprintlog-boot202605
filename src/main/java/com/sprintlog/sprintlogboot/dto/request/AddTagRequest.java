package com.sprintlog.sprintlogboot.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AddTagRequest(
        @NotBlank(message = "태그는 비워둘 수 없습니다.")
        String tag
) {
}
