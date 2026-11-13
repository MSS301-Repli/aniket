package com.animallocket.notification;

import java.util.Map;

/** Một thông báo gửi cho một người. Nội dung chung chung, không nêu alias hay thông tin định danh. */
public record Notification(String recipientPublicId, String type, String title, String body,
                           Map<String, String> data) {
}
