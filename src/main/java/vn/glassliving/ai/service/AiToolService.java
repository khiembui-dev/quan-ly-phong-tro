package vn.glassliving.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import vn.glassliving.ai.entity.AiConversation;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.maintenance.entity.MaintenanceTicket;
import vn.glassliving.maintenance.repository.MaintenanceTicketRepository;
import vn.glassliving.maintenance.service.MaintenanceService;
import vn.glassliving.notification.entity.Notification;
import vn.glassliving.notification.repository.NotificationRepository;
import vn.glassliving.payment.entity.Payment;
import vn.glassliving.payment.repository.PaymentRepository;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;
import vn.glassliving.utility.entity.UtilityReading;
import vn.glassliving.utility.repository.UtilityReadingRepository;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiToolService {

    private static final String SOURCE_SUMMARY = "Dữ liệu từ hệ thống SmartRent";
    private static final String GUIDE_SOURCE_SUMMARY = "Hướng dẫn SmartRent";
    private static final String NO_DATA_MESSAGE = "Hiện hệ thống chưa có dữ liệu này. Anh có thể kiểm tra lại hoặc gửi ticket hỗ trợ.";
    private static final String RESTRICTED_ADMIN_MESSAGE = "Thông tin doanh thu chỉ dành cho tài khoản quản trị.";
    private static final String UNSAFE_MESSAGE = "Em không thể hỗ trợ nội dung này. Em có thể hướng dẫn anh sử dụng SmartRent hoặc xử lý các vấn đề trong hệ thống.";
    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final UtilityReadingRepository utilityReadingRepository;
    private final NotificationRepository notificationRepository;
    private final MaintenanceTicketRepository ticketRepository;
    private final MaintenanceService maintenanceService;
    private final UserRepository userRepository;

    public ToolContext buildContext(UUID userId, AiConversation.Role role, String message) {
        DetectedIntent detectedIntent = new IntentDetector().detect(role, message);
        return buildContext(userId, role, detectedIntent, message);
    }

    public ToolContext buildContext(UUID userId,
                                    AiConversation.Role role,
                                    DetectedIntent detectedIntent,
                                    String message) {
        DetectedIntent detected = detectedIntent != null
                ? detectedIntent
                : new IntentDetector().detect(role, message);
        if (!detected.inScope()) {
            String messageText = detected.intent() == AiIntent.RESTRICTED_ADMIN_DATA
                    ? RESTRICTED_ADMIN_MESSAGE
                    : UNSAFE_MESSAGE;
            return ToolContext.outOfScope(detected, messageText, defaultSuggestions(role));
        }
        ToolContext context = role == AiConversation.Role.ADMIN
                ? adminContext(userId, detected, message)
                : customerContext(userId, detected, message);
        if (context.requiresData() && !context.dataFound() && isBlank(context.directAnswer())) {
            return ToolContext.noData(detected, context.context(), context.suggestions());
        }
        return context;
    }

    public List<Room> getMyRoom(UUID currentUser) {
        return safeList(roomRepository.findByCurrentTenantId(currentUser));
    }

    public List<Invoice> getMyInvoices(UUID currentUser) {
        return invoiceRepository.findByTenantUserId(
                currentUser,
                PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "issueDate"))).getContent();
    }

    public Optional<User> getMyProfile(UUID currentUser) {
        if (currentUser == null || userRepository == null) return Optional.empty();
        return userRepository.findById(currentUser);
    }

    public List<Invoice> getMyUnpaidInvoices(UUID currentUser) {
        return getMyInvoices(currentUser).stream()
                .filter(this::isOpenInvoice)
                .toList();
    }

    public List<Payment> getMyPayments(UUID currentUser) {
        if (paymentRepository == null) return List.of();
        List<Payment> payments = new ArrayList<>();
        getMyInvoices(currentUser).stream()
                .map(Invoice::getId)
                .filter(Objects::nonNull)
                .limit(12)
                .forEach(invoiceId -> payments.addAll(paymentRepository.findTop10ByInvoiceIdOrderByCreatedAtDesc(invoiceId)));
        return payments.stream()
                .filter(payment -> Objects.equals(payment.getUserId(), currentUser))
                .limit(20)
                .toList();
    }

    public List<Room> getMyRoomContractOrRentalPeriod(UUID currentUser) {
        return getMyRoom(currentUser);
    }

    public Optional<Invoice> getMyInvoiceDetail(UUID currentUser, UUID invoiceId) {
        if (invoiceId == null) return Optional.empty();
        return invoiceRepository.findById(invoiceId)
                .filter(invoice -> Objects.equals(invoice.getTenantUserId(), currentUser));
    }

    public List<UtilityReading> getMyUtilityHistory(UUID currentUser) {
        List<UtilityReading> readings = new ArrayList<>();
        for (Room room : getMyRoom(currentUser).stream().limit(3).toList()) {
            readings.addAll(utilityReadingRepository.findByRoomIdOrderByPeriodYearDescPeriodMonthDesc(
                    room.getId(), PageRequest.of(0, 3)).getContent());
        }
        return readings;
    }

    public List<MaintenanceTicket> getMyTickets(UUID currentUser) {
        return safeList(ticketRepository.findTop8ByReporterUserIdOrderByReportedAtDesc(currentUser));
    }

    public List<Notification> getMyNotifications(UUID currentUser) {
        return safeList(notificationRepository.findTop10ByUserIdOrderByCreatedAtDesc(currentUser));
    }

    public List<User> getLandlordContact(UUID currentUser) {
        return getMyRoom(currentUser).stream()
                .map(Room::getOwnerId)
                .filter(Objects::nonNull)
                .distinct()
                .limit(3)
                .map(userRepository::findById)
                .flatMap(Optional::stream)
                .toList();
    }

    public List<Room> getAvailableRooms() {
        return roomRepository.findByStatus(
                Room.RoomStatus.AVAILABLE,
                PageRequest.of(0, 8, Sort.by(Sort.Direction.ASC, "priceMonthly"))).getContent();
    }

    public MaintenanceTicket createSupportTicket(UUID userId, String title, String content, String type, String priority) {
        if (userId == null) throw new IllegalArgumentException("Missing user");
        String cleanTitle = truncate(firstNonBlank(title, content), 160);
        String cleanContent = truncate(firstNonBlank(content, title), 2000);
        if (cleanTitle.isBlank() || cleanContent.isBlank()) {
            throw new IllegalArgumentException("Missing ticket content");
        }
        Room room = roomRepository.findByCurrentTenantId(userId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Customer has no current room"));
        return maintenanceService.createForReporter(
                userId,
                room.getOwnerId(),
                room.getPropertyId(),
                room.getId(),
                firstNonBlank(type, "OTHER"),
                firstNonBlank(priority, "NORMAL"),
                cleanTitle,
                cleanContent);
    }

    public Map<String, Object> getAdminDashboardSummary(UUID ownerId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("availableRooms", roomRepository.countByOwnerIdAndStatus(ownerId, Room.RoomStatus.AVAILABLE));
        summary.put("occupiedRooms", roomRepository.countByOwnerIdAndStatus(ownerId, Room.RoomStatus.OCCUPIED));
        summary.put("maintenanceRooms", roomRepository.countByOwnerIdAndStatus(ownerId, Room.RoomStatus.MAINTENANCE));
        summary.put("paidRevenue", nullToZero(invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.PAID)));
        summary.put("pendingDebt", nullToZero(invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.PENDING))
                .add(nullToZero(invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.OVERDUE))));
        return summary;
    }

    public Map<String, Object> getRevenueSummary(UUID ownerId, Integer month, Integer year) {
        LocalDate now = LocalDate.now();
        short periodYear = (short) (year != null ? year.intValue() : now.getYear());
        short periodMonth = (short) (month != null ? month.intValue() : now.getMonthValue());
        List<Invoice> invoices = invoiceRepository.findForReport(ownerId, periodYear, periodMonth, periodYear, periodMonth);
        if (invoices == null) {
            return getAdminDashboardSummary(ownerId);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("period", String.format("%02d/%d", periodMonth, periodYear));
        summary.put("invoiceCount", invoices.size());
        summary.put("paidRevenue", invoices.stream()
                .filter(invoice -> invoice.getStatus() == Invoice.InvoiceStatus.PAID)
                .map(Invoice::getTotalAmount)
                .map(this::nullToZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        summary.put("pendingDebt", invoices.stream()
                .filter(this::isOpenInvoice)
                .map(this::remainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return summary;
    }

    public List<Invoice> getUnpaidInvoices(UUID ownerId) {
        List<Invoice> invoices = new ArrayList<>();
        invoices.addAll(invoiceRepository.findByOwnerIdAndStatus(ownerId, Invoice.InvoiceStatus.PENDING, PageRequest.of(0, 8)).getContent());
        invoices.addAll(invoiceRepository.findByOwnerIdAndStatus(ownerId, Invoice.InvoiceStatus.OVERDUE, PageRequest.of(0, 8)).getContent());
        return invoices;
    }

    public List<Invoice> getOverdueInvoices(UUID ownerId) {
        return invoiceRepository.findByOwnerIdAndStatus(ownerId, Invoice.InvoiceStatus.OVERDUE, PageRequest.of(0, 10)).getContent();
    }

    public Map<String, Object> getRoomStatusSummary(UUID ownerId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("availableRooms", roomRepository.countByOwnerIdAndStatus(ownerId, Room.RoomStatus.AVAILABLE));
        summary.put("occupiedRooms", roomRepository.countByOwnerIdAndStatus(ownerId, Room.RoomStatus.OCCUPIED));
        summary.put("maintenanceRooms", roomRepository.countByOwnerIdAndStatus(ownerId, Room.RoomStatus.MAINTENANCE));
        return summary;
    }

    public List<Room> getOccupiedRooms(UUID ownerId) {
        return roomRepository.findByOwnerId(ownerId, PageRequest.of(0, 12, Sort.by("code"))).getContent().stream()
                .filter(room -> room.getStatus() == Room.RoomStatus.OCCUPIED)
                .toList();
    }

    public Map<String, Object> getDashboardSummary(UUID ownerId) {
        return getAdminDashboardSummary(ownerId);
    }

    public Map<String, Object> getRoomSummary(UUID ownerId) {
        return getRoomStatusSummary(ownerId);
    }

    public Map<String, Object> getCustomerSummary(UUID ownerId) {
        List<Room> rooms = roomRepository.findByOwnerId(ownerId, PageRequest.of(0, 300, Sort.by("code"))).getContent();
        long activeCustomers = rooms.stream()
                .map(Room::getCurrentTenantId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("activeCustomers", activeCustomers);
        summary.put("occupiedRooms", rooms.stream().filter(room -> room.getStatus() == Room.RoomStatus.OCCUPIED).count());
        summary.put("availableRooms", rooms.stream().filter(room -> room.getStatus() == Room.RoomStatus.AVAILABLE).count());
        return summary;
    }

    public Map<String, Object> getInvoiceSummary(UUID ownerId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalInvoices", invoiceRepository.countByOwnerId(ownerId));
        summary.put("pendingInvoices", invoiceRepository.countByOwnerIdAndStatus(ownerId, Invoice.InvoiceStatus.PENDING));
        summary.put("overdueInvoices", invoiceRepository.countByOwnerIdAndStatus(ownerId, Invoice.InvoiceStatus.OVERDUE));
        summary.put("paidInvoices", invoiceRepository.countByOwnerIdAndStatus(ownerId, Invoice.InvoiceStatus.PAID));
        summary.put("pendingDebt", nullToZero(invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.PENDING))
                .add(nullToZero(invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.OVERDUE))));
        return summary;
    }

    public List<Room> searchRooms(UUID ownerId, String keyword) {
        String cleanKeyword = normalize(keyword);
        return roomRepository.findByOwnerId(ownerId, PageRequest.of(0, 20, Sort.by("code"))).getContent().stream()
                .filter(room -> cleanKeyword.isBlank()
                        || normalize(room.getCode()).contains(cleanKeyword)
                        || normalize(room.getTitle()).contains(cleanKeyword)
                        || normalize(room.getAddressLine()).contains(cleanKeyword)
                        || normalize(room.getDistrict()).contains(cleanKeyword))
                .limit(10)
                .toList();
    }

    public List<User> searchCustomers(UUID ownerId, String keyword) {
        return searchCustomer(ownerId, keyword);
    }

    public List<User> searchCustomer(UUID ownerId, String keyword) {
        String cleanKeyword = normalize(keyword);
        List<UUID> tenantIds = roomRepository.findByOwnerId(ownerId, PageRequest.of(0, 200, Sort.by("code"))).getContent().stream()
                .map(Room::getCurrentTenantId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (tenantIds.isEmpty()) return List.of();
        return userRepository.findAllById(tenantIds).stream()
                .filter(user -> cleanKeyword.isBlank()
                        || normalize(user.getFullName()).contains(cleanKeyword)
                        || normalize(user.getEmail()).contains(cleanKeyword)
                        || normalize(user.getPhone()).contains(cleanKeyword))
                .limit(8)
                .toList();
    }

    public List<MaintenanceTicket> getRecentTickets(UUID ownerId) {
        return ticketRepository.findTopForOwnerStats(ownerId, PageRequest.of(0, 8));
    }

    public Map<String, Object> getTicketSummary(UUID ownerId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("openTickets", ticketRepository.countByOwnerIdAndStatus(ownerId, MaintenanceTicket.Status.OPEN));
        summary.put("inProgressTickets", ticketRepository.countByOwnerIdAndStatus(ownerId, MaintenanceTicket.Status.IN_PROGRESS));
        summary.put("resolvedTickets", ticketRepository.countByOwnerIdAndStatus(ownerId, MaintenanceTicket.Status.RESOLVED));
        summary.put("recentTickets", getRecentTickets(ownerId));
        return summary;
    }

    public Map<String, Object> getPaymentSummary(UUID ownerId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("paid", nullToZero(invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.PAID)));
        summary.put("pending", nullToZero(invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.PENDING)));
        summary.put("overdue", nullToZero(invoiceRepository.sumTotalByOwnerAndStatus(ownerId, Invoice.InvoiceStatus.OVERDUE)));
        return summary;
    }

    public Map<String, Object> getUtilityAnomalies(UUID ownerId) {
        LocalDate now = LocalDate.now();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("currentMonthReadings", utilityReadingRepository.countByOwnerAndPeriod(ownerId, (short) now.getYear(), (short) now.getMonthValue()));
        summary.put("period", String.format("%02d/%d", now.getMonthValue(), now.getYear()));
        return summary;
    }

    private ToolContext customerContext(UUID userId, DetectedIntent detected, String message) {
        List<String> suggestions = new ArrayList<>();
        StringBuilder data = new StringBuilder();
        boolean dataFound = false;
        String directAnswer = null;
        String sourceSummary = detected.requiresData() ? SOURCE_SUMMARY : GUIDE_SOURCE_SUMMARY;

        switch (detected.intent()) {
            case GENERAL_HELP, UNKNOWN -> data.append(websiteGuideData(AiConversation.Role.CUSTOMER));
            case CUSTOMER_ACCOUNT -> {
                if (!detected.requiresData()) {
                    data.append(websiteGuideData(AiConversation.Role.CUSTOMER));
                    suggestions.add("Cập nhật hồ sơ");
                } else {
                    Optional<User> profile = getMyProfile(userId);
                    appendProfile(data, profile.orElse(null));
                    dataFound = profile.isPresent();
                    profile.ifPresent(user -> suggestions.add("Cập nhật hồ sơ"));
                }
            }
            case ROOM, MY_ROOM -> {
                if ("getAvailableRooms".equals(detected.toolName())) {
                    List<Room> rooms = getAvailableRooms();
                    appendRooms(data, "availableRooms", rooms);
                    dataFound = !rooms.isEmpty();
                    if (dataFound) directAnswer = buildAvailableRoomsAnswer(rooms);
                    suggestions.add("Xem phòng trống");
                } else {
                    List<Room> rooms = getMyRoomContractOrRentalPeriod(userId);
                    appendRooms(data, "myRooms", rooms);
                    dataFound = !rooms.isEmpty();
                    if (dataFound) directAnswer = buildRoomAnswer(rooms);
                    suggestions.add("Phòng của tôi");
                }
            }
            case PROPERTY -> {
                List<Room> rooms = getMyRoom(userId);
                appendProperties(data, rooms);
                dataFound = !rooms.isEmpty();
                suggestions.add("Phòng của tôi");
            }
            case INVOICE, MY_INVOICE, PAYMENT_GUIDE -> {
                List<Room> rooms = getMyRoom(userId);
                List<Invoice> invoices = getMyInvoices(userId);
                appendCustomerInvoiceData(data, invoices, rooms);
                dataFound = !invoices.isEmpty();
                if (dataFound) directAnswer = buildCustomerInvoiceAnswer(LocalDate.now(), invoices, rooms);
                if (invoices.stream().anyMatch(this::isOpenInvoice)) suggestions.add("Thanh toán ngay");
            }
            case INVOICE_DETAIL -> {
                UUID invoiceId = extractUuid(message).orElse(null);
                Optional<Invoice> invoice = getMyInvoiceDetail(userId, invoiceId);
                invoice.ifPresent(value -> appendInvoice(data, value, Map.of()));
                dataFound = invoice.isPresent();
                suggestions.add("Xem chi tiết hóa đơn");
            }
            case PAYMENT -> {
                if (!detected.requiresData()) {
                    data.append(paymentGuideData());
                    suggestions.add("Hóa đơn tháng này");
                    suggestions.add("Thanh toán QR");
                } else {
                    List<Payment> payments = getMyPayments(userId);
                    appendPayments(data, payments);
                    dataFound = !payments.isEmpty();
                    if (dataFound) directAnswer = buildPaymentAnswer(payments);
                    suggestions.add("Hóa đơn tháng này");
                }
            }
            case UTILITY, UTILITY_HISTORY -> {
                List<Room> rooms = getMyRoom(userId);
                Map<UUID, Room> roomMap = rooms.stream()
                        .filter(room -> room.getId() != null)
                        .collect(Collectors.toMap(Room::getId, room -> room, (left, right) -> left));
                List<UtilityReading> readings = getMyUtilityHistory(userId);
                data.append("utilityReadings:\n");
                if (readings.isEmpty()) {
                    data.append("[]\n");
                } else {
                    readings.forEach(reading -> appendUtility(data, roomMap.get(reading.getRoomId()), reading));
                }
                dataFound = !readings.isEmpty();
                if (dataFound) directAnswer = buildUtilityAnswer(readings, roomMap);
                suggestions.add("Lịch sử điện nước");
            }
            case TICKET, MY_TICKET, CREATE_TICKET -> {
                if (!detected.requiresData() || detected.intent() == AiIntent.CREATE_TICKET) {
                    data.append(ticketGuideData());
                    suggestions.add("Gửi ticket hỗ trợ");
                } else {
                    List<MaintenanceTicket> tickets = getMyTickets(userId);
                    appendTickets(data, "myTickets", tickets);
                    dataFound = !tickets.isEmpty();
                    suggestions.add("Gửi ticket hỗ trợ");
                }
            }
            case NOTIFICATION -> {
                List<Notification> notifications = getMyNotifications(userId);
                appendNotifications(data, notifications);
                dataFound = !notifications.isEmpty();
            }
            case LANDLORD_CONTACT -> {
                List<User> owners = getLandlordContact(userId);
                appendContacts(data, owners);
                dataFound = !owners.isEmpty();
                if (dataFound) directAnswer = buildContactAnswer(owners);
                suggestions.add("Liên hệ chủ trọ");
            }
            case AVAILABLE_ROOMS -> {
                List<Room> rooms = getAvailableRooms();
                appendRooms(data, "availableRooms", rooms);
                dataFound = !rooms.isEmpty();
                if (dataFound) directAnswer = buildAvailableRoomsAnswer(rooms);
                suggestions.add("Xem phòng trống");
            }
            default -> {
                dataFound = false;
            }
        }

        return new ToolContext(
                structuredContext(AiConversation.Role.CUSTOMER, detected, data),
                distinctSuggestions(suggestions, AiConversation.Role.CUSTOMER),
                directAnswer,
                detected,
                dataFound,
                sourceSummary);
    }

    private ToolContext adminContext(UUID ownerId, DetectedIntent detected, String message) {
        List<String> suggestions = new ArrayList<>();
        StringBuilder data = new StringBuilder();
        boolean dataFound = true;
        String directAnswer = null;
        String sourceSummary = detected.requiresData() ? SOURCE_SUMMARY : GUIDE_SOURCE_SUMMARY;

        switch (detected.intent()) {
            case GENERAL_HELP, UNKNOWN -> {
                data.append(websiteGuideData(AiConversation.Role.ADMIN));
                dataFound = false;
            }
            case ADMIN_DASHBOARD, DASHBOARD_SUMMARY, ROOM_STATUS -> {
                if ("getRevenueSummary".equals(detected.toolName())) {
                    Map<String, Object> summary = getRevenueSummary(ownerId, null, null);
                    appendMap(data, "revenueSummary", summary);
                    directAnswer = "Doanh thu đã thu: " + vnd((BigDecimal) summary.get("paidRevenue"))
                            + ". Công nợ còn lại: " + vnd((BigDecimal) summary.get("pendingDebt")) + ".";
                    suggestions.add("Hóa đơn chưa thanh toán");
                } else if ("getRoomSummary".equals(detected.toolName()) || detected.intent() == AiIntent.ROOM_STATUS) {
                    Map<String, Object> summary = getRoomSummary(ownerId);
                    appendMap(data, "roomStatus", summary);
                    directAnswer = "Tình trạng phòng: " + summary.get("availableRooms")
                            + " phòng trống, " + summary.get("occupiedRooms")
                            + " phòng đang thuê, " + summary.get("maintenanceRooms") + " phòng bảo trì.";
                    suggestions.add("Phòng còn trống");
                } else if ("getCustomerSummary".equals(detected.toolName())) {
                    Map<String, Object> summary = getCustomerSummary(ownerId);
                    appendMap(data, "customerSummary", summary);
                    directAnswer = "Khách thuê đang hoạt động: " + summary.get("activeCustomers")
                            + ". Phòng đang thuê: " + summary.get("occupiedRooms")
                            + ", phòng trống: " + summary.get("availableRooms") + ".";
                    suggestions.add("Khách thuê cần xử lý");
                } else {
                    Map<String, Object> summary = getDashboardSummary(ownerId);
                    appendMap(data, "dashboardSummary", summary);
                    directAnswer = "Tổng quan hiện tại: phòng trống " + summary.get("availableRooms")
                            + ", phòng đang thuê " + summary.get("occupiedRooms")
                            + ", phòng bảo trì " + summary.get("maintenanceRooms")
                            + ", đã thu " + vnd((BigDecimal) summary.get("paidRevenue"))
                            + ", công nợ " + vnd((BigDecimal) summary.get("pendingDebt")) + ".";
                }
            }
            case REVENUE_SUMMARY -> {
                Map<String, Object> summary = getRevenueSummary(ownerId, null, null);
                appendMap(data, "revenueSummary", summary);
                directAnswer = "Doanh thu đã thu: " + vnd((BigDecimal) summary.get("paidRevenue"))
                        + ". Công nợ còn lại: " + vnd((BigDecimal) summary.get("pendingDebt")) + ".";
                suggestions.add("Hóa đơn chưa thanh toán");
            }
            case INVOICE -> {
                if ("getInvoiceSummary".equals(detected.toolName())) {
                    Map<String, Object> summary = getInvoiceSummary(ownerId);
                    appendMap(data, "invoiceSummary", summary);
                    directAnswer = "Tổng hóa đơn: " + summary.get("totalInvoices")
                            + ". Đã thanh toán: " + summary.get("paidInvoices")
                            + ", chưa thanh toán: " + summary.get("pendingInvoices")
                            + ", quá hạn: " + summary.get("overdueInvoices")
                            + ". Công nợ còn lại: " + vnd((BigDecimal) summary.get("pendingDebt")) + ".";
                    suggestions.add("Hóa đơn chưa thanh toán");
                    suggestions.add("Hóa đơn quá hạn");
                } else {
                    List<Invoice> invoices = getUnpaidInvoices(ownerId);
                    appendInvoiceList(data, "unpaidInvoices", invoices);
                    directAnswer = invoices.isEmpty()
                            ? "Hiện không có hóa đơn chưa thanh toán trong dữ liệu quản trị."
                            : "Hiện có " + invoices.size() + " hóa đơn chưa thanh toán gần đây. Tổng hiển thị: " + vnd(sumRemaining(invoices)) + ".";
                    suggestions.add("Nhắc thanh toán");
                }
            }
            case UNPAID_INVOICES -> {
                List<Invoice> invoices = getUnpaidInvoices(ownerId);
                appendInvoiceList(data, "unpaidInvoices", invoices);
                directAnswer = invoices.isEmpty()
                        ? "Hiện không có hóa đơn chưa thanh toán trong dữ liệu quản trị."
                        : "Hiện có " + invoices.size() + " hóa đơn chưa thanh toán gần đây. Tổng hiển thị: " + vnd(sumRemaining(invoices)) + ".";
                suggestions.add("Nhắc thanh toán");
            }
            case OVERDUE_INVOICES -> {
                List<Invoice> invoices = getOverdueInvoices(ownerId);
                appendInvoiceList(data, "overdueInvoices", invoices);
                directAnswer = invoices.isEmpty()
                        ? "Hiện không có hóa đơn quá hạn trong dữ liệu quản trị."
                        : "Hiện có " + invoices.size() + " hóa đơn quá hạn gần đây. Tổng cần xử lý: " + vnd(sumRemaining(invoices)) + ".";
            }
            case ROOM -> {
                Map<String, Object> summary = getRoomSummary(ownerId);
                appendMap(data, "roomStatus", summary);
                directAnswer = "Tình trạng phòng: " + summary.get("availableRooms")
                        + " phòng trống, " + summary.get("occupiedRooms")
                        + " phòng đang thuê, " + summary.get("maintenanceRooms") + " phòng bảo trì.";
                suggestions.add("Phòng còn trống");
            }
            case CUSTOMER_SEARCH -> {
                List<User> customers = searchCustomer(ownerId, message);
                appendContacts(data, customers);
                dataFound = !customers.isEmpty();
            }
            case TICKET, TICKET_SUMMARY -> {
                Map<String, Object> summary = getTicketSummary(ownerId);
                appendMap(data, "ticketSummary", summary);
                @SuppressWarnings("unchecked")
                List<MaintenanceTicket> recentTickets = (List<MaintenanceTicket>) summary.get("recentTickets");
                appendTickets(data, "recentTickets", recentTickets);
                directAnswer = "Ticket hiện tại: " + summary.get("openTickets")
                        + " mới, " + summary.get("inProgressTickets")
                        + " đang xử lý, " + summary.get("resolvedTickets") + " đã hoàn tất.";
                suggestions.add("Ticket mới");
            }
            case PAYMENT, PAYMENT_SUMMARY -> {
                Map<String, Object> summary = getPaymentSummary(ownerId);
                appendMap(data, "paymentSummary", summary);
                directAnswer = "Thanh toán: đã thu " + vnd((BigDecimal) summary.get("paid"))
                        + ", đang chờ " + vnd((BigDecimal) summary.get("pending"))
                        + ", quá hạn " + vnd((BigDecimal) summary.get("overdue")) + ".";
            }
            case UTILITY, UTILITY_ANOMALY -> {
                Map<String, Object> summary = getUtilityAnomalies(ownerId);
                appendMap(data, "utilitySummary", summary);
                directAnswer = "Tháng " + summary.get("period") + " đã ghi " + summary.get("currentMonthReadings")
                        + " chỉ số điện nước. Hệ thống chưa có phân tích bất thường nâng cao.";
            }
            default -> dataFound = false;
        }

        return new ToolContext(
                structuredContext(AiConversation.Role.ADMIN, detected, data),
                distinctSuggestions(suggestions, AiConversation.Role.ADMIN),
                directAnswer,
                detected,
                dataFound,
                sourceSummary);
    }

    private String websiteGuideData(AiConversation.Role role) {
        String adminFeatures = role == AiConversation.Role.ADMIN
                ? "\n- quản lý phòng, cơ sở/chi nhánh, khách thuê\n- quản lý hóa đơn, thanh toán, công nợ\n- báo cáo doanh thu, dashboard tổng quan\n- xử lý ticket và thông báo"
                : "";
        return """
                helpScope:
                - Hướng dẫn sử dụng website SmartRent
                - xem phòng đang thuê, hạn thuê và thông tin phòng
                - xem phòng trống và gửi yêu cầu đặt phòng
                - xem hóa đơn, công nợ, trạng thái thanh toán
                - thanh toán QR/chuyển khoản và kiểm tra thanh toán
                - theo dõi chỉ số điện nước
                - gửi ticket hỗ trợ, phản hồi ticket, đính kèm ảnh
                - xem thông báo và liên hệ chủ trọ
                - quản lý tài khoản cá nhân%s

                guideRules:
                - Nếu câu hỏi chỉ là hướng dẫn thao tác, trả lời trực tiếp theo chức năng SmartRent.
                - Không tự bịa dữ liệu thật như số tiền, phòng, khách thuê, hóa đơn, doanh thu.
                """.formatted(adminFeatures);
    }

    private String paymentGuideData() {
        return """
                paymentGuide:
                - Vào trang Hóa đơn, chọn hóa đơn cần thanh toán.
                - Bấm Thanh toán để mở màn hình QR.
                - Quét QR hoặc chuyển khoản đúng số tiền và đúng nội dung hệ thống hiển thị.
                - Sau khi chuyển khoản, bấm Tôi đã chuyển khoản hoặc Kiểm tra thanh toán.
                - Nếu hệ thống chưa thấy giao dịch, chờ 1-3 phút rồi kiểm tra lại.
                """;
    }

    private String ticketGuideData() {
        return """
                ticketGuide:
                - Vào mục Hỗ trợ/Thông báo và hỗ trợ.
                - Bấm Ticket mới hoặc Tạo ticket.
                - Chọn phòng, loại yêu cầu, mức độ ưu tiên.
                - Nhập tiêu đề, mô tả rõ tình trạng, vị trí và thời điểm phát hiện.
                - Đính kèm ảnh nếu có rồi gửi để chủ trọ xử lý.
                """;
    }

    private void appendProfile(StringBuilder data, User user) {
        data.append("profile:\n");
        if (user == null) {
            data.append("[]\n");
            return;
        }
        data.append("- name: ").append(nullToDash(user.getFullName()))
                .append(", email: ").append(nullToDash(user.getEmail()))
                .append(", phone: ").append(nullToDash(user.getPhone()))
                .append(", status: ").append(user.getStatus())
                .append(", phoneVerified: ").append(user.isPhoneVerified())
                .append(", emailVerified: ").append(user.isEmailVerified())
                .append("\n");
    }

    private void appendProperties(StringBuilder data, List<Room> rooms) {
        data.append("properties:\n");
        List<UUID> propertyIds = safeList(rooms).stream()
                .map(Room::getPropertyId)
                .filter(Objects::nonNull)
                .distinct()
                .limit(8)
                .toList();
        if (propertyIds.isEmpty()) {
            data.append("[]\n");
            return;
        }
        propertyIds.forEach(propertyId -> propertyRepository.findById(propertyId).ifPresent(property -> data
                .append("- name: ").append(nullToDash(property.getName()))
                .append(", address: ").append(nullToDash(property.getAddressLine()))
                .append(", district: ").append(nullToDash(property.getDistrict()))
                .append(", city: ").append(nullToDash(property.getCity()))
                .append(", totalRooms: ").append(nullToDash(property.getTotalRooms()))
                .append("\n")));
    }

    private void appendPayments(StringBuilder data, List<Payment> payments) {
        data.append("payments:\n");
        if (payments == null || payments.isEmpty()) {
            data.append("[]\n");
            return;
        }
        payments.stream().limit(10).forEach(payment -> data.append("- code: ")
                .append(nullToDash(payment.getCode()))
                .append(", amount: ").append(vnd(payment.getAmount()))
                .append(", method: ").append(payment.getMethod())
                .append(", status: ").append(payment.getStatus())
                .append(", paidAt: ").append(nullToDash(payment.getPaidAt()))
                .append("\n"));
    }

    private String buildPaymentAnswer(List<Payment> payments) {
        List<Payment> safePayments = safeList(payments);
        if (safePayments.isEmpty()) return NO_DATA_MESSAGE;
        StringBuilder answer = new StringBuilder("Lịch sử thanh toán gần đây:\n");
        safePayments.stream().limit(5).forEach(payment -> answer.append("- ")
                .append(nullToDash(payment.getCode()))
                .append(": ").append(vnd(payment.getAmount()))
                .append(", trạng thái ").append(payment.getStatus())
                .append(", thời gian ").append(nullToDash(payment.getPaidAt()))
                .append(".\n"));
        return answer.toString().trim();
    }

    private String buildRoomAnswer(List<Room> rooms) {
        List<Room> safeRooms = safeList(rooms);
        if (safeRooms.isEmpty()) return NO_DATA_MESSAGE;
        StringBuilder answer = new StringBuilder("Phòng anh đang thuê:\n");
        safeRooms.stream().limit(4).forEach(room -> answer.append("- ")
                .append(nullToDash(room.getCode()))
                .append(" - ").append(nullToDash(room.getTitle()))
                .append(", giá ").append(vnd(room.getPriceMonthly()))
                .append(", trạng thái ").append(room.getStatus())
                .append(", đã thanh toán đến ").append(nullToDash(room.getCurrentTenantPaidUntil()))
                .append(".\n"));
        return answer.toString().trim();
    }

    private String buildUtilityAnswer(List<UtilityReading> readings, Map<UUID, Room> roomMap) {
        List<UtilityReading> safeReadings = safeList(readings);
        if (safeReadings.isEmpty()) return NO_DATA_MESSAGE;
        StringBuilder answer = new StringBuilder("Chỉ số điện nước gần đây:\n");
        safeReadings.stream().limit(4).forEach(reading -> {
            Room room = roomMap != null ? roomMap.get(reading.getRoomId()) : null;
            answer.append("- Phòng ")
                    .append(room != null ? nullToDash(room.getCode()) : "Chưa cập nhật")
                    .append(" kỳ ").append(reading.getPeriodMonth()).append("/").append(reading.getPeriodYear())
                    .append(": điện ").append(reading.getElectricUsage()).append(" kWh, ")
                    .append(vnd(reading.getElectricAmount()))
                    .append("; nước ").append(reading.getWaterUsage()).append(" m3, ")
                    .append(vnd(reading.getWaterAmount()))
                    .append(".\n");
        });
        return answer.toString().trim();
    }

    private String buildContactAnswer(List<User> owners) {
        List<User> safeOwners = safeList(owners);
        if (safeOwners.isEmpty()) return NO_DATA_MESSAGE;
        StringBuilder answer = new StringBuilder("Liên hệ chủ trọ/quản trị viên:\n");
        safeOwners.stream().limit(3).forEach(owner -> answer.append("- ")
                .append(nullToDash(owner.getFullName()))
                .append(", số điện thoại ").append(nullToDash(owner.getPhone()))
                .append(", email ").append(nullToDash(owner.getEmail()))
                .append(".\n"));
        return answer.toString().trim();
    }

    private String buildAvailableRoomsAnswer(List<Room> rooms) {
        List<Room> safeRooms = safeList(rooms);
        if (safeRooms.isEmpty()) return NO_DATA_MESSAGE;
        StringBuilder answer = new StringBuilder("Một số phòng đang trống:\n");
        safeRooms.stream().limit(5).forEach(room -> answer.append("- ")
                .append(nullToDash(room.getCode()))
                .append(" - ").append(nullToDash(room.getTitle()))
                .append(", giá ").append(vnd(room.getPriceMonthly()))
                .append(", trạng thái ").append(room.getStatus())
                .append(".\n"));
        return answer.toString().trim();
    }

    private String structuredContext(AiConversation.Role role, DetectedIntent detected, StringBuilder data) {
        String cleanData = data == null || data.toString().isBlank() ? "[]" : data.toString().trim();
        String source = detected.requiresData() ? SOURCE_SUMMARY : GUIDE_SOURCE_SUMMARY;
        String rule = detected.requiresData()
                ? """
                Chỉ được trả lời dựa trên DATA.
                Nếu DATA rỗng hoặc thiếu, nói rõ "Hiện hệ thống chưa có dữ liệu này."
                Không tự tạo số tiền, tên phòng, ngày tháng, hóa đơn, ticket hoặc doanh thu.
                """
                : """
                Đây là ngữ cảnh hướng dẫn sử dụng website SmartRent.
                Được trả lời trực tiếp các câu hỏi hướng dẫn thao tác hoặc giải thích chức năng.
                Không tự tạo số tiền, tên phòng, khách thuê, hóa đơn, ticket hoặc doanh thu.
                """;
        return """
                SYSTEM_CONTEXT:
                Role: %s
                Intent: %s
                Tool: %s
                Data source: %s

                DATA:
                %s

                RULE:
                %s
                """.formatted(role, detected.intent(), detected.toolName(), source, cleanData, rule.trim());
    }

    private void appendCustomerInvoiceData(StringBuilder data, List<Invoice> invoices, List<Room> rooms) {
        Map<UUID, String> roomLabels = roomLabels(rooms);
        LocalDate today = LocalDate.now();
        List<Invoice> safeInvoices = safeList(invoices);
        List<Invoice> openInvoices = safeInvoices.stream().filter(this::isOpenInvoice).toList();
        List<Invoice> currentMonthInvoices = safeInvoices.stream()
                .filter(invoice -> samePeriod(invoice, today))
                .toList();

        data.append("currentDate: ").append(today).append("\n");
        data.append("openInvoiceTotal: ").append(vnd(sumRemaining(openInvoices))).append("\n");
        data.append("openInvoiceCount: ").append(openInvoices.size()).append("\n");
        appendInvoiceList(data, "currentMonthInvoices", currentMonthInvoices, roomLabels, 6);
        appendInvoiceList(data, "openInvoices", openInvoices, roomLabels, 8);
        appendInvoiceList(data, "recentInvoices", safeInvoices, roomLabels, 6);
    }

    private String buildCustomerInvoiceAnswer(LocalDate today, List<Invoice> invoices, List<Room> rooms) {
        List<Invoice> safeInvoices = safeList(invoices);
        if (safeInvoices.isEmpty()) return NO_DATA_MESSAGE;
        Map<UUID, String> labels = roomLabels(rooms);
        List<Invoice> currentMonthInvoices = safeInvoices.stream().filter(invoice -> samePeriod(invoice, today)).toList();
        List<Invoice> openInvoices = safeInvoices.stream().filter(this::isOpenInvoice).toList();
        String period = String.format("%02d/%d", today.getMonthValue(), today.getYear());
        StringBuilder answer = new StringBuilder();
        if (!currentMonthInvoices.isEmpty()) {
            answer.append("Hóa đơn tháng ").append(period).append(" của anh:\n");
            currentMonthInvoices.stream().limit(4).forEach(invoice -> answer.append("- ")
                    .append(nullToDash(invoice.getCode()))
                    .append(", phòng ").append(labels.getOrDefault(invoice.getRoomId(), "Chưa cập nhật"))
                    .append(": cần thanh toán ").append(vnd(remainingAmount(invoice)))
                    .append(", hạn ").append(nullToDash(invoice.getDueDate()))
                    .append(", trạng thái ").append(invoice.getStatus())
                    .append(".\n"));
        } else {
            answer.append("Hiện chưa có hóa đơn kỳ ").append(period).append(" trong hệ thống.\n");
        }
        if (!openInvoices.isEmpty()) {
            answer.append("Tổng cần thanh toán tất cả hóa đơn mở: ")
                    .append(vnd(sumRemaining(openInvoices)))
                    .append(" (").append(openInvoices.size()).append(" hóa đơn).");
        } else {
            answer.append("Anh không còn hóa đơn mở cần thanh toán.");
        }
        return answer.toString();
    }

    private void appendRooms(StringBuilder data, String key, List<Room> rooms) {
        data.append(key).append(":\n");
        if (rooms == null || rooms.isEmpty()) {
            data.append("[]\n");
            return;
        }
        rooms.stream().limit(8).forEach(room -> {
            String propertyName = "";
            if (propertyRepository != null && room.getPropertyId() != null) {
                propertyName = propertyRepository.findById(room.getPropertyId()).map(Property::getName).orElse("");
            }
            data.append("- code: ").append(nullToDash(room.getCode()))
                    .append(", title: ").append(nullToDash(room.getTitle()))
                    .append(propertyName.isBlank() ? "" : ", property: " + propertyName)
                    .append(", price: ").append(vnd(room.getPriceMonthly()))
                    .append(", status: ").append(room.getStatus())
                    .append(", paidUntil: ").append(nullToDash(room.getCurrentTenantPaidUntil()))
                    .append("\n");
        });
    }

    private void appendInvoiceList(StringBuilder data, String key, List<Invoice> invoices) {
        appendInvoiceList(data, key, invoices, Map.of(), 10);
    }

    private void appendInvoiceList(StringBuilder data, String key, List<Invoice> invoices, Map<UUID, String> roomLabels, int limit) {
        data.append(key).append(":\n");
        if (invoices == null || invoices.isEmpty()) {
            data.append("[]\n");
            return;
        }
        invoices.stream().limit(limit).forEach(invoice -> appendInvoice(data, invoice, roomLabels));
    }

    private void appendInvoice(StringBuilder data, Invoice invoice, Map<UUID, String> roomLabels) {
        data.append("- invoiceId: ").append(invoice.getId())
                .append(", code: ").append(nullToDash(invoice.getCode()))
                .append(", room: ").append(roomLabels.getOrDefault(invoice.getRoomId(), "Chưa cập nhật"))
                .append(", billingMonth: ").append(invoice.getPeriodMonth()).append("/").append(invoice.getPeriodYear())
                .append(", roomPrice: ").append(vnd(invoice.getRentAmount()))
                .append(", totalAmount: ").append(vnd(invoice.getTotalAmount()))
                .append(", paidAmount: ").append(vnd(invoice.getPaidAmount()))
                .append(", remainingAmount: ").append(vnd(remainingAmount(invoice)))
                .append(", dueDate: ").append(nullToDash(invoice.getDueDate()))
                .append(", status: ").append(invoice.getStatus())
                .append("\n");
    }

    private void appendUtility(StringBuilder data, Room room, UtilityReading reading) {
        data.append("- room: ").append(room != null ? nullToDash(room.getCode()) : "Chưa cập nhật")
                .append(", period: ").append(reading.getPeriodMonth()).append("/").append(reading.getPeriodYear())
                .append(", electricUsage: ").append(reading.getElectricUsage()).append(" kWh")
                .append(", electricAmount: ").append(vnd(reading.getElectricAmount()))
                .append(", waterUsage: ").append(reading.getWaterUsage()).append(" m3")
                .append(", waterAmount: ").append(vnd(reading.getWaterAmount()))
                .append("\n");
    }

    private void appendTickets(StringBuilder data, String key, List<MaintenanceTicket> tickets) {
        data.append(key).append(":\n");
        if (tickets == null || tickets.isEmpty()) {
            data.append("[]\n");
            return;
        }
        tickets.stream().limit(8).forEach(ticket -> data.append("- code: ")
                .append(nullToDash(ticket.getCode()))
                .append(", title: ").append(nullToDash(ticket.getTitle()))
                .append(", status: ").append(ticket.getStatus())
                .append(", priority: ").append(ticket.getPriority())
                .append(", reportedAt: ").append(nullToDash(ticket.getReportedAt()))
                .append("\n"));
    }

    private void appendNotifications(StringBuilder data, List<Notification> notifications) {
        data.append("notifications:\n");
        if (notifications == null || notifications.isEmpty()) {
            data.append("[]\n");
            return;
        }
        notifications.stream().limit(8).forEach(notification -> data.append("- title: ")
                .append(nullToDash(notification.getTitle()))
                .append(", body: ").append(nullToDash(notification.getBody()))
                .append("\n"));
    }

    private void appendContacts(StringBuilder data, List<User> users) {
        data.append("contacts:\n");
        if (users == null || users.isEmpty()) {
            data.append("[]\n");
            return;
        }
        users.stream().limit(8).forEach(user -> data.append("- name: ")
                .append(nullToDash(user.getFullName()))
                .append(", phone: ").append(nullToDash(user.getPhone()))
                .append(", email: ").append(nullToDash(user.getEmail()))
                .append("\n"));
    }

    private void appendMap(StringBuilder data, String key, Map<String, Object> map) {
        data.append(key).append(":\n");
        map.forEach((name, value) -> {
            if (!(value instanceof Collection<?>)) {
                data.append("- ").append(name).append(": ").append(value).append("\n");
            }
        });
    }

    private Map<UUID, String> roomLabels(List<Room> rooms) {
        return safeList(rooms).stream()
                .filter(room -> room.getId() != null)
                .collect(Collectors.toMap(
                        Room::getId,
                        room -> nullToDash(room.getCode()) + " - " + nullToDash(room.getTitle()),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private Optional<UUID> extractUuid(String message) {
        Matcher matcher = UUID_PATTERN.matcher(message == null ? "" : message);
        if (!matcher.find()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(matcher.group()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private List<String> distinctSuggestions(List<String> suggestions, AiConversation.Role role) {
        LinkedHashSet<String> result = new LinkedHashSet<>(suggestions != null ? suggestions : List.of());
        result.addAll(defaultSuggestions(role));
        int limit = role == AiConversation.Role.ADMIN ? 6 : 8;
        return result.stream().limit(limit).toList();
    }

    private List<String> defaultSuggestions(AiConversation.Role role) {
        return role == AiConversation.Role.ADMIN
                ? List.of("Tổng quan hôm nay", "Doanh thu tháng này", "Phòng trống", "Hóa đơn chưa thanh toán", "Ticket mới", "Khách thuê cần xử lý")
                : List.of("Chatbot làm được gì?", "Phòng của tôi", "Hạn phòng của tôi", "Hóa đơn tháng này",
                "Thanh toán QR", "Lịch sử điện nước", "Gửi ticket", "Liên hệ chủ trọ");
    }

    private boolean samePeriod(Invoice invoice, LocalDate date) {
        return invoice != null
                && date != null
                && invoice.getPeriodMonth() != null
                && invoice.getPeriodYear() != null
                && invoice.getPeriodMonth() == date.getMonthValue()
                && invoice.getPeriodYear() == date.getYear();
    }

    private boolean isOpenInvoice(Invoice invoice) {
        return invoice != null
                && invoice.getStatus() != null
                && invoice.getStatus() != Invoice.InvoiceStatus.PAID
                && invoice.getStatus() != Invoice.InvoiceStatus.CANCELLED;
    }

    private BigDecimal sumRemaining(List<Invoice> invoices) {
        return safeList(invoices).stream()
                .map(this::remainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal remainingAmount(Invoice invoice) {
        if (invoice == null) return BigDecimal.ZERO;
        BigDecimal remaining = nullToZero(invoice.getTotalAmount()).subtract(nullToZero(invoice.getPaidAmount()));
        return remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining : BigDecimal.ZERO;
    }

    private String vnd(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        format.setMaximumFractionDigits(0);
        return format.format(nullToZero(amount)) + " VND";
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String nullToDash(Object value) {
        if (value == null) return "Chưa cập nhật";
        String text = String.valueOf(value).trim();
        return text.isBlank() ? "Chưa cập nhật" : text;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private String truncate(String value, int max) {
        String clean = value == null ? "" : value.trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    private <T> List<T> safeList(List<T> value) {
        return value != null ? value : List.of();
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    public record ToolContext(
            String context,
            List<String> suggestions,
            String directAnswer,
            AiIntent intent,
            String toolName,
            boolean requiresData,
            boolean inScope,
            boolean dataFound,
            String sourceSummary
    ) {
        public ToolContext(String context, List<String> suggestions) {
            this(context, suggestions, null);
        }

        public ToolContext(String context, List<String> suggestions, String directAnswer) {
            this(context, suggestions, directAnswer, DetectedIntent.of(AiIntent.GENERAL_HELP, "none", false), false, GUIDE_SOURCE_SUMMARY);
        }

        public ToolContext(String context,
                           List<String> suggestions,
                           String directAnswer,
                           DetectedIntent detectedIntent,
                           boolean dataFound,
                           String sourceSummary) {
            this(context,
                    suggestions != null ? suggestions : List.of(),
                    directAnswer,
                    detectedIntent != null ? detectedIntent.intent() : AiIntent.GENERAL_HELP,
                    detectedIntent != null ? detectedIntent.toolName() : "none",
                    detectedIntent != null && detectedIntent.requiresData(),
                    detectedIntent == null || detectedIntent.inScope(),
                    dataFound,
                    sourceSummary);
        }

        public static ToolContext noData(DetectedIntent detectedIntent, String context, List<String> suggestions) {
            return new ToolContext(
                    context,
                    suggestions,
                    NO_DATA_MESSAGE,
                    detectedIntent,
                    false,
                    SOURCE_SUMMARY);
        }

        public static ToolContext outOfScope(DetectedIntent detectedIntent, String message, List<String> suggestions) {
            return new ToolContext(
                    "SYSTEM_CONTEXT:\nDATA:\n[]\nRULE:\nKhông trả lời ngoài phạm vi hoặc ngoài quyền.",
                    suggestions,
                    message,
                    detectedIntent,
                    false,
                    GUIDE_SOURCE_SUMMARY);
        }
    }
}
