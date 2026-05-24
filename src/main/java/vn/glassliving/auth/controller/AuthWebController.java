package vn.glassliving.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.glassliving.auth.dto.RegisterRequest;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.service.AuthService;
import vn.glassliving.common.exception.BusinessException;

@Controller
@RequiredArgsConstructor
public class AuthWebController {

    private final AuthService authService;

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error,
                            @RequestParam(required = false) String logout,
                            Model model) {
        if (error != null) model.addAttribute("error", "Email hoặc mật khẩu không đúng.");
        if (logout != null) model.addAttribute("info", "Bạn đã đăng xuất.");
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("form", new RegisterFormBacking("", "", "", "", "TENANT", false));
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterFormBacking form,
                           BindingResult br,
                           RedirectAttributes ra,
                           Model model) {
        if (!form.acceptTerms()) {
            br.rejectValue("acceptTerms", "required", "Vui lòng đồng ý điều khoản.");
        }
        if (br.hasErrors()) return "auth/register";
        try {
            User.Role role = User.Role.valueOf(form.role());
            authService.register(new RegisterRequest(
                    form.fullName(), form.email(), form.phone(), form.password(), role, form.acceptTerms()
            ));
            ra.addFlashAttribute("info", "Đăng ký thành công. Đăng nhập để tiếp tục.");
            return "redirect:/login";
        } catch (BusinessException ex) {
            model.addAttribute("error", ex.getMessage());
            return "auth/register";
        }
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() { return "auth/forgot-password"; }

    @GetMapping("/post-login")
    public String postLogin(Authentication auth) {
        if (auth == null) return "redirect:/login";
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_OWNER") ||
                a.getAuthority().equals("ROLE_ADMIN") ||
                a.getAuthority().equals("ROLE_STAFF"));
        return isAdmin ? "redirect:/admin" : "redirect:/me";
    }

    public record RegisterFormBacking(
            String fullName,
            String email,
            String phone,
            String password,
            String role,
            boolean acceptTerms
    ) {}
}
