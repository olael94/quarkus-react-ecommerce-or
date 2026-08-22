package org.acme.controller;

import static io.restassured.RestAssured.given;
import static org.acme.TestAuthHelper.AuthenticatedUser;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.acme.TestAuthHelper;
import org.acme.entity.User;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Covers every endpoint in ProductController, grouped by concern. */
@QuarkusTest
class ProductControllerTest {

  private long idOf(AuthenticatedUser user) {
    return given()
        .cookie("session", user.sessionCookie())
        .get("/api/users/me")
        .jsonPath()
        .getLong("id");
  }

  // createProduct never returns the new product's id (just a message), so tests that need it
  // give the product a unique name and look it up in the full product list afterward.
  private long findProductIdByName(String productName) {
    List<Map<String, Object>> products =
        given().get("/api/products").then().extract().jsonPath().getList("$");
    return products.stream()
        .filter(p -> productName.equals(p.get("productName")))
        .map(p -> ((Number) p.get("id")).longValue())
        .findFirst()
        .orElseThrow();
  }

  @Nested
  class CreateProduct {

    private String bodyWithName(String productName) {
      return "{\"productName\":\""
          + productName
          + "\",\"description\":\"A test product\",\"price\":19.99,"
          + "\"imageURL\":\"https://example.com/image.png\",\"quantity\":5}";
    }

    @Test
    void createProduct_asVendor_returns201() {
      AuthenticatedUser vendor = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(vendor.email(), User.Role.VENDOR);

      given()
          .cookie("session", vendor.sessionCookie())
          .header("X-CSRF-Token", vendor.csrfToken())
          .contentType("application/json")
          .body(bodyWithName("Vendor Product"))
          .post("/api/products")
          .then()
          .statusCode(201);
    }

    @Test
    void createProduct_asAdmin_returns201() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .contentType("application/json")
          .body(bodyWithName("Admin Product"))
          .post("/api/products")
          .then()
          .statusCode(201);
    }

    @Test
    void createProduct_asCustomerOnly_returns403() {
      AuthenticatedUser customer = TestAuthHelper.registerAndLogin();

      given()
          .cookie("session", customer.sessionCookie())
          .header("X-CSRF-Token", customer.csrfToken())
          .contentType("application/json")
          .body(bodyWithName("Customer Product"))
          .post("/api/products")
          .then()
          .statusCode(403);
    }

    @Test
    void createProduct_noSession_returns401() {
      given()
          .contentType("application/json")
          .body(bodyWithName("No Session Product"))
          .post("/api/products")
          .then()
          .statusCode(401);
    }

    @Test
    void createProduct_setsCallerAsOwner() {
      AuthenticatedUser vendor = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(vendor.email(), User.Role.VENDOR);
      long vendorId = idOf(vendor);

      String uniqueName = "Owned Product " + UUID.randomUUID();
      given()
          .cookie("session", vendor.sessionCookie())
          .header("X-CSRF-Token", vendor.csrfToken())
          .contentType("application/json")
          .body(bodyWithName(uniqueName))
          .post("/api/products")
          .then()
          .statusCode(201);

      long productId = findProductIdByName(uniqueName);
      given()
          .get("/api/products/" + productId)
          .then()
          .statusCode(200)
          .body("ownerId", equalTo((int) vendorId));
    }

