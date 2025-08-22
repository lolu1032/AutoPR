package io.github.dalcol.GitHub.dto;

public class ChangedFile {
    private String filename;
    private String patch; // git diff hunk

    public ChangedFile(String filename, String patch) {
        this.filename = filename;
        this.patch = patch;
    }

    public String getFilename() {
        return filename;
    }

    public String getPatch() {
        return patch;
    }
}