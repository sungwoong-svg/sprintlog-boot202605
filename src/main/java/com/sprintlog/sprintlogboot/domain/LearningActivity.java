package com.sprintlog.sprintlogboot.domain;

import com.sprintlog.sprintlogboot.exception.InvalidActivityException;
import lombok.Getter;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Getter
public abstract class LearningActivity implements Serializable {

    // 이 파일의 클래스 구조가 현재 클래스와 같은지에 대한 버전 키 검사용 필드
    private static final long serialVersionUID = 1L;

    private static int totalCreateCount = 0;

    private final long id;
    private String title;
    private int minutes;
    private Visibility visibility;
    private final ActivityCategory category;
    private final Set<String> tags = new HashSet<>();


    public LearningActivity(String title, int minutes, Visibility visibility, ActivityCategory category) {
        validateTitle(title);
        validateMinutes(minutes);
        totalCreateCount++;
        this.id = totalCreateCount;
        this.title = title.trim(); // 좌우 공백 제거
        this.minutes = minutes;
        this.visibility = visibility;
        this.category = category;
    }

    /**
     * 태그를 추가한다. 공백은 제거하고, 소문자로 저장한다.
     * 중복 태그는 무시한다 (Set의 특성)
     */
    public void addTag(String tag) {
        if (tag == null || tag.isBlank()) {
            throw new InvalidActivityException("태그는 비워둘 수 없습니다.");
        }
        tags.add(tag.trim().toLowerCase());
    }

    /**
     * 등록된 태그 목록을 읽기 전용으로 반환한다.
     */
    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    /**
     * 해당 태그가 등록되어 있는지 확인한다.
     */
    public boolean hasTag(String tag) {
        if (tag == null) return false;
        return tags.contains(tag.trim().toLowerCase());
    }



    public static int getTotalCreatedCount() {
        return totalCreateCount;
    }

    public void extendStudy(int additionalMinutes) {
        if (additionalMinutes <= 0) {
            throw new InvalidActivityException(
                    "추가 학습 시간은 1분 이상이어야 합니다. 입력값: " + additionalMinutes);
        }

        this.minutes += additionalMinutes;
    }

    public void changeTitle(String newTitle) {
        validateTitle(newTitle);
        this.title = newTitle;
    }

    private void validateTitle(String newTitle) {
        if (newTitle == null || newTitle.isBlank()) {
            throw new InvalidActivityException("학습 제목은 비워둘 수 없습니다.");
        }
    }

    private void validateMinutes(int newMinutes) {
        if (newMinutes <= 0) {
            throw new InvalidActivityException("학습 시간은 1분 이상이여야 합니다. 입력값: " + newMinutes);
        }
    }

    public void openToPublic() {
        this.visibility = Visibility.PUBLIC;
    }

    public void hideFromPublic() {
        this.visibility = Visibility.PRIVATE;
    }

}