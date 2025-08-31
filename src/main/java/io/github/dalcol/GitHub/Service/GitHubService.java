package io.github.dalcol.GitHub.Service;

import io.github.dalcol.GitHub.Service.Common.CommonGitHubApiClient;
import io.github.dalcol.GitHub.dto.PullRequestRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class GitHubService {
    final String BASEURL = "https://api.github.com";
    private final CommonGitHubApiClient commonGitHubApiClient;
    /**
     * Repo READ
     */
    public ResponseEntity<List> getUserRepos(Authentication authentication) {
        String url = "/user/repos";
        return commonGitHubApiClient.githubGetApi(authentication,url);
    }

    /**
     * PR READ
     */
    public ResponseEntity<List> getRepoPulls(
            Authentication authentication,
            String owner,
            String repo
    ) {
        String url = "/repos/" + owner + "/" + repo + "/pulls";
        return commonGitHubApiClient.githubGetApi(authentication,url);
    }

    /**
     * PR CREATE
     */
    public ResponseEntity<Object> createPullRequest(
            Authentication authentication,
            String owner,
            String repo,
            PullRequestRequest request
    ) {
        String url = BASEURL + "/repos/" + owner + "/" + repo + "/pulls";

        Map<String, Object> body = new HashMap<>();
        body.put("title", request.getTitle());
        body.put("body", request.getBody());
        body.put("head", request.getHead());
        body.put("base", request.getBase());

        return commonGitHubApiClient.githubPostApi(authentication,body,url);
    }

}