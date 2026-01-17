package com.example.demo.service;

/**
 * 收益计算失败异常.
 */
public class ProfitCalculationException extends RuntimeException {

    public ProfitCalculationException(String message) {
        super(message);
    }

    public ProfitCalculationException(String message, Throwable cause) {
        super(message, cause);
    }
}
