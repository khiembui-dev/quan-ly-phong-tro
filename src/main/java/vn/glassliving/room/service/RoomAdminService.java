package vn.glassliving.room.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.util.SlugUtil;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.dto.RoomForm;
import vn.glassliving.room.entity.Amenity;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.AmenityRepository;
import vn.glassliving.room.repository.RoomRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomAdminService {

    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;
    private final AmenityRepository amenityRepository;
    private final UserRepository userRepository;

    @Transactional
    public Room create(UUID ownerId, RoomForm form) {
        Property prop = loadOwnedProperty(ownerId, form.getPropertyId());

        // Auto-generate code if blank, then validate uniqueness within the property
        String code = blankSafe(form.getCode());
        if (code == null) {
            code = "R-" + System.currentTimeMillis() % 100000;
        }
        code = code.trim().toUpperCase();
        if (roomRepository.existsByPropertyIdAndCodeIgnoreCase(prop.getId(), code)) {
            throw BusinessException.conflict("Mã phòng \"" + code + "\" đã tồn tại trong cơ sở này.");
        }

        String title = blankSafe(form.getTitle());
        if (title == null) title = "Phòng " + code;

        Room.RoomType type;
        try {
            type = Room.RoomType.valueOf(blankOr(form.getType(), "STUDIO"));
        } catch (IllegalArgumentException e) {
            type = Room.RoomType.STUDIO;
        }
        Room.RoomStatus status = parseStatus(form.getStatus());

        UUID currentTenantId = resolveTenantForStatus(status, form.getCurrentTenantId());
        if (currentTenantId != null) {
            status = Room.RoomStatus.OCCUPIED;
        }

        // Tariff: inheritance vs override
        boolean inherit = form.isInheritTariff();
        BigDecimal electric = inherit ? prop.getElectricUnit() : nz(form.getElectricUnit(), prop.getElectricUnit());
        BigDecimal water    = inherit ? prop.getWaterUnit()    : nz(form.getWaterUnit(),    prop.getWaterUnit());
        BigDecimal serviceFee = nz(form.getServiceFee(), prop.getServiceFeeDefault());

        // Slug from title + code
        String slug = ensureUniqueSlug(SlugUtil.slugify(title + " " + code));

        Room r = Room.builder()
                .propertyId(prop.getId())
                .ownerId(ownerId)
                .code(code)
                .slug(slug)
                .title(title.trim())
                .description(form.getDescription())
                .type(type)
                .floor(form.getFloor())
                .areaSqm(form.getAreaSqm())
                .bedrooms(nz(form.getBedrooms(), (short) 1))
                .bathrooms(nz(form.getBathrooms(), (short) 1))
                .maxOccupants(nz(form.getMaxOccupants(), (short) 2))
                .priceMonthly(nz(form.getPriceMonthly(), BigDecimal.ZERO))
                .depositAmount(nz(form.getDepositAmount(), BigDecimal.ZERO))
                .serviceFee(serviceFee)
                .inheritTariff(inherit)
                .electricUnit(electric)
                .waterUnit(water)
                .extraFees(buildExtraFees(form))
                .customAmenities(buildCustomAmenities(form))
                .status(status)
                .currentTenantId(currentTenantId)
                .currentTenantStartedOn(currentTenantId != null ? LocalDate.now() : null)
                .currentTenantPaidUntil(currentTenantId != null
                        ? resolvePaidUntil(form.getCurrentTenantPaidUntil(), null)
                        : null)
                .addressLine(form.getAddressLine())
                .district(prop.getDistrict())
                .city(prop.getCity())
                .ratingAvg(BigDecimal.ZERO)
                .ratingCount(0)
                .viewCount(0)
                .publishedAt(status == Room.RoomStatus.AVAILABLE ? OffsetDateTime.now() : null)
                .build();

        // Amenities
        r.setAmenities(loadAmenities(form.getAmenityCodes()));

        // Convenience flags mirrored from amenity codes for backward compat
        r.setPetAllowed(hasAmenity(form.getAmenityCodes(), "PET"));
        r.setHasBalcony(hasAmenity(form.getAmenityCodes(), "BALCONY"));

        r = roomRepository.save(r);
        detachTenantFromOtherRooms(ownerId, currentTenantId, r.getId());

        prop.setTotalRooms((prop.getTotalRooms() == null ? 0 : prop.getTotalRooms()) + 1);
        propertyRepository.save(prop);

        return r;
    }

    @Transactional
    public Room update(UUID ownerId, UUID id, RoomForm form) {
        Room r = roomRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Phòng"));
        if (!r.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu phòng này.");
        }

        // If code changed, validate uniqueness
        if (form.getCode() != null && !form.getCode().isBlank()) {
            String newCode = form.getCode().trim().toUpperCase();
            if (!newCode.equalsIgnoreCase(r.getCode())) {
                if (roomRepository.existsByPropertyIdAndCodeIgnoreCase(r.getPropertyId(), newCode)) {
                    throw BusinessException.conflict("Mã phòng \"" + newCode + "\" đã tồn tại trong cơ sở này.");
                }
                r.setCode(newCode);
            }
        }

        if (form.getTitle() != null && !form.getTitle().isBlank()) r.setTitle(form.getTitle().trim());
        r.setDescription(form.getDescription());
        if (form.getType() != null && !form.getType().isBlank()) {
            try { r.setType(Room.RoomType.valueOf(form.getType())); } catch (IllegalArgumentException ignored) {}
        }
        if (form.getFloor() != null)        r.setFloor(form.getFloor());
        if (form.getAreaSqm() != null)      r.setAreaSqm(form.getAreaSqm());
        if (form.getBedrooms() != null)     r.setBedrooms(form.getBedrooms());
        if (form.getBathrooms() != null)    r.setBathrooms(form.getBathrooms());
        if (form.getMaxOccupants() != null) r.setMaxOccupants(form.getMaxOccupants());
        if (form.getPriceMonthly() != null) r.setPriceMonthly(form.getPriceMonthly());
        if (form.getDepositAmount() != null)r.setDepositAmount(form.getDepositAmount());
        if (form.getServiceFee() != null)   r.setServiceFee(form.getServiceFee());

        r.setInheritTariff(form.isInheritTariff());
        if (form.isInheritTariff()) {
            // Refresh from property
            Property prop = propertyRepository.findById(r.getPropertyId())
                    .orElseThrow(() -> BusinessException.notFound("Cơ sở"));
            r.setElectricUnit(prop.getElectricUnit());
            r.setWaterUnit(prop.getWaterUnit());
        } else {
            if (form.getElectricUnit() != null) r.setElectricUnit(form.getElectricUnit());
            if (form.getWaterUnit() != null)    r.setWaterUnit(form.getWaterUnit());
        }

        r.setExtraFees(buildExtraFees(form));
        r.setCustomAmenities(buildCustomAmenities(form));

        if (form.getAddressLine() != null) r.setAddressLine(form.getAddressLine());

        UUID previousTenant = r.getCurrentTenantId();
        Room.RoomStatus newStatus = parseStatus(form.getStatus());
        UUID newTenant = resolveTenantForStatus(newStatus, form.getCurrentTenantId());
        if (newTenant != null) {
            newStatus = Room.RoomStatus.OCCUPIED;
        }
        r.setStatus(newStatus);
        r.setCurrentTenantId(newTenant);
        if (newTenant == null) {
            r.setCurrentTenantStartedOn(null);
            r.setCurrentTenantPaidUntil(null);
        } else {
            if (!newTenant.equals(previousTenant) || r.getCurrentTenantStartedOn() == null) {
                r.setCurrentTenantStartedOn(LocalDate.now());
            }
            r.setCurrentTenantPaidUntil(resolvePaidUntil(
                    form.getCurrentTenantPaidUntil(),
                    r.getCurrentTenantPaidUntil()));
        }
        if (newStatus == Room.RoomStatus.AVAILABLE && r.getPublishedAt() == null) {
            r.setPublishedAt(OffsetDateTime.now());
        }

        // Amenities
        r.setAmenities(loadAmenities(form.getAmenityCodes()));
        r.setPetAllowed(hasAmenity(form.getAmenityCodes(), "PET"));
        r.setHasBalcony(hasAmenity(form.getAmenityCodes(), "BALCONY"));

        r = roomRepository.save(r);
        detachTenantFromOtherRooms(ownerId, newTenant, r.getId());
        return r;
    }

    @Transactional
    public Room changeStatus(UUID ownerId, UUID id, String status) {
        Room r = roomRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Phòng"));
        if (!r.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu phòng này.");
        }
        Room.RoomStatus newStatus = Room.RoomStatus.valueOf(status);
        r.setStatus(newStatus);
        if (newStatus != Room.RoomStatus.OCCUPIED) {
            r.setCurrentTenantId(null);
            r.setCurrentTenantStartedOn(null);
            r.setCurrentTenantPaidUntil(null);
        }
        if (newStatus == Room.RoomStatus.AVAILABLE && r.getPublishedAt() == null) {
            r.setPublishedAt(OffsetDateTime.now());
        }
        return roomRepository.save(r);
    }

    @Transactional
    public void delete(UUID ownerId, UUID id) {
        Room r = roomRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Phòng"));
        if (!r.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu phòng này.");
        }
        if (r.getStatus() == Room.RoomStatus.OCCUPIED || r.getCurrentTenantId() != null) {
            throw BusinessException.conflict("Không thể xóa phòng đang có khách thuê. Hãy gỡ khách khỏi phòng trước.");
        }
        roomRepository.delete(r);

        propertyRepository.findById(r.getPropertyId()).ifPresent(p -> {
            int n = p.getTotalRooms() != null ? p.getTotalRooms() : 0;
            p.setTotalRooms(Math.max(0, n - 1));
            propertyRepository.save(p);
        });
    }

    /** Bulk-create N rooms with sequential codes. Skips existing codes. */
    @Transactional
    public BulkResult bulkCreate(UUID ownerId, UUID propertyId,
                                 String prefix, int from, int to, int padding,
                                 BigDecimal areaSqm, String type,
                                 BigDecimal priceMonthly, BigDecimal depositAmount) {
        if (to < from) throw BusinessException.badRequest("Số kết thúc phải >= số bắt đầu.");
        if (to - from + 1 > 100) throw BusinessException.badRequest("Tối đa 100 phòng mỗi lần.");

        Property prop = propertyRepository.findById(propertyId)
                .orElseThrow(() -> BusinessException.notFound("Cơ sở"));
        if (!prop.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu cơ sở này.");
        }

        int created = 0, skipped = 0;
        String safePrefix = prefix == null ? "" : prefix.trim().toUpperCase();
        int pad = Math.max(0, padding);

        for (int i = from; i <= to; i++) {
            String num = pad > 0 ? String.format("%0" + pad + "d", i) : Integer.toString(i);
            String code = safePrefix + num;
            if (roomRepository.existsByPropertyIdAndCodeIgnoreCase(prop.getId(), code)) {
                skipped++;
                continue;
            }
            String title = "Phòng " + code;
            String slug = ensureUniqueSlug(SlugUtil.slugify(title));

            Room r = Room.builder()
                    .propertyId(prop.getId())
                    .ownerId(ownerId)
                    .code(code)
                    .slug(slug)
                    .title(title)
                    .type(parseType(type))
                    .areaSqm(nz(areaSqm, new BigDecimal("20")))
                    .bedrooms((short) 1)
                    .bathrooms((short) 1)
                    .maxOccupants((short) 2)
                    .priceMonthly(nz(priceMonthly, BigDecimal.ZERO))
                    .depositAmount(nz(depositAmount, BigDecimal.ZERO))
                    .serviceFee(prop.getServiceFeeDefault())
                    .inheritTariff(true)
                    .electricUnit(prop.getElectricUnit())
                    .waterUnit(prop.getWaterUnit())
                    .extraFees(new ArrayList<>())
                    .status(Room.RoomStatus.AVAILABLE)
                    .district(prop.getDistrict())
                    .city(prop.getCity())
                    .ratingAvg(BigDecimal.ZERO)
                    .ratingCount(0)
                    .viewCount(0)
                    .publishedAt(OffsetDateTime.now())
                    .build();
            roomRepository.save(r);
            created++;
        }

        if (created > 0) {
            int n = prop.getTotalRooms() != null ? prop.getTotalRooms() : 0;
            prop.setTotalRooms(n + created);
            propertyRepository.save(prop);
        }
        return new BulkResult(created, skipped);
    }

    @Transactional(readOnly = true)
    public RoomForm getEditForm(UUID ownerId, UUID roomId) {
        Room r = roomRepository.findById(roomId)
                .filter(rr -> rr.getOwnerId().equals(ownerId))
                .orElse(null);
        if (r == null) return null;
        org.hibernate.Hibernate.initialize(r.getAmenities());
        return toForm(r);
    }

    @Transactional(readOnly = true)
    public RoomForm getCloneForm(UUID ownerId, UUID roomId) {
        Room r = roomRepository.findById(roomId)
                .filter(rr -> rr.getOwnerId().equals(ownerId))
                .orElse(null);
        if (r == null) return null;
        org.hibernate.Hibernate.initialize(r.getAmenities());
        return cloneForm(r);
    }

    @Transactional(readOnly = true)
    public RoomForm toForm(Room r) {
        RoomForm f = new RoomForm();
        f.setId(r.getId().toString());
        f.setPropertyId(r.getPropertyId().toString());
        f.setCode(r.getCode());
        f.setTitle(r.getTitle());
        f.setDescription(r.getDescription());
        f.setType(r.getType().name());
        f.setFloor(r.getFloor());
        f.setAreaSqm(r.getAreaSqm());
        f.setBedrooms(r.getBedrooms());
        f.setBathrooms(r.getBathrooms());
        f.setMaxOccupants(r.getMaxOccupants());
        f.setPriceMonthly(r.getPriceMonthly());
        f.setDepositAmount(r.getDepositAmount());
        f.setServiceFee(r.getServiceFee());
        f.setInheritTariff(r.isInheritTariff());
        f.setElectricUnit(r.getElectricUnit());
        f.setWaterUnit(r.getWaterUnit());
        f.setAddressLine(r.getAddressLine());
        f.setStatus(r.getStatus().name());
        if (r.getCurrentTenantId() != null) {
            f.setCurrentTenantId(r.getCurrentTenantId().toString());
        }
        f.setCurrentTenantPaidUntil(r.getCurrentTenantPaidUntil());
        // Amenities
        if (r.getAmenities() != null) {
            List<String> codes = new ArrayList<>(r.getAmenities().size());
            for (Amenity a : r.getAmenities()) codes.add(a.getCode());
            f.setAmenityCodes(codes);
        }
        // Extra fees
        if (r.getExtraFees() != null) {
            List<String> names = new ArrayList<>(r.getExtraFees().size());
            List<BigDecimal> amounts = new ArrayList<>(r.getExtraFees().size());
            for (var fee : r.getExtraFees()) {
                names.add(fee.getName());
                amounts.add(fee.getAmount());
            }
            f.setExtraFeeNames(names);
            f.setExtraFeeAmounts(amounts);
        }
        // Custom amenities
        if (r.getCustomAmenities() != null) {
            List<String> names = new ArrayList<>(r.getCustomAmenities().size());
            List<String> cats  = new ArrayList<>(r.getCustomAmenities().size());
            for (var ca : r.getCustomAmenities()) {
                names.add(ca.getName());
                cats.add(ca.getCategory());
            }
            f.setCustomAmenityNames(names);
            f.setCustomAmenityCategories(cats);
        }
        return f;
    }

    /** Build a form pre-filled from `source`, but with `code` cleared so user enters a new one. */
    @Transactional(readOnly = true)
    public RoomForm cloneForm(Room source) {
        RoomForm f = toForm(source);
        f.setId(null);
        f.setCode(null);
        f.setTitle(null);
        f.setStatus("AVAILABLE");
        f.setCurrentTenantId(null);
        f.setCurrentTenantPaidUntil(null);
        return f;
    }

    public record BulkResult(int created, int skipped) {}

    // ============================================================
    // Helpers
    // ============================================================

    private Property loadOwnedProperty(UUID ownerId, String propertyIdStr) {
        if (propertyIdStr == null || propertyIdStr.isBlank()) {
            throw BusinessException.badRequest("Vui lòng chọn cơ sở.");
        }
        UUID propId;
        try { propId = UUID.fromString(propertyIdStr); }
        catch (IllegalArgumentException e) { throw BusinessException.badRequest("Mã cơ sở không hợp lệ."); }
        Property prop = propertyRepository.findById(propId)
                .orElseThrow(() -> BusinessException.notFound("Cơ sở"));
        if (!prop.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu cơ sở này.");
        }
        return prop;
    }

    private Room.RoomStatus parseStatus(String s) {
        if (s == null || s.isBlank()) return Room.RoomStatus.AVAILABLE;
        try { return Room.RoomStatus.valueOf(s); }
        catch (IllegalArgumentException e) { return Room.RoomStatus.AVAILABLE; }
    }

    private Room.RoomType parseType(String s) {
        if (s == null || s.isBlank()) return Room.RoomType.STUDIO;
        try { return Room.RoomType.valueOf(s); }
        catch (IllegalArgumentException e) { return Room.RoomType.STUDIO; }
    }

    private UUID resolveTenantForStatus(Room.RoomStatus status, String tenantIdStr) {
        if (tenantIdStr != null && !tenantIdStr.isBlank()) {
            return loadTenant(parseTenantId(tenantIdStr)).getId();
        }
        if (status == Room.RoomStatus.OCCUPIED) {
            throw BusinessException.badRequest("Phòng đã thuê cần chọn khách hiện tại.");
        }
        return null;
    }

    private UUID parseTenantId(String tenantIdStr) {
        try {
            return UUID.fromString(tenantIdStr);
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("Mã khách thuê không hợp lệ.");
        }
    }

    private User loadTenant(UUID tenantId) {
        User tenant = userRepository.findById(tenantId)
                .orElseThrow(() -> BusinessException.notFound("Khách thuê"));
        if (!tenant.getRoles().contains(User.Role.TENANT)) {
            throw BusinessException.badRequest("Tài khoản đã chọn không phải vai trò khách thuê.");
        }
        return tenant;
    }

    private void detachTenantFromOtherRooms(UUID ownerId, UUID tenantId, UUID keepRoomId) {
        if (tenantId == null) return;
        for (Room other : roomRepository.findByOwnerIdAndCurrentTenantId(ownerId, tenantId)) {
            if (keepRoomId != null && keepRoomId.equals(other.getId())) continue;
            clearTenantFromRoom(other);
        }
    }

    private void clearTenantAssignments(UUID ownerId, UUID tenantId) {
        for (Room room : roomRepository.findByOwnerIdAndCurrentTenantId(ownerId, tenantId)) {
            clearTenantFromRoom(room);
        }
    }

    private void clearTenantFromRoom(Room room) {
        room.setCurrentTenantId(null);
        room.setCurrentTenantStartedOn(null);
        room.setCurrentTenantPaidUntil(null);
        if (room.getStatus() == Room.RoomStatus.OCCUPIED) {
            room.setStatus(Room.RoomStatus.AVAILABLE);
            if (room.getPublishedAt() == null) room.setPublishedAt(OffsetDateTime.now());
        }
        roomRepository.save(room);
    }

    private LocalDate resolvePaidUntil(LocalDate requested, LocalDate existing) {
        if (requested != null) return requested;
        if (existing != null) return existing;
        return LocalDate.now().plusMonths(1).minusDays(1);
    }

    private java.util.Set<Amenity> loadAmenities(List<String> codes) {
        if (codes == null || codes.isEmpty()) return new HashSet<>();
        return new HashSet<>(amenityRepository.findByCodeIn(codes));
    }

    private boolean hasAmenity(List<String> codes, String code) {
        return codes != null && codes.stream().anyMatch(c -> c != null && c.equalsIgnoreCase(code));
    }

    private static List<Room.CustomAmenity> buildCustomAmenities(RoomForm form) {
        List<String> names = form.getCustomAmenityNames();
        List<String> categories = form.getCustomAmenityCategories();
        if (names == null) return new ArrayList<>();
        int n = names.size();
        List<Room.CustomAmenity> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String name = names.get(i);
            if (name == null || name.isBlank()) continue;
            String cat = (categories != null && i < categories.size() && categories.get(i) != null
                            && !categories.get(i).isBlank())
                    ? categories.get(i).trim().toUpperCase() : "OTHER";
            // Whitelist categories
            if (!cat.equals("FURNITURE") && !cat.equals("UTILITY") && !cat.equals("RULE")) cat = "OTHER";
            out.add(new Room.CustomAmenity(name.trim(), cat));
        }
        return out;
    }

    private static List<vn.glassliving.property.entity.Property.ExtraFee> buildExtraFees(RoomForm form) {
        List<String> names = form.getExtraFeeNames();
        List<BigDecimal> amounts = form.getExtraFeeAmounts();
        if (names == null || amounts == null) return new ArrayList<>();
        int n = Math.min(names.size(), amounts.size());
        List<vn.glassliving.property.entity.Property.ExtraFee> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String name = names.get(i);
            BigDecimal amount = amounts.get(i);
            if (name == null || name.isBlank()) continue;
            out.add(new vn.glassliving.property.entity.Property.ExtraFee(name.trim(),
                    amount != null ? amount : BigDecimal.ZERO));
        }
        return out;
    }

    private String ensureUniqueSlug(String base) {
        String safeBase = (base == null || base.isBlank()) ? "phong" : base;
        String slug = safeBase;
        int n = 1;
        while (roomRepository.findBySlug(slug).isPresent()) {
            n++;
            slug = safeBase + "-" + n;
            if (n > 200) break;
        }
        return slug;
    }

    private static Short nz(Short v, Short fallback) { return v != null ? v : fallback; }
    private static BigDecimal nz(BigDecimal v, BigDecimal fallback) { return v != null ? v : fallback; }
    private static String blankSafe(String s) { return s == null || s.isBlank() ? null : s; }
    private static String blankOr(String s, String d) { return blankSafe(s) != null ? s : d; }

    /** Sync currentTenantId when an active contract is created/terminated. Called by ContractService. */
    @Transactional
    public void linkTenant(UUID roomId, UUID tenantUserId) {
        roomRepository.findById(roomId).ifPresent(r -> {
            r.setCurrentTenantId(tenantUserId);
            if (r.getCurrentTenantStartedOn() == null) {
                r.setCurrentTenantStartedOn(LocalDate.now());
            }
            r.setCurrentTenantPaidUntil(resolvePaidUntil(r.getCurrentTenantPaidUntil(), null));
            r.setStatus(Room.RoomStatus.OCCUPIED);
            roomRepository.save(r);
        });
    }

    @Transactional
    public void unlinkTenant(UUID roomId) {
        roomRepository.findById(roomId).ifPresent(r -> {
            r.setCurrentTenantId(null);
            r.setCurrentTenantStartedOn(null);
            r.setCurrentTenantPaidUntil(null);
            r.setStatus(Room.RoomStatus.AVAILABLE);
            if (r.getPublishedAt() == null) r.setPublishedAt(OffsetDateTime.now());
            roomRepository.save(r);
        });
    }

    /**
     * Assigns exactly one current room to a tenant for an owner.
     * Passing null for roomId clears that tenant's current room assignment.
     */
    @Transactional
    public Room assignTenantToRoom(UUID ownerId, UUID tenantUserId, UUID roomId) {
        return assignTenantToRoom(ownerId, tenantUserId, roomId, null);
    }

    @Transactional
    public Room assignTenantToRoom(UUID ownerId, UUID tenantUserId, UUID roomId, LocalDate paidUntil) {
        return assignTenantToRoom(ownerId, tenantUserId, roomId, null, paidUntil);
    }

    @Transactional
    public Room assignTenantToRoom(UUID ownerId, UUID tenantUserId, UUID roomId, LocalDate startedOn, LocalDate paidUntil) {
        User tenant = loadTenant(tenantUserId);

        if (roomId == null) {
            clearTenantAssignments(ownerId, tenant.getId());
            return null;
        }

        Room target = roomRepository.findById(roomId)
                .orElseThrow(() -> BusinessException.notFound("Phòng"));
        if (!target.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu phòng này.");
        }
        if (target.getCurrentTenantId() != null && !target.getCurrentTenantId().equals(tenant.getId())) {
            String currentName = userRepository.findById(target.getCurrentTenantId())
                    .map(User::getFullName)
                    .orElse("khách thuê khác");
            throw BusinessException.conflict("Phòng \"" + target.getCode() + "\" đang gán cho " + currentName + ".");
        }

        target.setCurrentTenantId(tenant.getId());
        if (startedOn != null) {
            target.setCurrentTenantStartedOn(startedOn);
        } else if (target.getCurrentTenantStartedOn() == null) {
            target.setCurrentTenantStartedOn(LocalDate.now());
        }
        target.setCurrentTenantPaidUntil(resolvePaidUntil(paidUntil, target.getCurrentTenantPaidUntil()));
        target.setStatus(Room.RoomStatus.OCCUPIED);
        Room saved = roomRepository.save(target);
        detachTenantFromOtherRooms(ownerId, tenant.getId(), saved.getId());
        return saved;
    }
}
