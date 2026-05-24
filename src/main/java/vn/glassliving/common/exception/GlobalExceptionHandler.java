package vn.glassliving.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.view.RedirectView;
import vn.glassliving.common.dto.ApiResponse;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestControllerAdvice(basePackages = "vn.glassliving")
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Object handleBusiness(BusinessException ex, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        log.warn("Business error [{}] {} {}: {}", ex.getStatus(), req.getMethod(), req.getRequestURI(), ex.getMessage());
        if (isHtmlRequest(req)) {
            return redirectBack(req, resp, ex.getMessage());
        }
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.fail(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (isHtmlRequest(req)) {
            return redirectBack(req, resp, "Dữ liệu không hợp lệ. Vui lòng kiểm tra các trường.");
        }
        List<ApiResponse.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ApiResponse.fail("Dữ liệu không hợp lệ", errors));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public Object handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (isHtmlRequest(req)) {
            return redirectBack(req, resp, "Dữ liệu đã được người khác cập nhật, vui lòng tải lại.");
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("Dữ liệu đã được người khác cập nhật, vui lòng tải lại."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("Bạn không có quyền truy cập."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("Yêu cầu đăng nhập."));
    }

    @ExceptionHandler(Exception.class)
    public Object handleAny(Exception ex, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        log.error("Unhandled error at {} {}", req.getMethod(), req.getRequestURI(), ex);
        if (isHtmlRequest(req)) {
            return redirectBack(req, resp, "Có lỗi xảy ra. Vui lòng thử lại.");
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("Có lỗi xảy ra. Vui lòng thử lại."));
    }

    private boolean isHtmlRequest(HttpServletRequest req) {
        if (req.getRequestURI().startsWith("/api/")) return false;
        String accept = req.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }

    /** Redirect back to the page that initiated the form POST, attaching a flash error via session. */
    private RedirectView redirectBack(HttpServletRequest req, HttpServletResponse resp, String message) throws IOException {
        String referer = req.getHeader("Referer");
        String target = (referer != null && !referer.isBlank()) ? referer : "/";
        // Flash via session attribute (read by GlobalModelAdvice or layout)
        req.getSession().setAttribute("flashKind", "ERR");
        req.getSession().setAttribute("flashMessage", message);
        RedirectView rv = new RedirectView(target);
        rv.setStatusCode(HttpStatus.SEE_OTHER);
        return rv;
    }
}
