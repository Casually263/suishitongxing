package com.suishi.ai.service;

import com.suishi.ai.config.AiProperties;
import com.suishi.ai.provider.AiFailure;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.*;

@Component
public class AiSafety {
    private final AiProperties properties;
    private static final Pattern EMAIL=Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE=Pattern.compile("(?<![\\p{L}\\d])\\+?\\d[\\d ()-]{6,}\\d(?!\\d)");
    private static final Pattern SECRET=Pattern.compile("(?i)(?:Bearer\\s+[A-Za-z0-9._~+/-]+|sk-[A-Za-z0-9_-]{6,}|eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)");
    private static final Pattern INPUT=Pattern.compile("(?i)(ignore.{0,25}(instruction|rule)|system\\s*prompt|提示词|api.?key|密钥|answer[ab]|正确答案|标准答案|发放.{0,8}印章|修改.{0,8}(变量|分数)|reveal.{0,12}(answer|secret)|award.{0,15}(stamp|reward))");
    private static final Pattern CITATION=Pattern.compile("\\[K(\\d{1,20})\\]");
    private static final List<String> CULTURAL=List.of("立春","雨水","惊蛰","春分","清明","谷雨","立夏","小满节气","芒种","夏至","小暑","大暑","立秋","处暑","白露","秋分","寒露","霜降","立冬","小雪","大雪","冬至","小寒","大寒","五辛盘","春盘","春饼","月饼","汤圆","春节","秦始皇","孔子","唐代","宋代","明代","清代","Lichun","Lunar New Year","solar longitude","spring pancakes","pungent vegetables","Winter Solstice","Qingming","Mid-Autumn");
    public AiSafety(AiProperties properties){this.properties=properties;}
    public String mask(String value){
        String text=value==null?"":value;
        if(!properties.getApiKey().isBlank())text=text.replace(properties.getApiKey(),"[REDACTED]");
        text=SECRET.matcher(text).replaceAll("[REDACTED]");text=EMAIL.matcher(text).replaceAll("[EMAIL]");text=PHONE.matcher(text).replaceAll("[PHONE]");
        text=text.replaceAll("(?i)(api[_ -]?key|password|密码|密钥|token)\\s*[:=：]\\s*[^\\s,，。;；]+","$1=[REDACTED]");
        text=text.replaceAll("(我叫|姓名[:：]\\s*)[\\p{IsHan}]{2,4}","$1[NAME]");
        return text.replaceAll("(?i)(my name is\\s+)[\\p{L}]+(?:\\s+[\\p{L}]+){0,2}","$1[NAME]");
    }
    public boolean blockedInput(String text){return INPUT.matcher(text).find() || Pattern.compile("(?i)(correct\\s+answer|right\\s+answer)").matcher(text).find();}
    public void validate(String text,AiContextService.Context context){
        if(text.isBlank())return;
        if(Pattern.compile("(?i)(the correct answer|the right choice|the answer is)").matcher(text).find())unsafe();
        if(text.length()>600 || !mask(text).equals(text) || text.contains("```") || text.contains("<") || text.matches("(?s).*(answerA|answerB|clueA|clueB|nodeId|api_key|system prompt|正确答案是|已.{0,8}(发放|增加|修改|盖章)|I (awarded|changed|granted)).*"))unsafe();
        String facts=context.cards().stream().map(card->card.title()+" "+card.body()).reduce("",(a,b)->a+" "+b)+" "+context.allowedClue();
        String lowerFacts=facts.toLowerCase(Locale.ROOT),lower=text.toLowerCase(Locale.ROOT);
        boolean cultural=false;
        for(String term:CULTURAL)if(lower.contains(term.toLowerCase(Locale.ROOT))){cultural=true;if(!lowerFacts.contains(term.toLowerCase(Locale.ROOT)))unsafe();}
        Set<String> allowed=new HashSet<>();context.cards().forEach(card->allowed.add(card.id()));
        Matcher cited=CITATION.matcher(text);boolean hasCitation=false;
        while(cited.find()){hasCitation=true;if(!allowed.contains(cited.group(1)))unsafe();}
        // The authorized fictional clue can be restated without pretending it is a reviewed cultural claim.
        boolean clueOnly=!context.allowedClue().isBlank() && text.strip().equals(context.allowedClue().strip());
        if(cultural && !hasCitation && !clueOnly)unsafe();
        Set<String> numbers=new HashSet<>();Matcher sourceNumbers=Pattern.compile("\\d+(?:\\.\\d+)?").matcher(facts);while(sourceNumbers.find())numbers.add(sourceNumbers.group());
        String withoutCitations=CITATION.matcher(text).replaceAll("");Matcher outputNumbers=Pattern.compile("\\d+(?:\\.\\d+)?").matcher(withoutCitations);while(outputNumbers.find())if(!numbers.contains(outputNumbers.group()))unsafe();
    }
    private static void unsafe(){throw new AiFailure(AiFailure.Kind.UNSAFE_OUTPUT,false);}
}
