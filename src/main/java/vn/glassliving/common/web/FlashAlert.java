package vn.glassliving.common.web;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Helper for adding flash messages to redirect attributes.
 * Templates render these via the `fragments/flash :: alert` fragment.
 */
public final class FlashAlert {

    public enum Kind { OK, INFO, WARN, ERR }

    public static void ok(RedirectAttributes ra, String message)   { add(ra, Kind.OK,   message); }
    public static void info(RedirectAttributes ra, String message) { add(ra, Kind.INFO, message); }
    public static void warn(RedirectAttributes ra, String message) { add(ra, Kind.WARN, message); }
    public static void err(RedirectAttributes ra, String message)  { add(ra, Kind.ERR,  message); }

    private static void add(RedirectAttributes ra, Kind kind, String message) {
        ra.addFlashAttribute("flashKind", kind.name());
        ra.addFlashAttribute("flashMessage", message);
    }

    private FlashAlert() {}
}
