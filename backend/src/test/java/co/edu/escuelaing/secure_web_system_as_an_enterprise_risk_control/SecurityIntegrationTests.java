package co.edu.escuelaing.secure_web_system_as_an_enterprise_risk_control;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityIntegrationTests {
  static final RSAKey key;
  static final HttpServer jwks;
  static {
    try {
      key = new RSAKeyGenerator(2048).keyID("test-key").generate();
      jwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      jwks.createContext("/jwks", exchange -> {
        byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var out = exchange.getResponseBody()) { out.write(body); }
      });
      jwks.start();
    } catch (Exception e) { throw new ExceptionInInitializerError(e); }
  }
  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "https://issuer.example/");
    registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:" + jwks.getAddress().getPort() + "/jwks");
  }
  @LocalServerPort int port;
  final HttpClient client = HttpClient.newHttpClient();
  String token(String issuer, String audience, String scope, Instant expiry, RSAKey signer) throws Exception {
    var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject("test-user")
        .issueTime(Date.from(Instant.now().minusSeconds(600))).expirationTime(Date.from(expiry)).claim("scope", scope).build();
    var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
    jwt.sign(new RSASSASigner(signer));
    return jwt.serialize();
  }
  String validToken(String scope) throws Exception {
    return token("https://issuer.example/", "https://enterprise-risk-api", scope, Instant.now().plusSeconds(300), key);
  }
  HttpResponse<String> get(String path, String token) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    if (token != null) request.header("Authorization", "Bearer " + token);
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }
  @Test void noTokenIsUnauthorized() throws Exception { assertEquals(401, get("/api/risk-status", null).statusCode()); }
  @Test void malformedTokenIsUnauthorized() throws Exception { assertEquals(401, get("/api/risk-status", "invalid").statusCode()); }
  @Test void validTokenSucceeds() throws Exception {
    var response = get("/api/risk-status", validToken("read:risk"));
    assertEquals(200, response.statusCode()); assertTrue(response.body().contains("test-user")); assertTrue(response.body().contains("protected"));
  }
  @Test void missingScopeIsForbidden() throws Exception { assertEquals(403, get("/api/risk-status", validToken("openid")).statusCode()); }
  @Test void wrongIssuerIsUnauthorized() throws Exception {
    assertEquals(401, get("/api/risk-status", token("https://wrong.example/", "https://enterprise-risk-api", "read:risk", Instant.now().plusSeconds(300), key)).statusCode());
  }
  @Test void wrongAudienceIsUnauthorized() throws Exception {
    assertEquals(401, get("/api/risk-status", token("https://issuer.example/", "other-api", "read:risk", Instant.now().plusSeconds(300), key)).statusCode());
  }
  @Test void expiredTokenIsUnauthorized() throws Exception {
    assertEquals(401, get("/api/risk-status", token("https://issuer.example/", "https://enterprise-risk-api", "read:risk", Instant.now().minusSeconds(180), key)).statusCode());
  }
  @Test void wrongSignatureIsUnauthorized() throws Exception {
    assertEquals(401, get("/api/risk-status", token("https://issuer.example/", "https://enterprise-risk-api", "read:risk", Instant.now().plusSeconds(300), new RSAKeyGenerator(2048).generate())).statusCode());
  }
  @Test void healthIsPublic() throws Exception { assertEquals(200, get("/actuator/health", null).statusCode()); }
  @Test void otherEndpointsAreDenied() throws Exception { assertEquals(403, get("/other", validToken("read:risk")).statusCode()); }
  @Test void allowedCorsPreflight() throws Exception { assertEquals(200, preflight("http://localhost:5173").statusCode()); }
  @Test void untrustedCorsPreflight() throws Exception { assertEquals(403, preflight("https://untrusted.example").statusCode()); }
  HttpResponse<String> preflight(String origin) throws Exception {
    return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/risk-status"))
        .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).header("Origin", origin)
        .header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "authorization").build(), HttpResponse.BodyHandlers.ofString());
  }
  @AfterAll static void stopJwks() { jwks.stop(0); }
}
