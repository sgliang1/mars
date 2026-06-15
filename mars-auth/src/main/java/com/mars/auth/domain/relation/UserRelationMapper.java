package com.mars.auth.domain.relation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserRelationMapper extends BaseMapper<UserRelation> {

    @Select("SELECT u.id AS userId, u.username " +
            "FROM user_relation r JOIN user u ON u.id = r.followed_id " +
            "WHERE r.follower_id = #{userId} ORDER BY r.created_at DESC")
    List<Map<String, Object>> selectFollowingList(@Param("userId") Long userId);

    @Select("SELECT u.id AS userId, u.username " +
            "FROM user_relation r JOIN user u ON u.id = r.follower_id " +
            "WHERE r.followed_id = #{userId} ORDER BY r.created_at DESC")
    List<Map<String, Object>> selectFollowerList(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) > 0 FROM user_relation " +
            "WHERE follower_id = #{followerId} AND followed_id = #{followedId}")
    boolean isFollowing(@Param("followerId") Long followerId, @Param("followedId") Long followedId);

    @Select("SELECT u.id AS userId, u.username, up.avatar_url AS avatarUrl, up.bio " +
            "FROM user_relation r1 " +
            "JOIN user_relation r2 ON r1.follower_id = r2.followed_id AND r1.followed_id = r2.follower_id " +
            "JOIN user u ON u.id = r1.followed_id " +
            "LEFT JOIN user_profile up ON up.user_id = u.id " +
            "WHERE r1.follower_id = #{userId}")
    List<Map<String, Object>> selectMutualFriends(@Param("userId") Long userId);
}