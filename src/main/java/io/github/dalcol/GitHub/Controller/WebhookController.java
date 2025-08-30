package io.github.dalcol.GitHub.Controller;

import io.github.dalcol.GitHub.Service.GitHubWebhokService;
import io.github.dalcol.GitHub.dto.WebHookDto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final GitHubWebhokService service;

    @PostMapping("/github")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader("X-GitHub-Event") String event,
            @RequestBody Payload payload
            ) throws Exception {

        // push 이벤트가 아니면 무시
        if (!"push".equals(event)) {
            return ResponseEntity.ok("Not a push event, skipping PR creation.");
        }

        try {
            service.handlePushEvent(payload);
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

}
