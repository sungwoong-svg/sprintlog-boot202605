package com.sprintlog.sprintlogboot.repository;

import com.sprintlog.sprintlogboot.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

// Spring-Data-JPA는 인터페이스만 선언해 놓으면 구현체는 자동으로 만들어 준다.
// JpaRepository 인터페이스를 상속만 받아놓으면 구현체가 알아서 세팅됩니다.
// 제네릭에는 <엔터티 타입, PK 타입>
public interface UserRepository extends JpaRepository<User, Long> {

}
