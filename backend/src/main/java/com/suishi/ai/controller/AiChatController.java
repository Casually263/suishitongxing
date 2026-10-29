package com.suishi.ai.controller;

import com.suishi.ai.dto.AiRequests.Chat;
import com.suishi.ai.service.AiChatService;
import com.suishi.common.Result;
import com.suishi.security.UserPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;

@RestController
@RequestMapping({"/api/portal/stories/{chapterId}/ai","/api/app/stories/{chapterId}/ai"})
public class AiChatController {
    private final AiChatService chats;
    public AiChatController(AiChatService chats){this.chats=chats;}
    @PostMapping(value="/chat",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal UserPrincipal user,@PathVariable @Positive Long chapterId,@Valid @RequestBody Chat body,HttpServletResponse response){response.setHeader("X-Accel-Buffering","no");response.setHeader("Cache-Control","no-store, no-transform");return chats.stream(user,chapterId,body);}
    @GetMapping("/history") public Result<List<AiChatService.History>> history(@AuthenticationPrincipal UserPrincipal user,@PathVariable @Positive Long chapterId){return Result.ok(chats.history(user,chapterId));}
}
