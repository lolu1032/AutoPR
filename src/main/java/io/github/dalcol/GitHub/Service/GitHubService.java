package io.github.dalcol.GitHub.Service;

import io.github.dalcol.GitHub.dto.PullRequestRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class GitHubService {
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final String BASEURL = "https://api.github.com";

    public ResponseEntity<List> getUserRepos(Authentication authentication) {
        String url = "/user/repos";
        return githubApi(authentication,url);
    }

    public ResponseEntity<List> getRepoPulls(
            Authentication authentication,
            String owner,
            String repo
    ) {
        String url = "/repos/" + owner + "/" + repo + "/pulls";
        return githubApi(authentication,url);
    }

    public ResponseEntity<Object> createPullRequest(
            Authentication authentication,
            String owner,
            String repo,
            PullRequestRequest request
    ) {
        String url = BASEURL + "/repos/" + owner + "/" + repo + "/pulls";

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName()
        );

        String accessToken = client.getAccessToken().getTokenValue();

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", "application/vnd.github+json");
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("title", request.getTitle());
        body.put("body", request.getBody());
        body.put("head", request.getHead());
        body.put("base", request.getBase());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Object> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Object.class
        );

        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    }

    private ResponseEntity<List> githubApi(Authentication authentication,String url) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName()
        );

        String accessToken = client.getAccessToken().getTokenValue();

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", "application/vnd.github+json");

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<List> response = restTemplate.exchange(
                BASEURL + url,
                HttpMethod.GET,
                entity,
                List.class
        );
        return ResponseEntity.ok(response.getBody());
    }
}