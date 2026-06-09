package vn.glassliving.admin.page.rooms;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.web.FlashAlert;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.dto.RoomForm;
import vn.glassliving.room.entity.Amenity;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.AmenityRepository;
import vn.glassliving.room.repository.RoomImageRepository;
import vn.glassliving.room.service.RoomAdminService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/admin/rooms")
@RequiredArgsConstructor
public class RoomsActionController {

    private final RoomAdminService roomAdminService;
    private final PropertyRepository propertyRepository;
    private final AmenityRepository amenityRepository;
    private final UserRepository userRepository;
    private final RoomImageRepository roomImageRepository;

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserDetails me,
                         @Valid @ModelAttribute("form") RoomForm form,
                         BindingResult br,
                         RedirectAttributes ra) {
        if (br.hasErrors()) {
            FlashAlert.err(ra, "Vui lòng kiểm tra các trường đã đánh dấu lỗi.");
            return "redirect:/admin/rooms";
        }
        try {
            Room r = roomAdminService.create(me.getId(), form);
            FlashAlert.ok(ra, "Đã tạo phòng \"" + r.getCode() + " · " + r.getTitle() + "\".");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            FlashAlert.err(ra, "Dữ liệu không hợp lệ: " + ex.getMessage());
        }
        return "redirect:/admin/rooms";
    }

    @PostMapping("/create")
    public String createFromPage(@AuthenticationPrincipal AppUserDetails me,
                                 @Valid @ModelAttribute("form") RoomForm form,
                                 BindingResult br,
                                 @RequestParam(required = false) MultipartFile[] roomImages,
                                 Model model,
                                 RedirectAttributes ra) {
        validateCreatePage(form, br);
        if (br.hasErrors()) {
            prepareRoomCreateModel(model, me);
            return "admin/room-create";
        }
        try {
            Room r = roomAdminService.create(me.getId(), form, roomImages);
            FlashAlert.ok(ra, "Đã tạo phòng \"" + r.getCode() + " · " + r.getTitle() + "\".");
            return "redirect:/admin/rooms?propertyId=" + r.getPropertyId();
        } catch (BusinessException ex) {
            br.reject("roomCreate", ex.getMessage());
            prepareRoomCreateModel(model, me);
            return "admin/room-create";
        } catch (IllegalArgumentException ex) {
            br.reject("roomCreate", "Dữ liệu không hợp lệ: " + ex.getMessage());
            prepareRoomCreateModel(model, me);
            return "admin/room-create";
        }
    }

    @PostMapping("/{id}/update")
    public String update(@AuthenticationPrincipal AppUserDetails me,
                         @PathVariable UUID id,
                         @Valid @ModelAttribute("form") RoomForm form,
                         BindingResult br,
                         RedirectAttributes ra) {
        if (br.hasErrors()) {
            FlashAlert.err(ra, "Vui lòng kiểm tra các trường đã đánh dấu lỗi.");
            return "redirect:/admin/rooms";
        }
        try {
            Room r = roomAdminService.update(me.getId(), id, form);
            FlashAlert.ok(ra, "Đã cập nhật phòng \"" + r.getCode() + "\".");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/rooms";
    }

    @PostMapping("/{id}/edit")
    public String updateFromPage(@AuthenticationPrincipal AppUserDetails me,
                                 @PathVariable UUID id,
                                 @Valid @ModelAttribute("form") RoomForm form,
                                 BindingResult br,
                                 @RequestParam(required = false) MultipartFile[] roomImages,
                                 Model model,
                                 RedirectAttributes ra) {
        form.setId(id.toString());
        validateCreatePage(form, br);
        if (br.hasErrors()) {
            prepareRoomEditModel(model, me, id);
            return "admin/room-create";
        }
        try {
            Room r = roomAdminService.update(me.getId(), id, form, roomImages);
            FlashAlert.ok(ra, "Đã cập nhật phòng \"" + r.getCode() + "\".");
            return "redirect:/admin/rooms/" + r.getId();
        } catch (BusinessException ex) {
            br.reject("roomUpdate", ex.getMessage());
            prepareRoomEditModel(model, me, id);
            return "admin/room-create";
        } catch (IllegalArgumentException ex) {
            br.reject("roomUpdate", "Dữ liệu không hợp lệ: " + ex.getMessage());
            prepareRoomEditModel(model, me, id);
            return "admin/room-create";
        }
    }

    @PostMapping("/{id}/status")
    public String changeStatus(@AuthenticationPrincipal AppUserDetails me,
                               @PathVariable UUID id,
                               @RequestParam String status,
                               RedirectAttributes ra) {
        try {
            Room r = roomAdminService.changeStatus(me.getId(), id, status);
            FlashAlert.ok(ra, "Đã đổi trạng thái phòng \"" + r.getCode() + "\" sang " + statusLabel(r.getStatus()));
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            FlashAlert.err(ra, "Trạng thái không hợp lệ.");
        }
        return "redirect:/admin/rooms";
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails me,
                         @PathVariable UUID id,
                         RedirectAttributes ra) {
        try {
            roomAdminService.delete(me.getId(), id);
            FlashAlert.ok(ra, "Đã xóa phòng.");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/rooms";
    }

    @PostMapping("/bulk")
    public String bulkCreate(@AuthenticationPrincipal AppUserDetails me,
                             @RequestParam UUID propertyId,
                             @RequestParam String prefix,
                             @RequestParam int fromNumber,
                             @RequestParam int toNumber,
                             @RequestParam(defaultValue = "3") int padding,
                             @RequestParam BigDecimal areaSqm,
                             @RequestParam(required = false, defaultValue = "STUDIO") String type,
                             @RequestParam(required = false) BigDecimal priceMonthly,
                             @RequestParam(required = false) BigDecimal depositAmount,
                             RedirectAttributes ra) {
        try {
            RoomAdminService.BulkResult res = roomAdminService.bulkCreate(
                    me.getId(), propertyId, prefix, fromNumber, toNumber, padding,
                    areaSqm, type, priceMonthly, depositAmount);
            String msg = "Đã tạo " + res.created() + " phòng" +
                    (res.skipped() > 0 ? " · bỏ qua " + res.skipped() + " mã đã tồn tại" : "") + ".";
            FlashAlert.ok(ra, msg);
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/rooms?propertyId=" + propertyId;
    }

    private void validateCreatePage(RoomForm form, BindingResult br) {
        if (isBlank(form.getPropertyId())) {
            br.rejectValue("propertyId", "room.property.required", "Vui lòng chọn cơ sở.");
        }
        if (isBlank(form.getAddressLine())) {
            br.rejectValue("addressLine", "room.address.required", "Vui lòng chọn đầy đủ địa chỉ phòng.");
        }
        if ("OCCUPIED".equals(form.getStatus()) && isBlank(form.getCurrentTenantId())) {
            br.rejectValue("currentTenantId", "room.tenant.required", "Phòng đang ở cần chọn khách thuê.");
        }
        validateIntegerMoney(form.getPriceMonthly(), "priceMonthly", "Giá thuê phải là số nguyên không âm.", br);
        validateIntegerMoney(form.getDepositAmount(), "depositAmount", "Tiền cọc phải là số nguyên không âm.", br);
        validateIntegerMoney(form.getServiceFee(), "serviceFee", "Phí dịch vụ phải là số nguyên không âm.", br);
        validateIntegerMoney(form.getElectricUnit(), "electricUnit", "Giá điện phải là số nguyên không âm.", br);
        validateIntegerMoney(form.getWaterUnit(), "waterUnit", "Giá nước phải là số nguyên không âm.", br);

        List<BigDecimal> amounts = form.getExtraFeeAmounts();
        if (amounts != null) {
            for (int i = 0; i < amounts.size(); i++) {
                BigDecimal amount = amounts.get(i);
                if (!isIntegerMoney(amount)) {
                    br.rejectValue("extraFeeAmounts[" + i + "]", "room.extraFee.invalid", "Chi phí khác phải là số nguyên không âm.");
                }
            }
        }
    }

    private static void validateIntegerMoney(BigDecimal value, String field, String message, BindingResult br) {
        if (!isIntegerMoney(value)) {
            br.rejectValue(field, field + ".invalid", message);
        }
    }

    private static boolean isIntegerMoney(BigDecimal value) {
        return value == null || (value.signum() >= 0 && value.stripTrailingZeros().scale() <= 0);
    }

    private void prepareRoomFormModel(Model model, AppUserDetails me, String title) {
        var properties = propertyRepository.findByOwnerIdOrderByNameAsc(me.getId());
        model.addAttribute("activeNav", "rooms");
        model.addAttribute("pageTitle", title);
        model.addAttribute("properties", properties);
        model.addAttribute("propertyTariffs", buildPropertyTariffs(properties));
        addTenantCatalog(model);
        addAmenityGroups(model);
    }

    private void prepareRoomEditModel(Model model, AppUserDetails me, UUID id) {
        prepareRoomFormModel(model, me, "Chỉnh sửa phòng");
        model.addAttribute("roomFormMode", "edit");
        model.addAttribute("roomFormAction", "/admin/rooms/" + id + "/edit");
        model.addAttribute("roomFormBackUrl", "/admin/rooms/" + id);
        model.addAttribute("roomFormHeading", "Chỉnh sửa phòng");
        model.addAttribute("roomFormDescription", "Cập nhật thông tin, giá thuê, trạng thái và ảnh phòng.");
        model.addAttribute("roomFormSubmitLabel", "Lưu thay đổi");
        model.addAttribute("roomFormSubmitLoading", "Đang lưu...");
        model.addAttribute("roomImages", roomImageRepository.findByRoomIdOrderBySortOrderAsc(id));
    }

    private void prepareRoomCreateModel(Model model, AppUserDetails me) {
        prepareRoomFormModel(model, me, "Thêm phòng");
        model.addAttribute("roomFormMode", "create");
        model.addAttribute("roomFormAction", "/admin/rooms/create");
        model.addAttribute("roomFormBackUrl", "/admin/rooms");
        model.addAttribute("roomFormHeading", "Thêm phòng");
        model.addAttribute("roomFormDescription", "Nhập thông tin phòng, giá thuê và tiện nghi hiển thị.");
        model.addAttribute("roomFormSubmitLabel", "Tạo phòng");
        model.addAttribute("roomFormSubmitLoading", "Đang tạo...");
    }

    private void addTenantCatalog(Model model) {
        var tenants = userRepository.findByRolesContaining(User.Role.TENANT);
        model.addAttribute("tenantUsers", tenants);

        List<Map<String, Object>> tenantsLite = new ArrayList<>(tenants.size());
        for (User tenant : tenants) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", tenant.getId().toString());
            m.put("fullName", tenant.getFullName() != null ? tenant.getFullName() : "");
            m.put("email", tenant.getEmail() != null ? tenant.getEmail() : "");
            m.put("phone", tenant.getPhone() != null ? tenant.getPhone() : "");
            tenantsLite.add(m);
        }
        model.addAttribute("tenantsJson", tenantsLite);
    }

    private void addAmenityGroups(Model model) {
        Map<String, List<Amenity>> amenityGroups = new LinkedHashMap<>();
        amenityGroups.put("FURNITURE", new ArrayList<>());
        amenityGroups.put("UTILITY", new ArrayList<>());
        amenityGroups.put("RULE", new ArrayList<>());
        for (Amenity amenity : amenityRepository.findAllByOrderBySortOrderAsc()) {
            String key = amenity.getCategory() != null ? amenity.getCategory().name() : "OTHER";
            amenityGroups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(amenity);
        }
        model.addAttribute("amenityGroups", amenityGroups);
    }

    private static Map<String, Object> buildPropertyTariffs(List<vn.glassliving.property.entity.Property> properties) {
        Map<String, Object> tariffs = new LinkedHashMap<>();
        for (vn.glassliving.property.entity.Property property : properties) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", property.getId().toString());
            item.put("name", property.getName());
            item.put("address", fallbackPropertyAddress(property));
            item.put("electricUnit", moneyValue(property.getElectricUnit()));
            item.put("waterUnit", moneyValue(property.getWaterUnit()));
            item.put("serviceFee", moneyValue(property.getServiceFeeDefault()));
            item.put("fees", propertyFixedFees(property));
            tariffs.put(property.getId().toString(), item);
        }
        return tariffs;
    }

    private static List<Map<String, Object>> propertyFixedFees(vn.glassliving.property.entity.Property property) {
        List<Map<String, Object>> fees = new ArrayList<>();
        addTariffFee(fees, "Internet", property.getInternetFee());
        addTariffFee(fees, "Rác", property.getGarbageFee());
        addTariffFee(fees, "Quản lý", property.getManagementFee());
        if (property.getExtraFees() != null) {
            for (vn.glassliving.property.entity.Property.ExtraFee fee : property.getExtraFees()) {
                if (fee == null || isBlank(fee.getName())) continue;
                addTariffFee(fees, fee.getName().trim(), fee.getAmount());
            }
        }
        return fees;
    }

    private static void addTariffFee(List<Map<String, Object>> fees, String name, BigDecimal amount) {
        Map<String, Object> fee = new LinkedHashMap<>();
        fee.put("name", name);
        fee.put("amount", moneyValue(amount));
        fees.add(fee);
    }

    private static String moneyValue(BigDecimal amount) {
        return amount != null ? amount.stripTrailingZeros().toPlainString() : "0";
    }

    private static String fallbackPropertyAddress(vn.glassliving.property.entity.Property property) {
        List<String> parts = new ArrayList<>();
        if (!isBlank(property.getAddressLine())) parts.add(property.getAddressLine().trim());
        if (!isBlank(property.getDistrict())) parts.add(property.getDistrict().trim());
        if (!isBlank(property.getCity())) parts.add(property.getCity().trim());
        return String.join(", ", parts);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String statusLabel(Room.RoomStatus s) {
        return switch (s) {
            case AVAILABLE -> "Còn trống";
            case OCCUPIED -> "Đang ở";
            case MAINTENANCE -> "Bảo trì";
            case HIDDEN -> "Tạm ẩn";
        };
    }
}
