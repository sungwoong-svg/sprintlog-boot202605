package com.sprintlog.sprintlogboot.domain;




import java.io.Serializable;

public class PracticeLog extends LearningActivity implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final int MINIMUM_COMPLETION_RATE = 70;

    private int completionRate; // PracticeLog만 가지는 고유한 필드

    public PracticeLog(String title, int minutes, Visibility visibility, int completionRate) {
        super(title, minutes, visibility, ActivityCategory.PRACTICE);
        this.completionRate = normalizeCompletionRate(completionRate);
    }

    public int getCompletionRate() {
        return completionRate;
    }

    private int normalizeCompletionRate(int completionRate) {
        if (completionRate < 0) {
            return 0;
        }

        if (completionRate > 100) {
            return 100;
        }

        return completionRate;
    }

}
