package com.suishi.system.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

public record ProfileRequest(
        @NotBlank @Size(max=64) String displayName,
        @Size(max=50) String country,
        @NotBlank @Pattern(regexp="zh|en") String nativeLang,
        @NotBlank @Pattern(regexp="zh|en") String learnLang,
        @NotEmpty @Size(max=10) Map<@Pattern(regexp="[a-z]{2,3}") String,@NotNull @Min(0) @Max(5) Integer> langLevel,
        @NotBlank @Size(max=64) String timezone,
        @NotNull @Size(max=28) List<@Valid Slot> availSlots,
        @NotNull @Size(max=10) List<@Pattern(regexp="[0-9]{1,19}") String> tagIds,
        @NotNull Map<String,Boolean> privacy,
        @NotNull Map<String,Boolean> notificationPreferences,
        @NotNull @Min(0) Integer expectedVersion) {
    public record Slot(@Min(1) @Max(7) int dayOfWeek,
                       @NotNull @Pattern(regexp="([01][0-9]|2[0-3]):[0-5][0-9]") String start,
                       @NotNull @Pattern(regexp="([01][0-9]|2[0-3]):[0-5][0-9]") String end) {}
}
