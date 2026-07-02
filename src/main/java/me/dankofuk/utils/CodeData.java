package me.dankofuk.utils;

/**
 * A pending sync code generated on the Discord side and redeemed in-game via {@code /sync <code>}.
 * Carries the Discord user and guild so that, on redemption, the plugin knows which member to
 * grant roles to and in which guild.
 */
public class CodeData {
    private final long userId;
    private final long guildId;
    private final String code;
    private final long expiryTime;

    public CodeData(long userId, long guildId, String code, long expiryTime) {
        this.userId = userId;
        this.guildId = guildId;
        this.code = code;
        this.expiryTime = expiryTime;
    }

    public long getUserId() {
        return userId;
    }

    public long getGuildId() {
        return guildId;
    }

    public String getCode() {
        return code;
    }

    public long getExpiryTime() {
        return expiryTime;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiryTime;
    }
}
