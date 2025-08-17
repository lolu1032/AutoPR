package io.github.dalcol.GitHub.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class InstallationTokenService {

    private final GitHubJwtService jwtService;

    public String createInstallationToken(String installationId) throws Exception {
        String jwt = jwtService.generateJwtToken();

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        headers.setAccept(MediaType.parseMediaTypes("application/vnd.github+json"));

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "https://api.github.com/app/installations/" + installationId + "/access_tokens",
                HttpMethod.POST,
                entity,
                Map.class
        );

        return (String) response.getBody().get("token");
    }
}
