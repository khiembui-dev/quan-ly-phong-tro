package vn.glassliving.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.contract.entity.Contract;
import vn.glassliving.contract.repository.ContractRepository;
import vn.glassliving.favorite.entity.Favorite;
import vn.glassliving.favorite.repository.FavoriteRepository;
import vn.glassliving.automation.entity.AutomationSetting;
import vn.glassliving.automation.repository.AutomationSettingRepository;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.maintenance.entity.MaintenanceTicket;
import vn.glassliving.maintenance.repository.MaintenanceTicketRepository;
import vn.glassliving.notification.entity.Notification;
import vn.glassliving.notification.repository.NotificationRepository;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.entity.Amenity;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.entity.RoomImage;
import vn.glassliving.room.repository.AmenityRepository;
import vn.glassliving.room.repository.RoomImageRepository;
import vn.glassliving.room.repository.RoomRepository;
import vn.glassliving.utility.entity.UtilityReading;
import vn.glassliving.utility.repository.UtilityReadingRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Seeds demo accounts and sample data on first boot when running in dev/H2.
 * Idempotent — checks for existing email before inserting.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final RoomImageRepository roomImageRepository;
    private final AmenityRepository amenityRepository;
    private final ContractRepository contractRepository;
    private final InvoiceRepository invoiceRepository;
    private final FavoriteRepository favoriteRepository;
    private final NotificationRepository notificationRepository;
    private final MaintenanceTicketRepository maintenanceTicketRepository;
    private final UtilityReadingRepository utilityReadingRepository;
    private final AutomationSettingRepository automationSettingRepository;

    @PostConstruct
    @Transactional
    public void seed() {
        if (userRepository.existsByEmailIgnoreCase("owner@glass.living")) {
            log.info("Dev seed users already present, skipping.");
            return;
        }
        log.info("Seeding dev users + sample data…");

        // Demo accounts. Password = "password123"
        User owner = userRepository.save(User.builder()
                .email("owner@glass.living")
                .emailVerified(true)
                .phone("0901234567")
                .fullName("Chủ trọ Glass")
                .passwordHash(passwordEncoder.encode("password123"))
                .roles(Set.of(User.Role.OWNER))
                .status(User.UserStatus.ACTIVE)
                .build());

        User tenant = userRepository.save(User.builder()
                .email("tenant@glass.living")
                .emailVerified(true)
                .phone("0907654321")
                .fullName("Khách Nguyễn Văn A")
                .passwordHash(passwordEncoder.encode("password123"))
                .roles(Set.of(User.Role.TENANT))
                .status(User.UserStatus.ACTIVE)
                .build());

        userRepository.save(User.builder()
                .email("admin@glass.living")
                .emailVerified(true)
                .phone("0900000099")
                .fullName("Quản trị viên")
                .passwordHash(passwordEncoder.encode("password123"))
                .roles(Set.of(User.Role.ADMIN))
                .status(User.UserStatus.ACTIVE)
                .build());

        // 3 properties, several rooms
        Property gt = propertyRepository.save(Property.builder()
                .ownerId(owner.getId()).name("Glass Tower").slug("glass-tower")
                .description("Tòa nhà cao cấp giữa lòng Quận 1")
                .addressLine("128 Nguyễn Huệ").district("Quận 1").city("TP.HCM").totalRooms(2).build());
        Property lh = propertyRepository.save(Property.builder()
                .ownerId(owner.getId()).name("Lotus House").slug("lotus-house")
                .description("Nhà trọ cao cấp Q3 yên tĩnh")
                .addressLine("45 Võ Văn Tần").district("Quận 3").city("TP.HCM").totalRooms(2).build());
        Property rs = propertyRepository.save(Property.builder()
                .ownerId(owner.getId()).name("Riverside").slug("riverside-q7")
                .description("View sông Q7 mát mẻ")
                .addressLine("88 Nguyễn Thị Thập").district("Quận 7").city("TP.HCM").totalRooms(2).build());

        // Amenity lookup by code (V2 migration seeded these)
        Map<String, Amenity> amenities = amenityRepository.findAll().stream()
                .collect(Collectors.toMap(Amenity::getCode, a -> a));
        Set<Amenity> baseAmenities = pickAmenities(amenities, "WIFI", "AIR_CON", "WATER_HEATER", "FRIDGE", "KITCHEN");
        Set<Amenity> premiumAmenities = pickAmenities(amenities,
                "WIFI", "AIR_CON", "WATER_HEATER", "FRIDGE", "KITCHEN",
                "WASHING", "PARKING", "SECURITY_24", "CCTV", "POOL", "GYM", "ELEVATOR", "BALCONY");

        Room r1 = roomRepository.save(buildRoom(gt, owner.getId(), "GT-A-301", "studio-glass-tower-301",
                "Studio Glass Tower 301", Room.RoomType.STUDIO, (short) 3, "28.50",
                "8500000", "8500000", "500000", "Quận 1", "TP.HCM", true, false,
                Room.RoomStatus.AVAILABLE, "4.92", 234, 1245));
        roomRepository.save(buildRoom(gt, owner.getId(), "GT-B-502", "penthouse-glass-tower-502",
                "Penthouse Glass Tower 502", Room.RoomType.PENTHOUSE, (short) 5, "65.00",
                "18000000", "18000000", "1200000", "Quận 1", "TP.HCM", true, true,
                Room.RoomStatus.AVAILABLE, "4.85", 89, 670));
        Room r3 = roomRepository.save(buildRoom(lh, owner.getId(), "LH-201", "phong-doi-lotus-201",
                "Phòng đôi Lotus 201", Room.RoomType.DOUBLE, (short) 2, "22.00",
                "6500000", "6500000", "300000", "Quận 3", "TP.HCM", false, false,
                Room.RoomStatus.OCCUPIED, "4.71", 156, 980));
        roomRepository.save(buildRoom(lh, owner.getId(), "LH-303", "studio-lotus-303",
                "Studio Lotus 303", Room.RoomType.STUDIO, (short) 3, "25.00",
                "6800000", "6800000", "300000", "Quận 3", "TP.HCM", false, false,
                Room.RoomStatus.MAINTENANCE, "4.40", 12, 56));
        roomRepository.save(buildRoom(rs, owner.getId(), "RS-101", "riverside-q7-101",
                "Riverside Q7 101", Room.RoomType.STUDIO, (short) 1, "32.00",
                "7500000", "7500000", "400000", "Quận 7", "TP.HCM", true, false,
                Room.RoomStatus.AVAILABLE, "4.66", 67, 432));
        Room r5 = roomRepository.save(buildRoom(rs, owner.getId(), "RS-205", "riverside-q7-205",
                "Riverside Q7 205", Room.RoomType.STUDIO, (short) 2, "30.00",
                "7800000", "7800000", "400000", "Quận 7", "TP.HCM", true, true,
                Room.RoomStatus.AVAILABLE, "4.55", 23, 110));

        // Attach amenities to all rooms
        attachAmenities(r1, premiumAmenities);
        attachAmenities(r3, baseAmenities);
        attachAmenities(r5, premiumAmenities);
        roomRepository.findBySlug("penthouse-glass-tower-502").ifPresent(r -> attachAmenities(r, premiumAmenities));
        roomRepository.findBySlug("studio-lotus-303").ifPresent(r -> attachAmenities(r, baseAmenities));
        roomRepository.findBySlug("riverside-q7-101").ifPresent(r -> attachAmenities(r, premiumAmenities));

        // Seed room images via Unsplash CDN (free hot-link).
        // 5 photos per room — front, living, bedroom, kitchen, bath.
        seedImages(r1, new String[]{
                "1522708323590-d24dbb6b0267", // open studio
                "1505693416388-ac5ce068fe85", // bedroom modern
                "1556909114-f6e7ad7d3136",    // kitchen
                "1493809842364-78817add7ffb", // cozy living
                "1554995207-c18c203602cb"     // interior detail
        });
        seedImages(r3, new String[]{
                "1540518614846-7eded433c457",
                "1631049307264-da0ec9d70304",
                "1560185007-cde436f6a4d0",
                "1502672260266-1c1ef2d93688",
                "1556912173-3bb406ef7e77"
        });
        seedImages(r5, new String[]{
                "1567767292278-a4f21aa2d36e",
                "1505691938895-1758d7feb511",
                "1631679706909-1844bbd07221",
                "1556909114-f6e7ad7d3136",
                "1620626011761-996317b8d101"
        });
        roomRepository.findBySlug("penthouse-glass-tower-502").ifPresent(r -> seedImages(r, new String[]{
                "1502672023488-70e25813eb80",
                "1505691938895-1758d7feb511",
                "1493809842364-78817add7ffb",
                "1556909114-f6e7ad7d3136",
                "1554995207-c18c203602cb"
        }));
        roomRepository.findBySlug("studio-lotus-303").ifPresent(r -> seedImages(r, new String[]{
                "1522708323590-d24dbb6b0267",
                "1540518614846-7eded433c457",
                "1556909114-f6e7ad7d3136",
                "1620626011761-996317b8d101",
                "1493809842364-78817add7ffb"
        }));
        roomRepository.findBySlug("riverside-q7-101").ifPresent(r -> seedImages(r, new String[]{
                "1502672260266-1c1ef2d93688",
                "1505693416388-ac5ce068fe85",
                "1556912173-3bb406ef7e77",
                "1552321554-5fefe8c9ef14",
                "1631049307264-da0ec9d70304"
        }));

        // Active contract on LH-201 for tenant
        Contract contract = contractRepository.save(Contract.builder()
                .code("HD-202604-0001")
                .roomId(r3.getId())
                .ownerId(owner.getId())
                .tenantUserId(tenant.getId())
                .startDate(LocalDate.of(2026, 4, 1))
                .endDate(LocalDate.of(2027, 3, 31))
                .durationMonths((short) 12)
                .rentMonthly(new BigDecimal("6500000"))
                .depositAmount(new BigDecimal("6500000"))
                .serviceFee(new BigDecimal("300000"))
                .electricUnit(new BigDecimal("4000"))
                .waterUnit(new BigDecimal("25000"))
                .billingDay((short) 1)
                .status(Contract.ContractStatus.ACTIVE)
                .build());

        // Two invoices
        invoiceRepository.save(Invoice.builder()
                .code("INV-202604-0001")
                .contractId(contract.getId()).ownerId(owner.getId())
                .tenantUserId(tenant.getId()).roomId(r3.getId())
                .periodYear((short) 2026).periodMonth((short) 4)
                .issueDate(LocalDate.of(2026, 4, 1)).dueDate(LocalDate.of(2026, 4, 10))
                .rentAmount(new BigDecimal("6500000"))
                .serviceAmount(new BigDecimal("300000"))
                .electricAmount(new BigDecimal("320000"))
                .waterAmount(new BigDecimal("75000"))
                .totalAmount(new BigDecimal("7195000"))
                .paidAmount(new BigDecimal("7195000"))
                .status(Invoice.InvoiceStatus.PAID)
                .build());
        invoiceRepository.save(Invoice.builder()
                .code("INV-202605-0001")
                .contractId(contract.getId()).ownerId(owner.getId())
                .tenantUserId(tenant.getId()).roomId(r3.getId())
                .periodYear((short) 2026).periodMonth((short) 5)
                .issueDate(LocalDate.of(2026, 5, 1)).dueDate(LocalDate.of(2026, 5, 10))
                .rentAmount(new BigDecimal("6500000"))
                .serviceAmount(new BigDecimal("300000"))
                .electricAmount(new BigDecimal("380000"))
                .waterAmount(new BigDecimal("80000"))
                .totalAmount(new BigDecimal("7260000"))
                .status(Invoice.InvoiceStatus.PENDING)
                .build());

        // Favorite + notifications
        favoriteRepository.save(Favorite.builder().userId(tenant.getId()).roomId(r1.getId()).build());

        notificationRepository.save(Notification.builder()
                .userId(tenant.getId()).type("INVOICE_NEW")
                .title("Hóa đơn tháng 5/2026")
                .body("Hóa đơn mới đã được phát hành. Hạn thanh toán: 10/05/2026.")
                .linkUrl("/me/invoices").build());
        notificationRepository.save(Notification.builder()
                .userId(owner.getId()).type("BOOKING_NEW")
                .title("Yêu cầu đặt phòng mới")
                .body("Có yêu cầu đặt phòng GT-A-301.")
                .linkUrl("/admin/tenants").build());

        // ----- Tariff defaults per property -----
        gt.setElectricUnit(new BigDecimal("4500"));
        gt.setWaterUnit(new BigDecimal("28000"));
        gt.setServiceFeeDefault(new BigDecimal("500000"));
        gt.setInternetFee(new BigDecimal("150000"));
        gt.setGarbageFee(new BigDecimal("30000"));
        gt.setManagementFee(new BigDecimal("100000"));
        gt.setBillingDayDefault((short) 1);
        propertyRepository.save(gt);

        lh.setElectricUnit(new BigDecimal("4000"));
        lh.setWaterUnit(new BigDecimal("25000"));
        lh.setServiceFeeDefault(new BigDecimal("300000"));
        lh.setInternetFee(new BigDecimal("100000"));
        lh.setGarbageFee(new BigDecimal("20000"));
        lh.setBillingDayDefault((short) 5);
        propertyRepository.save(lh);

        rs.setElectricUnit(new BigDecimal("3800"));
        rs.setWaterUnit(new BigDecimal("22000"));
        rs.setServiceFeeDefault(new BigDecimal("400000"));
        rs.setInternetFee(new BigDecimal("120000"));
        propertyRepository.save(rs);

        // ----- Sample utility readings (2 months for 3 rooms) -----
        seedReading(owner.getId(), gt.getId(), r1.getId(), (short) 2026, (short) 4,
                java.time.LocalDate.of(2026, 4, 30),
                "1820", "1894", "4500", "12.5", "16.2", "28000");
        seedReading(owner.getId(), gt.getId(), r1.getId(), (short) 2026, (short) 5,
                java.time.LocalDate.of(2026, 5, 31),
                "1894", "1972", "4500", "16.2", "20.1", "28000");
        seedReading(owner.getId(), lh.getId(), r3.getId(), (short) 2026, (short) 4,
                java.time.LocalDate.of(2026, 4, 30),
                "2410", "2493", "4000", "8.0", "11.0", "25000");
        seedReading(owner.getId(), lh.getId(), r3.getId(), (short) 2026, (short) 5,
                java.time.LocalDate.of(2026, 5, 31),
                "2493", "2588", "4000", "11.0", "14.2", "25000");
        seedReading(owner.getId(), rs.getId(), r5.getId(), (short) 2026, (short) 5,
                java.time.LocalDate.of(2026, 5, 31),
                "560", "612", "3800", "5.5", "7.8", "22000");

        // ----- Sample maintenance tickets -----
        maintenanceTicketRepository.save(MaintenanceTicket.builder()
                .code("REQ-20260501-0001").ownerId(owner.getId())
                .propertyId(lh.getId()).roomId(r3.getId()).reporterUserId(tenant.getId())
                .category(MaintenanceTicket.Category.AC_HVAC)
                .priority(MaintenanceTicket.Priority.URGENT)
                .status(MaintenanceTicket.Status.OPEN)
                .title("Máy lạnh không lạnh, có tiếng kêu")
                .description("Máy lạnh phòng LH-201 không hoạt động ổn định, có tiếng kêu lớn từ dàn lạnh khi bật. Đã thử reset nhưng không khắc phục được.")
                .estimatedCost(new BigDecimal("450000"))
                .reportedAt(java.time.OffsetDateTime.now().minusHours(3))
                .build());
        maintenanceTicketRepository.save(MaintenanceTicket.builder()
                .code("REQ-20260428-0002").ownerId(owner.getId())
                .propertyId(gt.getId()).roomId(r1.getId()).reporterUserId(tenant.getId())
                .category(MaintenanceTicket.Category.PLUMBING)
                .priority(MaintenanceTicket.Priority.HIGH)
                .status(MaintenanceTicket.Status.IN_PROGRESS)
                .title("Vòi nước bồn rửa rỉ")
                .description("Vòi nước bồn rửa nhà bếp bị rỉ, mỗi ngày mất khoảng 5L nước.")
                .estimatedCost(new BigDecimal("250000"))
                .scheduledFor(java.time.OffsetDateTime.now().plusDays(1))
                .reportedAt(java.time.OffsetDateTime.now().minusDays(2))
                .build());
        maintenanceTicketRepository.save(MaintenanceTicket.builder()
                .code("REQ-20260420-0003").ownerId(owner.getId())
                .propertyId(rs.getId()).roomId(r5.getId()).reporterUserId(tenant.getId())
                .category(MaintenanceTicket.Category.INTERNET)
                .priority(MaintenanceTicket.Priority.NORMAL)
                .status(MaintenanceTicket.Status.RESOLVED)
                .title("Wi-Fi yếu vào giờ cao điểm")
                .description("Wi-Fi yếu/mất sóng từ 19h-22h hằng ngày.")
                .estimatedCost(new BigDecimal("150000"))
                .actualCost(new BigDecimal("120000"))
                .resolvedAt(java.time.OffsetDateTime.now().minusDays(1))
                .resolutionNote("Đã thay router, kiểm tra đường truyền nhà mạng — đã ổn định.")
                .reportedAt(java.time.OffsetDateTime.now().minusDays(10))
                .build());
        maintenanceTicketRepository.save(MaintenanceTicket.builder()
                .code("REQ-20260415-0004").ownerId(owner.getId())
                .propertyId(gt.getId()).roomId(r1.getId()).reporterUserId(tenant.getId())
                .category(MaintenanceTicket.Category.FURNITURE)
                .priority(MaintenanceTicket.Priority.LOW)
                .status(MaintenanceTicket.Status.ACKNOWLEDGED)
                .title("Cửa tủ quần áo bị lỏng bản lề")
                .description("Cửa tủ quần áo phòng GT-A-301 bị lỏng bản lề, cần thay.")
                .reportedAt(java.time.OffsetDateTime.now().minusDays(6))
                .build());

        // ----- Default automation setting -----
        automationSettingRepository.save(AutomationSetting.builder()
                .ownerId(owner.getId())
                .invoiceAutoCreate(true)
                .invoiceCreateDay((short) 1)
                .reminderPreDueDays("7,3,1")
                .reminderOverdueDays("1,3,7,14")
                .reminderChannelEmail(true)
                .reminderChannelSms(false)
                .reminderChannelZalo(false)
                .contractRenewAlertDays((short) 30)
                .autoLateFeeEnabled(false)
                .autoLateFeePct(new BigDecimal("0.5"))
                .autoLateFeeAfterDays((short) 5)
                .quietHoursStart((short) 21)
                .quietHoursEnd((short) 8)
                .build());

        log.info("Dev seed complete. Login: owner@/tenant@/admin@glass.living  pwd: password123");
    }

    private void seedReading(UUID ownerId, UUID propertyId, UUID roomId,
                             Short year, Short month, java.time.LocalDate readingDate,
                             String elecPrev, String elecCurr, String elecPrice,
                             String waterPrev, String waterCurr, String waterPrice) {
        UtilityReading u = UtilityReading.builder()
                .ownerId(ownerId).propertyId(propertyId).roomId(roomId)
                .periodYear(year).periodMonth(month).readingDate(readingDate)
                .electricPrev(new BigDecimal(elecPrev))
                .electricCurr(new BigDecimal(elecCurr))
                .electricUnitPrice(new BigDecimal(elecPrice))
                .waterPrev(new BigDecimal(waterPrev))
                .waterCurr(new BigDecimal(waterCurr))
                .waterUnitPrice(new BigDecimal(waterPrice))
                .locked(false)
                .build();
        u.recompute();
        utilityReadingRepository.save(u);
    }

    private Room buildRoom(Property prop, UUID ownerId, String code, String slug, String title,
                           Room.RoomType type, Short floor, String areaSqm,
                           String price, String deposit, String serviceFee,
                           String district, String city, boolean balcony, boolean pet,
                           Room.RoomStatus status, String rating, int ratingCount, int viewCount) {
        return Room.builder()
                .propertyId(prop.getId())
                .ownerId(ownerId)
                .code(code).slug(slug).title(title).type(type).floor(floor)
                .areaSqm(new BigDecimal(areaSqm))
                .priceMonthly(new BigDecimal(price))
                .depositAmount(new BigDecimal(deposit))
                .serviceFee(new BigDecimal(serviceFee))
                .district(district).city(city)
                .hasBalcony(balcony).petAllowed(pet)
                .status(status)
                .ratingAvg(new BigDecimal(rating))
                .ratingCount(ratingCount).viewCount(viewCount)
                .description("Phòng được thiết kế tinh tế, đầy đủ nội thất, sẵn sàng dọn vào ở. " +
                        "Wi-Fi tốc độ cao, máy lạnh, máy nước nóng, an ninh 24/7. Gần các tiện ích thiết yếu.")
                .publishedAt(java.time.OffsetDateTime.now())
                .build();
    }

    private Set<Amenity> pickAmenities(Map<String, Amenity> all, String... codes) {
        Set<Amenity> result = new HashSet<>();
        for (String c : codes) {
            Amenity a = all.get(c);
            if (a != null) result.add(a);
        }
        return result;
    }

    private void attachAmenities(Room r, Set<Amenity> amenities) {
        r.setAmenities(new HashSet<>(amenities));
        roomRepository.save(r);
    }

    /** Seeds 5 RoomImage rows from Unsplash IDs. First image is set as cover_url. */
    private void seedImages(Room r, String[] unsplashIds) {
        // Re-fetch to get the latest version (attachAmenities bumped @Version above).
        Room fresh = roomRepository.findById(r.getId()).orElse(r);
        for (int i = 0; i < unsplashIds.length; i++) {
            boolean isCover = (i == 0);
            String url = "https://images.unsplash.com/photo-" + unsplashIds[i]
                    + "?ixlib=rb-4.0.3&auto=format&fit=crop&w=1600&q=80";
            roomImageRepository.save(RoomImage.builder()
                    .roomId(fresh.getId())
                    .url(url)
                    .alt(fresh.getTitle() + " — ảnh " + (i + 1))
                    .sortOrder(i)
                    .cover(isCover)
                    .build());
            if (isCover) {
                fresh.setCoverUrl(url);
                fresh = roomRepository.save(fresh);
            }
        }
    }
}
