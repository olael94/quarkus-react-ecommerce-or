package org.acme.controller;

import static io.restassured.RestAssured.given;
import static org.acme.TestAuthHelper.AuthenticatedUser;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import org.acme.TestAuthHelper;
import org.acme.entity.Order;
import org.acme.entity.User;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Covers every endpoint in OrderController, grouped by concern. */
@QuarkusTest
class OrderControllerTest {

  private long idOf(AuthenticatedUser user) {
    return given()
        .cookie("session", user.sessionCookie())
        .get("/api/users/me")
        .jsonPath()
        .getLong("id");
  }

  @Nested
  class GetOrder {

    @Test
    void getOrder_asOwner_returns200() {
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long orderId = TestAuthHelper.createOrderForUser(idOf(owner), 19.99);

      given()
          .cookie("session", owner.sessionCookie())
          .get("/api/orders/" + orderId)
          .then()
          .statusCode(200);
    }

    @Test
    void getOrder_asAdmin_forSomeoneElseOrder_returns200() {
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long orderId = TestAuthHelper.createOrderForUser(idOf(owner), 19.99);

      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      given()
          .cookie("session", admin.sessionCookie())
          .get("/api/orders/" + orderId)
          .then()
          .statusCode(200);
    }

    @Test
    void getOrder_asSupport_forSomeoneElseOrder_returns200() {
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long orderId = TestAuthHelper.createOrderForUser(idOf(owner), 19.99);

      AuthenticatedUser support = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(support.email(), User.Role.SUPPORT);

      given()
          .cookie("session", support.sessionCookie())
          .get("/api/orders/" + orderId)
          .then()
          .statusCode(200);
    }

    @Test
    void getOrder_asNonOwnerNonAdmin_returns403() {
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long orderId = TestAuthHelper.createOrderForUser(idOf(owner), 19.99);

      AuthenticatedUser someoneElse = TestAuthHelper.registerAndLogin();

      given()
          .cookie("session", someoneElse.sessionCookie())
          .get("/api/orders/" + orderId)
          .then()
          .statusCode(403);
    }

    @Test
    void getOrder_noSession_returns401() {
      given().get("/api/orders/1").then().statusCode(401);
    }

    @Test
    void getOrder_orderNotFound_returns404() {
      AuthenticatedUser user = TestAuthHelper.registerAndLogin();

      given()
          .cookie("session", user.sessionCookie())
          .get("/api/orders/999999999")
          .then()
          .statusCode(404);
    }

    @Test
    void getOrder_guestOrder_nonAdmin_returns403() {
      long orderId = TestAuthHelper.createGuestOrder(9.99);
      AuthenticatedUser user = TestAuthHelper.registerAndLogin();

      given()
          .cookie("session", user.sessionCookie())
          .get("/api/orders/" + orderId)
          .then()
          .statusCode(403);
    }

    @Test
    void getOrder_guestOrder_asAdmin_returns200() {
      long orderId = TestAuthHelper.createGuestOrder(9.99);
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      given()
          .cookie("session", admin.sessionCookie())
          .get("/api/orders/" + orderId)
          .then()
          .statusCode(200);
    }
  }

  @Nested
  class CreateOrder {

    private String singleItemBody(Long productId, int quantity) {
      return "{\"items\":[{\"productId\":" + productId + ",\"quantity\":" + quantity + "}]}";
    }

    private String singleItemBodyForUser(long userId, Long productId, int quantity) {
      return "{\"userId\":"
          + userId
          + ",\"items\":[{\"productId\":"
          + productId
          + ",\"quantity\":"
          + quantity
          + "}]}";
    }

    private String twoItemBody(Long productId1, int quantity1, Long productId2, int quantity2) {
      return "{\"items\":[{\"productId\":"
          + productId1
          + ",\"quantity\":"
          + quantity1
          + "},{\"productId\":"
          + productId2
          + ",\"quantity\":"
          + quantity2
          + "}]}";
    }

    // createOrder's response is just a tracking message - "Your order tracking number is 42" for
    // a user order, or "Your order guest tracking number is <uuid>" for a guest one - both end
    // with the id/tracking number, so tests parse it off the end rather than needing a new
    // response shape.
    private String lastToken(String message) {
      return message.substring(message.lastIndexOf(' ') + 1);
    }

    @Test
    void createOrder_asGuest_computesTotalFromProductPrice() {
      Long productId = TestAuthHelper.createProductWithNoOwner("Widget", 12.50);

      String message =
          given()
              .contentType("application/json")
              .body(singleItemBody(productId, 3))
              .post("/api/orders")
              .then()
              .statusCode(201)
              .extract()
              .jsonPath()
              .getString("message");

      Order order = TestAuthHelper.getGuestOrderWithItems(lastToken(message));

      assertEquals(37.50, order.getTotalAmount(), 0.001);
    }

