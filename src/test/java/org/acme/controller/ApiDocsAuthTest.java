package org.acme.controller;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.acme.DocsAuthTestProfile;
import org.junit.jupiter.api.Test;

// Runs under DocsAuthTestProfile, which simulates the %prod-only Basic Auth gate on
// /q/swagger-ui and /q/openapi so it can be verified without an actual production
// deployment - see application.properties for the real %prod config this mirrors.
@QuarkusTest
@TestProfile(DocsAuthTestProfile.class)
class ApiDocsAuthTest {

  @Test
  void swaggerUi_noCredentials_returns401() {
    given().when().get("/q/swagger-ui").then().statusCode(401);
  }

  @Test
  void swaggerUi_correctCredentials_isNotRejected() {
    given()
        .auth()
        .preemptive()
        .basic("teamdocs", "test-team-password")
        .when()
        .get("/q/swagger-ui")
        .then()
        .statusCode(org.hamcrest.Matchers.not(401));
  }

  @Test
  void swaggerUi_wrongCredentials_returns401() {
    given()
        .auth()
        .preemptive()
        .basic("teamdocs", "wrong-password")
        .when()
        .get("/q/swagger-ui")
        .then()
        .statusCode(401);
  }

  @Test
  void openapi_noCredentials_returns401() {
    given().when().get("/q/openapi").then().statusCode(401);
  }

  @Test
  void openapi_correctCredentials_returns200() {
    given()
        .auth()
        .preemptive()
        .basic("teamdocs", "test-team-password")
        .when()
        .get("/q/openapi")
        .then()
        .statusCode(200);
  }

  @Test
  void health_staysUnauthenticated() {
    given().when().get("/q/health").then().statusCode(200);
  }
}
