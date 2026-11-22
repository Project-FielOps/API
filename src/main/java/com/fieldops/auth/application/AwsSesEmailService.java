package com.fieldops.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fieldops.mail.provider", havingValue = "ses", matchIfMissing = true)
public class AwsSesEmailService implements EmailService {
    private final MailProperties properties;

    @Override
    public void sendPasswordResetEmail(String recipient, String resetUrl) {
        try (SesClient client = SesClient.builder().region(Region.of(properties.region())).build()) {
            String body = "Foi solicitada uma redefinição de senha da FieldOps.\n\n"
                    + "Acesse o link para redefinir sua senha: " + resetUrl + "\n\n"
                    + "O link expira em 30 minutos e funciona apenas uma vez. "
                    + "Se você não fez esta solicitação, ignore este e-mail.";
            client.sendEmail(SendEmailRequest.builder()
                    .source(properties.from())
                    .destination(Destination.builder().toAddresses(recipient).build())
                    .message(Message.builder()
                            .subject(Content.builder().data("Redefinição de senha FieldOps").charset("UTF-8").build())
                            .body(Body.builder().text(Content.builder().data(body).charset("UTF-8").build()).build())
                            .build())
                    .build());
        }
    }
}
