package org.acme.controller;

import static io.restassured.RestAssured.given;
import static org.acme.TestAuthHelper.AuthenticatedUser;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.acme.TestAuthHelper;
import org.acme.entity.User;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Covers every endpoint in AdminController, grouped by concern. */
@QuarkusTest
class AdminControllerTest {

  private long idOf(AuthenticatedUser user) {
    return given()
        .cookie("session", user.sessionCookie())
        .get("/api/users/me")
        .jsonPath()
        .getLong("id");
  }

  @Nested
  class GetAllUsers {

    @Test
    void getAllUsers_noSession_returns401() {
      given().get("/api/admin/users").then().statusCode(401);
    }

    @Test
    void getAllUsers_asAdmin_returns200() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      given()
          .cookie("session", admin.sessionCookie())
          .get("/api/admin/users")
          .then()
          .statusCode(200);
    }

    @Test
    void getAllUsers_nonAdmin_returns403() {
      AuthenticatedUser user = TestAuthHelper.registerAndLogin();

      given()
          .cookie("session", user.sessionCookie())
          .get("/api/admin/users")
          .then()
          .statusCode(403);
    }
  }

  @Nested
  class GrantRevokeRole {

    private long idOf(AuthenticatedUser user) {
      return given()
          .cookie("session", user.sessionCookie())
          .get("/api/users/me")
          .jsonPath()
          .getLong("id");
    }

    @Test
    void grantRole_asAdmin_success() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      AuthenticatedUser target = TestAuthHelper.registerAndLogin();
      long targetId = idOf(target);

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .post("/api/admin/users/" + targetId + "/roles/VENDOR")
          .then()
          .statusCode(200);

      org.junit.jupiter.api.Assertions.assertTrue(
          TestAuthHelper.getUserRoles(target.email()).contains(User.Role.VENDOR));
    }

    @Test
    void grantRole_nonAdmin_returns403() {
      AuthenticatedUser nonAdmin = TestAuthHelper.registerAndLogin();
      AuthenticatedUser target = TestAuthHelper.registerAndLogin();
      long targetId = idOf(target);

      given()
          .cookie("session", nonAdmin.sessionCookie())
          .header("X-CSRF-Token", nonAdmin.csrfToken())
          .post("/api/admin/users/" + targetId + "/roles/VENDOR")
          .then()
          .statusCode(403);
    }

    @Test
    void grantRole_noSession_returns401() {
      given().post("/api/admin/users/1/roles/VENDOR").then().statusCode(401);
    }

    @Test
    void grantRole_targetNotFound_returns404() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);
      long adminId = idOf(admin);
      long nonexistentId = adminId + 1_000_000L;

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .post("/api/admin/users/" + nonexistentId + "/roles/VENDOR")
          .then()
          .statusCode(404);
    }

    @Test
    void grantRole_alreadyHeld_isIdempotent() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      AuthenticatedUser target = TestAuthHelper.registerAndLogin();
      long targetId = idOf(target);
      TestAuthHelper.addUserRole(target.email(), User.Role.VENDOR);

      // Granting a role the target already has should just succeed, not error.
      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .post("/api/admin/users/" + targetId + "/roles/VENDOR")
          .then()
          .statusCode(200);

      org.junit.jupiter.api.Assertions.assertEquals(
          java.util.Set.of(User.Role.CUSTOMER, User.Role.VENDOR),
          TestAuthHelper.getUserRoles(target.email()));
    }

    @Test
    void grantRole_ownAccount_isAllowed() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);
      long adminId = idOf(admin);

      // Self-grant is allowed - only self-revoke is blocked.
      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .post("/api/admin/users/" + adminId + "/roles/VENDOR")
          .then()
          .statusCode(200);

      org.junit.jupiter.api.Assertions.assertTrue(
          TestAuthHelper.getUserRoles(admin.email()).contains(User.Role.VENDOR));
    }

    @Test
    void userCanHoldMultipleRolesSimultaneously() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      AuthenticatedUser target = TestAuthHelper.registerAndLogin();
      long targetId = idOf(target);

      // Grant a second role on top of the CUSTOMER role registration already gave them.
      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .post("/api/admin/users/" + targetId + "/roles/VENDOR")
          .then()
          .statusCode(200);

      org.junit.jupiter.api.Assertions.assertEquals(
          java.util.Set.of(User.Role.CUSTOMER, User.Role.VENDOR),
          TestAuthHelper.getUserRoles(target.email()));

      // Revoking just one role must not disturb the other.
      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .delete("/api/admin/users/" + targetId + "/roles/CUSTOMER")
          .then()
          .statusCode(200);

      org.junit.jupiter.api.Assertions.assertEquals(
          java.util.Set.of(User.Role.VENDOR), TestAuthHelper.getUserRoles(target.email()));
    }

    @Test
    void revokeRole_asAdmin_success() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      AuthenticatedUser target = TestAuthHelper.registerAndLogin();
      long targetId = idOf(target);
      TestAuthHelper.addUserRole(target.email(), User.Role.VENDOR);

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .delete("/api/admin/users/" + targetId + "/roles/VENDOR")
          .then()
          .statusCode(200);

      org.junit.jupiter.api.Assertions.assertFalse(
          TestAuthHelper.getUserRoles(target.email()).contains(User.Role.VENDOR));
    }

    @Test
    void revokeRole_nonAdmin_returns403() {
      AuthenticatedUser nonAdmin = TestAuthHelper.registerAndLogin();
      AuthenticatedUser target = TestAuthHelper.registerAndLogin();
      long targetId = idOf(target);

      given()
          .cookie("session", nonAdmin.sessionCookie())
          .header("X-CSRF-Token", nonAdmin.csrfToken())
          .delete("/api/admin/users/" + targetId + "/roles/CUSTOMER")
          .then()
          .statusCode(403);
    }

    @Test
    void revokeRole_noSession_returns401() {
      given().delete("/api/admin/users/1/roles/CUSTOMER").then().statusCode(401);
    }

    @Test
    void revokeRole_targetNotFound_returns404() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);
      long adminId = idOf(admin);
      long nonexistentId = adminId + 1_000_000L;

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .delete("/api/admin/users/" + nonexistentId + "/roles/CUSTOMER")
          .then()
          .statusCode(404);
    }

    @Test
    void revokeRole_notHeld_isIdempotent() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      AuthenticatedUser target = TestAuthHelper.registerAndLogin();
      long targetId = idOf(target);

      // Target never had VENDOR - revoking it anyway should just succeed, not error.
      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .delete("/api/admin/users/" + targetId + "/roles/VENDOR")
          .then()
          .statusCode(200);
    }

    @Test
    void revokeRole_ownAccount_returns400() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);
      long adminId = idOf(admin);

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .delete("/api/admin/users/" + adminId + "/roles/ADMIN")
          .then()
          .statusCode(400)
          .body("message", equalTo("You cannot revoke your own role"));
    }
  }

  @Nested
  class DeactivateReactivate {

    @Test
    void deactivateUser_asAdmin_success_killsTargetSessionAndBlocksLogin() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      AuthenticatedUser target = TestAuthHelper.registerAndLogin();
      long targetId =
          given()
              .cookie("session", target.sessionCookie())
              .get("/api/users/me")
              .jsonPath()
              .getLong("id");

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .post("/api/admin/users/" + targetId + "/deactivate")
          .then()
          .statusCode(200);

      // The target's already-existing session must be killed immediately, not
      // just future logins blocked.
      given().cookie("session", target.sessionCookie()).get("/api/users/me").then().statusCode(401);

      TestAuthHelper.login(target.email(), target.password())
          .then()
          .statusCode(401)
          .body("message", equalTo("Invalid email or password"));
    }

    @Test
    void deactivateUser_nonAdmin_returns403() {
      AuthenticatedUser nonAdmin = TestAuthHelper.registerAndLogin();
      AuthenticatedUser target = TestAuthHelper.registerAndLogin();
      long targetId =
          given()
              .cookie("session", target.sessionCookie())
              .get("/api/users/me")
              .jsonPath()
              .getLong("id");

      given()
          .cookie("session", nonAdmin.sessionCookie())
          .header("X-CSRF-Token", nonAdmin.csrfToken())
          .post("/api/admin/users/" + targetId + "/deactivate")
          .then()
          .statusCode(403);
    }

    @Test
    void deactivateUser_noSession_returns401() {
      given().post("/api/admin/users/1/deactivate").then().statusCode(401);
    }

    @Test
    void deactivateUser_targetNotFound_returns404() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);
      long adminId =
          given()
              .cookie("session", admin.sessionCookie())
              .get("/api/users/me")
              .jsonPath()
              .getLong("id");
      long nonexistentId = adminId + 1_000_000L;

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .post("/api/admin/users/" + nonexistentId + "/deactivate")
          .then()
          .statusCode(404);
    }

    @Test
    void deactivateUser_ownAccount_returns400() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);
      long adminId =
          given()
              .cookie("session", admin.sessionCookie())
              .get("/api/users/me")
              .jsonPath()
              .getLong("id");

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .post("/api/admin/users/" + adminId + "/deactivate")
          .then()
          .statusCode(400)
          .body("message", equalTo("You cannot deactivate your own account"));
    }

    @Test
    void reactivateUser_asAdmin_success_targetCanLoginAgain() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      AuthenticatedUser target = TestAuthHelper.registerAndLogin();
      long targetId =
          given()
              .cookie("session", target.sessionCookie())
              .get("/api/users/me")
              .jsonPath()
              .getLong("id");
      TestAuthHelper.setUserActive(target.email(), false);

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .post("/api/admin/users/" + targetId + "/reactivate")
          .then()
          .statusCode(200);

      TestAuthHelper.login(target.email(), target.password()).then().statusCode(200);
    }

    @Test
    void reactivateUser_nonAdmin_returns403() {
      AuthenticatedUser nonAdmin = TestAuthHelper.registerAndLogin();
      AuthenticatedUser target = TestAuthHelper.registerAndLogin();
      long targetId =
          given()
              .cookie("session", target.sessionCookie())
              .get("/api/users/me")
              .jsonPath()
              .getLong("id");

      given()
          .cookie("session", nonAdmin.sessionCookie())
          .header("X-CSRF-Token", nonAdmin.csrfToken())
          .post("/api/admin/users/" + targetId + "/reactivate")
          .then()
          .statusCode(403);
    }

    @Test
    void reactivateUser_targetNotFound_returns404() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);
      long adminId =
          given()
              .cookie("session", admin.sessionCookie())
              .get("/api/users/me")
              .jsonPath()
              .getLong("id");
      long nonexistentId = adminId + 1_000_000L;

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .post("/api/admin/users/" + nonexistentId + "/reactivate")
          .then()
          .statusCode(404);
    }
  }

  @Nested
  class AdminPasswordReset {

    @Test
    void adminRequestPasswordReset_asAdmin_success_targetCanCompleteReset() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      AuthenticatedUser target = TestAuthHelper.registerAndLogin();
      long targetId =
          given()
              .cookie("session", target.sessionCookie())
              .get("/api/users/me")
              .jsonPath()
              .getLong("id");

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .post("/api/admin/users/" + targetId + "/reset-password")
          .then()
          .statusCode(200);

      // The triggered reset must produce a real, usable token, not just a
      // 200 response - complete the flow end to end. This confirm endpoint is
      // self-service and stays under /api/users, not /api/admin.
      String token = TestAuthHelper.getLatestResetTokenFor(target.email());
      given()
          .contentType("application/json")
          .body("{\"token\":\"" + token + "\",\"newPassword\":\"newpassword456\"}")
          .post("/api/users/reset-password/confirm")
          .then()
          .statusCode(200);

      TestAuthHelper.login(target.email(), "newpassword456").then().statusCode(200);
    }

    @Test
    void adminRequestPasswordReset_nonAdmin_returns403() {
      AuthenticatedUser nonAdmin = TestAuthHelper.registerAndLogin();
      AuthenticatedUser target = TestAuthHelper.registerAndLogin();
      long targetId =
          given()
              .cookie("session", target.sessionCookie())
              .get("/api/users/me")
              .jsonPath()
              .getLong("id");

      given()
          .cookie("session", nonAdmin.sessionCookie())
          .header("X-CSRF-Token", nonAdmin.csrfToken())
          .post("/api/admin/users/" + targetId + "/reset-password")
          .then()
          .statusCode(403);
    }

    @Test
    void adminRequestPasswordReset_noSession_returns401() {
      given().post("/api/admin/users/1/reset-password").then().statusCode(401);
    }

    @Test
    void adminRequestPasswordReset_targetNotFound_returns404() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);
      long adminId =
          given()
              .cookie("session", admin.sessionCookie())
              .get("/api/users/me")
              .jsonPath()
              .getLong("id");
      long nonexistentId = adminId + 1_000_000L;

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .post("/api/admin/users/" + nonexistentId + "/reset-password")
          .then()
          .statusCode(404);
    }
  }

  @Nested
  class GetAllOrders {

    @Test
    void getAllOrders_asAdmin_returns200() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      given()
          .cookie("session", admin.sessionCookie())
          .get("/api/admin/orders")
          .then()
          .statusCode(200);
    }

    @Test
    void getAllOrders_nonAdmin_returns403() {
      AuthenticatedUser user = TestAuthHelper.registerAndLogin();

      given()
          .cookie("session", user.sessionCookie())
          .get("/api/admin/orders")
          .then()
          .statusCode(403);
    }

    @Test
    void getAllOrders_noSession_returns401() {
      given().get("/api/admin/orders").then().statusCode(401);
    }
  }

  @Nested
  class UpdateOrder {

    private static final String VALID_UPDATE_BODY =
        "{\"orderDate\":\"2026-01-01T10:00:00\",\"totalAmount\":29.99,\"status\":\"COMPLETED\"}";

    @Test
    void updateOrder_asAdmin_returns200() {
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long orderId = TestAuthHelper.createOrderForUser(idOf(owner), 19.99);

      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .contentType("application/json")
          .body(VALID_UPDATE_BODY)
          .put("/api/admin/orders/" + orderId)
          .then()
          .statusCode(200);
    }

    @Test
    void updateOrder_nonAdminEvenIfOwner_returns403() {
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long orderId = TestAuthHelper.createOrderForUser(idOf(owner), 19.99);

      given()
          .cookie("session", owner.sessionCookie())
          .header("X-CSRF-Token", owner.csrfToken())
          .contentType("application/json")
          .body(VALID_UPDATE_BODY)
          .put("/api/admin/orders/" + orderId)
          .then()
          .statusCode(403);
    }

    @Test
    void updateOrder_noSession_returns401() {
      given()
          .contentType("application/json")
          .body(VALID_UPDATE_BODY)
          .put("/api/admin/orders/1")
          .then()
          .statusCode(401);
    }

    @Test
    void updateOrder_missingOrderDate_returns400() {
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long orderId = TestAuthHelper.createOrderForUser(idOf(owner), 19.99);

      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .contentType("application/json")
          .body("{\"totalAmount\":29.99,\"status\":\"COMPLETED\"}")
          .put("/api/admin/orders/" + orderId)
          .then()
          .statusCode(400);
    }

    @Test
    void updateOrder_negativeTotalAmount_returns400() {
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long orderId = TestAuthHelper.createOrderForUser(idOf(owner), 19.99);

      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .contentType("application/json")
          .body(
              "{\"orderDate\":\"2026-01-01T10:00:00\",\"totalAmount\":-5.00,\"status\":\"COMPLETED\"}")
          .put("/api/admin/orders/" + orderId)
          .then()
          .statusCode(400);
    }

    @Test
    void updateOrder_orderNotFound_returns404() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .contentType("application/json")
          .body(VALID_UPDATE_BODY)
          .put("/api/admin/orders/999999999")
          .then()
          .statusCode(404);
    }
  }

  @Nested
  class DeleteOrder {

    @Test
    void deleteOrder_asAdmin_returns200() {
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long orderId = TestAuthHelper.createOrderForUser(idOf(owner), 19.99);

      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .delete("/api/admin/orders/" + orderId)
          .then()
          .statusCode(200);
    }

    @Test
    void deleteOrder_nonAdminEvenIfOwner_returns403() {
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long orderId = TestAuthHelper.createOrderForUser(idOf(owner), 19.99);

      given()
          .cookie("session", owner.sessionCookie())
          .header("X-CSRF-Token", owner.csrfToken())
          .delete("/api/admin/orders/" + orderId)
          .then()
          .statusCode(403);
    }

    @Test
    void deleteOrder_noSession_returns401() {
      given().delete("/api/admin/orders/1").then().statusCode(401);
    }

    @Test
    void deleteOrder_orderNotFound_returns404() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .delete("/api/admin/orders/999999999")
          .then()
          .statusCode(404);
    }
  }
}
