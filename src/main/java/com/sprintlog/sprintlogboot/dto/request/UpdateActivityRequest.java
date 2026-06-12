package com.sprintlog.sprintlogboot.dto.request;

import com.sprintlog.sprintlogboot.domain.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateActivityRequest(
        @NotBlank(message = "제목은 비워둘 수 없습니다.")
        String title,
        @NotNull(message = "공개 여부는 필수입니다.")
        Visibility visibility
) {
}
