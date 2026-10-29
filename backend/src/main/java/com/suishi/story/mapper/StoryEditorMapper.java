package com.suishi.story.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

public interface StoryEditorMapper {
    @Select("SELECT * FROM story WHERE chapter_id=#{id} AND status='PUBLISHED' ORDER BY version DESC LIMIT 1") com.suishi.story.entity.StoryEntities.Story latestStory(Long id);
    @Select("SELECT id FROM chapter WHERE id=#{id} FOR UPDATE") Long lockChapter(Long id);
    @Select("SELECT CAST(id AS CHAR) id,code,title_i18n title,intro_i18n intro,status FROM chapter ORDER BY id") List<Map<String,Object>> chapters();
    @Select("SELECT chapter_id,revision,graph_json,updated_by,updated_at FROM story_editor_draft WHERE chapter_id=#{id}") Map<String,Object> draft(Long id);
    @Insert("INSERT INTO story_editor_draft(chapter_id,revision,graph_json,updated_by) VALUES(#{chapter},1,#{graph},#{actor})")
    void insertDraft(@Param("chapter") Long chapter,@Param("graph") String graph,@Param("actor") Long actor);
    @Update("UPDATE story_editor_draft SET revision=revision+1,graph_json=#{graph},updated_by=#{actor},updated_at=UTC_TIMESTAMP(3) WHERE chapter_id=#{chapter} AND revision=#{revision}")
    int updateDraft(@Param("chapter") Long chapter,@Param("revision") long revision,@Param("graph") String graph,@Param("actor") Long actor);
    @Select("SELECT COALESCE(MAX(version),0) FROM story WHERE chapter_id=#{id}") int maxVersion(Long id);
    @Select("SELECT CAST(s.id AS CHAR) id,s.version,s.status,p.draft_revision draftRevision,CAST(p.published_by AS CHAR) publishedBy,p.review_note reviewNote,p.published_at publishedAt FROM story s LEFT JOIN story_publication p ON p.story_id=s.id WHERE s.chapter_id=#{id} ORDER BY s.version DESC") List<Map<String,Object>> versions(Long id);
    @Select("SELECT graph_json FROM story_publication WHERE story_id=#{id}") String publishedGraph(Long id);
    @Select("SELECT CAST(story_id AS CHAR) id FROM story_publication WHERE chapter_id=#{chapter} AND draft_revision=#{revision}")
    String alreadyPublished(@Param("chapter") Long chapter,@Param("revision") long revision);
    @Insert("INSERT INTO story_edge(id,story_id,from_node,to_node,action_key,priority,condition_json) VALUES(#{id},#{storyId},#{fromNode},#{toNode},#{actionKey},#{priority},#{conditionJson})")
    void edge(com.suishi.story.entity.StoryEntities.Edge edge);
    @Insert("INSERT INTO story_publication(story_id,chapter_id,draft_revision,graph_json,published_by,review_note) VALUES(#{story},#{chapter},#{revision},#{graph},#{actor},#{note})")
    void publication(@Param("story") Long story,@Param("chapter") Long chapter,@Param("revision") long revision,@Param("graph") String graph,@Param("actor") Long actor,@Param("note") String note);
    @Insert("INSERT INTO chapter(id,code,title_i18n,intro_i18n,status,release_mode) VALUES(#{id},#{code},#{title},#{intro},'DRAFT','PRACTICE')")
    void createChapter(@Param("id") Long id,@Param("code") String code,@Param("title") String title,@Param("intro") String intro);
    @Insert("INSERT INTO knowledge_card(id,chapter_id,content_i18n,source_title,source_url,review_status) VALUES(#{id},#{chapter},#{content},#{title},#{url},'SOURCE_CHECKED')")
    void copyCard(@Param("id") Long id,@Param("chapter") Long chapter,@Param("content") String content,@Param("title") String title,@Param("url") String url);
    @Update("UPDATE chapter SET status=#{status} WHERE id=#{id}") void visibility(@Param("id") Long id,@Param("status") String status);
    @Insert("INSERT INTO story_editor_preview(id,owner_id,chapter_id,graph_json,state_json,expires_at) VALUES(#{id},#{actor},#{chapter},#{graph},#{state},UTC_TIMESTAMP(3)+INTERVAL 1 HOUR)")
    void preview(@Param("id") String id,@Param("actor") Long actor,@Param("chapter") Long chapter,@Param("graph") String graph,@Param("state") String state);
    @Select("SELECT * FROM story_editor_preview WHERE id=#{id} AND owner_id=#{actor} AND expires_at>UTC_TIMESTAMP(3) FOR UPDATE")
    Map<String,Object> lockPreview(@Param("id") String id,@Param("actor") Long actor);
    @Update("UPDATE story_editor_preview SET state_json=#{state},revision=revision+1 WHERE id=#{id}") void updatePreview(@Param("id") String id,@Param("state") String state);
    @Delete("DELETE FROM story_editor_preview WHERE expires_at<=UTC_TIMESTAMP(3) LIMIT 100") void prunePreviews();
}
