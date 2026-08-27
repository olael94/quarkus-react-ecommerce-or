package org.acme;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

// Mirrors this app's %prod-only Basic Auth gate on /q/swagger-ui and /q/openapi (see
// application.properties) under the test profile, so it can be verified live without an
// actual production deployment - same technique as StrictRateLimitTestProfile.
public class DocsAuthTestProfile implements QuarkusTestProfile {
  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of(
        "quarkus.http.auth.basic", "true",
        "quarkus.security.users.embedded.enabled", "true",
        "quarkus.security.users.embedded.plain-text", "true",
        "quarkus.security.users.embedded.users.teamdocs", "test-team-password",
        "quarkus.security.users.embedded.roles.teamdocs", "docs-viewer",
        "quarkus.http.auth.policy.docs-policy.roles-allowed", "docs-viewer",
        "quarkus.http.auth.permission.docs.paths", "/q/swagger-ui*,/q/openapi*",
        "quarkus.http.auth.permission.docs.policy", "docs-policy",
        "quarkus.swagger-ui.always-include", "true");
  }
}
