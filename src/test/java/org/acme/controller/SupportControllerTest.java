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
}
