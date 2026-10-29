package com.suishi.story.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.suishi.story.entity.StoryEntities.Node;
import com.suishi.story.entity.StoryEntities.Edge;
import org.apache.ibatis.annotations.Select;
import java.util.List;
public interface StoryNodeMapper extends BaseMapper<Node> {
    @Select("SELECT * FROM story_node WHERE story_id=#{storyId} ORDER BY id") List<Node> nodes(Long storyId);
    @Select("SELECT * FROM story_edge WHERE story_id=#{storyId} ORDER BY priority DESC,id") List<Edge> edges(Long storyId);
}
