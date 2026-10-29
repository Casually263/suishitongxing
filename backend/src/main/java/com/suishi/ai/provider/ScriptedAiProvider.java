package com.suishi.ai.provider;

import com.suishi.ai.service.AiContextService;
import org.springframework.stereotype.Component;
import java.util.Locale;

@Component
public class ScriptedAiProvider {
    public String reply(AiContextService.Context context,String question){
        boolean en=context.language().equals("en");String q=question.toLowerCase(Locale.ROOT);
        if(!context.allowedClue().isBlank() && (q.contains("线索") || q.contains("clue") || context.nodeType().equals("dual")))return en?"I’m Xiaoman, your scripted guide. Here is the clue I may share: "+context.allowedClue()+" What do you notice? Your choice remains yours.":"我是小满（脚本向导）。我能转述的线索是："+context.allowedClue()+" 你注意到了什么？选择仍由你自己提交。";
        if(context.nodeType().equals("gift") || q.contains("分享") || q.contains("my family"))return en?"Thank you for sharing. As a fictional scripted guide, I can help you ask a gentle follow-up: what makes this experience meaningful to you? Nothing is automatically published.":"谢谢你的分享。我是虚构的脚本向导。可以再想一想：这段经历对你有什么特别的意义？这段对话不会自动发布回礼。";
        if(!context.cards().isEmpty() && (q.contains("节气") || q.contains("立春") || q.contains("春盘") || q.contains("solar") || q.contains("lichun") || q.contains("knowledge"))){
            var card=context.cards().stream().filter(c->(q.contains("春盘") || q.contains("plate")) && (c.body().contains("春盘") || c.body().toLowerCase(Locale.ROOT).contains("plate"))).findFirst().orElse(context.cards().get(0));
            return (en?"I’m Xiaoman, in script mode. From the reviewed card: ":"我是小满（脚本模式）。根据已审校卡片：")+card.body()+" [K"+card.id()+"]";
        }
        if(context.cards().isEmpty())return en?"I’m Xiaoman, your scripted guide. No reviewed knowledge card is available yet, so I won’t invent cultural facts. We can look at the current scene together. Story choices and rewards stay with the story rules.":"我是小满（脚本向导）。目前还没有已审校知识卡片，我不会编造文化知识。我们可以先看看当前场景；剧情选择与奖励仍按故事规则处理。";
        return en?"I’m Xiaoman, in script mode. I’m not sure about that from the reviewed material. Let’s check the knowledge cards, or tell me what you noticed in this scene.":"我是小满（脚本模式）。这个问题在已审校资料里还不够明确，我们先看看知识卡片吧。也可以告诉我，你在当前场景里注意到了什么？";
    }
}
