package io.github.dalcol.GitHub.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
public class WebHookDto {
    @Builder
    public record Payload(
            String ref,
            Repository repository,
            Installation installation
    ) {
    }

    @Builder
    public record Repository(
            String name,
            String default_branch,
            Owner owner
    ) {
    }

    @Builder
    public record Owner(
            String login
    ) {
    }

    @Builder
    public record Installation(
            String id,
            Account account
    ) {
    }

    @Builder
    public record CompareResponse(
            List<ChangedFileInfo> files
    ) {
    }

    @Builder
    public record ChangedFileInfo(
            String filename,
            String patch
    ) {
    }

    @Builder
    public record Account(
            String login
    ) {

    }
    @Builder
    public record PullReqeustInfo (
            Integer number,
            String state
    ){

    }
    @Builder
    public record SonarIssueResponse(
            List<Issue> issues,
            Integer total
    ) {
    }

    @Builder
    public record Issue(
            String key,
            String rule,
            String severity,
            String component,
            String project,
            Integer line,
            String message,
            String type
    ) {
    }
    public record PullRequestResponse(
            Long id,
            Integer number,
            String state,
            String title,
            String body,
            String html_url
    ) {}
}

