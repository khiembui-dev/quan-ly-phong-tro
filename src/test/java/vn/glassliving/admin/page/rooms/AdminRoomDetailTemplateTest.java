package vn.glassliving.admin.page.rooms;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.ILinkBuilder;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import vn.glassliving.auth.entity.User;
import vn.glassliving.common.util.MoneyFormatter;
import vn.glassliving.property.entity.Property;
import vn.glassliving.room.entity.Room;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminRoomDetailTemplateTest {

    @Test
    void roomDetailShowsCurrentTenantPaidUntilInTenantCard() {
        UUID ownerId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        Room room = Room.builder()
                .ownerId(ownerId)
                .propertyId(propertyId)
                .currentTenantId(tenantId)
                .currentTenantPaidUntil(LocalDate.of(2026, 8, 20))
                .code("A1")
                .title("Phong A1")
                .type(Room.RoomType.STUDIO)
                .status(Room.RoomStatus.OCCUPIED)
                .areaSqm(new BigDecimal("20"))
                .priceMonthly(new BigDecimal("10000"))
                .depositAmount(BigDecimal.ZERO)
                .serviceFee(BigDecimal.ZERO)
                .electricUnit(new BigDecimal("4000"))
                .waterUnit(new BigDecimal("25000"))
                .district("Quan 1")
                .city("TP.HCM")
                .build();
        room.setId(roomId);

        User tenant = User.builder()
                .email("customer@example.test")
                .phone("0901234567")
                .fullName("Khach thue")
                .build();
        tenant.setId(tenantId);

        Property property = Property.builder()
                .ownerId(ownerId)
                .name("Co so A")
                .slug("co-so-a")
                .addressLine("123 Test")
                .district("Quan 1")
                .city("TP.HCM")
                .totalRooms(1)
                .build();
        property.setId(propertyId);

        Context context = new Context(Locale.forLanguageTag("vi-VN"));
        context.setVariable("room", room);
        context.setVariable("property", property);
        context.setVariable("tenant", tenant);
        context.setVariable("roomImages", List.of());
        context.setVariable("roomFixedFees", List.of());
        context.setVariable("activeContract", null);
        context.setVariable("latestInvoice", null);
        context.setVariable("lateInvoice", null);
        context.setVariable("h", new TemplateMoneyHelper());

        String html = templateEngine().process("admin/room-detail", context);

        assertThat(html).contains("Đã thanh toán đến");
        assertThat(html).contains("20/08/2026");
    }

    private SpringTemplateEngine templateEngine() {
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setPrefix("src/main/resources/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setLinkBuilder(new RootLinkBuilder());
        return engine;
    }

    public static class TemplateMoneyHelper {
        public String vnd(BigDecimal amount) {
            return MoneyFormatter.vnd(amount);
        }
    }

    private static class RootLinkBuilder implements ILinkBuilder {
        @Override
        public String getName() {
            return "test-root-link-builder";
        }

        @Override
        public Integer getOrder() {
            return 1;
        }

        @Override
        public String buildLink(IExpressionContext context, String base, Map<String, Object> parameters) {
            return base;
        }
    }
}
