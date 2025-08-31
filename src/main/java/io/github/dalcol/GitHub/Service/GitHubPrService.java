package io.github.dalcol.GitHub.Service;

import io.github.dalcol.GitHub.dto.ChangedFile;
import io.github.dalcol.GitHub.dto.PullRequestRequest;
import io.github.dalcol.GitHub.dto.WebHookDto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
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
                                          String sonarToken,String sonarBranch) throws Exception {

        // 1. Installation Token 발급
        String token = installationTokenService.createInstallationToken(installationId);

        // 2. SonarCloud 분석 결과 가져오기 (변경 파일 기준)
        String sonarReport = "";
        try {
            sonarReport = getSonarCloudReport(sonarProjectKey, sonarToken, sonarBranch, changedFiles);
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
            ResponseEntity<PullRequestResponse> response = restTemplate.exchange(
                    "https://api.github.com/repos/" + owner + "/" + repo + "/pulls",
                    HttpMethod.POST,
                    entity,
                    PullRequestResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                PullRequestResponse prResponse = response.getBody();
                System.out.println("PR 생성 성공: " + prResponse.html_url());
            } else {
                System.out.println("PR 생성 실패: " + response);
            }

        } else {
            Map<String, String> updateBody = Map.of("body", prBody.toString());
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(updateBody, headers);

            ResponseEntity<PullRequestResponse> response = restTemplate.exchange(
                    "https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + prNumber,
                    HttpMethod.PATCH,
                    entity,
                    PullRequestResponse.class
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

        String token = installationTokenService.createInstallationToken(installationId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/pulls" +
                "?head=" + owner + ":" + sourceBranch + "&base=" + targetBranch + "&state=open";

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<PullReqeustInfo[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, PullReqeustInfo[].class);

        PullReqeustInfo[] prs = response.getBody();
        if (prs != null && prs.length > 0) {
            return prs[0].number();
        }
        return null;
    }

    /**
     * Sonar을 통해 코드 리뷰
     */
    private String getSonarCloudReport(String projectKey, String sonarToken, String branch, List<ChangedFile> changedFiles) {
        try {
            log.info("[LOG] SonarCloud Report 조회 시작");
            log.info("[LOG] projectKey: {}, branch: {}", projectKey, branch);

            String apiUrl = "https://sonarcloud.io/api/issues/search?componentKeys=" + projectKey +
                    "&resolved=false&branch=" + branch;
            log.info("[LOG] API URL: {}", apiUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(sonarToken, "");
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            log.info("[LOG] Authorization 헤더: {}", headers.getFirst(HttpHeaders.AUTHORIZATION));

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<SonarIssueResponse> response = restTemplate.exchange(apiUrl, HttpMethod.GET, entity, SonarIssueResponse.class);
            log.info("[LOG] HTTP 상태 코드: {}", response.getStatusCode());

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.info("[LOG] 응답 바디: {}", response.getBody());
                throw new RuntimeException("SonarCloud API 호출 실패: " + response.getStatusCode());
            }

            SonarIssueResponse body = response.getBody();
            if (body == null) {
                log.info("[LOG] 응답 바디가 null입니다.");
                return "";
            }

            List<Issue> issues = body.issues();
            log.info("[LOG] 전체 이슈 개수: {}", issues != null ? issues.size() : 0);

            // PR 변경 파일만 필터링
            Set<String> changedFilePaths = changedFiles.stream()
                    .map(ChangedFile::getFilename)
                    .collect(Collectors.toSet());
            log.info("[LOG] 변경 파일 목록: {}", changedFilePaths);

            StringBuilder report = new StringBuilder();
            if (issues != null) {
                for (Issue  issue : issues) {
                    String type = issue.type();
                    String component = issue.component();
                    String message = issue.message();
                    String severity = issue.severity();

                    if ((type.equals("CODE_SMELL") || type.equals("BUG") || type.equals("VULNERABILITY"))
                            && component.contains("src/main/java")) {

                        String filePath = component.split(":", 2)[1];
                        log.info("[LOG] 이슈 파일: {}, severity: {}, message: {}", filePath, severity, message);

                        if (!changedFilePaths.contains(filePath)) {
                            log.info("[LOG] 변경 파일 아님, 스킵: {}", filePath);
                            continue; // 변경 파일만
                        }

                        report.append("[").append(severity).append("] ")
                                .append(filePath).append(": ")
                                .append(message)
                                .append("\n");
                    }
                }
            }

            log.info("[LOG] 최종 필터링 결과:\n{}", report);
            return report.toString();
        } catch (Exception e) {
            log.error("[LOG] 예외 발생", e);
            return "";
        }
    }



}
