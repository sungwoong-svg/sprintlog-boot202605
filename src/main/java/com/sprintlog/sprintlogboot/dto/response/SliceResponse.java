package com.sprintlog.sprintlogboot.dto.response;

import java.util.List;

public record SliceResponse<T>(
        List<T> content, // 이번 페이지의 데이터
        int page, // 현재 페이지 번호
        int size, // 현재 페이지의 데이터 개수
        boolean hasNext // 다음 페이지가 있는가? (전체 개수는 모른다.)
) {
}
