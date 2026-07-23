package com.sprintlog.sprintlogboot.service;

import com.sprintlog.sprintlogboot.aspect.LogExecutionTime;
import com.sprintlog.sprintlogboot.domain.ActivityCategory;
import com.sprintlog.sprintlogboot.domain.LearningActivity;
import com.sprintlog.sprintlogboot.domain.WeeklyGoal;
import com.sprintlog.sprintlogboot.repository.ActivityRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service // 빈 등록 어노테이션, @Component랑 기능은 똑같고, 계층을 좀 더 명시적으로 표기
@RequiredArgsConstructor // final로 선언된 필드만 받는 생성자를 자동으로 만들어 줌.
public class ActivityDashboard {

    private final ActivityRepository repository;


    /**
     * 카테고리별 활동 수를 세어 Summary를 만들자.
     */
    @LogExecutionTime
    public Summary summarize() {

        // 로컬 클래스 선언: summarize() 밖에서는 사용할 수 없다.
        class Counter {
            private int totalCount;
            private int lectureCount;
            private int practiceCount;
            private int readingCount;

            void add(LearningActivity activity) {
                totalCount++;
                // getCategory()는 LearningActivity의 public API
                switch (activity.getCategory()) {
                    case LECTURE  -> lectureCount++;
                    case PRACTICE -> practiceCount++;
                    case READING  -> readingCount++;
                }
            }

            Summary toSummary() {
                return new Summary(totalCount, lectureCount, practiceCount, readingCount);
            }
        } // end Counter class

        Counter counter = new Counter();
        for (LearningActivity activity : repository.findAll()) {
            counter.add(activity);
        }
        return counter.toSummary();

    } // end summarize()

    // 내부 클래스에 static을 붙이는 이유는 메모리 누수를 방지하고 독립성을 가지기 위해서 입니다.
    public static class Summary {

        private final int totalCount;
        private final int lectureCount;
        private final int practiceCount;
        private final int readingCount;

        public Summary(int totalCount, int lectureCount, int practiceCount, int readingCount) {
            this.totalCount = totalCount;
            this.lectureCount = lectureCount;
            this.practiceCount = practiceCount;
            this.readingCount = readingCount;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public int getLectureCount() {
            return lectureCount;
        }

        public int getPracticeCount() {
            return practiceCount;
        }

        public int getReadingCount() {
            return readingCount;
        }
    }

    // 카테고리별 그룹화 -------------------------------------------------
    // 카테고리별로 활동(Log)을 그룹화해서 Map으로 반환한다.
    public Map<ActivityCategory, List<LearningActivity>> groupByCategory() {
        Map<ActivityCategory, List<LearningActivity>> result = new TreeMap<>(); // HashMap -> TreeMap으로 변경: 카테고리(enum) 선언 순서대로 정렬되어 출력이 일관된다.
        for (LearningActivity activity : repository.findAll()) {
            ActivityCategory cat = activity.getCategory();

            // 해당 카테고리가 Map에 없으면 빈 List를 먼저 만들어서 put한다.
            if (!result.containsKey(cat)) {
                result.put(cat, new ArrayList<>());
            }

            // 카테고리별 리스트를 얻어온 후 리스트에 활동 객체를 add 하자.
            List<LearningActivity> list = result.get(cat);
            list.add(activity);
        }
        return result;
    }

    // 모든 활동에서 태그를 모아 알파벳순 정렬 Set으로 반환한다.
    public Set<String> getSortedTagSet() {
        Set<String> tags = new TreeSet<>();
        for (LearningActivity activity : repository.findAll()) {
            tags.addAll(activity.getTags());
        }
        return Collections.unmodifiableSet(tags);
    }


    // 태그 필터링-------------------------------------------------------

    public List<LearningActivity> filterByTag(String tag) {
        List<LearningActivity> result = new ArrayList<>();
        for (LearningActivity activity : repository.findAll()) {
            if (activity.hasTag(tag)) {
                result.add(activity);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public int achievementRate(int goalMinutes) {
        WeeklyGoal goal = new WeeklyGoal(goalMinutes);
        int studied = 0;
        for (LearningActivity activity : repository.findAll()) {
            studied += activity.getMinutes();
        }
        return goal.achievementRate(studied);
    }

}