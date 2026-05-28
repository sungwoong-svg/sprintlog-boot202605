package com.sprintlog.sprintlogboot.domain;

import lombok.Getter;
import java.io.Serializable;

@Getter
public class ReadingLog extends LearningActivity implements Serializable {

    private static final long serialVersionUID = 1L;
    private String bookTitle;

    public ReadingLog(String title, int minutes, Visibility visibility, String bookTitle) {
        super(title, minutes, visibility, ActivityCategory.READING);
        this.bookTitle = bookTitle;
    }

}