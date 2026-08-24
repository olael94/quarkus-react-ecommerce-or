package org.acme.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import org.acme.dto.MessageDto;
import org.acme.entity.Order;
import org.acme.entity.OrderItem;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/api/webhooks/stripe")
public class StripeWebhookController {
  private static final Logger logger = LoggerFactory.getLogger(StripeWebhookController.class);

  @Inject
  @ConfigProperty(name = "app.stripe.webhook-secret")
  String webhookSecret; // Stripe signs the exact bytes it sent, so the body must stay a raw String.

  // Re-serializing a parsed object could change whitespace and break signature verification.

  @Inject Mailer mailer;

  // Stripe sends a POST request to this endpoint with the raw JSON payload and the Stripe-Signature header
  @POST
  @Transactional
  public Response receiveWebhook(String payload, @Context HttpHeaders httpHeaders) {
    // The Stripe-Signature header's value contains a comma (t=...,v1=...). Some clients/proxies
    // split a header value at commas and send it as two separate headers instead of one string.
    // @HeaderParam only reads the first one, silently cutting off the v1= part we need to verify
    // the signature. getHeaderString() reads all instances and joins them back into the full value
    String signatureHeader = httpHeaders.getHeaderString("Stripe-Signature");

    Event event;
    try {
      event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
    } catch (SignatureVerificationException signatureVerificationException) {
      logger.warn("Rejected webhook request with an invalid Stripe signature");
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new MessageDto("Invalid Stripe signature"))
          .build();
    }

    logger.info("Verified Stripe webhook event of type: {}", event.getType());

    if ("checkout.session.completed".equals(event.getType())) {
      handleCheckoutSessionCompleted(event);
    }
    return Response.ok().build();
  }

  private void handleCheckoutSessionCompleted(Event event) {
    Optional<StripeObject> stripeObject = event.getDataObjectDeserializer().getObject();
    if (stripeObject.isEmpty()) {
      logger.warn("Could not deserialize checkout.session.completed event data");
      return;
    }

    Session session = (Session) stripeObject.get();
    Long orderId = Long.parseLong(session.getClientReferenceId());

    Order order = Order.findById(orderId);
    if (order == null) {
      logger.warn("No order found for id {} from Stripe session {}", orderId, session.getId());
      return;
    }

    if (order.getStatus() != Order.Status.PENDING) {
      logger.warn(
          "Order {} is already {} - ignoring duplicate webhook event", orderId, order.getStatus());
      return;
    }

    order.setStatus(Order.Status.COMPLETED);
    logger.info("Order {} marked COMPLETED via Stripe webhook", orderId);
    sendOrderConfirmationEmail(order);
  }

  private void sendOrderConfirmationEmail(Order order) {
    String recipientEmail;
    if (order.getUser() != null) {
      recipientEmail = order.getUser().getEmail();
    } else {
      recipientEmail = order.getGuestEmail();
    }

    if (recipientEmail == null) {
      logger.info("No email available for order {} - skipping confirmation email", order.id);
      return;
    }

    StringBuilder itemsList = new StringBuilder();
    for (OrderItem orderItem : order.getItems()) {
      itemsList
          .append(orderItem.getQuantity())
          .append("x ")
          .append(orderItem.getProduct().getProductName())
          .append(" - $")
          .append(orderItem.getUnitPrice())
          .append("\n");
    }

    String emailBody =
        "Thank you for your order! Here is your order summary:\n\n"
            + itemsList
            + "\nTotal: $"
            + order.getTotalAmount()
            + "\n\nOrder number: "
            + order.id;

    mailer.send(Mail.withText(recipientEmail, "Order Confirmation", emailBody));
    logger.info("Order confirmation email sent for order {}", order.id);
  }
}
