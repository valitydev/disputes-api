package dev.vality.disputes.auth.utils;

import io.jsonwebtoken.Jwts;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.PrivateKey;
import java.time.Instant;
import java.util.UUID;

public class JwtTokenBuilder {

    private static final String DEFAULT_USERNAME = "Darth Vader";
    private static final String DEFAULT_EMAIL = "darkside-the-best@mail.com";

    private final String userId;
    private final String username;
    private final String email;
    private final PrivateKey privateKey;

    public JwtTokenBuilder(PrivateKey privateKey) {
        this(UUID.randomUUID().toString(), DEFAULT_USERNAME, DEFAULT_EMAIL, privateKey);
    }

    public JwtTokenBuilder(String userId, String username, String email, PrivateKey privateKey) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.privateKey = privateKey;
    }

    public String generateJwtWithRoles(String... roles) {
        long iat = Instant.now().getEpochSecond();
        long exp = iat + 60 * 10;
        return generateJwtWithRoles(iat, exp, roles);
    }

    public String generateJwtWithRoles(long iat, long exp, String... roles) {
        String payload;
        try {
            payload = new JSONObject()
                    .put("jti", UUID.randomUUID().toString())
                    .put("exp", exp)
                    .put("nbf", "0")
                    .put("iat", iat)
                    .put("aud", "private-api")
                    .put("sub", userId)
                    .put("typ", "Bearer")
                    .put("azp", "private-api")
                    .put("resource_access", new JSONObject()
                            .put("common-api", new JSONObject()
                                    .put("roles", new JSONArray(roles))))
                    .put("preferred_username", username)
                    .put("email", email).toString();
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }

        return Jwts.builder()
                .content(payload)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }
}
