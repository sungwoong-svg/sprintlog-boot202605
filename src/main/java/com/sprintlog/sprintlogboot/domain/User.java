package com.sprintlog.sprintlogboot.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@Getter
@Entity // 이 클래스는 JPA가 관리한다. 이 클래스는 데이터베이스의 한 행에 정확하게 대응된다.
@Table(name = "users")
public class User extends BaseEntity{

  // Column 속성으로 컬럼 제약을 표현한다. (null, length, unique)
  @Column(nullable = false, length = 50)
  private String nickname;

  @Column(nullable = false, unique = true, length = 100)
  private String email;

  // 연관관계의 주인이 아닌 User는 mappedBy 속성을 세팅해 줍니다.
  // "owner"라는 값을 세팅한 건, 연관 관계의 주인 쪽 필드가 User다 라는 것을 말해주는 것.
  // 여기에 선언된 활동 리스트는 실제 DB에는 존재하지 않는 데이터입니다. 이건 JPA가 연관관계를 보고 만들어주는 가상의 컬럼(읽기 전용)
  @OneToMany(mappedBy = "owner")
  @JsonIgnore
  private List<LearningActivity> activities = new ArrayList<>();

  // JPA가 엔터티를 만들 때 사용하는 기본 생성자. 우리가 호출하는 게 아닙니다.
  protected User() {}

  // 우리가 실제로 사용하는 생성자.
  public User(String nickname, String email) {
    this.nickname = nickname;
    this.email = email;
  }
}
