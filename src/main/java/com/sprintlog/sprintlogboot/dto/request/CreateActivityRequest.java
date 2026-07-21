package com.sprintlog.sprintlogboot.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sprintlog.sprintlogboot.domain.ActivityCategory;
import com.sprintlog.sprintlogboot.domain.Visibility;
import com.sprintlog.sprintlogboot.validation.ValidActivityByType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.Set;

@Schema(description = "활동 생성 요청 본문")
@ValidActivityByType
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
        @Size(max = 10, message = "태그는 최대 10개까지 붙일 수 있습니다.")
        Set<
                @Size(max = 20, message = "각 태그는 20자를 넘을 수 없습니다.")
                @Pattern(regexp = "^[A-Za-z0-9가-힣_-]+$", message = "태그는 한글, 영문, 숫자, _, -만 쓸 수 있습니다(공백, 특수문자 불가).")
                String> tags,

        @Schema(description = "학습한 날짜(선택, 미래 불가)", example = "2026-07-20")
        @PastOrPresent(message = "학습한 날짜는 미래일 수 없습니다.")
        LocalDate studiedOn,

        @Schema(description = "강사 이름 (type=LECTURE 일 때)", example = "이강사")
        @Size(max = 50, message = "강사 이름은 50자를 넘을 수 없습니다.")
        String instructorName,

        @Schema(description = "완료율 % (type=PRACTICE 일 때)", example = "85")
        @Min(value = 0, message = "완료율은 0 이상이어야 합니다.")
        @Max(value = 100, message = "완료율은 100을 넘을 수 없습니다.")
        Integer completionRate,

        @Schema(description = "책 제목 (type=READING 일 때)", example = "테스트 책")
        @Size(max = 200, message = "책 젬족은 200자를 넘을 수 없습니다.")
        String bookTitle
) {
}
