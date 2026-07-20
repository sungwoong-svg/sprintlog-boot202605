package com.sprintlog.sprintlogboot.exception;

public class ActivityNotFoundException extends BusinessException {
    public ActivityNotFoundException(Long id) {
        super(ErrorCode.ACTIVITY_NOT_FOUND, "활동을 찾을 수 없습니다. id= " + id);
    }
}
