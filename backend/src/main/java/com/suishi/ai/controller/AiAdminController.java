package com.suishi.ai.controller;

import com.suishi.ai.dto.AiRequests.*;
import com.suishi.ai.service.AiConfigService;
import com.suishi.common.Result;
import com.suishi.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/admin/ai")
public class AiAdminController {
    private final AiConfigService config;
    public AiAdminController(AiConfigService config){this.config=config;}
    @GetMapping("/config") @PreAuthorize("hasAuthority('ai:read')") public Result<Map<String,Object>> config(){return Result.ok(config.view());}
    @PutMapping("/config") @PreAuthorize("hasAuthority('ai:write')") public Result<Map<String,Object>> configure(@AuthenticationPrincipal UserPrincipal user,@Valid @RequestBody Configure body){return Result.ok(config.configure(user.id(),body));}
    @GetMapping("/usage") @PreAuthorize("hasAuthority('ai:read')") public Result<Map<String,Object>> usage(){return Result.ok(config.usage());}
    @GetMapping("/knowledge") @PreAuthorize("hasAuthority('knowledge:review')") public Result<List<Map<String,Object>>> cards(){return Result.ok(config.knowledge());}
    @PutMapping("/knowledge/{id}/review") @PreAuthorize("hasAuthority('knowledge:review')") public Result<Void> review(@AuthenticationPrincipal UserPrincipal user,@PathVariable @Positive Long id,@Valid @RequestBody Review body){config.review(user.id(),id,body);return Result.ok(null);}
}
