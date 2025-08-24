package io.github.dalcol.GitHub.Service;

import io.github.dalcol.GitHub.dto.ChangedFile;
import io.github.dalcol.GitHub.dto.PullRequestRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GitHubPrService {

    private final InstallationTokenService installationTokenService;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * PR 남김
     */
    public void createPullRequestWithDiff(String installationId, String owner, String repo,
                                          String sourceBranch, String targetBranch,
                                          List<ChangedFile> changedFiles,
                                          String sonarProjectKey,
                                          String sonarToken) throws Exception {

        // 1. Installation Token 발급
        String token = installationTokenService.createInstallationToken(installationId);

        // 2. SonarCloud 분석 결과 가져오기 (변경 파일 기준)
        String sonarReport = "";
        try {
            sonarReport = getSonarCloudReport(sonarProjectKey, sonarToken, sourceBranch, changedFiles);
        } catch (Exception e) {
            System.out.println("SonarCloud 분석 실패: " + e.getMessage());
            // PR 생성은 계속 진행
        }

        // 3. PR Body 생성 (diff 포함 + SonarCloud 결과)
        StringBuilder prBody = new StringBuilder("자동 생성된 Pull Request입니다.\n\n변경 파일:\n");
        for (ChangedFile file : changedFiles) {
            prBody.append(file.getFilename()).append("\n");
            prBody.append("```diff\n")
                    .append(file.getPatch())
                    .append("\n```\n");
        }

        if (!sonarReport.isEmpty()) {
            prBody.append("\nSonarCloud 분석 결과 (변경 파일 기준):\n");
            prBody.append(sonarReport);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        Integer prNumber = findExistingPr(installationId, owner, repo, sourceBranch, targetBranch);

        if (prNumber == null) {
            // === 신규 PR 생성 ===
            PullRequestRequest prRequest = new PullRequestRequest(
                    "[Auto PR] " + sourceBranch + " -> " + targetBranch,
                    prBody.toString(),
                    sourceBranch,
                    targetBranch
            );

            HttpEntity<PullRequestRequest> entity = new HttpEntity<>(prRequest, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.github.com/repos/" + owner + "/" + repo + "/pulls",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("PR 생성 성공: " + response.getBody().get("html_url"));
            } else {
                System.out.println("PR 생성 실패: " + response.getBody());
            }

        } else {
            Map<String, String> updateBody = Map.of("body", prBody.toString());
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(updateBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + prNumber,
                    HttpMethod.PATCH,
                    entity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("PR 업데이트 성공: #" + prNumber);
            } else {
                System.out.println("PR 업데이트 실패: " + response.getBody());
            }
        }
    }

    private Integer findExistingPr(String installationId, String owner, String repo,
                                   String sourceBranch, String targetBranch) throws Exception {

        // 1. 설치 토큰 발급
        String token = installationTokenService.createInstallationToken(installationId);

        // 2. 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // 3. API 호출
        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/pulls" +
                "?head=" + owner + ":" + sourceBranch + "&base=" + targetBranch + "&state=open";

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, entity, List.class);

        List<Map<String, Object>> prs = response.getBody();
        if (prs != null && !prs.isEmpty()) {
            // 첫 번째 열린 PR 번호 반환
            return (Integer) prs.get(0).get("number");
        }
        return null; // PR 없음
    }

    /**
     * Sonar을 통해 코드 리뷰
     */
    private String getSonarCloudReport(String projectKey, String sonarToken, String branch, List<ChangedFile> changedFiles) {
        String apiUrl = "https://sonarcloud.io/api/issues/search?componentKeys=" + projectKey +
                "&resolved=false&branch=" + branch;

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(sonarToken, "");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.GET, entity, Map.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("SonarCloud API 호출 실패: " + response.getStatusCode());
        }

        Map<String, Object> body = response.getBody();
        List<Map<String, Object>> issues = (List<Map<String, Object>>) body.get("issues");

        // PR 변경 파일만 필터링
        Set<String> changedFilePaths = changedFiles.stream()
                .map(ChangedFile::getFilename)
                .collect(Collectors.toSet());

        StringBuilder report = new StringBuilder();
        for (Map<String, Object> issue : issues) {
            String type = (String) issue.get("type");
            String component = (String) issue.get("component");
            String message = (String) issue.get("message");
            String severity = (String) issue.get("severity");

            if ((type.equals("CODE_SMELL") || type.equals("BUG") || type.equals("VULNERABILITY"))
                    && component.contains("src/main/java")) {

                String filePath = component.split(":", 2)[1];

                if (!changedFilePaths.contains(filePath)) continue; // 변경 파일만

                report.append("[").append(severity).append("] ")
                        .append(filePath).append(": ")
                        .append(message)
                        .append("\n");
            }
        }

        return report.toString();
    }


}
