package com.interstellar.common.push;

import java.util.List;

/**
 * 推送服务接口
 */
public interface PushService {

    /**
     * 向单个用户的所有设备发送推送
     */
    void sendToUser(Long userId, PushPayload payload);

    /**
     * 向多个用户发送推送
     */
    void sendToUsers(List<Long> userIds, PushPayload payload);
}