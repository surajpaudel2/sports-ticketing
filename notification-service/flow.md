# Notification Service — Flow Documentation

> **Version:** v1.0.0-foundation
> **Stage:** Pre inter-service communication (before Section 8)
> **Note:** This documents the current state of Notification Service before any
> inter-service communication, Kafka, or real email sending is wired.

---

## Overview

Notification Service is the **single source of truth** for all user communications.
Other services never compose messages — they send a notification request with data,
and Notification Service handles template selection, message composition, and sending.

---

## Architecture

```
Controller
    └── NotificationServiceImpl
            └── NotificationDispatcher
                    ├── BookingNotificationService
                    │       ├── handleBookingConfirmed()
                    │       └── handleBookingCancelled()
                    └── PaymentNotificationService
                            ├── handlePaymentSucceeded()
                            ├── handlePaymentFailed()
                            ├── handleRefundSucceeded()
                            └── handleRefundFailed()
                                    └── EmailService (@Async)
                                            └── Thymeleaf Template Engine
```

---

## End-to-End Flow

### Step 1 — Request Received
```
POST /api/v1/notification
{
    "userId": 1,
    "notificationType": "PAYMENT_SUCCEEDED",
    "channel": "EMAIL",
    "recipientEmail": "user@example.com",
    "templateVariables": {
        "paymentId": 1,
        "eventName": "IPL 2025 Final",
        "amount": 5000.00,
        "receiptUrl": "https://receipts.stub.com/1"
    }
}
```

### Step 2 — NotificationController
- Receives and validates request
- Delegates to `NotificationServiceImpl.sendNotification()`

### Step 3 — NotificationServiceImpl
- Creates `Notification` record in DB with status `PENDING`
- Delegates to `NotificationDispatcher.dispatch()`
- Returns `NotificationResponse` immediately — does not wait for email

### Step 4 — NotificationDispatcher
- Reads `notificationType` from request
- Routes to correct handler:
    - `BOOKING_CONFIRMED` / `BOOKING_CANCELLED` → `BookingNotificationService`
    - `PAYMENT_SUCCEEDED` / `PAYMENT_FAILED` → `PaymentNotificationService`
    - `REFUND_SUCCEEDED` / `REFUND_FAILED` → `PaymentNotificationService`

### Step 5 — Handler (e.g. PaymentNotificationService)
- Queries `notification_templates` table for matching `notificationType` + `channel`
- Gets `subjectTemplate` → e.g. "Your payment was successful!"
- Gets `bodyTemplateName` → e.g. "email/payment/payment-succeeded"
- Updates `Notification` subject in DB
- Calls `EmailService.sendEmail()`
- On success → updates `Notification` status to `SENT`, sets `sentAt`
- On failure → updates `Notification` status to `FAILED`, sets `failureReason`

### Step 6 — EmailService (@Async)
- Runs in a **background thread** — caller does not wait
- Creates Thymeleaf `Context` and injects `templateVariables`
- Renders HTML template → e.g. `resources/templates/email/payment/payment-succeeded.html`
- Builds `MimeMessage` with subject + HTML body
- Sends via `JavaMailSender`
- **Currently STUBBED** — uncomment `mailSender.send(message)` once SMTP credentials are set up

### Step 7 — Final DB State
```
notifications table:
{
    id: 1,
    userId: 1,
    notificationType: PAYMENT_SUCCEEDED,
    channel: EMAIL,
    recipientEmail: "user@example.com",
    subject: "Your payment was successful!",
    status: SENT,
    sentAt: 2025-02-25T10:00:00,
    failureReason: null
}
```

---

## Template Structure

```
resources/
  templates/
    email/
      booking/
        booking-confirmed.html
        booking-cancelled.html
      payment/
        payment-succeeded.html
        payment-failed.html
        refund-succeeded.html
        refund-failed.html
      auth/                        (future — Section 12)
        welcome-email.html
        password-reset.html
      event/                       (future)
        event-cancelled.html
        event-date-changed.html
```

---

## Database Tables

### notifications
Audit trail for every notification sent or attempted.

| Field            | Description                                      |
|------------------|--------------------------------------------------|
| id               | Primary key                                      |
| userId           | Reference to User Service (no FK constraint)     |
| notificationType | Enum — BOOKING_CONFIRMED, PAYMENT_SUCCEEDED etc  |
| channel          | EMAIL (SMS future)                               |
| recipientEmail   | Stored directly — avoids User Service call       |
| subject          | Composed subject after template is fetched       |
| status           | PENDING → SENT or FAILED                         |
| failureReason    | Null if sent successfully                        |
| sentAt           | Null until successfully sent                     |

### notification_templates
Single source of truth for all email templates.

| Field            | Description                                              |
|------------------|----------------------------------------------------------|
| notificationType | Enum — one template per type+channel combination        |
| channel          | EMAIL (SMS future)                                       |
| subjectTemplate  | Email subject e.g. "Your payment was successful!"       |
| bodyTemplateName | Thymeleaf file path e.g. "email/payment/payment-succeeded" |

---

## Supported Notification Types

| Type               | Handler                      | Triggered By         |
|--------------------|------------------------------|----------------------|
| BOOKING_CONFIRMED  | BookingNotificationService   | Booking Service      |
| BOOKING_CANCELLED  | BookingNotificationService   | Booking Service      |
| PAYMENT_SUCCEEDED  | PaymentNotificationService   | Payment Service      |
| PAYMENT_FAILED     | PaymentNotificationService   | Payment Service      |
| REFUND_SUCCEEDED   | PaymentNotificationService   | Payment Service      |
| REFUND_FAILED      | PaymentNotificationService   | Payment Service      |

---

## What's Stubbed (Pre Section 8)

| Stub                          | Will Be Implemented In |
|-------------------------------|------------------------|
| Real email sending (SMTP)     | When Gmail App Password is set up |
| Called by other services      | Section 8 — Inter-service communication |
| Kafka event subscriptions     | Section 14 — Kafka     |
| Retry scheduler               | Future                 |
| Auth notification types       | Section 12             |

---

## Key Design Decisions

1. **Centralized templates** — templates live in Notification Service only,
   other services never compose messages

2. **Dispatcher pattern** — single entry point routes to correct handler,
   controller never needs to change when new types are added

3. **Async email sending** — `@Async` on `EmailService.sendEmail()` means
   API response returns immediately, email sends in background

4. **Save before send** — Notification record saved as `PENDING` before
   attempting to send, ensures audit trail even if sending fails

5. **No FK constraints** — `userId` is a plain `Long` reference,
   microservices own their own data