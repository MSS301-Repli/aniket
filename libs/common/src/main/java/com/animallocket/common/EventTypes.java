package com.animallocket.common;

/** Giá trị trường {@code type} của EventEnvelope. Quy ước: giá trị trùng tên hằng số. */
public final class EventTypes {
    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String USER_DELETED = "USER_DELETED";
    public static final String USER_BLOCKED = "USER_BLOCKED";
    public static final String LEAD_ANIMAL_CHANGED = "LEAD_ANIMAL_CHANGED";
    public static final String AVATAR_CHANGED = "AVATAR_CHANGED";
    public static final String FRIEND_REQUEST_RECEIVED = "FRIEND_REQUEST_RECEIVED";
    public static final String FRIEND_ACCEPTED = "FRIEND_ACCEPTED";
    public static final String FRIEND_REMOVED = "FRIEND_REMOVED";
    public static final String DISCOVERY_DISABLED = "DISCOVERY_DISABLED";
    public static final String PHOTO_READY = "PHOTO_READY";
    public static final String PHOTO_REJECTED = "PHOTO_REJECTED";
    public static final String POST_PUBLISHED = "POST_PUBLISHED";
    public static final String POST_LIKED = "POST_LIKED";
    public static final String POST_COMMENTED = "POST_COMMENTED";
    public static final String MESSAGE_SENT = "MESSAGE_SENT";
    public static final String SUBSCRIPTION_ACTIVATED = "SUBSCRIPTION_ACTIVATED";
    public static final String SUBSCRIPTION_EXPIRED = "SUBSCRIPTION_EXPIRED";
    public static final String CONSUMABLE_GRANTED = "CONSUMABLE_GRANTED";

    private EventTypes() {
    }
}
