package com.alderichoarau.azurequiz.exception;

public class InvalidQuizRequestException extends RuntimeException {
    public InvalidQuizRequestException(String message) {
        super(message);
    }
}
