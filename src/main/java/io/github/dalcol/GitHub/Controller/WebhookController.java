package io.github.dalcol.GitHub.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dalcol.GitHub.Service.GitHubJwtService;
import io.github.dalcol.GitHub.Service.GitHubWebhhokService;
import io.github.dalcol.GitHub.Service.InstallationTokenService;
import io.github.dalcol.GitHub.dto.PullRequestRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final GitHubWebhhokService service;
    private final InstallationTokenService installationTokenService;
    private final GitHubJwtService jwtService;

    @PostMapping("/github")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader("X-GitHub-Event") String event,
            @RequestBody Map<String, Object> payload) throws Exception {

        // push 이벤트가 아니면 무시
        if (!"push".equals(event)) {
            return ResponseEntity.ok("Not a push event, skipping PR creation.");
        }

        Map<String, Object> repository = (Map<String, Object>) payload.get("repository");
        String owner = ((Map<String, String>) repository.get("owner")).get("login");
        String repo = (String) repository.get("name");
        String defaultBranch = (String) repository.get("default_branch");

        Object refObj = payload.get("ref");
        if (refObj == null) {
            return ResponseEntity.ok("No branch info in payload, skipping PR creation.");
        }
        String branch = ((String) refObj).replace("refs/heads/", "");

        // installation_id 가져오기
        Map<String, Object> installation = (Map<String, Object>) payload.get("installation");
        String installationId;
        if (installation != null && installation.get("id") != null) {
            installationId = String.valueOf(installation.get("id"));
        } else {
            installationId = fetchInstallationId(owner);
        }

        String installationToken = installationTokenService.createInstallationToken(installationId);

        PullRequestRequest prRequest = new PullRequestRequest(
                "[Auto PR] " + branch + " -> " + defaultBranch,
                "자동 생성된 Pull Request 입니다.",
                branch,
                defaultBranch
        );

        try {
            service.createPullRequest(installationToken, owner, repo, prRequest);
        } catch (Exception e) {
            // 예외 안전 처리
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("PR 생성 중 예외: " + e.getMessage());
        }

        return ResponseEntity.ok("PR 요청 완료");
    }


    @PostMapping("/register")
    public ResponseEntity<Object> registerWebhook(
            Authentication authentication,
            @RequestParam String owner,
            @RequestParam String repo,
            @RequestParam String branch
    ) {
        return service.registerWebhook(authentication, owner, repo);
    }
    private String fetchInstallationId(String owner) throws Exception {
        String jwt = jwtService.generateJwtToken();
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        headers.setAccept(MediaType.parseMediaTypes("application/vnd.github+json"));

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map[]> response = restTemplate.exchange(
                "https://api.github.com/app/installations",
                HttpMethod.GET,
                entity,
                Map[].class
        );

        // owner에 맞는 installation 찾기
        for (Map<String, Object> inst : response.getBody()) {
            Map<String, String> account = (Map<String, String>) inst.get("account");
            if (account.get("login").equals(owner)) {
                return String.valueOf(inst.get("id"));
            }
        }

        throw new RuntimeException("설치된 App을 찾을 수 없습니다: " + owner);
    }

}
