package com.sprintlog.sprintlogboot.controller;

import com.sprintlog.sprintlogboot.domain.*;
import com.sprintlog.sprintlogboot.dto.request.UpdateActivityRequest;
import com.sprintlog.sprintlogboot.dto.response.ActivityResponse;
import com.sprintlog.sprintlogboot.dto.response.PagedResponse;
import com.sprintlog.sprintlogboot.dto.request.CreateActivityRequest;
import com.sprintlog.sprintlogboot.dto.response.SliceResponse;
import com.sprintlog.sprintlogboot.service.ActivityDashboard;
import com.sprintlog.sprintlogboot.service.ActivityService;
import com.sprintlog.sprintlogboot.service.FileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@Tag(name = "활동(Activity)", description = "학습 활동 조회, 생성, 수정, 삭제 API")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/activities", "/api/activities"}) // 경로를 둘로 받아서 기존의 요청도 해결할 수 있도록.
public class ActivityController implements ActivityControllerDocs{

    private final ActivityDashboard dashboard;
    private final FileService fileService;
    private final ActivityService activityService;

    // 모든 활동 목록(페이징)
    @GetMapping
    public ResponseEntity<PagedResponse<ActivityResponse>> getAll(
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long ownerId
    ) {
        Page<LearningActivity> result
            = activityService.page(sort, page, size, ownerId);

        // 원본 리스트를 꺼낼 때는 getContent()를 통해서 꺼낼 수 있다.
        List<ActivityResponse> content = result.getContent().stream()
            .map(a -> ActivityResponse.from(a))
            .toList();

        return ResponseEntity.ok().body(new PagedResponse<>(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages())
            );
    }
    @GetMapping("/slice")
    public ResponseEntity<SliceResponse<ActivityResponse>> slice(
        @RequestParam(defaultValue = "PUBLIC") Visibility visibility,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        Slice<LearningActivity> result = activityService.sliceByVisibility(visibility, page, size);
        List<ActivityResponse> content = result.getContent().stream()
            .map(a -> ActivityResponse.from(a))
            .toList();

        return ResponseEntity.ok().body(
            new SliceResponse<>(content, result.getNumber(), result.getSize(), result.hasNext()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<ActivityResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok().body(toModel(activityService.get(id)));
    }

    // 카테고리 별로 그룹화된 활동 목록
    @GetMapping("/dashboard")
    public ResponseEntity<Map<ActivityCategory, List<LearningActivity>>> getDashboard() {
        Map<ActivityCategory, List<LearningActivity>> map = dashboard.groupByCategory();
        return ResponseEntity.ok().body(map);
    }

    // 활동 수 요약 정보 (전체 / 강의 / 실습 / 독서) -> ActivityDashBoard / Summary
    @GetMapping("/summary")
    public ResponseEntity<ActivityDashboard.Summary> getSummary() {
        return ResponseEntity.ok().body(dashboard.summarize());
    }

    // --------------------------------------------------------------------------------
    // 변경 작업 -- 생성(POST) / 수정(PUT) / 삭제(DELETE) --
    @PostMapping
    public ResponseEntity<EntityModel<ActivityResponse>> create(
            @Valid @RequestPart("data") CreateActivityRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {

        String savedFileName = null;

        if (file != null && !file.isEmpty()) {
            savedFileName = fileService.saveFile(file);
        }

        LearningActivity saved = activityService.create(request, savedFileName);

        // 성공 시 201 Created + Location Header(생성된 자원의 주소)를 함께 응답한다.
        URI location = URI.create("/api/activities/" + saved.getId());
        return ResponseEntity.created(location).body(toModel(saved));
    }

    // 활동 수정, 자원 식별은 Path(/{id}), 변경할 내용은 본문(UpdateActivityRequest)
    // 대상이 없으면 404, 있으면 제목, 공개 여부를 변경하고 200.
    @PutMapping("/{id}") // 패치지만 리엑트때문에 풋으로 변경
    public ResponseEntity<EntityModel<ActivityResponse>> update(@PathVariable Long id,
                                                                @Valid @RequestBody UpdateActivityRequest request) {
        return ResponseEntity.ok()
            .body(toModel(activityService.update(id, request)));
    }


    // 활동 삭제. 성공 시 본문 없이 204 No Content, 대상이 없으면 404.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        activityService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- 응답 DTO + HATEOAS 링크 만들기 (필수 아님) ---
    private EntityModel<ActivityResponse> toModel(LearningActivity activity) {
        long id = activity.getId();
        return EntityModel.of(
                ActivityResponse.from(activity),
                linkTo(methodOn(ActivityController.class).getById(id)).withSelfRel(),
                linkTo(ActivityController.class).withRel("activities"),
                linkTo(methodOn(ActivityTagController.class).getTags(id)).withRel("tags")
        );
    }

    @GetMapping("/find")
    public ResponseEntity<List<ActivityResponse>> find(
        @RequestParam(required = false) ActivityCategory category,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer minMinutes
    ) {
        List<ActivityResponse> dtoList = activityService.search(category, keyword, minMinutes);
        return ResponseEntity.ok().body(dtoList);
    }
}
