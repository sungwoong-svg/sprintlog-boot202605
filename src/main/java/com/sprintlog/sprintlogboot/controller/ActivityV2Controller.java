package com.sprintlog.sprintlogboot.controller;

import com.sprintlog.sprintlogboot.domain.*;
import com.sprintlog.sprintlogboot.dto.request.CreateActivityRequest;
import com.sprintlog.sprintlogboot.dto.request.PagedResponse;
import com.sprintlog.sprintlogboot.dto.request.UpdateActivityRequest;
import com.sprintlog.sprintlogboot.exception.ActivityNotFoundException;
import com.sprintlog.sprintlogboot.repository.ActivityRepository;
import com.sprintlog.sprintlogboot.service.ActivityDashboard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/activities")
public class ActivityV2Controller {

    private final ActivityRepository repository;
    private final ActivityDashboard dashboard;

    // 모든 활동 목록(페이징)
    @GetMapping
    public ResponseEntity<PagedResponse<LearningActivity>> getAll(
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Comparator<LearningActivity> comparator = switch (sort) {
            case "minutes" -> Comparator.comparingInt(LearningActivity::getMinutes);
            case "title" -> Comparator.comparing(LearningActivity::getTitle);
            default -> Comparator.comparing(LearningActivity::getId);
        };


        List<LearningActivity> list = repository.findAll().stream()
                .sorted(comparator)
                .skip((long) page * size) // 0페이지면 0개 건너뛰고 size개, 1페이지면 size개 건너뛰고 size개
                .limit(size) // 이 줄까지 같이 봐야함 / 1페이지면 0 * 10이라 0개 / 그래서 0페이지면 0개 건너뛰고 10개, 1페이지면 10개 건너뛰고 10개.
                .toList();

        // v1과의 차이: 데이터에 더해 page/size/전체 개수를 함께 내려준다.
        PagedResponse<LearningActivity> resDto = new PagedResponse<>(list, page, size, list.size());
        return ResponseEntity.ok().body(resDto);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LearningActivity> getById(@PathVariable Long id) {
        LearningActivity activity = repository.findFirst(a -> a.getId() == id)
                .orElseThrow(() -> new ActivityNotFoundException(id));
        return ResponseEntity.ok().body(activity);
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
        return ResponseEntity.ok().body(list);

    }

    // -- 생성(POST) / 수정(PUT) / 삭제(DELETE) --
    @PostMapping
    public ResponseEntity<LearningActivity> create(@Valid @RequestBody CreateActivityRequest request) {
        LearningActivity activity = toActivity(request);
        repository.add(activity);

        // 성공 시 201 Created + Location Header(생성된 자원의 주소)를 함께 응답한다.
        URI location = URI.create("/api/activities/" + activity.getId());
        return ResponseEntity.created(location).body(activity);
    }

    // 활동 수정, 자원 식별은 Path(/{id}), 변경할 내용은 본문(UpdateActivityRequest)
    // 대상이 없으면 404, 있으면 제목, 공개 여부를 변경하고 200.
    @PutMapping("/{id}") // 패치지만 리엑트때문에 풋으로 변경
    public ResponseEntity<LearningActivity> update(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateActivityRequest request) {

        LearningActivity activity = repository.findFirst(a -> a.getId() == id)
                .orElseThrow(() -> new ActivityNotFoundException(id));

        activity.changeTitle(request.title());
        if (request.visibility() == Visibility.PUBLIC) {
            activity.openToPublic();
        } else {
            activity.hideFromPublic();
        }
        repository.update(activity);
        return ResponseEntity.ok().body(activity);
    }


    // 활동 삭제. 성공 시 본문 없이 204 No Content, 대상이 없으면 404.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.removeById(id)) {
            throw new ActivityNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }

    private LearningActivity toActivity(CreateActivityRequest request) {
        LearningActivity activity = switch (request.type()) {
            case LECTURE -> new LectureLog(request.title(), request.minutes(), request.visibility(), request.instructorName());
            case PRACTICE -> new PracticeLog(request.title(), request.minutes(), request.visibility(), request.completionRate());
            case READING -> new ReadingLog(request.title(), request.minutes(), request.visibility(), request.bookTitle());
        };

        if (request.tags() != null) {
            request.tags().forEach(activity::addTag);
        }

        return activity;
    }

}
