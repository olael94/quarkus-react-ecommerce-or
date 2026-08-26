package org.acme.controller;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.acme.StrictRateLimitTestProfile;
import org.acme.TestAuthHelper;
import org.junit.jupiter.api.Test;

// Runs under a separate config profile with the rate-limit ceilings dropped to 2, so the
// actual 429 behavior can be tested directly without affecting the rest of the suite
// (which relies on a generous %test default so its own incidental register/reset calls
// never trip a limit).
@QuarkusTest
@TestProfile(StrictRateLimitTestProfile.class)
class RateLimitTest {

  private void requestReset(String email) {
    given()
        .contentType("application/json")
        .body("{\"email\":\"" + email + "\"}")
        .post("/api/users/reset-password/request")
        .then()
        .statusCode(200);
  }

  private void confirmReset(String token, int expectedStatus) {
    given()
        .contentType("application/json")
        .body("{\"token\":\"" + token + "\",\"newPassword\":\"newpassword123\"}")
        .post("/api/users/reset-password/confirm")
        .then()
        .statusCode(expectedStatus);
  }

  @Test
  void register_exceedsRateLimit_returns429() {
    TestAuthHelper.register(TestAuthHelper.uniqueEmail(), TestAuthHelper.PASSWORD)
        .then()
        .statusCode(201);
    TestAuthHelper.register(TestAuthHelper.uniqueEmail(), TestAuthHelper.PASSWORD)
        .then()
        .statusCode(201);

    TestAuthHelper.register(TestAuthHelper.uniqueEmail(), TestAuthHelper.PASSWORD)
        .then()
        .statusCode(429);
  }

  @Test
  void requestPasswordReset_exceedsRateLimit_returns429() {
    requestReset(TestAuthHelper.uniqueEmail());
    requestReset(TestAuthHelper.uniqueEmail());

    given()
        .contentType("application/json")
        .body("{\"email\":\"" + TestAuthHelper.uniqueEmail() + "\"}")
        .post("/api/users/reset-password/request")
        .then()
        .statusCode(429);
  }

  @Test
  void confirmPasswordReset_exceedsRateLimit_returns429() {
    // Rate limit check happens before token validation, so an invalid token still
    // counts as an attempt and still returns the normal 401 until the limit trips.
    confirmReset("bogus-token-1", 401);
    confirmReset("bogus-token-2", 401);

    confirmReset("bogus-token-3", 429);
  }
}
