package org.acme.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.acme.entity.Order;
import org.acme.entity.OrderItem;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped // It will be instantiated once and shared across the entire application.
public class StripeCheckoutService {

  @Inject
  @ConfigProperty(name = "app.stripe.secret-key")
  String stripeSecretKey; // This is for making API calls to Stripe.

  @Inject
  @ConfigProperty(name = "app.base-url")
  String baseUrl; // Used to construct the success and cancel URLs for Stripe Checkout.

  @PostConstruct
  public void configureStripeApiKey() {
    Stripe.apiKey = stripeSecretKey;
  }

  // This method will create a Stripe Checkout session for the given order.
  private List<SessionCreateParams.LineItem> buildLineItems(Order order) {
    // convert our List<OrderItem> to List<SessionCreateParams.LineItem> format that Stripe expects.
    List<SessionCreateParams.LineItem> lineItems = new ArrayList<>();

    for (OrderItem orderItem : order.getItems()) {
      long unitAmountInCents =
          Math.round(
              orderItem.getUnitPrice()
                  * 100); // This converts dollars to cents, as Stripe expects amounts in the
      // smallest currency unit.

      SessionCreateParams.LineItem.PriceData.ProductData productData =
          SessionCreateParams.LineItem.PriceData.ProductData.builder()
              .setName(orderItem.getProduct().getProductName())
              .build();

      SessionCreateParams.LineItem.PriceData priceData =
          SessionCreateParams.LineItem.PriceData.builder()
              .setCurrency("usd")
              .setUnitAmount(unitAmountInCents)
              .setProductData(productData)
              .build();

      SessionCreateParams.LineItem lineItem =
          SessionCreateParams.LineItem.builder()
              .setQuantity((long) orderItem.getQuantity())
              .setPriceData(priceData)
              .build();

      lineItems.add(lineItem);
    }

    return lineItems;
  }

  public String createCheckoutSession(Order order) throws StripeException {
    String customerEmail;
    if (order.getUser() != null) {
      customerEmail = order.getUser().getEmail();
    } else {
      customerEmail = order.getGuestEmail();
    }

    SessionCreateParams.Builder paramsBuilder =
        SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setClientReferenceId(
                order.id.toString()) // This stamps our order id with Stripe's checkout session for
            // later reference.
            .setSuccessUrl(baseUrl + "/checkout/success?session_id={CHECKOUT_SESSION_ID}")
            .setCancelUrl(baseUrl + "/checkout/cancel")
            .addAllLineItem(buildLineItems(order));

    if (customerEmail != null) {
      paramsBuilder.setCustomerEmail(customerEmail);
    }

    SessionCreateParams params = paramsBuilder.build();
    Session session = Session.create(params);

    return session.getUrl();
  }
}
