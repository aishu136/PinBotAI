package com.example.starter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.VerticleBase;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
//public class MainVerticle  extends AbstractVerticle  {
//
//	  public void start(Promise<Void> startPromise) {
//
//	    // Create a router to handle routes
//	    Router router = Router.router(vertx);
//
//	    // Allow POST bodies
//	    router.route().handler(BodyHandler.create());
//
//	    // Example bot endpoint
//	    router.post("/bot").handler(ctx -> {
//	      // Get user message from body JSON
//	    
//	    	String userMessage = ctx.body().asJsonObject().getString("message");
//
//
//	      // Generate a simple bot reply
//	      String reply = getBotReply(userMessage);
//
//	      ctx.response()
//	        .putHeader("Content-Type", "application/json")
//	        .end("{\"reply\":\"" + reply + "\"}");
//	    });
//
//	    // Start HTTP server
//	    vertx.createHttpServer()
//	      .requestHandler(router)
//	      .listen(8888)
//	      .onSuccess(server -> {
//	        System.out.println("🤖 Bot server started on http://localhost:8888");
//	        startPromise.complete();
//	      })
//	      .onFailure(startPromise::fail);
//	  }
//
//	  // Simple bot logic (you can make it smarter)
//	  private String getBotReply(String message) {
//	    if (message == null || message.isBlank()) {
//	      return "Hi! Please say something.";
//	    }
//	    String lower = message.toLowerCase();
//	    if (lower.contains("hello") || lower.contains("hi")) {
//	      return "Hello! How can I help you today?";
//	    } else if (lower.contains("bye")) {
//	      return "Goodbye! Have a great day!";
//	    } else {
//	      return "I'm just a simple Vert.x bot. You said: " + message;
//	    }
//	  }
//}

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;

