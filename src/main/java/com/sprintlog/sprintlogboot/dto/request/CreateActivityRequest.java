package com.sprintlog.sprintlogboot.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sprintlog.sprintlogboot.domain.ActivityCategory;
import com.sprintlog.sprintlogboot.domain.Visibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.Set;

@Schema(description = "활동 생성 요청 본문")
public record CreateActivityRequest(

        @Schema(description = "활동 유형", example = "LECTURE", requiredMode = Schema.RequiredMode.REQUIRED)
        // 빈 문자열과, 공백 문자열 허용 / null은 안됨!
        @NotNull(message = "활동 유형(type)은 필수입니다.")
        @JsonProperty("category")
        ActivityCategory type,

        @Schema(description = "학습 제목", example = "Spring Bean Scope", requiredMode = Schema.RequiredMode.REQUIRED)
//        @NotEmpty: 공백 문자열 허용 / 빈문자열, null은 안됨!
        // 빈 문자열, 공백 문자열, null 모두 안됨!
        @NotBlank(message = "제목은 비워둘 수 없습니다.")
        String title,

        @Schema(description = "학습 시간(분, 1~1440)", example = "90분", requiredMode = Schema.RequiredMode.REQUIRED)
        @Min(value = 1, message = "학습 시간은 1분 이상이어야 합니다.")
        @Max(value = 1440, message = "학습 시간은 하루(1440분)를 넘을 수 없습니다.")
        int minutes,

        @Schema(description = "공개 여부", example = "PUBLIC", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "공개 여부는 필수입니다.")
        Visibility visibility,


        // 선택값들
        @Schema(description = "태그 목록", example = "[\"spring\", \"java\"]")
        Set<String> tags,

        @Schema(description = "강사 이름 (type=LECTURE 일 때)", example = "이강사")
        String instructorName,

        @Schema(description = "완료율 % (type=PRACTICE 일 때)", example = "85")
        int completionRate,

        @Schema(description = "책 제목 (type=READING 일 때)", example = "테스트 책")
        String bookTitle
) {
}
