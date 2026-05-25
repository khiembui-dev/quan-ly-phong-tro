package vn.glassliving.admin.page.rooms;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.web.FlashAlert;
import vn.glassliving.room.dto.RoomForm;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.service.RoomAdminService;

import java.math.BigDecimal;
import java.util.UUID;

@Controller
@RequestMapping("/admin/rooms")
@RequiredArgsConstructor
public class RoomsActionController {

    private final RoomAdminService roomAdminService;

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

    private static String statusLabel(Room.RoomStatus s) {
        return switch (s) {
            case AVAILABLE   -> "Còn trống";
            case OCCUPIED    -> "Đang ở";
            case MAINTENANCE -> "Bảo trì";
            case HIDDEN      -> "Tạm ẩn";
        };
    }
}