import io.vertx.ext.web.RoutingContext;
import io.vertx.core.Handler;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AmazonPinterestGeminiAuto extends AbstractVerticle {

    private static final Logger logger = Logger.getLogger(AmazonPinterestGeminiAuto.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();
      private final String GEMINI_API_KEY = "YOUR_GEMINI_API_KEY";
    private final String PINTEREST_ACCESS_TOKEN = "YOUR_PINTEREST_ACCESS_TOKEN";
    private final String PINTEREST_BOARD_ID = "YOUR_PINTEREST_BOARD_ID";
    private final String AMAZON_ASSOCIATE_TAG = "YOUR_AMAZON_ASSOCIATE_TAG";

    private final String FIXED_QUERY = "Kitchen & Dining";

    public static class Product {
        public String title;
        public String price;
        public String link;
        public String imageUrl;
        public String aiTitle;
        public String aiDescription;
        public String pinUrl;

        public Product(String title, String price, String link, String imageUrl) {
            this.title = title;
            this.price = price;
            this.link = link;
            this.imageUrl = imageUrl;
        }
    }

    @Override
    public void start() {
        logger.info("Starting AmazonPinterestGeminiAuto Verticle...");

        Router router = Router.router(vertx);
        router.get("/generate-pins").handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext ctx) {
                logger.info("Received request to /generate-pins");
                generatePins(ctx);
            }
        });

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(8888)
                .onSuccess(server -> logger.info("🚀 Server running at http://localhost:8888/generate-pins"))
                .onFailure(err -> logger.log(Level.SEVERE, "Failed to start server", err));
    }

    private void generatePins(RoutingContext ctx) {
        logger.info("Starting pin generation process...");
        vertx.executeBlocking(new java.util.concurrent.Callable<List<Product>>() {
            @Override
            public List<Product> call() throws Exception {
                logger.info("Scraping Amazon for query: " + FIXED_QUERY);
                List<Product> products = scrapeAmazon(FIXED_QUERY);
                if (products.isEmpty()) {
                    logger.warning("No products found from Amazon scrape.");
                    return Collections.emptyList();
                }

                List<Product> topProducts = products.subList(0, Math.min(5, products.size()));
                logger.info("Processing top " + topProducts.size() + " products...");

                for (Product product : topProducts) {
                    if (!product.link.contains("?")) {
                        product.link = product.link + "?tag=" + AMAZON_ASSOCIATE_TAG;
                    } else {
                        product.link = product.link + "&tag=" + AMAZON_ASSOCIATE_TAG;
                    }
                    logger.info("Updated product link with associate tag: " + product.link);

                    String prompt = "Generate a catchy Pinterest Pin title and description for the following product:\n"
                            + "Product: " + product.title + "\nPrice: " + product.price + "\nLink: " + product.link;

                    logger.info("Calling Gemini AI for product: " + product.title);
                    String aiText = callGeminiAI(prompt);
                    if (aiText != null && !aiText.isEmpty()) {
                        String[] parts = aiText.split("\n", 2);
                        product.aiTitle = parts[0].replace("Title:", "").trim();
                        product.aiDescription = parts.length > 1 ? parts[1].replace("Description:", "").trim() : product.aiTitle;
                        logger.info("AI generated title: " + product.aiTitle + ", description: " + product.aiDescription);
                    } else {
                        logger.warning("Gemini AI returned empty result for product: " + product.title);
                        product.aiTitle = product.title;
                        product.aiDescription = product.title;
                    }

                    logger.info("Creating Pinterest pin for product: " + product.title);
                    product.pinUrl = createPinterestPin(product);
                    logger.info("Pinterest Pin URL: " + product.pinUrl);
                }

                return topProducts;
            }
        }, false).onSuccess(new Handler<List<Product>>() {
            @Override
            public void handle(List<Product> result) {
                try {
                    logger.info("Successfully generated pins, returning response.");
                    ctx.response().putHeader("Content-Type", "application/json")
                            .end(mapper.writeValueAsString(result));
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error serializing response", e);
                    ctx.response().setStatusCode(500).end(e.getMessage());
                }
            }
        }).onFailure(new Handler<Throwable>() {
            @Override
            public void handle(Throwable err) {
                logger.log(Level.SEVERE, "Error generating pins", err);
                ctx.response().setStatusCode(500).end(err.getMessage());
            }
        });
    }

    // private List<Product> scrapeAmazon(String query) {
    //     List<Product> products = new ArrayList<>();
    //     try (Playwright playwright = Playwright.create()) {
    //         Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    //         Page page = browser.newPage();
    //         page.navigate("https://www.amazon.com");
    //         page.locator("#twotabsearchtextbox").fill(query);
    //         page.locator("#nav-search-submit-button").click();
    //         page.waitForSelector("div.s-main-slot");

    //         logger.info("Scraping Amazon search results...");
    //         List<ElementHandle> items = page.querySelectorAll("div.s-main-slot div[data-component-type='s-search-result']");
    //         for (ElementHandle item : items.subList(0, Math.min(5, items.size()))) {
    //             String title = Optional.ofNullable(item.querySelector("a-size-large product-title-word-break"))
    //                     .map(ElementHandle::innerText).orElse("N/A");
    //             String price = Optional.ofNullable(item.querySelector("span.a-price-whole"))
    //                     .map(ElementHandle::innerText).orElse("N/A");
    //             String link = Optional.ofNullable(item.querySelector("h2 a"))
    //                     .map(e -> e.getAttribute("href")).orElse("");
    //             String imageUrl = Optional.ofNullable(item.querySelector("img.s-image"))
    //                     .map(e -> e.getAttribute("src")).orElse("");
    //             if (!link.isEmpty() && !link.startsWith("http")) link = "https://www.amazon.in" + link;
    //             products.add(new Product(title, price, link, imageUrl));
    //             logger.info("Scraped product: " + title + ", price: " + price + ", link: " + link);
    //         }

    //         browser.close();
    //     } catch (Exception e) {
    //         logger.log(Level.SEVERE, "Error scraping Amazon", e);
    //     }
    //     return products;
    // }

     
