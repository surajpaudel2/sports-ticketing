package com.suraj.sport.notificationservice.service;

import com.suraj.sport.notificationservice.exception.NotificationSendException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${notification.mail.from}")
    private String fromEmail;

    @Value("${notification.mail.from-name}")
    private String fromName;

    /**
     * Sends an HTML email using a Thymeleaf template asynchronously.
     *
     * ARCHITECTURAL DECISION — Async email sending:
     *   Email sending is @Async — the caller does not wait for the email to be sent.
     *   This means the API response returns immediately while the email is sent in the background.
     *   If email fails, the Notification record is updated to FAILED — the user is not affected.
     *   This improves response time and prevents email failures from blocking the main flow.
     *
     * TODO: Configure a dedicated thread pool for async email sending in production.
     *   Default Spring async thread pool may not be sufficient for high volume.
     *
     * TODO: Wire Gmail App Password once set up.
     *   Currently stubbed — actual sending will fail until SMTP credentials are configured.
     *   Set MAIL_USERNAME and MAIL_PASSWORD environment variables.
     */
    @Override
    @Async
    public void sendEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        try {
            // Build Thymeleaf context with template variables
            Context context = new Context();
            context.setVariables(variables);

            // Render Thymeleaf template to HTML string
            String htmlBody = templateEngine.process(templateName, context);

            // Build MIME message
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            // TODO: sendEmail — wire actual sending once SMTP credentials are configured
            // mailSender.send(message); // STUB — uncomment when Gmail App Password is set up
            log.info("STUB: Email would be sent to: {} with subject: {}", to, subject);

        } catch (MessagingException | java.io.UnsupportedEncodingException ex) {
            log.error("Failed to send email to: {} | Error: {}", to, ex.getMessage());
            throw new NotificationSendException("Failed to send email to: " + to);
        }
    }
}