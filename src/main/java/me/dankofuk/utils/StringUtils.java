package me.dankofuk.utils;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {
    private StringUtils() {}

    private static final Pattern PATTERN = Pattern.compile("#[a-zA-Z0-9]{6}");

    public static String replace(String input, String... replacements) {
        if (input == null) {
            return null;
        }
        if (replacements.length % 2 != 0) {
            throw new IllegalArgumentException("replacements must be even");
        }

        // Literal replacement: the search tokens are plain placeholders (e.g. %player%) and the
        // values can contain regex-special characters like $ or \, which would break replaceAll.
        for (int i = 0; i < replacements.length; i += 2) {
            input = input.replace(replacements[i], replacements[i + 1]);
        }

        return input;
    }

    public static String replace(String input, Object... replacements) {
        if (input == null) {
            return null;
        }
        if (replacements.length % 2 != 0) {
            throw new IllegalArgumentException("replacements must be even");
        }

        for (int i = 0; i < replacements.length; i += 2) {
            input = input.replace(replacements[i].toString(), replacements[i + 1].toString());
        }

        return input;
    }

    private static final Pattern HEX_PATTERN = Pattern.compile("&#[0-9a-fA-F]{6}");

    private static String parseHex(String hex) {
        StringBuilder builder = new StringBuilder();
        for (char c : hex.toCharArray()) {
            if (c == '#') {
                builder.append(ChatColor.COLOR_CHAR).append("x");
            } else if (c != '&') {
                builder.append(ChatColor.COLOR_CHAR).append(c);
            }
        }
        return builder.toString();
    }

    public static String colorize(String input) {
        if (input == null) {
            return null;
        }
        Matcher matcher = HEX_PATTERN.matcher(input);

        while (matcher.find()) {
            String hexCode = matcher.group();
            String colorCode = parseHex(hexCode);
            input = input.replace(hexCode, colorCode);
        }

        return ChatColor.translateAlternateColorCodes('&', input);
    }

    public static String format(String input, String... replacements) throws IllegalArgumentException {
        return colorize(replace(input, replacements));
    }

    public static String format(String[] input, String... replacements) throws IllegalArgumentException {
        StringBuilder output = new StringBuilder();
        for (String line : input) {
            output.append(format(line, replacements)).append("\n");
        }
        return output.toString();
    }

    public static String format(List<String> input, String... replacements) throws IllegalArgumentException {
        StringBuilder output = new StringBuilder();
        for (String line : input) {
            output.append(format(line, replacements)).append("\n");
        }
        return output.toString();
    }

    public static String[] formatToArray(String[] input, String... replacements) throws IllegalArgumentException {
        return Arrays.stream(input).map(line -> format(line, replacements)).toArray(String[]::new);
    }

    public static List<String> formatToList(List<String> input, String... replacements) throws IllegalArgumentException {
        List<String> output = new ArrayList<>();
        for (String line : input) {
            output.add(format(line, replacements));
        }
        return output;
    }

}