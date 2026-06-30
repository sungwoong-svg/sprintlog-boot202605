package com.sprintlog.sprintlogboot.repository;

import com.sprintlog.sprintlogboot.domain.ActivityCategory;
import com.sprintlog.sprintlogboot.domain.LearningActivity;
import com.sprintlog.sprintlogboot.domain.User;
import com.sprintlog.sprintlogboot.domain.Visibility;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 직접 인메모리(ArrayList)에 저장하던 방식을 버리고, Spring Date JPA로 교체
public interface ActivityRepository extends JpaRepository<LearningActivity, Long> {

  // Spring Data JPA는 PK를 활용한 조회 기능, 저장, 수정, 삭제는 기본적인 메서드를 제공합니다. (구현체가)
  // 이번에는 우리가 PK가 아닌 FK(user_id)를 이용해서 조회를 시도하려고 한다.
  // 이런 경우에는 직접 메서드를 선언해 주셔야 합니다. (쿼리 메서드, JPQL 사용 등)
  // findByOwnerId: 'where owner_id = ?' 쿼리로 자동 생성
  List<LearningActivity> findByOwnerId(Long ownerId);

  // 우리가 직접 작성한 쿼리 메서드도 페이징 처리가 가능합니다.
  // 정렬, 페이지 자르기, 전체 개수를 포함한 Page 객체를 리턴하게 하면 된다.
  Page<LearningActivity> findByOwnerId(Long ownerId, Pageable pageable);

  // Slice: 더보기 or 무한스크롤 페이징에서 사용하는 객체. 전체 개수 count 쿼리 없이 다음 페이지 유무만 앏니다.(Page보다 가벼움)
  Slice<LearningActivity> findByVisibility(Visibility visibility, Pageable pageable);

  Optional<LearningActivity> findByTitle(String title);

  // 종류별로 조회: WHERE category = ?
  List<LearningActivity> findByCategory(ActivityCategory category);

  // 제목에 키워드 포함(대소문자 무시): WHERE lower(title) LIKE lower('%키워드%');
  List<LearningActivity> findByTitleContainingIgnoreCase(String keyword);

  // 학습 시간이 기준 이상: WHERE minutes >= ?
  List<LearningActivity> findByMinutesGreaterThanEqual(int minutes);

  List<LearningActivity> findByMinutesLessThanEqual(int minutes);

  // 두 조건 AND + 정렬: WHERE category = ? AND visibility = ? ORDER BY minutes DESC
  List<LearningActivity> findByCategoryAndVisibilityOrderByMinutesDesc(ActivityCategory category, Visibility visibility);

  // 개수 세기: SELECT COUNT(*) FROM activities WHERE category = ?
  long countByCategory(ActivityCategory category);


  // 쿼리 메서드의 키워드를 모두 외울 필요는 전혀 없습니다. 조금만 길어져도 잘 안써요. (조건이 많아지거나 JOIN이 들어가거나)
  // 간단한 조회문을 빠르게 만들 때 (PK가 아닌 컬럼을 조건식으로 쓸 때)

  ///////////////////////////////

  // JPQL과 Native Query
  // 테이블명과 컬럼명으로 작성되는 SQL과는 달리 JPQL은 엔티티 클래스명과 필드명으로 쿼리를 작성합니다.
  // SELECT * FROM activities WHERE minutes >= ? ORDER BY minutes DESC
  @Query("SELECT a FROM LearningActivity a WHERE a.minutes >= :min ORDER BY a.minutes DESC")
  List<LearningActivity> findLongActivities(@Param("min") int min);

  @Query("SELECT a FROM LearningActivity a JOIN a.tags t WHERE t = :tag")
  List<LearningActivity> findByTag(@Param("tag") String tag);

  @Query("SELECT a FROM LearningActivity a WHERE a.category = ?1 AND a.visibility = ?2 ORDER BY a.minutes DESC")
  List<LearningActivity> findCategoryAndVisibility(ActivityCategory category, Visibility visibility);

  // Native Query: 순수 SQL을 그대로 작성, DB에 종속적이라 표준 기능으로 안 될 때만 사용합니다.
  // 특정 데이터베이스 전용 함수 같은 것들을 써야할 때.(JPA는 ANSI 표준 문법만 제공합니다.)
  @Query(value = "SELECT * FROM activities WHERE minutes >= :min ORDER BY minutes DESC", nativeQuery = true)
  List<LearningActivity> findLongActivitiesNative(@Param("min") int min);

  @Modifying // SELECT 아니면 무조건 붙여야 합니다. JPQL은 기본 SELECT를 기반으로 동작합니다.
  @Query("DELETE FROM LearningActivity a WHERE a.title = ?1 AND a.category = ?2")
  void deleteByTitleAndCategoryWithJPQL(String title, ActivityCategory category);

  // 연관은 기본을 LAZY 로딩으로 두고, 정말 필요한 조회에서만 FETCH JOIN이나 EntityGraph를 사용해서
  // 조인 결과를 함께 들고오는 방식을 선호.
  @Query("SELECT a FROM LearningActivity a LEFT JOIN FETCH a.owner LEFT JOIN FETCH a.tags")
  List<LearningActivity> findAllFetchJoin();

  @EntityGraph(attributePaths = {"owner", "tags"})
  @Query("SELECT a FROM LearningActivity a")
  List<LearningActivity> findAllWithDetails();
}
