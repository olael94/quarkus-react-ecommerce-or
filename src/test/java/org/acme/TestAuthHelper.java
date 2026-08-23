package org.acme;

import static io.restassured.RestAssured.given;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.restassured.response.Response;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import org.acme.entity.Order;
import org.acme.entity.PasswordResetToken;
import org.acme.entity.Product;
import org.acme.entity.Session;
import org.acme.entity.User;

/** Shared helpers for auth-related tests: each test gets its own isolated account. */
public class TestAuthHelper {

  public static final String PASSWORD = "testpassword123";

  public record AuthenticatedUser(
      String email, String password, String sessionCookie, String csrfToken) {}

  public static String uniqueEmail() {
    return "test-" + UUID.randomUUID() + "@example.com";
  }

  public static Response register(String email, String password) {
    return given()
        .contentType("application/json")
        .body(
            "{\"username\":\"testuser\",\"email\":\""
                + email
                + "\",\"password\":\""
                + password
                + "\"}")
        .post("/api/users/register");
  }

  public static Response login(String email, String password) {
    return given()
        .contentType("application/json")
        .body("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
        .post("/api/users/login");
  }

  /** Registers a brand new user with a unique email and logs them in. */
  public static AuthenticatedUser registerAndLogin() {
    String email = uniqueEmail();
    register(email, PASSWORD).then().statusCode(201);

    Response loginResponse = login(email, PASSWORD);
    loginResponse.then().statusCode(200);

    String sessionCookie = loginResponse.getCookie("session");
    String csrfToken = loginResponse.getCookie("csrf_token");

    return new AuthenticatedUser(email, PASSWORD, sessionCookie, csrfToken);
  }

  /**
   * Reads the most recently issued (unused) password-reset token for an email directly from the
   * database. Tests can't read it out of an email - the mailer is mocked in the test profile - so
   * this is the only way to get a real token to exercise /reset-password/confirm.
   */
  public static String getLatestResetTokenFor(String email) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              User user = User.find("email", email).firstResult();
              PasswordResetToken token =
                  PasswordResetToken.find("user = ?1 and used = false order by id desc", user)
                      .firstResult();
              return token.token;
            });
  }

  /** Directly overrides a session's expiry fields, so sliding-expiry tests don't have to wait. */
  public static void setSessionExpiry(
      String sessionToken, Instant expiresAt, Instant absoluteExpiresAt) {
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              Session session = Session.find("token", sessionToken).firstResult();
              session.expiresAt = expiresAt;
              session.absoluteExpiresAt = absoluteExpiresAt;
              session.persist();
            });
  }

  /**
   * Directly grants a user a role, bypassing the app - there's no self-service way to become an
   * admin.
   */
  public static void addUserRole(String email, User.Role role) {
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              User user = User.find("email", email).firstResult();
              user.addRole(role);
              user.persist();
            });
  }

  /** Directly deactivates a user, bypassing the app - there's no admin endpoint for this yet. */
  public static void setUserActive(String email, boolean active) {
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              User user = User.find("email", email).firstResult();
              user.setActive(active);
              user.persist();
            });
  }

  /**
   * Directly creates an order owned by the given user, bypassing the app - createOrder's response
   * only gives back a tracking message, not a usable order id.
   */
  public static Long createOrderForUser(Long userId, double totalAmount) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              Order order = new Order();
              order.setUser(User.findById(userId));
              order.setOrderDate(LocalDateTime.now());
              order.setTotalAmount(totalAmount);
              order.persist();
              return order.id;
            });
  }

  /** Directly creates a guest order (no owning user), same reasoning as createOrderForUser. */
  public static Long createGuestOrder(double totalAmount) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              Order order = new Order();
              order.setOrderDate(LocalDateTime.now());
              order.setTotalAmount(totalAmount);
              order.persist();
              return order.id;
            });
  }

  /**
   * Directly creates a product owned by the given user, bypassing the app - createProduct's
   * response only gives back a message, not a usable product id.
   */
  public static Long createProductForUser(Long userId, String productName, double price) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              Product product = new Product();
              product.setOwner(User.findById(userId));
              product.setProductName(productName);
              product.setDescription("Test description");
              product.setPrice(price);
              product.setImageURL("https://example.com/image.jpg");
              product.setQuantity(10);
              product.persist();
              return product.id;
            });
  }

  /**
   * Directly creates a product with no owner (a house product), same reasoning as
   * createProductForUser.
   */
  public static Long createProductWithNoOwner(String productName, double price) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              Product product = new Product();
              product.setOwner(null);
              product.setProductName(productName);
              product.setDescription("Test description");
              product.setPrice(price);
              product.setImageURL("https://example.com/image.jpg");
              product.setQuantity(10);
              product.persist();
              return product.id;
            });
  }

  /** Directly reads a product's current stock quantity, bypassing the app. */
  public static Integer getProductQuantity(Long productId) {
    return QuarkusTransaction.requiringNew()
        .call(() -> Product.<Product>findById(productId).getQuantity());
  }

  /**
   * Directly loads an order by id with its items collection initialized, bypassing the app -
   * createOrder's response only gives back a tracking message, and GET /api/orders/{id} doesn't
   * expose totalAmount, status, or items.
   */
  public static Order getOrderWithItems(Long orderId) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              Order order = Order.findById(orderId);
              order
                  .getItems()
                  .size(); // force the lazy collection to load before the session closes
              return order;
            });
  }

  /** Same as getOrderWithItems, but looked up by guestTrackingId for guest orders. */
  public static Order getGuestOrderWithItems(String guestTrackingId) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              Order order = Order.find("guestTrackingId", guestTrackingId).firstResult();
              order.getItems().size();
              return order;
            });
  }

  /** Directly reads a user's roles, bypassing the app - there's no admin endpoint for this yet. */
  public static Set<User.Role> getUserRoles(String email) {
    return QuarkusTransaction.requiringNew()
        .call(() -> User.find("email", email).<User>firstResult().getRoles());
  }

  /**
   * Directly reads a session's expiry fields, bypassing the app - there's no admin endpoint for
   * this yet.
   */
  public static Instant getSessionExpiresAt(String sessionToken) {
    return QuarkusTransaction.requiringNew()
        .call(() -> Session.find("token", sessionToken).<Session>firstResult().expiresAt);
  }

  /**
   * Calls Session.findValid() with no ambient transaction, matching how it's actually called in
   * production (from filters, which aren't @Transactional).
   */
  public static Session findValidSession(String sessionToken) {
    return Session.findValid(sessionToken);
  }
}
