package vn.synera;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ContextTest {
  @Test
  void startsWithMigrationsAndBootstrap() {}
}
