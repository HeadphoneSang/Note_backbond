package org.template.exceptions;

public class OptimisticLockException extends RuntimeException {
    public OptimisticLockException(String message) {
        super(message);
    }
}