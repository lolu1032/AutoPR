package io.github.dalcol.GitHub.Service;

import io.github.dalcol.GitHub.Service.Common.CommonGitHubApiClient;
import io.github.dalcol.GitHub.dto.ChangedFile;
import io.github.dalcol.GitHub.dto.PullRequestRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GitHubWebhhokService {

    private final GitHubJwtService jwtService;
    private final InstallationTokenService installationTokenService;
    private final String BASEURL = "https://api.github.com";
    private final CommonGitHubApiClient commonGitHubApiClient;
    private final GitHubPrService gitHubPrService;

    public ResponseEntity<Object> registerWebhook(
            Authentication authentication,
            String owner,
            String repo
    ) {

        String secret = generateSecretKey();
        System.out.println("Generated Webhook secret: " + secret);

        Map<String, Object> config = new HashMap<>();
        config.put("url", "https://38cc5368c7d7.ngrok-free.app/api/webhook/github");
        config.put("content_type", "json");
        config.put("secret", secret);
        config.put("insecure_ssl", "0");

        Map<String, Object> body = new HashMap<>();
        body.put("name", "web");
        body.put("active", true);
        body.put("events", List.of("push"));
        body.put("config", config);

        String url = BASEURL + "/repos/" + owner + "/" + repo + "/hooks";

        return commonGitHubApiClient.githubPostApi(authentication,body,url);
    }

    public void handlePushEvent(Map<String, Object> payload) throws Exception {
        Map<String, Object> repository = (Map<String, Object>) payload.get("repository");
        String owner = ((Map<String, String>) repository.get("owner")).get("login");
        String repo = (String) repository.get("name");
        String defaultBranch = (String) repository.get("default_branch");

        String branch = extractBranch(payload);

        // installation_id 가져오기
        Map<String, Object> installation = (Map<String, Object>) payload.get("installation");
        String installationId;
        if (installation != null && installation.get("id") != null) {
            installationId = String.valueOf(installation.get("id"));
        } else {
            installationId = fetchInstallationId(owner);
        }

        List<ChangedFile> changedFiles = fetchChangedFiles(owner, repo, defaultBranch, branch, installationId);


        String sonarProjectKey = owner + "_" + repo; // 예: GitHub repo 기준
        String sonarToken = System.getenv("SONAR_TOKEN"); // SonarCloud Personal Token

        gitHubPrService.createPullRequestWithDiff(
                installationId,
                owner,
                repo,
                branch,
                defaultBranch,
                changedFiles,
                sonarProjectKey,
                sonarToken
        );
    }

//    public ResponseEntity<Object> createPullRequest(
//            String token,
//            String owner,
//            String repo,
//            PullRequestRequest request) {
//
//        RestTemplate restTemplate = new RestTemplate();
//        HttpHeaders headers = new HttpHeaders();
//        headers.setBearerAuth(token);
//        headers.setContentType(MediaType.APPLICATION_JSON);
//        headers.set("Accept", "application/vnd.github+json");
//
//        String checkUrl = BASEURL + "/repos/" + owner + "/" + repo + "/pulls?head=" + owner + ":" + request.getHead()
//                + "&base=" + request.getBase();
//        ResponseEntity<Object[]> existingPrs = restTemplate.exchange(
//                checkUrl,
//                HttpMethod.GET,
//                new HttpEntity<>(headers),
//                Object[].class
//        );
//
//        if (existingPrs.getBody() != null && existingPrs.getBody().length > 0) {
//            System.out.println("이미 PR 존재: " + request.getHead());
//            return ResponseEntity.ok("이미 PR이 존재합니다.");
//        }
//
//        Map<String, Object> body = new HashMap<>();
//        body.put("title", request.getTitle());
//        body.put("body", request.getBody());
//        body.put("head", request.getHead());
//        body.put("base", request.getBase());
//        if (request.getReviewers() != null && !request.getReviewers().isEmpty()) {
//            body.put("reviewers", request.getReviewers());
//        }
//
//        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
//        String url = BASEURL + "/repos/" + owner + "/" + repo + "/pulls";
//
//        ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.POST, entity, Object.class);
//        System.out.println("PR 생성 결과: " + response.getStatusCode());
//
//        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
//    }

    private String extractBranch(Map<String, Object> payload) {
        Object refObj = payload.get("ref");
        if (refObj == null) throw new IllegalArgumentException("No branch info in payload");
        return ((String) refObj).replace("refs/heads/", "");
    }

    private List<ChangedFile> fetchChangedFiles(String owner, String repo, String base, String head, String installationId) throws Exception {
        String token = installationTokenService.createInstallationToken(installationId);

        String url = BASEURL + "/repos/" + owner + "/" + repo + "/compare/" + base + "..." + head;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        Map<String, Object> body = response.getBody();
        List<Map<String,Object>> files = (List<Map<String,Object>>) body.get("files");

        List<ChangedFile> changedFiles = new ArrayList<>();
        for (Map<String,Object> file : files) {
            String filename = (String) file.get("filename");
            String patch = (String) file.get("patch");
            changedFiles.add(new ChangedFile(filename, patch != null ? patch : ""));
        }
        return changedFiles;
    }

    private String fetchInstallationId(String owner) throws Exception {
        String jwt = jwtService.generateJwtToken();
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        headers.setAccept(MediaType.parseMediaTypes("application/vnd.github+json"));

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map[]> response = restTemplate.exchange(
                // 나중에 이런 api들 모아둔 파일 만들어서 사용하면될듯 enum으로?
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

    private String generateSecretKey() {
        byte[] bytes = new byte[32]; // 256-bit key
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
