package com.suishi.story.controller;

import com.suishi.common.Result;
import com.suishi.security.UserPrincipal;
import com.suishi.story.dto.EditorRequests.*;
import com.suishi.story.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/admin/story")
public class StoryEditorController {
    private final StoryEditorService editor;
    public StoryEditorController(StoryEditorService editor) { this.editor=editor; }
    @GetMapping("/chapters") @PreAuthorize("hasAuthority('story:read')")
    public Result<List<Map<String,Object>>> chapters() { return Result.ok(editor.chapters()); }
    @PostMapping("/chapters") @PreAuthorize("hasAuthority('story:write')")
    public Result<Map<String,Object>> create(@AuthenticationPrincipal UserPrincipal p,@Valid @RequestBody CreateChapter body) { return Result.ok(editor.createChapter(p.id(),body)); }
    @GetMapping("/chapters/{id}/draft") @PreAuthorize("hasAuthority('story:read')")
    public Result<Map<String,Object>> draft(@PathVariable @Positive Long id) { return Result.ok(editor.draft(id)); }
    @PutMapping("/chapters/{id}/draft") @PreAuthorize("hasAuthority('story:write')")
    public Result<Map<String,Object>> save(@AuthenticationPrincipal UserPrincipal p,@PathVariable @Positive Long id,@Valid @RequestBody Save body) { return Result.ok(editor.save(p.id(),id,body)); }
    @PostMapping("/validate") @PreAuthorize("hasAuthority('story:read')")
    public Result<StoryEditorCodec.Validation> validate(@Valid @RequestBody Graph body) { return Result.ok(editor.validate(body)); }
    @PostMapping("/chapters/{id}/publish") @PreAuthorize("hasAuthority('story:publish')")
    public Result<Map<String,Object>> publish(@AuthenticationPrincipal UserPrincipal p,@PathVariable @Positive Long id,@Valid @RequestBody Publish body) { return Result.ok(editor.publish(p.id(),id,body)); }
    @GetMapping("/chapters/{id}/versions") @PreAuthorize("hasAuthority('story:read')")
    public Result<List<Map<String,Object>>> versions(@PathVariable @Positive Long id) { return Result.ok(editor.versions(id)); }
    @GetMapping("/chapters/{id}/versions/{storyId}") @PreAuthorize("hasAuthority('story:read')")
    public Result<Graph> version(@PathVariable @Positive Long id,@PathVariable @Positive Long storyId) { return Result.ok(editor.versionGraph(id,storyId)); }
    @PostMapping("/chapters/{id}/restore") @PreAuthorize("hasAuthority('story:write')")
    public Result<Map<String,Object>> restore(@AuthenticationPrincipal UserPrincipal p,@PathVariable @Positive Long id,@Valid @RequestBody Restore body) { return Result.ok(editor.restore(p.id(),id,body)); }
    @PostMapping("/chapters/{id}/archive") @PreAuthorize("hasAuthority('story:publish')")
    public Result<Void> archive(@AuthenticationPrincipal UserPrincipal p,@PathVariable @Positive Long id) { editor.archive(p.id(),id);return Result.ok(null); }
    @PostMapping("/chapters/{id}/preview") @PreAuthorize("hasAuthority('story:preview')")
    public Result<Map<String,Object>> preview(@AuthenticationPrincipal UserPrincipal p,@PathVariable @Positive Long id,@Valid @RequestBody Preview body) { return Result.ok(editor.startPreview(p.id(),id,body)); }
    @GetMapping("/previews/{token}") @PreAuthorize("hasAuthority('story:preview')")
    public Result<Map<String,Object>> previewState(@AuthenticationPrincipal UserPrincipal p,@PathVariable @Pattern(regexp="[a-f0-9-]{36}") String token) { return Result.ok(editor.preview(p.id(),token)); }
    @PostMapping("/previews/{token}/step") @PreAuthorize("hasAuthority('story:preview')")
    public Result<Map<String,Object>> previewStep(@AuthenticationPrincipal UserPrincipal p,@PathVariable @Pattern(regexp="[a-f0-9-]{36}") String token,@Valid @RequestBody PreviewAction body) { return Result.ok(editor.stepPreview(p.id(),token,body)); }
}
