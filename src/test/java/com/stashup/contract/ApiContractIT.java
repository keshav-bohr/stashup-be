package com.stashup.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.stashup.support.MySqlTestBase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The HTTP contract: status codes, the shared error envelope, and authentication.
 *
 * <p>These go through the real filter chain, so they exercise what the service-level tests cannot
 * — security defaults, correlation IDs, request validation, and JSON shape.
 */
@AutoConfigureMockMvc
class ApiContractIT extends MySqlTestBase {

  private static final String PASSWORD = "correct-horse-battery-staple";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;

  @Test
  @DisplayName("every non-auth endpoint requires authentication")
  void endpointsDenyByDefault() throws Exception {
    mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/entries")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/categories")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/scores/2026-03")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/comparison/2026-03")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/friends")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("health is reachable without credentials, because probes have none")
  void healthIsPublic() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("registration returns 201 and a token pair, and echoes a correlation id")
  void registerReturnsTokens() throws Exception {
    register(uniqueEmail())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").isNotEmpty())
        .andExpect(jsonPath("$.expiresIn").value(900))
        .andExpect(header().exists("X-Correlation-Id"));
  }

  @Test
  @DisplayName("a validation failure uses the shared problem envelope")
  void validationFailureUsesProblemEnvelope() throws Exception {
    mvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"not-an-email","password":"short","displayName":"",
                 "baseCurrency":"inr","timezone":"Asia/Kolkata"}"""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty())
        .andExpect(jsonPath("$.errors").isArray());
  }

  @Test
  @DisplayName("bad credentials return 401 with a stable code")
  void badCredentialsReturnProblem() throws Exception {
    mvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("email", uniqueEmail(), "password", PASSWORD)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  @DisplayName("the full authenticated journey: profile, category, entry, summary, score")
  void authenticatedJourney() throws Exception {
    String token = registeredToken();

    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseCurrency").value("INR"));

    mvc.perform(patch("/api/v1/me")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("displayName", "Renamed")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Renamed"));

    String categoryId = firstCategoryId(token, "INCOME");

    mvc.perform(post("/api/v1/entries")
            .header("Authorization", "Bearer " + token)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"entryType":"INCOME","amount":{"amountMinor":100000,"currency":"INR"},
                 "entryDate":"2026-03-01","categoryId":"%s"}""".formatted(categoryId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.amount.amountMinor").value(100000))
        .andExpect(jsonPath("$.amount.currency").value("INR"));

    mvc.perform(get("/api/v1/entries").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray());

    mvc.perform(get("/api/v1/summaries/2026-03").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.period").value("2026-03"));

    mvc.perform(get("/api/v1/scores/2026-03").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.score").value(0))
        .andExpect(jsonPath("$.completeness").value("COMPLETE"))
        .andExpect(jsonPath("$.inputs.moneyIn.amountMinor").value(100000));

    mvc.perform(get("/api/v1/comparison/2026-03").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.streakLookbackMonths").value(24))
        .andExpect(jsonPath("$.ranked").isArray());
  }

  @Test
  @DisplayName("a malformed period is a 400 with the period code, not a 500")
  void malformedPeriodRejected() throws Exception {
    String token = registeredToken();

    mvc.perform(get("/api/v1/scores/not-a-period").header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
  }

  @Test
  @DisplayName("an over-limit page size is rejected rather than silently clamped")
  void overLimitPageSizeRejected() throws Exception {
    String token = registeredToken();

    mvc.perform(get("/api/v1/entries?limit=5000").header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PAGE_SIZE_EXCEEDED"));
  }

  @Test
  @DisplayName("a zero amount is rejected with its specific code")
  void zeroAmountRejected() throws Exception {
    String token = registeredToken();
    String categoryId = firstCategoryId(token, "EXPENSE");

    mvc.perform(post("/api/v1/entries")
            .header("Authorization", "Bearer " + token)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"entryType":"EXPENSE","amount":{"amountMinor":0,"currency":"INR"},
                 "entryDate":"2026-03-01","categoryId":"%s"}""".formatted(categoryId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("replaying an idempotency key creates one entry; reusing it with a new body is 409")
  void idempotencyBehaviour() throws Exception {
    String token = registeredToken();
    String categoryId = firstCategoryId(token, "EXPENSE");
    String key = UUID.randomUUID().toString();
    String payload = """
        {"entryType":"EXPENSE","amount":{"amountMinor":1200,"currency":"INR"},
         "entryDate":"2026-03-01","categoryId":"%s"}""".formatted(categoryId);

    mvc.perform(entryPost(token, key, payload)).andExpect(status().isCreated());
    mvc.perform(entryPost(token, key, payload)).andExpect(status().isCreated());

    String different = payload.replace("1200", "9900");
    mvc.perform(entryPost(token, key, different))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
  }

  @Test
  @DisplayName("a friend request to oneself is rejected; the friends list starts empty")
  void friendshipEndpoints() throws Exception {
    String token = registeredToken();
    String myId = json.readTree(
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getContentAsString())
        .get("id").asString();

    mvc.perform(post("/api/v1/friend-requests")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("userId", myId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SELF_REQUEST"));

    mvc.perform(get("/api/v1/friends").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/friend-requests?direction=INCOMING")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("reconciliation on a period with no data is a 404")
  void reconciliationOnEmptyPeriod() throws Exception {
    String token = registeredToken();

    mvc.perform(get("/api/v1/periods/2025-01/reconciliation")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));

    mvc.perform(put("/api/v1/periods/2025/drawdown-acknowledgment")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
  }

  @Test
  @DisplayName("another user's entry is reported as not found, never as forbidden")
  void foreignEntryIsNotFound() throws Exception {
    String token = registeredToken();

    mvc.perform(get("/api/v1/entries/" + UUID.randomUUID())
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("a category can be created and then deleted")
  void categoryLifecycle() throws Exception {
    String token = registeredToken();

    String created = mvc.perform(post("/api/v1/categories")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"entryType":"EXPENSE","name":"Contract Test Category"}"""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.system").value(false))
        .andReturn().getResponse().getContentAsString();

    String id = json.readTree(created).get("id").asString();

    mvc.perform(delete("/api/v1/categories/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
  }

  private org.springframework.test.web.servlet.RequestBuilder entryPost(
      String token, String key, String payload) {
    return post("/api/v1/entries")
        .header("Authorization", "Bearer " + token)
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload);
  }

  private String firstCategoryId(String token, String entryType) throws Exception {
    String body = mvc.perform(get("/api/v1/categories?entryType=" + entryType)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    JsonNode first = json.readTree(body).get(0);
    return first.get("id").asString();
  }

  private String registeredToken() throws Exception {
    String body = register(uniqueEmail()).andReturn().getResponse().getContentAsString();
    return json.readTree(body).get("accessToken").asString();
  }

  private ResultActions register(String email) throws Exception {
    return mvc.perform(post("/api/v1/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"email":"%s","password":"%s","displayName":"Contract User",
             "baseCurrency":"INR","timezone":"Asia/Kolkata"}""".formatted(email, PASSWORD)));
  }

  private static String body(String... keyValues) {
    StringBuilder builder = new StringBuilder("{");
    for (int i = 0; i < keyValues.length; i += 2) {
      if (i > 0) {
        builder.append(',');
      }
      builder.append('"').append(keyValues[i]).append("\":\"").append(keyValues[i + 1])
          .append('"');
    }
    return builder.append('}').toString();
  }

  private static String uniqueEmail() {
    return "contract-" + UUID.randomUUID() + "@example.com";
  }
}
