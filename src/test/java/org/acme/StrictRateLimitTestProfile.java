package org.acme;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

// Overrides the rate-limit ceilings down to something small enough to actually trip in a
// test, without affecting the rest of the suite's generous %test defaults.
public class StrictRateLimitTestProfile implements QuarkusTestProfile {
  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of(
        "app.rate-limit.register.max-attempts", "2",
        "app.rate-limit.password-reset-request.max-attempts", "2",
        "app.rate-limit.password-reset-confirm.max-attempts", "2");
  }
}
