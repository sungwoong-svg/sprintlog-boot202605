package com.sprintlog.sprintlogboot.domain;

public enum ActivityCategory {

    LECTURE("강의", 60),
    PRACTICE("실습", 60),
    READING("독서", 45);

    private final String label;
    private final int reviewThresholdMinutes;

    ActivityCategory(String label, int reviewThresholdMinutes) {
        this.label = label;
        this.reviewThresholdMinutes = reviewThresholdMinutes;
    }

    public String getLabel() { return label; }

    public boolean isShortStudy(int minutes) {
        return minutes < reviewThresholdMinutes;
    }



}
