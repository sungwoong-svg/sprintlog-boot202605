package com.sprintlog.sprintlogboot.service;

import com.sprintlog.sprintlogboot.domain.ActivityCategory;
import com.sprintlog.sprintlogboot.domain.LearningActivity;
import com.sprintlog.sprintlogboot.domain.Visibility;
import com.sprintlog.sprintlogboot.dto.request.CreateActivityRequest;
import com.sprintlog.sprintlogboot.dto.request.UpdateActivityRequest;
import com.sprintlog.sprintlogboot.dto.response.ActivityResponse;
import com.sprintlog.sprintlogboot.exception.ActivityNotFoundException;
import com.sprintlog.sprintlogboot.repository.ActivityRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
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


  public Page<LearningActivity> page(String sort, int page, int size, Long ownerId) {
    // 기존에는 정렬 기준을 Comparator로 지정했는데, JPA에서 제공하는 페이징 기능을 사용하기 위해
    // Sort 타입으로 정렬 기준을 지정
    Sort sortBy = switch (sort) {
      case "minutes" -> Sort.by(Sort.Direction.DESC,"minutes");
      case "title" -> Sort.by("title");
      default -> Sort.by("id");
    };
    // 페이지 정보를 담을 객체 생성
    // 여기서는 페이지 번호가 zero-base임. 1페이지를 0으로 취급.
    Pageable pageable = PageRequest.of(page -1, size, sortBy);

    return (ownerId != null)
        ? repository.findByOwnerId(ownerId, pageable)
        : repository.findAll(pageable);

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

  public Slice<LearningActivity> sliceByVisibility(Visibility visibility, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("id"));
    return repository.findByVisibility(visibility, pageable);
  }
}
