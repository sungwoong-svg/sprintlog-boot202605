package com.sprintlog.sprintlogboot.domain;

// enum: 정해진 선택지를 코드의 타입으로 만드는 문법
public enum Visibility {

    PUBLIC("공개", true),
    PRIVATE("비공개", false);

    private final String label;
    private final boolean shareable;

    Visibility(String label, boolean shareable) {
        this.label = label;
        this.shareable = shareable;
    }

    public String getLabel() {
        return label;
    }

    public boolean isShareable() {
        return shareable;
    }
}
