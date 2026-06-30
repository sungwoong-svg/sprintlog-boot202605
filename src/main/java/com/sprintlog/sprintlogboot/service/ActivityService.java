package com.sprintlog.sprintlogboot.service;

import com.sprintlog.sprintlogboot.domain.ActivityCategory;
import com.sprintlog.sprintlogboot.domain.LearningActivity;
import com.sprintlog.sprintlogboot.domain.Visibility;
import com.sprintlog.sprintlogboot.dto.request.CreateActivityRequest;
import com.sprintlog.sprintlogboot.dto.request.UpdateActivityRequest;
import com.sprintlog.sprintlogboot.dto.response.ActivityResponse;
import com.sprintlog.sprintlogboot.exception.ActivityNotFoundException;
import com.sprintlog.sprintlogboot.repository.ActivityRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityService {

  private final ActivityRepository repository;


  public List<ActivityResponse> search(ActivityCategory category, String keyword, Integer minMinutes) {

    if (category != null) {
      return convertToDtoList(repository.findByCategory(category));
    }
    if (keyword != null && !keyword.isBlank()) {
      return convertToDtoList(repository.findByTitleContainingIgnoreCase(keyword));
    }
    if (minMinutes != null) {
      return convertToDtoList(repository.findLongActivities(minMinutes));
    }

    return convertToDtoList(repository.findAll());
  }

  private List<ActivityResponse> convertToDtoList(List<LearningActivity> list) {
    return list.stream()
        .map(a -> ActivityResponse.from(a))
        .toList();

  }

  @Transactional
  public void deleteByTitleAndCategory(String title, ActivityCategory category) {
    repository.deleteByTitleAndCategoryWithJPQL(title, category);

  }

  public List<LearningActivity> list(String sort, int page, int size, Long ownerId) {

    Comparator<LearningActivity> comparator = switch (sort) {
      case "minutes" -> Comparator.comparingInt(LearningActivity::getMinutes);
      case "title" -> Comparator.comparing(LearningActivity::getTitle);
      default -> Comparator.comparing(LearningActivity::getId);
    };

    List<LearningActivity> source =
        (ownerId != null) ? repository.findByOwnerId(ownerId) : repository.findAll();

    return source.stream()
        .sorted(comparator)
        .skip((long) page * size) // 0페이지면 0개 건너뛰고 size개, 1페이지면 size개 건너뛰고 size개
        .limit(size) // 이 줄까지 같이 봐야함 / 1페이지면 0 * 10이라 0개 / 그래서 0페이지면 0개 건너뛰고 10개, 1페이지면 10개 건너뛰고 10개.
        .toList();

  }

  public LearningActivity get(Long id) {
    return repository.findById(id)
        .orElseThrow(() -> new ActivityNotFoundException(id));
  }

  public LearningActivity create(CreateActivityRequest request, String savedFileName) {
    LearningActivity activity = toActivity(request);
    activity.attachFile(savedFileName);
    return repository.save(activity);
  }

  // 평탄화 후 — 하위 타입 생성 switch 가 사라졌다.
  // 종류(type)와 종류별 필드를 그대로 단일 생성자에 넘기면 된다(엔티티가 category 로 구분).
  private LearningActivity toActivity(CreateActivityRequest request) {
    LearningActivity activity = new LearningActivity(
        request.type(), request.title(), request.minutes(), request.visibility(),
        request.instructorName(), request.completionRate(), request.bookTitle());

    if (request.tags() != null) {
      request.tags().forEach(activity::addTag);
    }
    return activity;
  }

  @Transactional
  public LearningActivity update(Long id, UpdateActivityRequest request) {
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
    return repository.save(activity);
  }

  @Transactional
  public void delete(Long id) {
    // existsById: 해당 id에 대한 데이터 존재 여부 확인(true/false 응답)
    if (!repository.existsById(id)) {
      throw new ActivityNotFoundException(id);
    }
    repository.deleteById(id);
  }
}
