package com.sprintlog.sprintlogboot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "study_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyRoom extends BaseEntity {

  @Column(nullable = false, unique = true, length = 50)
  private String name;

  @Column(nullable = false, length = 100)
  private String topic;

  public StudyRoom(String name, String topic) {
    this.name = name;
    this.topic = topic;
  }
}
