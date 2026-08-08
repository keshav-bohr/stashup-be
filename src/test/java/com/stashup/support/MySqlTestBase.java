package com.stashup.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base class for every test that touches the database.
 *
 * <p>Always runs against a real MySQL 8.4, never H2. H2's MySQL compatibility mode diverges on
 * index behaviour, date handling, and constraint semantics — exactly the things this schema
 * depends on — so a green H2 suite would give false confidence about Flyway migrations that had
 * never run against the real engine.
 *
 * <p>Two ways to get that engine:
 *
 * <ul>
 *   <li><b>Testcontainers</b> when a Docker daemon is available. This is the CI path and the
 *       default, because it pins the exact MySQL version and leaves nothing on the machine.
 *   <li><b>An external MySQL</b> named by {@code STASHUP_TEST_DB_URL} when Docker is not
 *       running. Developers with a local 8.4 should not be blocked from running the suite, and
 *       an external real MySQL tests the same things a containerised one does.
 * </ul>
 *
 * <p>If neither is available the suite fails rather than silently degrading to an in-memory
 * database — a passing run must mean the migrations actually ran on MySQL.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
public abstract class MySqlTestBase {

  private static final boolean DOCKER_AVAILABLE = isDockerAvailable();

  private static final String EXTERNAL_URL = System.getenv("STASHUP_TEST_DB_URL");
  private static final String EXTERNAL_USER =
      System.getenv().getOrDefault("STASHUP_TEST_DB_USERNAME", "root");
  private static final String EXTERNAL_PASSWORD =
      System.getenv().getOrDefault("STASHUP_TEST_DB_PASSWORD", "");

  private static final MySQLContainer<?> MYSQL = DOCKER_AVAILABLE ? startContainer() : null;

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    if (MYSQL != null) {
      registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
      registry.add("spring.datasource.username", MYSQL::getUsername);
      registry.add("spring.datasource.password", MYSQL::getPassword);
      return;
    }
    if (EXTERNAL_URL == null || EXTERNAL_URL.isBlank()) {
      throw new IllegalStateException(
          "No MySQL available for integration tests. Start Docker, or set STASHUP_TEST_DB_URL "
              + "to a MySQL 8.4 instance. H2 is deliberately not offered as a fallback.");
    }
    registry.add("spring.datasource.url", () -> EXTERNAL_URL);
    registry.add("spring.datasource.username", () -> EXTERNAL_USER);
    registry.add("spring.datasource.password", () -> EXTERNAL_PASSWORD);
    // The external database is reused across runs, so each run starts from a known schema.
    registry.add("spring.flyway.clean-on-validation-error", () -> "true");
  }

  @SuppressWarnings("resource") // Ryuk reaps the container; closing it here would break sharing.
  private static MySQLContainer<?> startContainer() {
    MySQLContainer<?> container = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("stashup")
        .withUsername("stashup")
        .withPassword("stashup");
    container.start();
    return container;
  }

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (RuntimeException ex) {
      return false;
    }
  }
}
