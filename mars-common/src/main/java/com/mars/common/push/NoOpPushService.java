package com.mars.common.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 推送服务空实现 (当 mars.push.enabled 未配置或为 false 时使用)
 * 保证依赖 PushService 的组件正常启动，但不实际发送推送
 */
@Service
@ConditionalOnMissingBean(FcmPushService.class)
public class NoOpPushService implements PushService {

    private static final Logger log = LoggerFactory.getLogger(NoOpPushService.class);

    @Override
    public void sendToUser(Long userId, PushPayload payload) {
        log.debug("[NoOpPush] 推送跳过 (FCM 未启用): userId={}, title={}", userId, payload.getTitle());
    }

    @Override
    public void sendToUsers(List<Long> userIds, PushPayload payload) {
        log.debug("[NoOpPush] 批量推送跳过 (FCM 未启用): count={}", userIds.size());
    }
}