    @Test
    void createProduct_ignoresClientSuppliedOwner() {
      AuthenticatedUser vendor = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(vendor.email(), User.Role.VENDOR);
      long vendorId = idOf(vendor);

      AuthenticatedUser other = TestAuthHelper.registerAndLogin();
      long otherId = idOf(other);

      String uniqueName = "Spoofed Owner Product " + UUID.randomUUID();
      String bodyWithSpoofedOwner =
          "{\"productName\":\""
              + uniqueName
              + "\",\"description\":\"A test product\",\"price\":19.99,"
              + "\"imageURL\":\"https://example.com/image.png\",\"quantity\":5,"
              + "\"owner\":{\"id\":"
              + otherId
              + "}}";

      given()
          .cookie("session", vendor.sessionCookie())
          .header("X-CSRF-Token", vendor.csrfToken())
          .contentType("application/json")
          .body(bodyWithSpoofedOwner)
          .post("/api/products")
          .then()
          .statusCode(201);

      long productId = findProductIdByName(uniqueName);
      given()
          .get("/api/products/" + productId)
          .then()
          .statusCode(200)
          .body("ownerId", equalTo((int) vendorId))
          .body("ownerId", not(equalTo((int) otherId)));
    }
  }

  @Nested
  class GetAllProducts {

    @Test
    void getAllProducts_noSession_returns200() {
      given().get("/api/products").then().statusCode(200);
    }
  }

  @Nested
  class GetProduct {

    @Test
    void getProduct_withOwner_returnsOwnerIdWithoutLeakingPassword() {
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long ownerId = idOf(owner);
      long productId = TestAuthHelper.createProductForUser(ownerId, "Owned Product", 9.99);

      given()
          .get("/api/products/" + productId)
          .then()
          .statusCode(200)
          .body(not(containsString("password")))
          .body("ownerId", equalTo((int) ownerId));
    }

    @Test
    void getProduct_noOwner_returnsNullOwnerId() {
      long productId = TestAuthHelper.createProductWithNoOwner("House Product", 9.99);

      given().get("/api/products/" + productId).then().statusCode(200).body("ownerId", nullValue());
    }

    @Test
    void getProduct_notFound_returns404() {
      given().get("/api/products/999999999").then().statusCode(404);
    }
  }

  @Nested
  class UpdateProduct {

    private static final String VALID_UPDATE_BODY =
        "{\"productName\":\"Updated Product\",\"description\":\"Updated description\","
            + "\"price\":29.99,\"imageURL\":\"https://example.com/updated.png\"}";

    @Test
    void updateProduct_asOwner_returns200() {
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long ownerId = idOf(owner);
      long productId = TestAuthHelper.createProductForUser(ownerId, "Original Product", 9.99);

      given()
          .cookie("session", owner.sessionCookie())
          .header("X-CSRF-Token", owner.csrfToken())
          .contentType("application/json")
          .body(VALID_UPDATE_BODY)
          .put("/api/products/" + productId)
          .then()
          .statusCode(200);
    }

    @Test
    void updateProduct_asAdmin_forSomeoneElseProduct_returns200() {
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long ownerId = idOf(owner);
      long productId = TestAuthHelper.createProductForUser(ownerId, "Original Product", 9.99);

      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .contentType("application/json")
          .body(VALID_UPDATE_BODY)
          .put("/api/products/" + productId)
          .then()
          .statusCode(200);
    }

    @Test
    void updateProduct_asNonOwnerNonAdmin_returns403() {
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long ownerId = idOf(owner);
      long productId = TestAuthHelper.createProductForUser(ownerId, "Original Product", 9.99);

      AuthenticatedUser someoneElse = TestAuthHelper.registerAndLogin();

      given()
          .cookie("session", someoneElse.sessionCookie())
          .header("X-CSRF-Token", someoneElse.csrfToken())
          .contentType("application/json")
          .body(VALID_UPDATE_BODY)
          .put("/api/products/" + productId)
          .then()
          .statusCode(403);
    }

    @Test
    void updateProduct_noSession_returns401() {
      given()
          .contentType("application/json")
          .body(VALID_UPDATE_BODY)
          .put("/api/products/1")
          .then()
          .statusCode(401);
    }

    @Test
    void updateProduct_notFound_returns404() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .contentType("application/json")
          .body(VALID_UPDATE_BODY)
          .put("/api/products/999999999")
          .then()
          .statusCode(404);
    }
  }

  @Nested
  class DeleteProduct {

    @Test
    void deleteProduct_asOwner_returns200() {
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long ownerId = idOf(owner);
      long productId = TestAuthHelper.createProductForUser(ownerId, "Original Product", 9.99);

      given()
          .cookie("session", owner.sessionCookie())
          .header("X-CSRF-Token", owner.csrfToken())
          .delete("/api/products/" + productId)
          .then()
          .statusCode(200);
    }

    @Test
    void deleteProduct_asAdmin_forSomeoneElseProduct_returns200() {
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long ownerId = idOf(owner);
      long productId = TestAuthHelper.createProductForUser(ownerId, "Original Product", 9.99);

      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .delete("/api/products/" + productId)
          .then()
          .statusCode(200);
    }

    @Test
    void deleteProduct_asNonOwnerNonAdmin_returns403() {
      AuthenticatedUser owner = TestAuthHelper.registerAndLogin();
      long ownerId = idOf(owner);
      long productId = TestAuthHelper.createProductForUser(ownerId, "Original Product", 9.99);

      AuthenticatedUser someoneElse = TestAuthHelper.registerAndLogin();

      given()
          .cookie("session", someoneElse.sessionCookie())
          .header("X-CSRF-Token", someoneElse.csrfToken())
          .delete("/api/products/" + productId)
          .then()
          .statusCode(403);
    }

    @Test
    void deleteProduct_noSession_returns401() {
      given().delete("/api/products/1").then().statusCode(401);
    }

    @Test
    void deleteProduct_notFound_returns404() {
      AuthenticatedUser admin = TestAuthHelper.registerAndLogin();
      TestAuthHelper.addUserRole(admin.email(), User.Role.ADMIN);

      given()
          .cookie("session", admin.sessionCookie())
          .header("X-CSRF-Token", admin.csrfToken())
          .delete("/api/products/999999999")
          .then()
          .statusCode(404);
    }
  }
}
