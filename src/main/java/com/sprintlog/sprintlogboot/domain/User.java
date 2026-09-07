package com.sprintlog.sprintlogboot.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

  // nullable = false 안넣는 이유는 소셜로그인
  @Column(length = 100)
  private String password;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role = Role.USER;

  // 연관관계의 주인이 아닌 User는 mappedBy 속성을 세팅해 줍니다.
  // "owner"라는 값을 세팅한 건, 연관 관계의 주인 쪽 필드가 User다 라는 것을 말해주는 것.
  // 여기에 선언된 활동 리스트는 실제 DB에는 존재하지 않는 데이터입니다. 이건 JPA가 연관관계를 보고 만들어주는 가상의 컬럼(읽기 전용)
  // 부모가 자식의 생명주기를 100% 제어하고 싶을 때 cascade와 orphanRemoval을 같이 걸어주는 경우가 흔합니다.
  @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
  @JsonIgnore
  private List<LearningActivity> activities = new ArrayList<>();

      /*
        # CascadeType.PERSIST
        엔터티를 영속화할 때, 연관된 하위 엔터티도 함께 영속화 한다.
        연관 관계의 주인은 Employee. Department의 list는 가상 데이터.
        주인이 아닌 부모가 자식의 생명 주기에 영향을 주고 싶을 때 사용. (저장)

        ## CascadeType.ALL
        모든 Cascade를 적용한다.

        # CascadeType.MERGE
        엔티티 상태를 병합(Merge)할 때, 연관된 하위 엔티티도 모두 병합한다.
        부모를 수정해서 반영하면 그 안에 있는 자식도 함께 반영됩니다.

        # CascadeType.REMOVE
        엔티티를 제거할 때, 연관된 하위 엔티티도 모두 제거한다.

        CascadeType.DETACH
        영속성 컨텍스트에서 엔티티 제거
        부모 엔티티를 detach()(Entity Manager) 수행하면, 연관 하위 엔티티도 detach()상태가 되어 변경 사항을 반영하지 않는다.

        CascadeType.REFRESH (Entity Manager)
        상위 엔티티를 새로고침(Refresh)할 때, 연관된 하위 엔티티도 모두 새로고침한다.

     */

  // JPA가 엔터티를 만들 때 사용하는 기본 생성자. 우리가 호출하는 게 아닙니다.
  protected User() {}

  // 우리가 실제로 사용하는 생성자.
  public User(String nickname, String email) {
    this.nickname = nickname;
    this.email = email;
  }

  // 비밀번호까지 전달받는 생성자.
  public User(String nickname, String email, String password) {
    this.nickname = nickname;
    this.email = email;
    this.password = password;
  }

  public User(String nickname, String email, String password, Role role) {
    this.nickname = nickname;
    this.email = email;
    this.password = password;
    this.role = role;
  }
}
