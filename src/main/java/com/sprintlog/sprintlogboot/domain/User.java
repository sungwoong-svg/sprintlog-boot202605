package com.sprintlog.sprintlogboot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity // 이 클래스는 JPA가 관리한다. 이 클래스는 데이터베이스의 한 행에 정확하게 대응된다.
@Table(name = "users")
public class User extends BaseEntity{

  // Column 속성으로 컬럼 제약을 표현한다. (null, length, unique)
  @Column(nullable = false, length = 50)
  private String nickname;

  @Column(nullable = false, unique = true, length = 100)
  private String email;

  // JPA가 엔터티를 만들 때 사용하는 기본 생성자. 우리가 호출하는 게 아닙니다.
  protected User() {}

  // 우리가 실제로 사용하는 생성자.
  public User(String nickname, String email) {
    this.nickname = nickname;
    this.email = email;
  }
}
