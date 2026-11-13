package vn.synera.config;

import java.nio.file.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalStopSignal {
  private final ConfigurableApplicationContext context;

  public LocalStopSignal(ConfigurableApplicationContext context) {
    this.context = context;
  }

  @Scheduled(fixedDelay = 1000)
  public void poll() throws java.io.IOException {
    if (Files.deleteIfExists(Path.of(".data/runtime/stop.request")))
      Thread.ofPlatform()
          .name("local-graceful-stop")
          .start(
              () -> {
                context.close();
              });
  }
}
