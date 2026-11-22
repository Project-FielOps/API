package com.fieldops.auth.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "fieldops.mail.provider", havingValue = "noop")
public class NoopEmailService implements EmailService {
    @Override public void sendPasswordResetEmail(String recipient, String resetUrl) { }
}
