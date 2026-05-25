package vn.glassliving.admin.page.properties;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.web.FlashAlert;
import vn.glassliving.property.dto.PropertyForm;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.service.PropertyService;

import java.util.UUID;

@Controller
@RequestMapping("/admin/properties")
@RequiredArgsConstructor
public class PropertiesActionController {

    private final PropertyService propertyService;

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserDetails me,
                         @Valid @ModelAttribute("form") PropertyForm form,
                         BindingResult br,
                         RedirectAttributes ra) {
        if (br.hasErrors()) {
            FlashAlert.err(ra, "Vui lòng kiểm tra các trường đã đánh dấu lỗi.");
            return "redirect:/admin/properties";
        }
        try {
            Property p = propertyService.create(me.getId(), form);
            FlashAlert.ok(ra, "Đã tạo cơ sở \"" + p.getName() + "\" thành công.");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/properties";
    }

    @PostMapping("/{id}/update")
    public String update(@AuthenticationPrincipal AppUserDetails me,
                         @PathVariable UUID id,
                         @Valid @ModelAttribute("form") PropertyForm form,
                         BindingResult br,
                         RedirectAttributes ra) {
        if (br.hasErrors()) {
            FlashAlert.err(ra, "Vui lòng kiểm tra các trường đã đánh dấu lỗi.");
            return "redirect:/admin/properties";
        }
        try {
            Property p = propertyService.update(me.getId(), id, form);
            FlashAlert.ok(ra, "Đã cập nhật cơ sở \"" + p.getName() + "\".");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/properties";
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails me,
                         @PathVariable UUID id,
                         RedirectAttributes ra) {
        try {
            propertyService.delete(me.getId(), id);
            FlashAlert.ok(ra, "Đã xóa cơ sở.");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/properties";
    }
}
