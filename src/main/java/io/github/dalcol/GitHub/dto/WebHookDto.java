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
}

