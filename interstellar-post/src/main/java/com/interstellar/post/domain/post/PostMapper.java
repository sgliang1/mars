package com.interstellar.post.domain.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interstellar.post.domain.post.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PostMapper extends BaseMapper<Post> {

    @Update("UPDATE post SET like_count = like_count + 1 WHERE id = #{postId}")
    int incrementLikeCount(Long postId);

    @Update("UPDATE post SET like_count = GREATEST(like_count - 1, 0) WHERE id = #{postId}")
    int decrementLikeCount(Long postId);

    @Update("UPDATE post SET comment_count = comment_count + 1 WHERE id = #{postId}")
    int incrementCommentCount(Long postId);

    @Update("UPDATE post SET like_count = #{count} WHERE id = #{postId}")
    int updateLikeCountDirect(Long postId, long count);

    @Update("UPDATE post SET comment_count = #{count} WHERE id = #{postId}")
    int updateCommentCountDirect(Long postId, long count);

    @Update("UPDATE post SET view_count = view_count + #{count} WHERE id = #{postId}")
    int incrementViewCount(Long postId, long count);

    @Update("UPDATE post SET share_count = share_count + 1 WHERE id = #{postId}")
    int incrementShareCount(Long postId);

    @Update("UPDATE post SET is_pinned = #{pinned}, pinned_at = #{pinnedAt} WHERE id = #{postId}")
    int updatePinStatus(Long postId, Integer pinned, java.time.LocalDateTime pinnedAt);

    @Update("UPDATE post SET is_featured = #{featured} WHERE id = #{postId}")
    int updateFeatureStatus(Long postId, Integer featured);
}