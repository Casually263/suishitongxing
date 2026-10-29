package com.suishi.story.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.suishi.story.entity.StoryEntities.Story;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

public interface StoryMapper extends BaseMapper<Story> {
    @Select("SELECT s.* FROM story s JOIN chapter c ON c.id=s.chapter_id WHERE s.chapter_id=#{chapterId} AND s.status='PUBLISHED' AND c.status='PUBLISHED' ORDER BY s.version DESC LIMIT 1")
    Story published(Long chapterId);
    @Select("SELECT CAST(c.id AS CHAR) id,c.code,c.title_i18n title,c.intro_i18n intro,c.release_mode releaseMode FROM chapter c WHERE c.status='PUBLISHED' ORDER BY c.id")
    List<Map<String,Object>> chapters();
    @Select("SELECT CAST(id AS CHAR) id,content_i18n content,source_title sourceTitle,source_url sourceUrl,review_status reviewStatus FROM knowledge_card WHERE chapter_id=#{chapterId} ORDER BY id")
    List<Map<String,Object>> cards(Long chapterId);
    @Insert("INSERT INTO story_reward(id,user_id,chapter_id,progress_id,stamp_code,ending_key,partner_mode) SELECT #{id},#{userId},#{chapterId},#{progressId},code,#{endingKey},#{mode} FROM chapter WHERE id=#{chapterId}")
    void reward(@Param("id") Long id,@Param("userId") Long userId,@Param("chapterId") Long chapterId,@Param("progressId") Long progressId,@Param("endingKey") String endingKey,@Param("mode") String mode);
    @Select("SELECT CAST(chapter_id AS CHAR) chapterId,stamp_code stampCode,ending_key endingKey,partner_mode partnerMode,awarded_at awardedAt FROM story_reward WHERE user_id=#{userId} ORDER BY awarded_at")
    List<Map<String,Object>> rewards(Long userId);
}
