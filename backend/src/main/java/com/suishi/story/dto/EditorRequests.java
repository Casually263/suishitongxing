package com.suishi.story.dto;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class EditorRequests {
    private EditorRequests() {}
    public record Position(@DecimalMin("-100000") @DecimalMax("100000") double x,
                           @DecimalMin("-100000") @DecimalMax("100000") double y) {}
    public record EditorNode(@NotBlank @Pattern(regexp="[a-z][a-z0-9_-]{0,63}") String id,
        @NotBlank @Pattern(regexp="narrative|choice|dual|gift|checkin|ending") String type,
        @NotNull ObjectNode content, @NotNull ObjectNode config, @NotNull @Valid Position position) {}
    public record EditorEdge(@NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{1,64}") String id,
        @NotBlank @Size(max=64) String source, @NotBlank @Size(max=64) String target,
        @NotBlank @Pattern(regexp="[a-z][a-z0-9_-]{0,63}") String action,
        @Min(0) @Max(1000) int priority, @NotNull ObjectNode condition) {}
    public record Graph(@NotNull @Size(max=64) String startNode, @NotNull ObjectNode initialVariables,
        @NotNull @Size(max=200) List<@NotNull @Valid EditorNode> nodes,
        @NotNull @Size(max=800) List<@NotNull @Valid EditorEdge> edges) {}
    public record Save(@Min(0) long expectedRevision, @NotNull @Valid Graph graph) {}
    public record Publish(@Min(1) long expectedRevision, @AssertTrue boolean culturalChecked,
        @AssertTrue boolean bilingualChecked, @NotBlank @Size(min=5,max=500) String note) {}
    public record Restore(@Min(0) long expectedRevision, @Positive long storyId) {}
    public record Preview(@Min(1) long expectedRevision, @NotBlank @Pattern(regexp="A|B") String perspective) {}
    public record PreviewAction(@Min(0) long expectedRevision, @NotBlank @Size(max=64) String action,
        @Size(max=64) String answer, @Size(max=64) String companionAnswer,
        @Size(max=2000) String textZh, @Size(max=2000) String textEn) {}
    public record Bilingual(@NotBlank @Size(max=200) String zh, @NotBlank @Size(max=200) String en) {}
    public record CreateChapter(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{1,31}") String code,
        @NotNull @Valid Bilingual title, @NotNull @Valid Bilingual intro, @Positive long copyFromChapterId) {}
}
