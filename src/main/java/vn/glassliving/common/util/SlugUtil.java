package vn.glassliving.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SlugUtil {
    private static final Pattern NON_WORD = Pattern.compile("[^\\w\\s-]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern DASHES = Pattern.compile("-+");

    private SlugUtil() {}

    /** "Phòng Studio Q1" → "phong-studio-q1" */
    public static String slugify(String input) {
        if (input == null || input.isBlank()) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace("đ", "d").replace("Đ", "D");
        String slug = NON_WORD.matcher(normalized).replaceAll("");
        slug = WHITESPACE.matcher(slug.trim()).replaceAll("-");
        slug = DASHES.matcher(slug).replaceAll("-");
        return slug.toLowerCase(Locale.ROOT);
    }
}
