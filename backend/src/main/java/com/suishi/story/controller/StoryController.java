package com.suishi.story.controller;

import com.suishi.common.Result;
import com.suishi.security.UserPrincipal;
import com.suishi.story.dto.StoryRequests.*;
import com.suishi.story.service.StoryService;
import com.suishi.story.vo.StoryView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/portal","/api/app"})
public class StoryController {
    private final StoryService stories;
    public StoryController(StoryService stories){this.stories=stories;}
    @GetMapping("/chapters") public Result<List<Map<String,Object>>> chapters(@AuthenticationPrincipal UserPrincipal p){return Result.ok(stories.chapters(p.id()));}
    @GetMapping("/chapters/{id}/cards") public Result<List<Map<String,Object>>> cards(@AuthenticationPrincipal UserPrincipal p,@PathVariable @Positive Long id){return Result.ok(stories.cards(p.id(),id));}
    @GetMapping("/stories/{id}/state") public Result<StoryView> state(@AuthenticationPrincipal UserPrincipal p,@PathVariable @Positive Long id){return Result.ok(stories.state(p.id(),id));}
    @PostMapping("/stories/{id}/start") public Result<StoryView> start(@AuthenticationPrincipal UserPrincipal p,@PathVariable @Positive Long id,@Valid @RequestBody Start body){return Result.ok(stories.start(p.id(),id,body));}
    @PostMapping("/stories/{id}/join") public Result<StoryView> join(@AuthenticationPrincipal UserPrincipal p,@PathVariable @Positive Long id,@Valid @RequestBody Join body){return Result.ok(stories.join(p.id(),id,body));}
    @PostMapping("/stories/{id}/choose") public Result<StoryView> choose(@AuthenticationPrincipal UserPrincipal p,@PathVariable @Positive Long id,@Valid @RequestBody Action body){return Result.ok(stories.choose(p.id(),id,body));}
    @GetMapping("/stories/rewards") public Result<List<Map<String,Object>>> rewards(@AuthenticationPrincipal UserPrincipal p){return Result.ok(stories.rewards(p.id()));}
}
