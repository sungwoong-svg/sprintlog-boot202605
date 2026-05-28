package com.sprintlog.sprintlogboot.domain;

import lombok.Getter;

import java.io.Serializable;

@Getter
public class LectureLog extends LearningActivity implements Serializable {

    private static final long serialVersionUID = 1L;

    private String instructorName; // 강사 이름 (LectureLog만 가지는 고유한 필드)

    public LectureLog(String title, int minutes, Visibility visibility, String instructorName) {
        super(title, minutes, visibility, ActivityCategory.LECTURE);
        this.instructorName = normalizeInstructorName(instructorName);
    }

    private String normalizeInstructorName(String instructorName) {
        if (instructorName == null || instructorName.isBlank()) {
            return "강사 미정";
        }

        return instructorName;
    }


}




