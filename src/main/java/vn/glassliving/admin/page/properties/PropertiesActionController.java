package vn.glassliving.admin.page.properties;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
import java.util.Locale;

@Controller
@RequestMapping("/admin/properties")
@RequiredArgsConstructor
public class PropertiesActionController {

    private final PropertyService propertyService;
    private final MessageSource messageSource;

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserDetails me,
                         @Valid @ModelAttribute("form") PropertyForm form,
                         BindingResult br,
                         Locale locale,
                         RedirectAttributes ra) {
        if (br.hasErrors()) {
            FlashAlert.err(ra, msg(locale, "validation.invalidInfo"));
            return "redirect:/admin/properties";
        }
        try {
            Property p = propertyService.create(me.getId(), form);
            FlashAlert.ok(ra, msg(locale, "properties.flash.created", p.getName()));
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/properties";
    }

    @PostMapping("/create")
    public String createFromPage(@AuthenticationPrincipal AppUserDetails me,
                                 @Valid @ModelAttribute("form") PropertyForm form,
                                 BindingResult br,
                                 Model model,
                                 Locale locale,
                                 RedirectAttributes ra) {
        validateCreateAddress(form, br, locale);
        if (br.hasErrors()) {
            prepareCreateModel(model, locale);
            return "admin/property-create";
        }
        try {
            Property p = propertyService.create(me.getId(), form);
            FlashAlert.ok(ra, msg(locale, "properties.flash.created", p.getName()));
            return "redirect:/admin/properties";
        } catch (BusinessException ex) {
            br.reject("propertyCreate", ex.getMessage());
            prepareCreateModel(model, locale);
            return "admin/property-create";
        }
    }

    @PostMapping("/{id}/update")
    public String update(@AuthenticationPrincipal AppUserDetails me,
                         @PathVariable UUID id,
                         @Valid @ModelAttribute("form") PropertyForm form,
                         BindingResult br,
                         Model model,
                         Locale locale,
                         RedirectAttributes ra) {
        if (br.hasErrors()) {
            prepareEditModel(model, id, locale);
            return "admin/property-edit";
        }
        try {
            Property p = propertyService.update(me.getId(), id, form);
            FlashAlert.ok(ra, msg(locale, "properties.flash.updated", p.getName()));
            return "redirect:/admin/properties";
        } catch (BusinessException ex) {
            br.reject("propertyUpdate", ex.getMessage());
            prepareEditModel(model, id, locale);
            return "admin/property-edit";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails me,
                         @PathVariable UUID id,
                         Locale locale,
                         RedirectAttributes ra) {
        try {
            propertyService.delete(me.getId(), id);
            FlashAlert.ok(ra, msg(locale, "properties.flash.deleted"));
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/properties";
    }

    private void validateCreateAddress(PropertyForm form, BindingResult br, Locale locale) {
        if (isBlank(form.getProvinceCode()) || isBlank(form.getProvinceName())) {
            br.rejectValue("city", "property.city.required", msg(locale, "properties.form.validation.cityRequired"));
        }
        if (isBlank(form.getDistrictCode()) || isBlank(form.getDistrictName())) {
            br.rejectValue("district", "property.district.required", msg(locale, "properties.form.validation.districtRequired"));
        }
        if (isBlank(form.getWardCode()) || isBlank(form.getWardName())) {
            br.rejectValue("wardName", "property.ward.required", msg(locale, "properties.form.validation.wardRequired"));
        }
    }

    private void prepareCreateModel(Model model, Locale locale) {
        model.addAttribute("activeNav", "properties");
        model.addAttribute("pageTitle", msg(locale, "properties.form.createTitle"));
    }

    private void prepareEditModel(Model model, UUID id, Locale locale) {
        model.addAttribute("propertyId", id);
        model.addAttribute("activeNav", "properties");
        model.addAttribute("pageTitle", msg(locale, "properties.form.editTitle"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String msg(Locale locale, String code, Object... args) {
        return messageSource.getMessage(code, args, locale);
    }
}
