package io.github.dalcol.GitHub.Controller;

import io.github.dalcol.GitHub.Service.GitHubService;
import io.github.dalcol.GitHub.dto.PullRequestRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/repos")
public class RepoController {

    private final GitHubService service;

    /**
     * repo read
     */
    @GetMapping
    public ResponseEntity<List> getUserRepos(Authentication authentication) {
        return service.getUserRepos(authentication);
    }

    /**
     * PR read
     */
    @GetMapping("/{owner}/{repo}/pulls")
    public ResponseEntity<List> getRepoPulls(
            Authentication authentication,
            @PathVariable String owner,
            @PathVariable String repo
    ) {
        return service.getRepoPulls(authentication,owner,repo);
    }

    /**
     * PR create
     */
    @PostMapping("/{owner}/{repo}/pulls")
    public ResponseEntity<Object> createPullRequest(
            Authentication authentication,
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestBody PullRequestRequest request
    ) {
        return service.createPullRequest(authentication, owner, repo, request);
    }

}
