package io.github.dalcol.GitHub.Service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
//@ConfigurationProperties(prefix = spring.sec)
public class GitHubJwtService {

    @Value("${spring.security.oauth2.client.provider.github.app.id}")
    private String appId;

    @Value("${spring.security.oauth2.client.provider.github.app.private-key}")
    private Resource privateKeyResource;

//     private key 파일(.pem) 읽기
    private PrivateKey getPrivateKey() throws Exception {
        try (InputStream is = privateKeyResource.getInputStream()) {
            String key = new String(is.readAllBytes())
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(key);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        }
    }

//     JWT 생성
    public String generateJwtToken() throws Exception {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(9 * 60); // 9분짜리 (최대 10분)

        return Jwts.builder()
                .setIssuer(appId) // iss = App ID
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(getPrivateKey(), SignatureAlgorithm.RS256)
                .compact();
    }

}
