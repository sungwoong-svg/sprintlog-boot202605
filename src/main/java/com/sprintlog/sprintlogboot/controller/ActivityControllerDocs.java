package com.sprintlog.sprintlogboot.controller;

import com.sprintlog.sprintlogboot.domain.ActivityCategory;
import com.sprintlog.sprintlogboot.domain.LearningActivity;
import com.sprintlog.sprintlogboot.dto.request.CreateActivityRequest;
import com.sprintlog.sprintlogboot.dto.request.UpdateActivityRequest;
import com.sprintlog.sprintlogboot.dto.response.ActivityResponse;
import com.sprintlog.sprintlogboot.dto.response.PagedResponse;
import com.sprintlog.sprintlogboot.service.ActivityDashboard;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

// Swagger 전용 인터페이스를 하나 선언해서 비즈니스 로직과 문서화 로직 분리
// 기존 컨트롤러는 본연의 역할에 집중
public interface ActivityControllerDocs {

    // 모든 활동 목록(페이징)
    @Operation(summary = "활동 목록 조회", description = "정렬(sort), 페이지(page), 크기(size), 쿼리 파라미터로 활동 목록을 가볍게(요약) 반환한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공(요약 목록)")
    @GetMapping
    public ResponseEntity<PagedResponse<ActivityResponse>> getAll(
            @Parameter(description = "정렬 기준", example = "id", schema = @Schema(allowableValues = {"id", "minutes", "title"}))
            @RequestParam(defaultValue = "id") String sort,
            @Parameter(description = "페이지 번호(0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "한 화면에 보여질 데이터 크기", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "주인 사용자 id(선택)", example = "1")
            @RequestParam(required = false) Long ownerId
    );

    @Operation(summary = "활동 단건 조회", description = "id로 활동 하나를 상세하게 반환한다. 없으면 404(ProblemDetail)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 id의 활동이 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "type": "about:blank",
                                                "title": "활동을 찾을 수 없음",
                                                "status": 404,
                                                "detail": "활동을 찾을 수 없습니다. id=xxx",
                                                "instance": "/api/activities/xxx",
                                                "timestamp": "2026-06-12T01:38:25.989279Z"
                                            }
                                            """
                            )
                    )
            )
    })

    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<ActivityResponse>> getById(
            @Parameter(description = "활동 식별자", example = "1") @PathVariable Long id);

    // 카테고리 별로 그룹화된 활동 목록
    @GetMapping("/dashboard")
    public ResponseEntity<Map<ActivityCategory, List<LearningActivity>>> getDashboard();

    // 활동 수 요약 정보 (전체 / 강의 / 실습 / 독서) -> ActivityDashBoard / Summary
    @Hidden
    @GetMapping("/summary")
    public ResponseEntity<ActivityDashboard.Summary> getSummary();


    // -- 생성(POST) / 수정(PUT) / 삭제(DELETE) --
    @PostMapping
    public ResponseEntity<EntityModel<ActivityResponse>> create(
        @Valid @RequestPart("data") CreateActivityRequest request,
        @RequestPart(value = "file", required = false) MultipartFile file,
        Authentication authentication);

    // 활동 수정, 자원 식별은 Path(/{id}), 변경할 내용은 본문(UpdateActivityRequest)
    // 대상이 없으면 404, 있으면 제목, 공개 여부를 변경하고 200.
    @PutMapping("/{id}") // 패치지만 리엑트때문에 풋으로 변경
    public ResponseEntity<EntityModel<ActivityResponse>> update(@PathVariable Long id,
                                                                @Valid @RequestBody UpdateActivityRequest request);


    // 활동 삭제. 성공 시 본문 없이 204 No Content, 대상이 없으면 404.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id);

}
