package com.mars.auth.domain.account;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
    
    // 原子操作：增减粉丝数 (加粉 delta 传 1，掉粉传 -1)
    @Update("UPDATE user_profile SET follower_count = follower_count + #{delta} WHERE user_id = #{userId}")
    void updateFollowerCount(@Param("userId") Long userId, @Param("delta") int delta);

    // 原子操作：增减关注数
    @Update("UPDATE user_profile SET following_count = following_count + #{delta} WHERE user_id = #{userId}")
    void updateFollowingCount(@Param("userId") Long userId, @Param("delta") int delta);

    // 原子操作：增减获赞总数
    @Update("UPDATE user_profile SET total_liked_count = total_liked_count + #{delta} WHERE user_id = #{userId}")
    void updateTotalLikedCount(@Param("userId") Long userId, @Param("delta") int delta);
}