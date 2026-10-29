package com.suishi.story.dto;

import jakarta.validation.constraints.*;

public final class StoryRequests {
    private StoryRequests() {}
    public record Start(@NotNull @Pattern(regexp="SCRIPT|HUMAN") String mode) {}
    public record Join(@NotBlank @Pattern(regexp="[a-f0-9]{32}") String inviteCode) {}
    public record Action(@NotNull @Positive Long nodeId,@NotNull @Min(0) Long expectedVersion,
                         @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,64}") String requestId,
                         @NotBlank @Size(max=64) String action,
                         @Size(max=64) String answer,@Size(max=2000) String textZh,@Size(max=2000) String textEn) {}
}