public List<Product> scrapeAmazon(String query) {
    List<Product> products = new ArrayList<>();

    try (Playwright playwright = Playwright.create()) {
        Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setSlowMo(50)
        );

        BrowserContext context = browser.newContext(
                new Browser.NewContextOptions()
                        .setViewportSize(1280, 800)
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/120.0.0.0 Safari/537.36")
        );

        Page page = context.newPage();

        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        page.navigate("https://www.amazon.com/s?k=" + encodedQuery,
                new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.NETWORKIDLE)
                        .setTimeout(60000) // longer timeout
        );

        // Accept cookies if any
        try {
            Locator cookie = page.locator("input#sp-cc-accept, input[name='accept']");
            if (cookie.isVisible()) cookie.click();
        } catch (Exception ignore) {}

        // Wait for search results
        page.waitForSelector("div.s-main-slot div[data-component-type='s-search-result']",
                new Page.WaitForSelectorOptions().setTimeout(60000));

        // Scroll to load lazy images/prices
        for (int i = 0; i < 5; i++) {
            page.evaluate("window.scrollBy(0, 1000)");
            page.waitForTimeout(1000);
        }

        List<ElementHandle> items = page.querySelectorAll(
                "div.s-main-slot div[data-component-type='s-search-result']");

        for (ElementHandle item : items.subList(0, Math.min(15, items.size()))) {
            // Title
            String title = Optional.ofNullable(item.querySelector("h2 span"))
                    .map(ElementHandle::innerText)
                    .orElse("N/A");

            // Price
            String price = Optional.ofNullable(item.querySelector(".a-price > .a-offscreen"))
                    .map(ElementHandle::innerText)
                    .orElse("N/A");

            // Link
            String link = Optional.ofNullable(item.querySelector("h2 a"))
                    .map(e -> e.getAttribute("href"))
                    .orElse("");
            if (!link.isEmpty() && !link.startsWith("http")) link = "https://www.amazon.com" + link;

            // Image
            String imageUrl = Optional.ofNullable(item.querySelector("img.s-image"))
                    .map(e -> {
                        String src = e.getAttribute("data-src");
                        if (src == null || src.isEmpty()) src = e.getAttribute("src");
                        return src != null ? src : "";
                    }).orElse("");

            products.add(new Product(title, price, link, imageUrl));
            logger.info("Scraped: " + title + " | " + price + " | " + link);
        }

        browser.close();
    } catch (Exception e) {
        logger.log(Level.SEVERE, "Error scraping Amazon", e);
    }

    if (products.isEmpty()) {
        logger.warning("No products found from Amazon scrape.");
    }
    return products;
}





    private String callGeminiAI(String prompt) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            Map<String, Object> body = Map.of(
                    "prompt", prompt,
                    "max_tokens", 150
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("https://gemini.googleapis.com/v1/generateText"))
                    .header("Authorization", "Bearer " + GEMINI_API_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String result = mapper.readTree(response.body()).get("output").get(0).get("content").asText();
            logger.info("Gemini AI response: " + result);
            return result;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error calling Gemini AI", e);
            return null;
        }
    }

    private String createPinterestPin(Product product) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            Map<String, Object> body = new HashMap<>();
            body.put("board", PINTEREST_BOARD_ID);
            body.put("note", product.aiDescription);
            body.put("link", product.link);
            body.put("image_url", product.imageUrl);
            body.put("title", product.aiTitle);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("https://api.pinterest.com/v1/pins/"))
                    .header("Authorization", "Bearer " + PINTEREST_ACCESS_TOKEN)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String pinId = mapper.readTree(response.body()).get("data").get("id").asText();
            logger.info("Pinterest pin created with ID: " + pinId);
            return "https://www.pinterest.com/pin/" + pinId;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error creating Pinterest pin for product: " + product.title, e);
        }
        return null;
    }
}

