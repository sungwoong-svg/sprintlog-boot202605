package com.sprintlog.sprintlogboot.controller;

import com.sprintlog.sprintlogboot.domain.*;
import com.sprintlog.sprintlogboot.dto.request.UpdateActivityRequest;
import com.sprintlog.sprintlogboot.dto.response.ActivityResponse;
import com.sprintlog.sprintlogboot.exception.ActivityNotFoundException;
import com.sprintlog.sprintlogboot.dto.request.CreateActivityRequest;
import com.sprintlog.sprintlogboot.repository.ActivityRepository;
import com.sprintlog.sprintlogboot.service.ActivityDashboard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@Tag(name = "활동(Activity)", description = "학습 활동 조회, 생성, 수정, 삭제 API")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/activities", "/api/activities"}) // 경로를 둘로 받아서 기존의 요청도 해결할 수 있도록.
public class ActivityController implements ActivityControllerDocs{

    private final ActivityRepository repository;
    private final ActivityDashboard dashboard;

    // 모든 활동 목록(페이징)
    @GetMapping
    public ResponseEntity<List<EntityModel<ActivityResponse>>> getAll(
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Comparator<LearningActivity> comparator = switch (sort) {
            case "minutes" -> Comparator.comparingInt(LearningActivity::getMinutes);
            case "title" -> Comparator.comparing(LearningActivity::getTitle);
            default -> Comparator.comparing(LearningActivity::getId);
        };


        List<EntityModel<ActivityResponse>> list = repository.findAll().stream()
                .sorted(comparator)
                .skip((long) page * size) // 0페이지면 0개 건너뛰고 size개, 1페이지면 size개 건너뛰고 size개
                .limit(size) // 이 줄까지 같이 봐야함 / 1페이지면 0 * 10이라 0개 / 그래서 0페이지면 0개 건너뛰고 10개, 1페이지면 10개 건너뛰고 10개.
                .map(this::toModel)
                .toList();

        return ResponseEntity.ok().body(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<ActivityResponse>> getById(@PathVariable Long id) {
        LearningActivity activity = repository.findById(id)
                .orElseThrow(() -> new ActivityNotFoundException(id));
        return ResponseEntity.ok().body(toModel(activity));
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

    // 태그로 활동을 필터링
    @GetMapping("/search")
    public ResponseEntity<List<LearningActivity>> searchByTag(@RequestParam String tag,
                                                              @RequestParam String name,
                                                              @RequestParam int age) {

        log.info("tag: {}, name: {}, age: {}", tag, name, age);

        List<LearningActivity> list = dashboard.filterByTag(tag);
        return ResponseEntity.ok()
                .header("Deprecation", "true")
                .header("Sunset", "Thu, 31 Dec 2026 23:59:59 GMT")
                .header("Link",
                        "<https://docs.sprintlog.example/guides/migration#search>; rel=\"deprecation\"")
                .body(list);

    }

    // 변경 작업 -- 생성(POST) / 수정(PUT) / 삭제(DELETE) --
    @PostMapping
    public ResponseEntity<EntityModel<ActivityResponse>> create(@Valid @RequestBody CreateActivityRequest request) {
        LearningActivity activity = toActivity(request);
        LearningActivity saved = repository.save(activity);

        // 성공 시 201 Created + Location Header(생성된 자원의 주소)를 함께 응답한다.
        URI location = URI.create("/api/activities/" + saved.getId());
        return ResponseEntity.created(location).body(toModel(activity));
    }

    // 활동 수정, 자원 식별은 Path(/{id}), 변경할 내용은 본문(UpdateActivityRequest)
    // 대상이 없으면 404, 있으면 제목, 공개 여부를 변경하고 200.
    @PutMapping("/{id}") // 패치지만 리엑트때문에 풋으로 변경
    public ResponseEntity<EntityModel<ActivityResponse>> update(@PathVariable Long id,
                                                                @Valid @RequestBody UpdateActivityRequest request) {

        LearningActivity activity = repository.findById(id)
                .orElseThrow(() -> new ActivityNotFoundException(id));

        activity.changeTitle(request.title());
        if (request.visibility() == Visibility.PUBLIC) {
            activity.openToPublic();
        } else {
            activity.hideFromPublic();
        }

        // JPA가 적용된 상태에서의 update는 findById로 조회해 온 Entity를 setter로 변경
        // 변경 후에 명시적으로 save()를 호출하면 영속성 컨텍스트의 변경 감지(dirty checking)에 의해 update 쿼리가 날아감
        repository.save(activity);
        return ResponseEntity.ok().body(toModel(activity));
    }


    // 활동 삭제. 성공 시 본문 없이 204 No Content, 대상이 없으면 404.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        // existsById: 해당 id에 대한 데이터 존재 여부 확인(true/false 응답)
        if (!repository.existsById(id)) {
            throw new ActivityNotFoundException(id);
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- 응답 DTO + HATEOAS 링크 만들기 ---
    private EntityModel<ActivityResponse> toModel(LearningActivity activity) {
        long id = activity.getId();
        return EntityModel.of(
                ActivityResponse.from(activity),
                linkTo(methodOn(ActivityController.class).getById(id)).withSelfRel(),
                linkTo(ActivityController.class).withRel("activities"),
                linkTo(methodOn(ActivityTagController.class).getTags(id)).withRel("tags")
        );
    }

    // 평탄화 후 — 하위 타입 생성 switch 가 사라졌다.
    //   종류(type)와 종류별 필드를 그대로 단일 생성자에 넘기면 된다(엔티티가 category 로 구분).
    private LearningActivity toActivity(CreateActivityRequest request) {
        LearningActivity activity = new LearningActivity(
            request.type(), request.title(), request.minutes(), request.visibility(),
            request.instructorName(), request.completionRate(), request.bookTitle());

        if (request.tags() != null) {
            request.tags().forEach(activity::addTag);
        }
        return activity;
    }


}
