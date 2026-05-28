package com.sprintlog.sprintlogboot.repository;


import com.sprintlog.sprintlogboot.domain.*;
import org.springframework.stereotype.Repository;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@Repository // 이 클래스는 Repository 역할을 하는 클래스고, bean으로 등록해 줘.
public class ActivityRepository {

    private final List<LearningActivity> storage = new ArrayList<>();

    public void add(LearningActivity activity) {
        if (activity == null) {
            throw new IllegalArgumentException("저장할 활동은 null일 수 없습니다.");
        }
        storage.add(activity);
    }

    // 저장된 모든 활동을 반환한다.
    public List<LearningActivity> findAll() {
        return Collections.unmodifiableList(storage);
    }

    // 조건에 맞는 활동'들'만 골라 반환한다.
    public List<LearningActivity> filter(Predicate<LearningActivity> predicate) {
        List<LearningActivity> result = new ArrayList<>();
        for (LearningActivity activity : storage) {
            if (predicate.test(activity)) {
                result.add(activity);
            }
        }
        return result;
    }

    // 조건에 맞는 첫 번째 활동을 골라 반환한다.
    public Optional<LearningActivity> findFirst(Predicate<LearningActivity> predicate) {
        for (LearningActivity activity : storage) {
            if (predicate.test(activity)) {
                return Optional.of(activity);
            }
        }
        return Optional.empty();
    }

    // 저장한 활동 수를 반환한다.
    public int count() {
        return storage.size();
    }

    // 저장된 모든 활동의 총 학습 시간(분)을 반환한다.
    public int getTotalMinutes() {
        int total = 0;
        for (LearningActivity activity : storage) {
            total += activity.getMinutes(); // LearningActivity가 LearningActivity의 자식이기 때문에 getMinutes() 호출 가능
        }
        return total;
    }

}