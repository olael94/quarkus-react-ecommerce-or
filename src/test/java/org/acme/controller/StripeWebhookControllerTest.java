package org.acme.controller;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.acme.TestAuthHelper;
import org.acme.entity.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers StripeWebhookController. Since these tests never call the real Stripe API, a valid request
 * has to be built by hand: the webhook payload is a real JSON event envelope, and the
 * Stripe-Signature header is computed with the same HMAC-SHA256 scheme Stripe itself uses, signed
 * with the %test profile's fake webhook secret from application.properties.
 */
@QuarkusTest
class StripeWebhookControllerTest {

  // Matches %test.app.stripe.webhook-secret in application.properties.
  private static final String WEBHOOK_SECRET = "whsec_fake_secret_for_tests";

  // Must match com.stripe.ApiVersion.CURRENT in the stripe-java version this project depends on -
  // Stripe's SDK refuses to deserialize an event's data.object if the api_version doesn't match
  // what the SDK itself was built against.
  private static final String STRIPE_API_VERSION = "2025-05-28.basil";

  @Inject MockMailbox mailbox;

  @BeforeEach
  void clearMailbox() {
    mailbox.clear();
  }

  // Stripe's documented signing scheme: HMAC-SHA256 of "timestamp.payload", using the webhook
  // secret's raw UTF-8 bytes as the key, hex-encoded, sent as "t=<timestamp>,v1=<signature>".
  private String stripeSignatureFor(String payload) throws Exception {
    long timestamp = Instant.now().getEpochSecond();
    String signedPayload = timestamp + "." + payload;

    Mac hmacSha256 = Mac.getInstance("HmacSHA256");
    hmacSha256.init(
        new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] hash = hmacSha256.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));

    StringBuilder hexHash = new StringBuilder();
    for (byte hashByte : hash) {
      hexHash.append(String.format("%02x", hashByte));
    }

    return "t=" + timestamp + ",v1=" + hexHash;
  }

  private String checkoutSessionCompletedPayload(Long orderId) {
    return "{"
        + "\"id\":\"evt_test_1\","
        + "\"object\":\"event\","
        + "\"api_version\":\""
        + STRIPE_API_VERSION
        + "\","
        + "\"created\":1700000000,"
        + "\"type\":\"checkout.session.completed\","
        + "\"data\":{\"object\":{"
        + "\"id\":\"cs_test_1\","
        + "\"object\":\"checkout.session\","
        + "\"client_reference_id\":\""
        + orderId
        + "\","
        + "\"mode\":\"payment\","
        + "\"payment_status\":\"paid\""
        + "}}"
        + "}";
  }

  @Test
  void receiveWebhook_validSignature_marksPendingOrderCompleted() throws Exception {
    Long orderId = TestAuthHelper.createPendingOrder("buyer@example.com", 42.00);
    String payload = checkoutSessionCompletedPayload(orderId);
    String signature = stripeSignatureFor(payload);

    given()
        .contentType("application/json")
        .header("Stripe-Signature", signature)
        .body(payload)
        .post("/api/webhooks/stripe")
        .then()
        .statusCode(200);

    Order order = TestAuthHelper.getOrderWithItems(orderId);
    assertEquals(Order.Status.COMPLETED, order.getStatus());
  }

  @Test
  void receiveWebhook_validSignature_sendsConfirmationEmail() throws Exception {
    Long orderId = TestAuthHelper.createPendingOrder("buyer@example.com", 42.00);
    String payload = checkoutSessionCompletedPayload(orderId);
    String signature = stripeSignatureFor(payload);

    given()
        .contentType("application/json")
        .header("Stripe-Signature", signature)
        .body(payload)
        .post("/api/webhooks/stripe")
        .then()
        .statusCode(200);

    List<Mail> sentMails = mailbox.getMailsSentTo("buyer@example.com");
    assertEquals(1, sentMails.size());
  }

  @Test
  void receiveWebhook_invalidSignature_returns400AndDoesNotUpdateOrder() throws Exception {
    Long orderId = TestAuthHelper.createPendingOrder("buyer@example.com", 42.00);
    String payload = checkoutSessionCompletedPayload(orderId);

    given()
        .contentType("application/json")
        .header("Stripe-Signature", "t=1700000000,v1=not_a_real_signature")
        .body(payload)
        .post("/api/webhooks/stripe")
        .then()
        .statusCode(400);

    Order order = TestAuthHelper.getOrderWithItems(orderId);
    assertEquals(Order.Status.PENDING, order.getStatus());
  }

  @Test
  void receiveWebhook_replayedEvent_doesNotReprocessOrResendEmail() throws Exception {
    Long orderId = TestAuthHelper.createPendingOrder("buyer@example.com", 42.00);
    String payload = checkoutSessionCompletedPayload(orderId);
    String signature = stripeSignatureFor(payload);

    given()
        .contentType("application/json")
        .header("Stripe-Signature", signature)
        .body(payload)
        .post("/api/webhooks/stripe")
        .then()
        .statusCode(200);

    // Same event delivered a second time - Stripe doesn't guarantee exactly-once delivery.
    given()
        .contentType("application/json")
        .header("Stripe-Signature", signature)
        .body(payload)
        .post("/api/webhooks/stripe")
        .then()
        .statusCode(200);

    Order order = TestAuthHelper.getOrderWithItems(orderId);
    assertEquals(Order.Status.COMPLETED, order.getStatus());

    List<Mail> sentMails = mailbox.getMailsSentTo("buyer@example.com");
    assertEquals(1, sentMails.size());
  }

  @Test
  void receiveWebhook_unknownOrderId_returns200WithoutError() throws Exception {
    String payload = checkoutSessionCompletedPayload(999999999L);
    String signature = stripeSignatureFor(payload);

    given()
        .contentType("application/json")
        .header("Stripe-Signature", signature)
        .body(payload)
        .post("/api/webhooks/stripe")
        .then()
        .statusCode(200);
  }

  @Test
  void receiveWebhook_unrelatedEventType_returns200WithoutSendingEmail() throws Exception {
    String payload =
        "{\"id\":\"evt_test_2\",\"object\":\"event\",\"api_version\":\""
            + STRIPE_API_VERSION
            + "\",\"created\":1700000000,\"type\":\"payment_intent.created\",\"data\":{\"object\":{}}}";
    String signature = stripeSignatureFor(payload);

    given()
        .contentType("application/json")
        .header("Stripe-Signature", signature)
        .body(payload)
        .post("/api/webhooks/stripe")
        .then()
        .statusCode(200);

    assertEquals(0, mailbox.getTotalMessagesSent());
  }
}
