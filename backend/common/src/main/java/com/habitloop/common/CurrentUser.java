package com.habitloop.common;

import java.util.UUID;

public record CurrentUser(UUID userId, UUID tenantId, String email, String name, long expiresAtEpochSecond) {
    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    static void set(CurrentUser user) { HOLDER.set(user); }
    static void clear() { HOLDER.remove(); }

    public static CurrentUser get() {
        CurrentUser user = HOLDER.get();
        if (user == null) throw new IllegalStateException("No authenticated user in this request");
        return user;
    }
}
