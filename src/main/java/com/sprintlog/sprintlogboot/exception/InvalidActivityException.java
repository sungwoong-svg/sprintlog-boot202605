package com.sprintlog.sprintlogboot.exception;

public class InvalidActivityException extends BusinessException{

    public InvalidActivityException(String message) {
        super(ErrorCode.INVALID_ACTIVITY_INPUT, message);
    }

}