    @Test
    void createOrder_setsStatusPending() {
      Long productId = TestAuthHelper.createProductWithNoOwner("Widget", 5.00);

      String message =
          given()
              .contentType("application/json")
              .body(singleItemBody(productId, 1))
              .post("/api/orders")
              .then()
              .statusCode(201)
              .extract()
              .jsonPath()
              .getString("message");

      Order order = TestAuthHelper.getGuestOrderWithItems(lastToken(message));

      assertEquals(Order.Status.PENDING, order.getStatus());
    }

    @Test
    void createOrder_decrementsProductStock() {
      Long productId = TestAuthHelper.createProductWithNoOwner("Widget", 5.00); // starts at 10

      given()
          .contentType("application/json")
          .body(singleItemBody(productId, 4))
          .post("/api/orders")
          .then()
          .statusCode(201);

      assertEquals(6, TestAuthHelper.getProductQuantity(productId).intValue());
    }

    @Test
    void createOrder_multipleItems_computesCorrectTotalAndItemCount() {
      Long productId1 = TestAuthHelper.createProductWithNoOwner("Widget", 10.00);
      Long productId2 = TestAuthHelper.createProductWithNoOwner("Gadget", 3.50);

      String message =
          given()
              .contentType("application/json")
              .body(twoItemBody(productId1, 2, productId2, 4))
              .post("/api/orders")
              .then()
              .statusCode(201)
              .extract()
              .jsonPath()
              .getString("message");

      Order order = TestAuthHelper.getGuestOrderWithItems(lastToken(message));

      assertEquals(2, order.getItems().size());
      assertEquals(34.00, order.getTotalAmount(), 0.001); // (2 * 10.00) + (4 * 3.50)
    }

    @Test
    void createOrder_insufficientStock_returns400AndLeavesStockUnchanged() {
      Long productId = TestAuthHelper.createProductWithNoOwner("Widget", 5.00); // starts at 10

      given()
          .contentType("application/json")
          .body(singleItemBody(productId, 11))
          .post("/api/orders")
          .then()
          .statusCode(400);

      assertEquals(10, TestAuthHelper.getProductQuantity(productId).intValue());
    }

    @Test
    void createOrder_secondItemInsufficientStock_rollsBackFirstItemsDecrement() {
      Long productId1 = TestAuthHelper.createProductWithNoOwner("Widget", 10.00); // starts at 10
      Long productId2 = TestAuthHelper.createProductWithNoOwner("Gadget", 3.50); // starts at 10

      given()
          .contentType("application/json")
          .body(twoItemBody(productId1, 5, productId2, 20)) // second item exceeds its stock
          .post("/api/orders")
          .then()
          .statusCode(400);

      // Regression test: the first item's stock decrement has to be rolled back too, not just
      // rejected on the second item - otherwise a failed order would still silently consume
      // real stock (see the createOrder commit message for why this needed throw, not return).
      assertEquals(10, TestAuthHelper.getProductQuantity(productId1).intValue());
      assertEquals(10, TestAuthHelper.getProductQuantity(productId2).intValue());
    }

    @Test
    void createOrder_unknownProduct_returns400() {
      given()
          .contentType("application/json")
          .body(singleItemBody(999999999L, 1))
          .post("/api/orders")
          .then()
          .statusCode(400);
    }

    @Test
    void createOrder_emptyItems_returns400() {
      given()
          .contentType("application/json")
          .body("{\"items\":[]}")
          .post("/api/orders")
          .then()
          .statusCode(400);
    }

    @Test
    void createOrder_missingProductId_returns400() {
      given()
          .contentType("application/json")
          .body("{\"items\":[{\"quantity\":1}]}")
          .post("/api/orders")
          .then()
          .statusCode(400);
    }

    @Test
    void createOrder_nonPositiveQuantity_returns400() {
      Long productId = TestAuthHelper.createProductWithNoOwner("Widget", 5.00);

      given()
          .contentType("application/json")
          .body(singleItemBody(productId, 0))
          .post("/api/orders")
          .then()
          .statusCode(400);
    }

    @Test
    void createOrder_unknownUserId_returns400() {
      Long productId = TestAuthHelper.createProductWithNoOwner("Widget", 5.00);

      given()
          .contentType("application/json")
          .body(singleItemBodyForUser(999999999L, productId, 1))
          .post("/api/orders")
          .then()
          .statusCode(400);
    }

    @Test
    void createOrder_asRegisteredUser_setsOwner() {
      AuthenticatedUser buyer = TestAuthHelper.registerAndLogin();
      long buyerId = idOf(buyer);
      Long productId = TestAuthHelper.createProductWithNoOwner("Widget", 5.00);

      String message =
          given()
              .contentType("application/json")
              .body(singleItemBodyForUser(buyerId, productId, 1))
              .post("/api/orders")
              .then()
              .statusCode(201)
              .extract()
              .jsonPath()
              .getString("message");

      long orderId = Long.parseLong(lastToken(message));
      Order order = TestAuthHelper.getOrderWithItems(orderId);

      assertEquals(buyerId, order.getUser().id);
    }
  }
}
