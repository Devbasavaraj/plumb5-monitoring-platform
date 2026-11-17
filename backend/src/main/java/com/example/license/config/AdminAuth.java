package com.example.license.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Guards dashboard/admin actions (sending push, creating in-app messages,
 * replying to chats).
 *
 * Why a separate secret: the license API key is compiled into every copy of
 * the mobile app, so anyone can extract it. It identifies the client for
 * SDK calls, but must not be enough to push messages to all of that client's
 * users. Admin endpoints therefore also require the header
 * X-Admin-Token matching the property plumb5.admin.token. When the property
 * is empty (default) all admin endpoints are disabled.
 */
@Component
public class AdminAuth {

    @Value("${plumb5.admin.token:}")
    private String adminToken;

    public boolean isConfigured() {
        return adminToken != null && !adminToken.isBlank();
    }

    public boolean isAuthorized(String presentedToken) {
        if (!isConfigured() || presentedToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                adminToken.getBytes(StandardCharsets.UTF_8),
                presentedToken.getBytes(StandardCharsets.UTF_8));
    }
}
