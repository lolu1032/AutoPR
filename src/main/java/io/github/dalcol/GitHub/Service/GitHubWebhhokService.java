package io.github.dalcol.GitHub.Service;

import io.github.dalcol.GitHub.dto.PullRequestRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GitHubWebhhokService {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final String BASEURL = "https://api.github.com";

    public ResponseEntity<Object> registerWebhook(
            Authentication authentication,
            String owner,
            String repo
    ) {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName()
        );

        String accessToken = client.getAccessToken().getTokenValue();

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/vnd.github+json");

        String secret = generateSecretKey();
        System.out.println("Generated Webhook secret: " + secret);

        Map<String, Object> config = new HashMap<>();
        config.put("url", "https://f46bb24c8aaf.ngrok-free.app/api/webhook/github");
        config.put("content_type", "json");
        config.put("secret", secret);
        config.put("insecure_ssl", "0");

        Map<String, Object> body = new HashMap<>();
        body.put("name", "web");
        body.put("active", true);
        body.put("events", List.of("push"));
        body.put("config", config);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        String url = BASEURL + "/repos/" + owner + "/" + repo + "/hooks";

        ResponseEntity<Object> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Object.class
        );

        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    }

    public ResponseEntity<Object> createPullRequest(
            String token,
            String owner,
            String repo,
            PullRequestRequest request) {

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/vnd.github+json");

        // 1️⃣ 먼저 PR 있는지 확인
        String checkUrl = BASEURL + "/repos/" + owner + "/" + repo + "/pulls?head=" + owner + ":" + request.getHead()
                + "&base=" + request.getBase();
        ResponseEntity<Object[]> existingPrs = restTemplate.exchange(
                checkUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Object[].class
        );

        if (existingPrs.getBody() != null && existingPrs.getBody().length > 0) {
            System.out.println("이미 PR 존재: " + request.getHead());
            return ResponseEntity.ok("이미 PR이 존재합니다.");
        }

        // 2️⃣ PR 생성
        Map<String, Object> body = new HashMap<>();
        body.put("title", request.getTitle());
        body.put("body", request.getBody());
        body.put("head", request.getHead());
        body.put("base", request.getBase());
        if (request.getReviewers() != null && !request.getReviewers().isEmpty()) {
            body.put("reviewers", request.getReviewers());
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String url = BASEURL + "/repos/" + owner + "/" + repo + "/pulls";

        ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.POST, entity, Object.class);
        System.out.println("PR 생성 결과: " + response.getStatusCode());

        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    }

    private String generateSecretKey() {
        byte[] bytes = new byte[32]; // 256-bit key
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
