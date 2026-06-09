package vn.glassliving.ai.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import vn.glassliving.ai.entity.AiConversation;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.maintenance.repository.MaintenanceTicketRepository;
import vn.glassliving.maintenance.service.MaintenanceService;
import vn.glassliving.notification.repository.NotificationRepository;
import vn.glassliving.payment.repository.PaymentRepository;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;
import vn.glassliving.utility.entity.UtilityReading;
import vn.glassliving.utility.repository.UtilityReadingRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiToolServiceSecurityTest {

    private final RoomRepository roomRepository = mock(RoomRepository.class);
    private final PropertyRepository propertyRepository = mock(PropertyRepository.class);
    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final UtilityReadingRepository utilityReadingRepository = mock(UtilityReadingRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final MaintenanceTicketRepository ticketRepository = mock(MaintenanceTicketRepository.class);
    private final MaintenanceService maintenanceService = null;
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AiToolService service = new AiToolService(
            roomRepository,
            propertyRepository,
            invoiceRepository,
            paymentRepository,
            utilityReadingRepository,
            notificationRepository,
            ticketRepository,
            maintenanceService,
            userRepository
    );

    @Test
    void customerAdminRevenueQuestionIsBlockedBeforeAdminTools() {
        UUID customerId = UUID.randomUUID();

        AiToolService.ToolContext context = service.buildContext(customerId, AiConversation.Role.CUSTOMER,
                "Doanh thu thang nay bao nhieu?");

        assertThat(context.inScope()).isFalse();
        assertThat(context.dataFound()).isFalse();
        assertThat(context.directAnswer()).contains("Thông tin doanh thu chỉ dành cho tài khoản quản trị");
        verify(invoiceRepository, never()).sumTotalByOwnerAndStatus(any(), any());
        verify(invoiceRepository, never()).findByOwnerIdAndStatus(any(), any(), any());
    }

    @Test
    void generalHelpDoesNotCallDataRepositoriesAndUsesGuideSource() {
        UUID customerId = UUID.randomUUID();

        AiToolService.ToolContext context = service.buildContext(customerId, AiConversation.Role.CUSTOMER,
                "Co cac tien ich nao?");

        assertThat(context.intent()).isEqualTo(AiIntent.GENERAL_HELP);
        assertThat(context.requiresData()).isFalse();
        assertThat(context.dataFound()).isFalse();
        assertThat(context.directAnswer()).isNull();
        assertThat(context.sourceSummary()).isEqualTo("Hướng dẫn SmartRent");
        assertThat(context.context()).contains("Hướng dẫn sử dụng website").contains("DATA:");
        verify(roomRepository, never()).findByCurrentTenantId(any());
        verify(invoiceRepository, never()).findByTenantUserId(any(), any());
    }

    @Test
    void customerInvoiceContextUsesOnlyCurrentCustomerData() {
        UUID customerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Room room = room(customerId, ownerId, "PP3");
        Invoice invoice = invoice(customerId, ownerId, "PT-HD-1", new BigDecimal("850000"));
        invoice.setRoomId(room.getId());
        when(roomRepository.findByCurrentTenantId(customerId)).thenReturn(List.of(room));
        when(invoiceRepository.findByTenantUserId(eq(customerId), any()))
                .thenReturn(new PageImpl<>(List.of(invoice)));

        AiToolService.ToolContext context = service.buildContext(customerId, AiConversation.Role.CUSTOMER,
                "Hoa don thang nay bao nhieu?");

        assertThat(context.context())
                .contains("SYSTEM_CONTEXT")
                .contains("Intent: INVOICE")
                .contains("Tool: getMyInvoices")
                .contains("PT-HD-1")
                .contains("PP3")
                .contains("850.000")
                .contains("RULE");
        assertThat(context.dataFound()).isTrue();
        assertThat(context.directAnswer()).contains("850.000");
        verify(invoiceRepository).findByTenantUserId(eq(customerId), any());
        verify(invoiceRepository, never()).sumTotalByOwnerAndStatus(any(), any());
    }

    @Test
    void customerInvoiceContextReturnsNoDataWithoutCallingGeminiLater() {
        UUID customerId = UUID.randomUUID();
        when(roomRepository.findByCurrentTenantId(customerId)).thenReturn(List.of());
        when(invoiceRepository.findByTenantUserId(eq(customerId), any()))
                .thenReturn(new PageImpl<>(List.of()));

        AiToolService.ToolContext context = service.buildContext(customerId, AiConversation.Role.CUSTOMER,
                "Hoa don thang nay bao nhieu?");

        assertThat(context.requiresData()).isTrue();
        assertThat(context.dataFound()).isFalse();
        assertThat(context.directAnswer()).contains("Hiện hệ thống chưa có dữ liệu này");
    }

    @Test
    void customerRoomIntentReturnsCurrentRoomOnly() {
        UUID customerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(roomRepository.findByCurrentTenantId(customerId)).thenReturn(List.of(room(customerId, ownerId, "A1")));

        AiToolService.ToolContext context = service.buildContext(customerId, AiConversation.Role.CUSTOMER,
                "Phong toi dang o la phong nao?");

        assertThat(context.toolName()).isEqualTo("getMyRoom");
        assertThat(context.dataFound()).isTrue();
        assertThat(context.context()).contains("A1").contains("myRooms");
        assertThat(context.directAnswer()).contains("A1");
    }

    @Test
    void customerUtilityIntentUsesCurrentRoomReadings() {
        UUID customerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Room room = room(customerId, ownerId, "A1");
        UtilityReading reading = utilityReading(ownerId, room.getId());
        when(roomRepository.findByCurrentTenantId(customerId)).thenReturn(List.of(room));
        when(utilityReadingRepository.findByRoomIdOrderByPeriodYearDescPeriodMonthDesc(eq(room.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(reading)));

        AiToolService.ToolContext context = service.buildContext(customerId, AiConversation.Role.CUSTOMER,
                "Tien dien nuoc thang nay bao nhieu?");

        assertThat(context.toolName()).isEqualTo("getMyUtilityHistory");
        assertThat(context.dataFound()).isTrue();
        assertThat(context.context()).contains("utilityReadings").contains("A1").contains("120.000").contains("50.000");
        assertThat(context.directAnswer()).contains("A1").contains("120.000").contains("50.000");
    }

    @Test
    void customerLandlordContactUsesRoomOwnerOnly() {
        UUID customerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Room room = room(customerId, ownerId, "A1");
        when(roomRepository.findByCurrentTenantId(customerId)).thenReturn(List.of(room));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(user(ownerId, "Chu tro", "0909000000")));

        AiToolService.ToolContext context = service.buildContext(customerId, AiConversation.Role.CUSTOMER,
                "Cho toi so dien thoai chu tro");

        assertThat(context.toolName()).isEqualTo("getLandlordContact");
        assertThat(context.dataFound()).isTrue();
        assertThat(context.context()).contains("Chu tro").contains("0909000000");
        assertThat(context.directAnswer()).contains("Chu tro").contains("0909000000");
    }

    @Test
    void adminContextUsesOwnerScopedSummary() {
        UUID ownerId = UUID.randomUUID();
        Invoice paid = invoice(UUID.randomUUID(), ownerId, "PT-HD-PAID", new BigDecimal("12000000"));
        paid.setStatus(Invoice.InvoiceStatus.PAID);
        Invoice pending = invoice(UUID.randomUUID(), ownerId, "PT-HD-PENDING", new BigDecimal("2500000"));
        pending.setStatus(Invoice.InvoiceStatus.PENDING);
        when(invoiceRepository.findForReport(eq(ownerId), eq((short) 2026), eq((short) 6), eq((short) 2026), eq((short) 6)))
                .thenReturn(List.of(paid, pending));

        AiToolService.ToolContext context = service.buildContext(ownerId, AiConversation.Role.ADMIN,
                "Thong ke doanh thu thang nay");

        assertThat(context.context())
                .contains("Role: ADMIN")
                .contains("Intent: ADMIN_DASHBOARD")
                .contains("period: 06/2026")
                .contains("paidRevenue: 12000000")
                .contains("pendingDebt: 2500000");
        assertThat(context.directAnswer()).contains("12.000.000").contains("2.500.000");
    }

    @Test
    void adminInvoiceSummaryUsesAggregateCountsWhenRequested() {
        UUID ownerId = UUID.randomUUID();
        when(invoiceRepository.countByOwnerId(ownerId)).thenReturn(6L);
        when(invoiceRepository.countByOwnerIdAndStatus(ownerId, Invoice.InvoiceStatus.PENDING)).thenReturn(2L);
        when(invoiceRepository.countByOwnerIdAndStatus(ownerId, Invoice.InvoiceStatus.OVERDUE)).thenReturn(1L);
        when(invoiceRepository.countByOwnerIdAndStatus(ownerId, Invoice.InvoiceStatus.PAID)).thenReturn(3L);
        when(invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.PENDING))
                .thenReturn(new BigDecimal("2500000"));
        when(invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.OVERDUE))
                .thenReturn(new BigDecimal("1200000"));

        AiToolService.ToolContext context = service.buildContext(ownerId, AiConversation.Role.ADMIN,
                DetectedIntent.of(AiIntent.INVOICE, "getInvoiceSummary", true),
                "Thong ke hoa don");

        assertThat(context.context())
                .contains("invoiceSummary")
                .contains("totalInvoices: 6")
                .contains("pendingInvoices: 2")
                .contains("overdueInvoices: 1")
                .contains("paidInvoices: 3")
                .contains("pendingDebt: 3700000");
        assertThat(context.directAnswer()).contains("6").contains("3.700.000");
        verify(invoiceRepository, never()).findByOwnerIdAndStatus(any(), any(), any());
    }

    private Invoice invoice(UUID customerId, UUID ownerId, String code, BigDecimal total) {
        Invoice invoice = Invoice.builder()
                .code(code)
                .contractId(UUID.randomUUID())
                .ownerId(ownerId)
                .tenantUserId(customerId)
                .roomId(UUID.randomUUID())
                .periodYear((short) 2026)
                .periodMonth((short) 6)
                .issueDate(LocalDate.of(2026, 6, 1))
                .dueDate(LocalDate.of(2026, 6, 10))
                .rentAmount(total)
                .totalAmount(total)
                .paidAmount(BigDecimal.ZERO)
                .status(Invoice.InvoiceStatus.PENDING)
                .build();
        invoice.setId(UUID.randomUUID());
        return invoice;
    }

    private Room room(UUID customerId, UUID ownerId, String code) {
        Room room = new Room();
        room.setId(UUID.randomUUID());
        room.setOwnerId(ownerId);
        room.setCurrentTenantId(customerId);
        room.setCode(code);
        room.setTitle("Phong " + code);
        room.setPriceMonthly(new BigDecimal("8500000"));
        room.setStatus(Room.RoomStatus.OCCUPIED);
        return room;
    }

    private UtilityReading utilityReading(UUID ownerId, UUID roomId) {
        UtilityReading reading = UtilityReading.builder()
                .ownerId(ownerId)
                .propertyId(UUID.randomUUID())
                .roomId(roomId)
                .periodYear((short) 2026)
                .periodMonth((short) 6)
                .readingDate(LocalDate.of(2026, 6, 1))
                .electricPrev(new BigDecimal("100"))
                .electricCurr(new BigDecimal("130"))
                .electricAmount(new BigDecimal("120000"))
                .waterPrev(new BigDecimal("10"))
                .waterCurr(new BigDecimal("12"))
                .waterAmount(new BigDecimal("50000"))
                .build();
        reading.setId(UUID.randomUUID());
        return reading;
    }

    private User user(UUID id, String name, String phone) {
        User user = User.builder()
                .email(name.toLowerCase().replace(" ", "") + "@example.com")
                .fullName(name)
                .phone(phone)
                .passwordHash("hash")
                .status(User.UserStatus.ACTIVE)
                .roles(Set.of(User.Role.ADMIN))
                .build();
        user.setId(id);
        return user;
    }
}
