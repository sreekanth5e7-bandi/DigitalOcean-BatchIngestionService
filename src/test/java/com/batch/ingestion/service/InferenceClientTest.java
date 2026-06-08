package com.batch.ingestion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.batch.ingestion.config.BatchProperties;
import com.batch.ingestion.exception.RateLimitException;
import com.batch.ingestion.model.PromptItem;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.springframework.http.HttpStatus;

class InferenceClientTest {

  private BatchProperties properties;
  private MockRestServiceServer mockServer;
  private InferenceClient client;

  @BeforeEach
  void setUp() {
    properties = new BatchProperties();
    properties.setMockApiUrl("http://localhost/mock/infer");
    properties.setMaxRetries(3);
    properties.setInitialBackoffMs(1);
    properties.setMaxBackoffMs(10);

    RestClient.Builder restClientBuilder = RestClient.builder();
    mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    client = new InferenceClient(properties, restClientBuilder.build());
  }

  @Test
  void inferSucceedsOnFirstAttempt() throws InterruptedException {
    mockServer.expect(requestTo("http://localhost/mock/infer"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"response\":\"Hello\"}", MediaType.APPLICATION_JSON));

    var result = client.infer(new PromptItem("p1", "Say hello"));

    assertEquals("p1", result.getPromptId());
    assertEquals("Hello", result.getResponse());
    assertEquals(0, result.getRetries());
    mockServer.verify();
  }

  @Test
  void inferRetriesOn429ThenSucceeds() throws InterruptedException {
    mockServer.expect(requestTo("http://localhost/mock/infer"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
    mockServer.expect(requestTo("http://localhost/mock/infer"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
    mockServer.expect(requestTo("http://localhost/mock/infer"))
        .andRespond(withSuccess("{\"response\":\"Success\"}", MediaType.APPLICATION_JSON));

    var result = client.infer(new PromptItem("p2", "Retry me"));

    assertEquals("Success", result.getResponse());
    assertEquals(2, result.getRetries());
    mockServer.verify();
  }

  @Test
  void inferThrowsRateLimitErrorAfterMaxRetries() {
    for (int i = 0; i <= properties.getMaxRetries(); i++) {
      mockServer.expect(requestTo("http://localhost/mock/infer"))
          .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
    }

    assertThrows(RateLimitException.class,
        () -> client.infer(new PromptItem("p3", "Always blocked")));
    mockServer.verify();
  }

  @Test
  void backoffIncreasesExponentially() {
    long d0 = client.computeBackoff(0);
    long d1 = client.computeBackoff(1);
    long d3 = client.computeBackoff(3);

    assertTrue(d0 >= properties.getInitialBackoffMs());
    assertTrue(d1 >= d0);
    assertTrue(d3 <= properties.getMaxBackoffMs() + 3000);
  }
}
