package vn.glassliving.auth.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;
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
import vn.glassliving.auth.service.AuthService;
import vn.glassliving.auth.service.PasswordResetService;
import vn.glassliving.common.exception.BusinessException;

import java.util.Locale;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class AuthWebController {

    private static final String RESET_EMAIL_SESSION_KEY = "PASSWORD_RESET_EMAIL";
    private static final String RESET_CODE_ID_SESSION_KEY = "PASSWORD_RESET_CODE_ID";

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

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
        model.addAttribute("form", new RegisterFormBacking("", "", "", "", false));
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
            authService.register(new RegisterRequest(
                    form.fullName(), form.email(), form.phone(), form.password(), form.acceptTerms()
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

    @PostMapping("/forgot-password")
    public String requestPasswordReset(@RequestParam String email,
                                       RedirectAttributes ra) {
        try {
            passwordResetService.requestResetCode(email);
            String normalizedEmail = normalizeEmailForView(email);
            ra.addFlashAttribute("info", "Nếu email tồn tại trong hệ thống, SmartRent đã gửi mã xác nhận 6 số.");
            ra.addFlashAttribute("resetEmail", normalizedEmail);
            return "redirect:/reset-password?email=" + normalizedEmail;
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            ra.addFlashAttribute("resetEmail", email);
            return "redirect:/forgot-password";
        }
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam(required = false) String email,
                                    HttpSession session,
                                    Model model) {
        String viewEmail = email != null ? email : (String) session.getAttribute(RESET_EMAIL_SESSION_KEY);
        String verifiedEmail = (String) session.getAttribute(RESET_EMAIL_SESSION_KEY);
        UUID resetCodeId = (UUID) session.getAttribute(RESET_CODE_ID_SESSION_KEY);
        boolean verified = viewEmail != null && viewEmail.equalsIgnoreCase(verifiedEmail) && resetCodeId != null;
        model.addAttribute("resetEmail", viewEmail);
        model.addAttribute("verified", verified);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password/verify")
    public String verifyPasswordResetCode(@RequestParam String email,
                                          @RequestParam String code,
                                          HttpSession session,
                                          RedirectAttributes ra) {
        try {
            PasswordResetService.VerifiedReset verified = passwordResetService.verifyCode(email, code);
            session.setAttribute(RESET_EMAIL_SESSION_KEY, verified.email());
            session.setAttribute(RESET_CODE_ID_SESSION_KEY, verified.resetCodeId());
            ra.addFlashAttribute("info", "Mã xác nhận hợp lệ. Vui lòng nhập mật khẩu mới.");
            return "redirect:/reset-password?email=" + verified.email();
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            ra.addFlashAttribute("resetEmail", email);
            return "redirect:/reset-password?email=" + normalizeEmailForView(email);
        }
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String email,
                                @RequestParam String password,
                                @RequestParam String confirmPassword,
                                HttpSession session,
                                RedirectAttributes ra) {
        try {
            String verifiedEmail = (String) session.getAttribute(RESET_EMAIL_SESSION_KEY);
            UUID resetCodeId = (UUID) session.getAttribute(RESET_CODE_ID_SESSION_KEY);
            if (verifiedEmail == null || resetCodeId == null || !verifiedEmail.equalsIgnoreCase(email)) {
                throw BusinessException.badRequest("Vui lòng xác nhận mã trước khi đặt mật khẩu mới.");
            }
            passwordResetService.resetPassword(email, resetCodeId, password, confirmPassword);
            session.removeAttribute(RESET_EMAIL_SESSION_KEY);
            session.removeAttribute(RESET_CODE_ID_SESSION_KEY);
            ra.addFlashAttribute("info", "Đã đổi mật khẩu. Vui lòng đăng nhập bằng mật khẩu mới.");
            return "redirect:/login";
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            ra.addFlashAttribute("resetEmail", email);
            return "redirect:/reset-password?email=" + normalizeEmailForView(email);
        }
    }

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
            boolean acceptTerms
    ) {}

    private static String normalizeEmailForView(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
