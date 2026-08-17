package com.fieldops.shared.exception;

/** Thrown when a request is well-formed but violates a domain/business rule (HTTP 422). */
public class BusinessRuleException extends RuntimeException {
    private final String ruleCode;

    public BusinessRuleException(String ruleCode, String message) {
        super(message);
        this.ruleCode = ruleCode;
    }

    public BusinessRuleException(String message) {
        this("BUSINESS_RULE_VIOLATION", message);
    }

    public String getRuleCode() {
        return ruleCode;
    }
}
