//package com.example.starter;
//
//import io.vertx.core.Vertx;
//import io.vertx.core.json.JsonObject;
//import io.vertx.ext.web.client.WebClient;
//import io.vertx.junit5.VertxExtension;
//import io.vertx.junit5.VertxTestContext;
//import org.junit.jupiter.api.BeforeAll;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//@ExtendWith(VertxExtension.class)
//public class MainVerticleTest {
//
//  private static WebClient client;
//
//  @BeforeAll
//  static void prepareClient(Vertx vertx) {
//    // Create a WebClient before tests
//    client = WebClient.create(vertx);
//  }
//
//  @Test
//  void testBotEndpoint(Vertx vertx, VertxTestContext testContext) {
//    vertx.deployVerticle(new MainVerticle())   // <— Future in Vert.x 5
//      .onComplete(ar -> {
//        if (ar.failed()) {
//          testContext.failNow(ar.cause());
//          return;
//        }
//
//        JsonObject payload = new JsonObject().put("message", "hello");
//
//        client.post(8888, "localhost", "/bot")
//          .sendJsonObject(payload)
//          .onComplete(resp -> {
//            if (resp.failed()) {
//              testContext.failNow(resp.cause());
//              return;
//            }
//
//            String body = resp.result().bodyAsString();
//            assertTrue(body.contains("Hello"), "Bot reply should contain Hello");
//            testContext.completeNow();
//          });
//      });
//  }
//
//  @Test
//  void testBotEmptyMessage(Vertx vertx, VertxTestContext testContext) {
//    vertx.deployVerticle(new MainVerticle())
//      .onComplete(ar -> {
//        if (ar.failed()) {
//          testContext.failNow(ar.cause());
//          return;
//        }
//
//        JsonObject payload = new JsonObject().put("message", "");
//
//        client.post(8888, "localhost", "/bot")
//          .sendJsonObject(payload)
//          .onComplete(resp -> {
//            if (resp.failed()) {
//              testContext.failNow(resp.cause());
//              return;
//            }
//
//            String body = resp.result().bodyAsString();
//            assertTrue(body.contains("Hi! Please say something."),
//              "Bot reply should ask user to say something");
//            testContext.completeNow();
//          });
//      });
//  }
//}
