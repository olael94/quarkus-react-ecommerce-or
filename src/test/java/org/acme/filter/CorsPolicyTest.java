package org.acme.filter;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CorsPolicyTest {

  // CORS is disabled (quarkus.http.cors.enabled=false), so no Access-Control-*
  // headers should ever come back - that absence is what makes a browser
  // refuse to let cross-origin JS read the response. The request itself
  // still succeeds server-side; CORS is enforced by the browser, not the API.

  @Test
  void crossOriginRequest_getsNoAccessControlAllowOriginHeader() {
    given()
        .header("Origin", "http://evil.example.com")
        .get("/api/products")
        .then()
        .statusCode(200)
        .header("Access-Control-Allow-Origin", nullValue());
  }

  @Test
  void crossOriginRequest_getsNoAccessControlAllowCredentialsHeader() {
    given()
        .header("Origin", "http://evil.example.com")
        .get("/api/products")
        .then()
        .statusCode(200)
        .header("Access-Control-Allow-Credentials", nullValue());
  }
}
