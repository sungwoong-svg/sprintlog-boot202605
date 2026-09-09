package com.sprintlog.sprintlogboot.repository;

import com.sprintlog.sprintlogboot.domain.PersistentLogin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Remember-me 영구 토큰을 다루는 JPA 레파지토리
public interface PersistentLoginRepository extends JpaRepository<PersistentLogin, String> {

  // findById로도 가능하지만, 의미가 드러나게 명시적으로 선언(가독성)
  Optional<PersistentLogin> findBySeries(String series);

  // 특정 사용자의 활성 토큰 수(로그아웃/삭제 시 로그 판단용)
  long countByUsername(String username);

  // 로그아웃, 비밀번호 변경 등에서 그 사용자의 모든 토큰을 삭제 (자동 로그인 해제 )
  @Modifying
  @Query("DELETE from PersistentLogin p WHERE p.username = :username")
  void deleteByUsername(@Param("username") String username);

}
