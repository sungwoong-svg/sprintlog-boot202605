package com.sprintlog.sprintlogboot.service;

import com.sprintlog.sprintlogboot.domain.ActivityAuditLog;
import com.sprintlog.sprintlogboot.domain.ActivityCategory;
import com.sprintlog.sprintlogboot.domain.LearningActivity;
import com.sprintlog.sprintlogboot.domain.Visibility;
import com.sprintlog.sprintlogboot.dto.request.CreateActivityRequest;
import com.sprintlog.sprintlogboot.dto.request.UpdateActivityRequest;
import com.sprintlog.sprintlogboot.dto.response.ActivityResponse;
import com.sprintlog.sprintlogboot.exception.ActivityArchiveException;
import com.sprintlog.sprintlogboot.exception.ActivityNotFoundException;
import com.sprintlog.sprintlogboot.repository.ActivityRepository;
import com.sprintlog.sprintlogboot.repository.AuditLogRepository;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
@Transactional(readOnly = true) // 클래스 레벨에 설정하면 전역으로 설정
public class ActivityService {

  private final ActivityRepository repository;
  private final AuditLogRepository auditLogRepository;
  private final AuditService auditService;

  // 지표 수집기(MeterRegistry) - 커스텀 지표를 여기에 등록 후 증감시킨다.
  private final MeterRegistry meterRegistry;




  @Transactional(readOnly = true)
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

  @Timed(value = "sprintlog.activity.find.paging", description = "활동 조회 소요 시간(페이징)")
  @Transactional(readOnly = true)
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

  @Transactional
  public LearningActivity create(CreateActivityRequest request, String savedFileName) {
    return meterRegistry.timer("sprintlog.activity.create.time").record(() -> {
      LearningActivity activity = toActivity(request);
      activity.attachFile(savedFileName);
      LearningActivity saved = repository.save(activity);

      //  카테고리별로 태그를 쪼개서 생성된 활동 객체의 개수를 카운팅
      meterRegistry.counter("sprintlog.activities.created", "category", saved.getCategory().name()).increment();

       // 개수가 아니라 '양'을 누적: 어떤 활동 객체이든 상관 없이 학습한 시간(분)을 누적해서 더해라.
      meterRegistry.counter("sprintlog.study.minutes.total").increment(saved.getMinutes());

      log.info("활동 생성 완료 id={}, category={}, title={}", saved.getId(), saved.getCategory(), saved.getTitle());
      return saved;
      });
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

  @Transactional // 메서드 레벨이 트랜잭션을 걸면 클래스 레벨보다 우선시 된다.
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
    LearningActivity saved = repository.save(activity);
    log.info("활동 생성 완료 id={}", saved.getId());
    return saved;
  }

  @Transactional
  public void delete(Long id) {
    // existsById: 해당 id에 대한 데이터 존재 여부 확인(true/false 응답)
    if (!repository.existsById(id)) {
      throw new ActivityNotFoundException(id);
    }
    repository.deleteById(id);
    log.info("활동 삭제가 완료 id={}", id);
  }

  public Slice<LearningActivity> sliceByVisibility(Visibility visibility, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("id"));
    return repository.findByVisibility(visibility, pageable);
  }

  public List<LearningActivity> withDetails() {
    return repository.findAllWithDetails();
  }

  public List<ActivityAuditLog> history() {
    return auditLogRepository.findAllByOrderByIdDesc();
  }

  @Transactional
  public void demoAtomicRegister(boolean fail) {
    LearningActivity activity = repository.save(new LearningActivity(
        ActivityCategory.LECTURE, "원자성 데모 학습",
        30, Visibility.PUBLIC, "이강사",
        null, null
    ));

    auditLogRepository.save(new ActivityAuditLog("CREATE", "활동 생성(원자성 데모)" + activity.getTitle()));

    if (fail) {
      throw new IllegalArgumentException("원자성 시연: 등록 도중 실패 -> 활동, 이력 둘 다 롤백");
    }
  }

  @Transactional // REQUIRED 기본값이라 미기재
  public void demoPropagation(boolean fail) {
    // ① 시도 이력 — REQUIRES_NEW(별도 빈 호출 → 프록시 경유 → 독립 트랜잭션으로 즉시 커밋)
    auditService.logAttempt("CREATE_ATTEMPT", "활동 등록 시도(전파 데모)");

    // ② 본 작업 — 부모 트랜잭션에서 활동 저장
    repository.save(new LearningActivity(
        ActivityCategory.LECTURE, "전파 데모 학습", 30, Visibility.PUBLIC, "이강사", null, null));

    // ③ 실패하면 부모만 롤백 — 위 시도 이력(①)은 이미 커밋되어 살아남는다.
    if (fail) {
      throw new IllegalStateException("전파 시연: 등록 실패 → 활동은 롤백, 시도 이력은 남음(REQUIRES_NEW)");
    }
  }

  @Transactional(rollbackFor = ActivityArchiveException.class)
  public void archive(boolean fail) throws ActivityArchiveException {
    repository.save(new LearningActivity(
        ActivityCategory.READING, "보관 시연 활동(rollbackFor 없음)", 20, Visibility.PUBLIC, null, null, "보관용 책"));

    if (fail) {
      // 체크 예외 — 기본 롤백 대상이 아니다 → 위 저장은 커밋되어 남는다.
      throw new ActivityArchiveException("보관 실패(체크 예외) — 하지만 기본 롤백은 안 된다");
    }

  }
}
