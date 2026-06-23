package com.mars.common.push;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于 Firebase Cloud Messaging 的推送实现
 * 通过 mars.push.enabled=true 启用, mars.push.fcm.service-account-path 指定凭据文件
 */
@Service
@ConditionalOnProperty(name = "mars.push.enabled", havingValue = "true")
public class FcmPushService implements PushService {

    private static final Logger log = LoggerFactory.getLogger(FcmPushService.class);

    @Value("${mars.push.fcm.service-account-path:}")
    private String serviceAccountPath;

    @Autowired
    private DeviceTokenMapper deviceTokenMapper;

    @PostConstruct
    public void init() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                if (serviceAccountPath == null || serviceAccountPath.isBlank()) {
                    log.warn("FCM service-account-path 未配置，推送服务不可用");
                    return;
                }
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(com.google.auth.oauth2.GoogleCredentials
                                .fromStream(new FileInputStream(serviceAccountPath)))
                        .build();
                FirebaseApp.initializeApp(options);
                log.info("Firebase 初始化成功");
            }
        } catch (IOException e) {
            log.error("Firebase 初始化失败: {}", e.getMessage());
        }
    }

    @Override
    public void sendToUser(Long userId, PushPayload payload) {
        List<DeviceToken> tokens = deviceTokenMapper.selectByUserId(userId);
        if (tokens.isEmpty()) {
            log.debug("用户 {} 无注册设备，跳过推送", userId);
            return;
        }
        List<String> fcmTokens = tokens.stream()
                .map(DeviceToken::getToken)
                .collect(Collectors.toList());
        sendToTokens(fcmTokens, payload, tokens);
    }

    @Override
    public void sendToUsers(List<Long> userIds, PushPayload payload) {
        for (Long userId : userIds) {
            sendToUser(userId, payload);
        }
    }

    private void sendToTokens(List<String> fcmTokens, PushPayload payload, List<DeviceToken> tokenEntities) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("Firebase 未初始化，跳过推送");
            return;
        }

        Message.Builder msgBuilder = Message.builder()
                .setNotification(Notification.builder()
                        .setTitle(payload.getTitle())
                        .setBody(payload.getBody())
                        .build());

        if (payload.getClickAction() != null) {
            msgBuilder.putData("click_action", payload.getClickAction());
        }
        if (payload.getCategory() != null) {
            msgBuilder.putData("category", payload.getCategory());
        }
        payload.getData().forEach(msgBuilder::putData);

        // 逐个发送以便识别失效令牌
        List<DeviceToken> toRemove = new ArrayList<>();
        for (int i = 0; i < fcmTokens.size(); i++) {
            String token = fcmTokens.get(i);
            try {
                msgBuilder.setToken(token);
                FirebaseMessaging.getInstance().send(msgBuilder.build());
                log.debug("推送成功: userId={}, token={}", tokenEntities.get(i).getUserId(),
                        token.substring(0, Math.min(8, token.length())) + "...");
            } catch (FirebaseMessagingException e) {
                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                        || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                    toRemove.add(tokenEntities.get(i));
                    log.info("失效令牌将清理: userId={}, token={}", tokenEntities.get(i).getUserId(),
                            token.substring(0, Math.min(8, token.length())) + "...");
                } else {
                    log.warn("推送失败: token={}, error={}", token.substring(0, Math.min(8, token.length())), e.getMessage());
                }
            }
        }

        // 清理失效令牌
        for (DeviceToken dt : toRemove) {
            try {
                deviceTokenMapper.deleteById(dt.getId());
            } catch (Exception e) {
                log.warn("清理失效令牌失败: id={}", dt.getId());
            }
        }
    }
}