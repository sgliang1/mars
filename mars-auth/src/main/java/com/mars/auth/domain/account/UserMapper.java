package com.mars.auth.domain.account;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mars.common.model.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper // å…³é”®æ³¨è§£ï¼Œäº¤ç»?Spring ç®¡ç†
public interface UserMapper extends BaseMapper<User> {
    // MP è‡ªåŠ¨å¸®ä½ å†™å¥½äº?CRUDï¼Œè¿™é‡Œä»€ä¹ˆéƒ½ä¸ç”¨å†?
}
