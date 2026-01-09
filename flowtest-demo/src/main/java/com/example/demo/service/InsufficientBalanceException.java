package com.example.demo.service;

/**
 * Exception thrown when user has insufficient balance.
 */
public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
