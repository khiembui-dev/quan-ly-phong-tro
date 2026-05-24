package vn.glassliving.common.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.glassliving.common.util.IconUtil;
import vn.glassliving.common.util.MoneyFormatter;
import vn.glassliving.common.util.SlugUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Wrapper around static utility classes so Thymeleaf templates can call them
 * without using {@code T(...)} type-reference expressions, which Thymeleaf 3.1.x
 * blocks under its restricted SpEL sandbox.
 *
 * Exposed in every model as {@code h} via {@link GlobalModelAdvice}.
 *
 * Usage in templates:
 *   <span th:text="${h.vnd(room.priceMonthly)}">8.500.000₫</span>
 *   <img th:src="${h.amenityIcon(a.code)}" />
 */
@Component
@RequiredArgsConstructor
public class TemplateHelper {

    private final ObjectMapper objectMapper;

    public String vnd(BigDecimal amount)         { return MoneyFormatter.vnd(amount); }
    public String vndCompact(BigDecimal amount)  { return MoneyFormatter.vndCompact(amount); }

    public String amenityIcon(String code)       { return IconUtil.amenityIcon(code); }
    public String fluency(String name)           { return IconUtil.fluency(name); }
    public String color(String name)             { return IconUtil.color(name); }

    public String slugify(String input)          { return SlugUtil.slugify(input); }

    public long daysUntil(LocalDate date) {
        if (date == null) return 0;
        return ChronoUnit.DAYS.between(LocalDate.now(), date);
    }

    public String roomUsageLabel(LocalDate paidUntil) {
        if (paidUntil == null) return "Chưa đặt hạn";
        long days = daysUntil(paidUntil);
        if (days > 0) return "Còn " + days + " ngày";
        if (days == 0) return "Hết hạn hôm nay";
        return "Quá hạn " + Math.abs(days) + " ngày";
    }

    public String roomUsageBadgeClass(LocalDate paidUntil) {
        if (paidUntil == null) return "badge-mute";
        long days = daysUntil(paidUntil);
        if (days < 0) return "badge-rose";
        if (days <= 7) return "badge-amber";
        return "badge-emerald";
    }

    public String roomUsageTextClass(LocalDate paidUntil) {
        if (paidUntil == null) return "text-[color:var(--text-mute)]";
        long days = daysUntil(paidUntil);
        if (days < 0) return "text-[color:var(--err)]";
        if (days <= 7) return "text-[color:var(--warn)]";
        return "text-[color:var(--ok)]";
    }

    /** JSON-encode any object for safe inline embedding (e.g. inside a {@code <script type="application/json">} tag). */
    public String json(Object value) {
        if (value == null) return "[]";
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
