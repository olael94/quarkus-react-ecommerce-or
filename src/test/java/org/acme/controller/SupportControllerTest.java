package org.acme.controller;

import static io.restassured.RestAssured.given;
import static org.acme.TestAuthHelper.AuthenticatedUser;

import io.quarkus.test.junit.QuarkusTest;
import org.acme.TestAuthHelper;
import org.acme.entity.User;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Covers every endpoint in SupportController, grouped by concern. */
@QuarkusTest
class SupportControllerTest {

  @Nested
  class GetAllOrders {

    @Test
    void getAllOrders_asSupport_returns200() {
      AuthenticatedUser support = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(support.email(), User.Role.SUPPORT);

      given()
          .cookie("session", support.sessionCookie())
          .get("/api/support/orders")
          .then()
          .statusCode(200);
    }

    @Test
    void getAllOrders_asAdmin_returns200() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      given()
          .cookie("session", admin.sessionCookie())
          .get("/api/support/orders")
          .then()
          .statusCode(200);
    }

    @Test
    void getAllOrders_asPlainCustomer_returns403() {
      AuthenticatedUser customer = TestAuthHelper.registerAndLogin();

      given()
          .cookie("session", customer.sessionCookie())
          .get("/api/support/orders")
          .then()
          .statusCode(403);
    }

    @Test
    void getAllOrders_noSession_returns401() {
      given().get("/api/support/orders").then().statusCode(401);
    }
  }

  @Nested
  class RefundOrder {

    private long idOf(AuthenticatedUser user) {
      return given()
          .cookie("session", user.sessionCookie())
          .get("/api/users/me")
          .jsonPath()
          .getLong("id");
    }

    @Test
    void refundOrder_asSupport_returns200() {
      // createOrderForUser never sets a status (it stays null) - this also proves refund
      // isn't restricted to COMPLETED orders, since Order.status isn't tied to real
      // delivery/fulfillment in this app.
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long orderId = TestAuthHelper.createOrderForUser(idOf(owner), 19.99);

      AuthenticatedUser support = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(support.email(), User.Role.SUPPORT);

      given()
          .cookie("session", support.sessionCookie())
          .header("X-CSRF-Token", support.csrfToken())
          .post("/api/support/orders/" + orderId + "/refund")
          .then()
          .statusCode(200);
    }

    @Test
    void refundOrder_asAdmin_returns200() {
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long orderId = TestAuthHelper.createOrderForUser(idOf(owner), 19.99);

      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .post("/api/support/orders/" + orderId + "/refund")
          .then()
          .statusCode(200);
    }

    @Test
    void refundOrder_asPlainCustomer_returns403() {
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long orderId = TestAuthHelper.createOrderForUser(idOf(owner), 19.99);

      given()
          .cookie("session", owner.sessionCookie())
          .header("X-CSRF-Token", owner.csrfToken())
          .post("/api/support/orders/" + orderId + "/refund")
          .then()
          .statusCode(403);
    }

    @Test
    void refundOrder_noSession_returns401() {
      given().post("/api/support/orders/1/refund").then().statusCode(401);
    }

    @Test
    void refundOrder_orderNotFound_returns404() {
      AuthenticatedUser support = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(support.email(), User.Role.SUPPORT);

      given()
          .cookie("session", support.sessionCookie())
          .header("X-CSRF-Token", support.csrfToken())
          .post("/api/support/orders/999999999/refund")
          .then()
          .statusCode(404);
    }

    @Test
    void refundOrder_alreadyRefunded_returns400() {
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long orderId = TestAuthHelper.createOrderForUser(idOf(owner), 19.99);

      AuthenticatedUser support = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(support.email(), User.Role.SUPPORT);

      given()
          .cookie("session", support.sessionCookie())
          .header("X-CSRF-Token", support.csrfToken())
          .post("/api/support/orders/" + orderId + "/refund")
          .then()
          .statusCode(200);

      // Refunding the same order again must be rejected, not silently re-processed.
      given()
          .cookie("session", support.sessionCookie())
          .header("X-CSRF-Token", support.csrfToken())
          .post("/api/support/orders/" + orderId + "/refund")
          .then()
          .statusCode(400);
    }
  }
}
