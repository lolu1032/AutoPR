package io.github.dalcol.GitHub.Service;

import io.github.dalcol.GitHub.Service.Common.CommonGitHubApiClient;
import io.github.dalcol.GitHub.dto.ChangedFile;
import io.github.dalcol.GitHub.dto.WebHookDto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubWebhokService {

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
        config.put("url", "https://db8de4bc4750.ngrok-free.app/api/webhook/github");
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

    public void handlePushEvent(Payload payload) throws Exception {

        String owner = payload.repository().owner().login();
        log.info(owner);
        String repo = payload.repository().name();
        log.info(repo);
        String defaultBranch = payload.repository().default_branch();
        log.info(defaultBranch + "defaultBranch");

        String branch = extractBranch(payload);
        log.info(branch + " branch");

        String sonarBranch = extractSonarBranch(payload);
        log.info(sonarBranch + " sonarBranch");

        String installationId = payload.installation() != null ? payload.installation().id() : fetchInstallationId(owner);

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
                sonarToken,
                sonarBranch
        );
    }

    private String extractBranch(Payload payload) {
        String refObj = payload.ref();
        if (refObj == null) throw new IllegalArgumentException("No branch info in payload");
        return refObj.replace("refs/heads/", "");
    }

    private String extractSonarBranch(Payload payload) {
        String refObj = payload.ref();
        if (refObj == null) throw new IllegalArgumentException("No branch info in payload");

        String branchName = refObj.replace("refs/heads/", "");
        if(branchName.contains("/")) {
            return branchName.split("/")[0]; // 앞부분만
        } else {
            return branchName;
        }
    }

    private List<ChangedFile> fetchChangedFiles(String owner, String repo, String base, String head, String installationId) throws Exception {
        String token = installationTokenService.createInstallationToken(installationId);

        String url = BASEURL + "/repos/" + owner + "/" + repo + "/compare/" + base + "..." + head;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<CompareResponse> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), CompareResponse.class);

        CompareResponse body = response.getBody();

        return body != null && body.files() != null
                ? body.files().stream()
                .map(f -> new ChangedFile(f.filename(), f.patch() != null ? f.patch() : ""))
                .toList()
                : Collections.emptyList();
    }

    private String fetchInstallationId(String owner) throws Exception {
        String jwt = jwtService.generateJwtToken();
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        headers.setAccept(MediaType.parseMediaTypes("application/vnd.github+json"));

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Installation[]> response = restTemplate.exchange(
                "https://api.github.com/app/installations",
                HttpMethod.GET,
                entity,
                Installation[].class
        );

        // owner에 맞는 installation 찾기
        Installation[] installations = response.getBody();
        if(installations == null) {
            throw new RuntimeException("설치된 App을 찾을 수 없습니다: " + owner);
        }
        for (Installation inst : response.getBody()) {
            if (inst.account().login().equals(owner)) {
                return String.valueOf(inst.id());
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
