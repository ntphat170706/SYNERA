package vn.synera.config;

import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.ApiResponse;

@RestController
public class PublicConfigController {
  private final Environment environment;

  public PublicConfigController(Environment environment) {
    this.environment = environment;
  }

  @GetMapping("/api/v1/public/config")
  public ApiResponse<?> config() {
    return ApiResponse.ok(
        Map.of(
            "localMode",
            environment.acceptsProfiles(Profiles.of("local", "test")),
            "application",
            "SYNERA",
            "version",
            "1.0.0"));
  }
}
