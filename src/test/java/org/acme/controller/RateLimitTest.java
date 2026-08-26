package org.acme.controller;

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
}
