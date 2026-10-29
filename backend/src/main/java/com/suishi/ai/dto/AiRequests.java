package com.suishi.ai.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class AiRequests {
    private AiRequests() {}
    public record Chat(@NotNull @Positive Long nodeId,@NotBlank @Size(max=1000) String message,
                       @NotNull @Pattern(regexp="zh|en") String language,
                       @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,64}") String requestId) {}
    public record Configure(@NotNull Boolean enabled,@Size(max=500) String baseUrl,
                            @Size(max=128) @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}|^$") String model,
                            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal temperature,
                            @NotNull @Min(500) @Max(15000) Integer timeoutMs,
                            @NotNull @Min(0) @Max(1) Integer retries,
                            @NotNull @Min(1) @Max(200) Integer dailyLimit,
                            @NotNull @Min(1) @Max(100) Integer maxRounds,
                            @NotNull @Min(1) @Max(30) Integer minuteLimit,
                            @NotNull @Min(64) @Max(512) Integer maxTokens,
                            @NotNull Boolean includeUsage,@NotNull @Min(0) Long expectedVersion) {}
    public record Review(@NotBlank @Pattern(regexp="[a-f0-9]{64}") String expectedHash,
                         @NotNull @Pattern(regexp="APPROVED|REVOKED") String decision,
                         @NotNull Boolean culturalChecked,@NotNull Boolean bilingualChecked,
                         @NotBlank @Size(min=5,max=500) String note) {}
}
