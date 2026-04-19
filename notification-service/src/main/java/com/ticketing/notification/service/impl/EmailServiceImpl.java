package com.ticketing.notification.service.impl;

import com.ticketing.notification.dto.BookingResultEvent;
import com.ticketing.notification.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${notification.mail.from}")
    private String fromAddress;

    @Override
    public void sendBookingConfirmed(BookingResultEvent event) {
        String subject = "Booking Confirmed — " + event.eventName();
        String body = buildConfirmedBody(event);
        send(event.recipientEmail(), subject, body);
    }

    @Override
    public void sendBookingFailed(BookingResultEvent event) {
        String subject = "Booking Failed — " + event.eventName();
        String body = buildFailedBody(event);
        send(event.recipientEmail(), subject, body);
    }

    private void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent to={} subject={}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to={} subject={}: {}", to, subject, e.getMessage());
        }
    }

    private String buildConfirmedBody(BookingResultEvent event) {
        return """
                <html><body>
                <h2>Your Booking is Confirmed!</h2>
                <p>Great news — your seats are secured for <strong>%s</strong>.</p>
                <table>
                  <tr><td><strong>Booking ID</strong></td><td>%d</td></tr>
                  <tr><td><strong>Seats Booked</strong></td><td>%d</td></tr>
                  <tr><td><strong>Price per Seat</strong></td><td>&pound;%.2f</td></tr>
                  <tr><td><strong>Total Amount</strong></td><td>&pound;%.2f</td></tr>
                </table>
                <p>See you at the event!</p>
                </body></html>
                """.formatted(
                event.eventName(),
                event.bookingId(),
                event.seatsBooked(),
                event.pricePerSeat(),
                event.totalAmount()
        );
    }

    private String buildFailedBody(BookingResultEvent event) {
        String reason = event.reason() != null ? event.reason() : "An unexpected error occurred.";
        return """
                <html><body>
                <h2>Booking Could Not Be Completed</h2>
                <p>Unfortunately your booking for <strong>%s</strong> did not go through.</p>
                <p><strong>Reason:</strong> %s</p>
                <p>Please try again or contact support if the issue persists.</p>
                </body></html>
                """.formatted(event.eventName(), reason);
    }
}
