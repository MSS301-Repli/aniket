package com.animallocket.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** DeviceDirectory giả trong bộ nhớ cho test: ghi lại token bị gỡ, có thể mô phỏng user-service sập. */
class FakeDirectory implements DeviceDirectory {
    final Map<String, List<String>> tokens;
    final Map<String, List<String>> friends;
    final List<String> removed = new ArrayList<>();
    boolean failTokens;
    boolean failFriends;

    FakeDirectory(Map<String, List<String>> tokens, Map<String, List<String>> friends) {
        this.tokens = tokens;
        this.friends = friends;
    }

    @Override
    public List<String> tokens(String id) {
        if (failTokens) {
            throw new IllegalStateException("user-service down");
        }
        return tokens.getOrDefault(id, List.of());
    }

    @Override
    public List<String> friendIds(String id) {
        if (failFriends) {
            throw new IllegalStateException("user-service down");
        }
        return friends.getOrDefault(id, List.of());
    }

    @Override
    public void removeToken(String token) {
        removed.add(token);
    }
}
