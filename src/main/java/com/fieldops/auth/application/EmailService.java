package com.fieldops.auth.application;

public interface EmailService {
    void sendPasswordResetEmail(String recipient, String resetUrl);
}
