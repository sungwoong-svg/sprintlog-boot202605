package com.sprintlog.sprintlogboot.controller;

import com.sprintlog.sprintlogboot.domain.LearningActivity;
import com.sprintlog.sprintlogboot.dto.request.AddTagRequest;
import com.sprintlog.sprintlogboot.exception.ActivityNotFoundException;
import com.sprintlog.sprintlogboot.repository.ActivityRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@RestController
@RequiredArgsConstructor
// 태그는 "활동에 속한" 자원이다. 그래서 독립 주소로(/api/tags)가 아니라 부모를 경로에 포함한 중첩 url로 표현하자.
// 어떤 활동의 태그인지 url만 봐도 드러날 수 있도록(계층 구조 반영)
@RequestMapping({"/api/v1/activities/{activityId}/tags", "/api/activities/{activityId}/tags"})
public class ActivityTagController {

    private final ActivityRepository repository;

    @GetMapping
    public CollectionModel<String> getTags(@PathVariable Long activityId) {
        LearningActivity activity = findActivity(activityId);
        return CollectionModel.of(
                activity.getTags(),
                linkTo(methodOn(ActivityTagController.class).getTags(activityId)).withSelfRel(),
                linkTo(methodOn(ActivityController.class).getById(activityId)).withRel("activity")
        );
    }

    // 특정 활동에 태그 하나를 추가한다. 성공 시 201 Created + 추가된 태그의 주소(Location) + 갱신된 목록.
    @PostMapping
    public ResponseEntity<CollectionModel<String>> addTag(@PathVariable Long activityId,
                                                          @Valid @RequestBody AddTagRequest request) {
        LearningActivity activity = findActivity(activityId);
        activity.addTag(request.tag());

        String normalized = request.tag().trim().toLowerCase();
        URI location = URI.create("/api/activities/" + activityId + "/tags/" + normalized);
        return ResponseEntity.created(location).body(getTags(activityId));
    }

    // 특정 활동의 특정 태그를 제거한다.
    //   DELETE 는 멱등(idempotent) — 같은 요청을 여러 번 보내도 결과(그 태그가 없는 상태)는 같다.
    //   그래서 원래 없던 태그를 지우려 해도 *오류가 아니라* 똑같이 204 로 응답한다. (활동 자체가 없으면 404)
    @DeleteMapping("/{tag}")
    public ResponseEntity<Void> removeTag(@PathVariable Long activityId, @PathVariable String tag) {
        LearningActivity activity = findActivity(activityId);
        activity.removeTag(tag);
        return ResponseEntity.noContent().build();
    }

    private LearningActivity findActivity(Long activityId) {
        return repository.findById(activityId)
                .orElseThrow(() -> new ActivityNotFoundException(activityId));
    }

}
