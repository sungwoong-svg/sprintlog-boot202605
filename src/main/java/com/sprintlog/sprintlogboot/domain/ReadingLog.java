package com.sprintlog.sprintlogboot.domain;


import com.sprintlog.sprintlogboot.policy.Reviewable;
import com.sprintlog.sprintlogboot.policy.Shareable;

import java.io.Serializable;

public class ReadingLog extends LearningActivity implements Reviewable, Shareable, Serializable {

    private static final long serialVersionUID = 1L;
    private String bookTitle;

    public ReadingLog(String title, int minutes, Visibility visibility, String bookTitle) {
        super(title, minutes, visibility, ActivityCategory.READING);
        this.bookTitle = bookTitle;
    }


    @Override
    public boolean needsReview() {
        return getCategory().isShortStudy(getMinutes());
    }

    @Override
    public void printReviewTarget() {
        System.out.println("[복습 권장] " + getTitle() + " (" + bookTitle + ")");
    }

    @Override
    public boolean canShare() {
        return isPublicActivity();
    }

    @Override
    public String getShareTitle() {
        return getTitle();
    }

    @Override
    public String getActivityType() {
        return "독서";
    }

    @Override
    public String getDetailText() {
        return "책: " + bookTitle;
    }

    public String getBookTitle() {
        return bookTitle;
    }
}