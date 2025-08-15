package io.github.dalcol.GitHub.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class PullRequestRequest {
    private String title;
    private String body;
    private String head;
    private String base;
    private List<String> reviewers;
}
