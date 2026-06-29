package com.sprintlog.sprintlogboot.repository;

import com.sprintlog.sprintlogboot.domain.LearningActivity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 직접 인메모리(ArrayList)에 저장하던 방식을 버리고, Spring Date JPA로 교체
public interface ActivityRepository extends JpaRepository<LearningActivity, Long> {

  // Spring Data JPA는 PK를 활용한 조회 기능, 저장, 수정, 삭제는 기본적인 메서드를 제공합니다. (구현체가)
  // 이번에는 우리가 PK가 아닌 FK(user_id)를 이용해서 조회를 시도하려고 한다.
  // 이런 경우에는 직접 메서드를 선언해 주셔야 합니다. (쿼리 메서드, JPQL 사용 등)
  // findByOwnerId: 'where owner_id = ?' 쿼리로 자동 생성
  List<LearningActivity> findByOwnerId(Long ownerId);

  Optional<LearningActivity> findByTitle(String title);

}
