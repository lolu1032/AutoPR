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

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class GitHubService {
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final String BASEURL = "https://api.github.com";

    /**
     * Repo READ
     * @param authentication
     * @return
     */
    public ResponseEntity<List> getUserRepos(Authentication authentication) {
        String url = "/user/repos";
        return githubApi(authentication,url);
    }

    /**
     * PR READ
     * @param authentication
     * @param owner
     * @param repo
     * @return
     */
    public ResponseEntity<List> getRepoPulls(
            Authentication authentication,
            String owner,
            String repo
    ) {
        String url = "/repos/" + owner + "/" + repo + "/pulls";
        return githubApi(authentication,url);
    }

    /**
     * PR CREATE
     * @param authentication
     * @param owner
     * @param repo
     * @param request
     * @return
     */
    public ResponseEntity<Object> createPullRequest(
            Authentication authentication,
            String owner,
            String repo,
            PullRequestRequest request
    ) {
        String url = BASEURL + "/repos/" + owner + "/" + repo + "/pulls";

        RestTemplate restTemplate = new RestTemplate();
        Map<String, Object> body = new HashMap<>();
        body.put("title", request.getTitle());
        body.put("body", request.getBody());
        body.put("head", request.getHead());
        body.put("base", request.getBase());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, header(authentication));
        ResponseEntity<Object> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Object.class
        );

        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    }

    /**
     *  공통 로직
     */
    private ResponseEntity<List> githubApi(Authentication authentication,String url) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<String> entity = new HttpEntity<>(header(authentication));
        ResponseEntity<List> response = restTemplate.exchange(
                BASEURL + url,
                HttpMethod.GET,
                entity,
                List.class
        );
        return ResponseEntity.ok(response.getBody());
    }
    private HttpHeaders header(Authentication authentication) {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName()
        );

        String accessToken = client.getAccessToken().getTokenValue();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", "application/vnd.github+json");
        return headers;
    }
}