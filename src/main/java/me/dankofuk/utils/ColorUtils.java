package me.dankofuk.utils;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {

    // Matches hex colors written as &#RRGGBB
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");

    /**
     * Translates both legacy {@code &} color codes and {@code &#RRGGBB} hex codes.
     *
     * <p>Hex codes are expanded into the {@code §x§R§R§G§G§B§B} sequence understood by 1.16+
     * clients; on older servers the sequence is simply ignored rather than throwing. Legacy
     * {@code &} codes work on every supported version.</p>
     *
     * @param text the raw text, may be {@code null}
     * @return the colorized text, or {@code null} if {@code text} was {@code null}
     */
    public static String translateColorCodes(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder();
            replacement.append(ChatColor.COLOR_CHAR).append('x');
            for (char c : matcher.group(1).toCharArray()) {
                replacement.append(ChatColor.COLOR_CHAR).append(c);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(result);
        return ChatColor.translateAlternateColorCodes('&', result.toString());
    }
}
