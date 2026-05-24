package vn.glassliving.common.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public final class MoneyFormatter {
    private static final Locale VN = new Locale("vi", "VN");

    private MoneyFormatter() {}

    /** "8.500.000₫" */
    public static String vnd(BigDecimal amount) {
        if (amount == null) return "0₫";
        NumberFormat nf = NumberFormat.getInstance(VN);
        nf.setMaximumFractionDigits(0);
        return nf.format(amount) + "₫";
    }

    public static String vndCompact(BigDecimal amount) {
        if (amount == null) return "0";
        NumberFormat nf = NumberFormat.getInstance(VN);
        nf.setMaximumFractionDigits(0);
        return nf.format(amount);
    }
}
