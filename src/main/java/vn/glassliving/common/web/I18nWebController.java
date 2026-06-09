package vn.glassliving.common.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

@RestController
@RequestMapping("/i18n")
public class I18nWebController {

    @GetMapping(value = "/{lang}.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> bundle(@PathVariable String lang) {
        String normalized = "en".equalsIgnoreCase(lang) ? "en" : "vi";
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.putAll(loadProperties("messages.properties"));
        bundle.putAll(loadProperties("messages_" + normalized + ".properties"));
        bundle.put("legacy", Map.of(
                "text", legacyText(normalized),
                "attributes", legacyAttributes(normalized)
        ));
        return bundle;
    }

    private static Map<String, String> loadProperties(String name) {
        ClassPathResource resource = new ClassPathResource(name);
        if (!resource.exists()) return Map.of();
        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (Exception ignored) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        properties.stringPropertyNames().stream()
                .sorted()
                .forEach(key -> result.put(key, properties.getProperty(key)));
        return result;
    }

    private static Map<String, String> legacyAttributes(String lang) {
        if (!"en".equals(lang)) return Map.of();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("Tìm ticket", "Search tickets");
        values.put("ban@gmail.com", "you@gmail.com");
        values.put("VD: Máy lạnh không lạnh", "Example: Air conditioner is not cooling");
        values.put("Mô tả tình trạng, vị trí, thời điểm phát hiện", "Describe the issue, location, and when it happened");
        return values;
    }

    private static Map<String, String> legacyText(String lang) {
        if (!"en".equals(lang)) return Map.of();
        Map<String, String> values = new LinkedHashMap<>();

        values.put("Trang chủ", "Home");
        values.put("Phòng trống", "Available rooms");
        values.put("Phòng còn trống", "Available rooms");
        values.put("Phòng của tôi", "My rooms");
        values.put("Phòng", "Rooms");
        values.put("Hóa đơn", "Invoices");
        values.put("Hồ sơ", "Profile");
        values.put("Hỗ trợ", "Support");
        values.put("Đăng nhập", "Log in");
        values.put("Đăng ký", "Register");
        values.put("Đăng xuất", "Log out");
        values.put("Hồ sơ cá nhân", "Personal profile");
        values.put("Khu vực quản trị", "Admin area");
        values.put("Khách thuê", "Tenants");
        values.put("Tìm phòng", "Find rooms");
        values.put("Phòng đã lưu", "Saved rooms");
        values.put("Ticket hỗ trợ", "Support tickets");
        values.put("Gọi hỗ trợ", "Call support");
        values.put("Nhắn Zalo", "Message on Zalo");
        values.put("Chưa cập nhật thông tin liên hệ.", "Contact information is not updated yet.");
        values.put("Thông tin thuê phòng minh bạch, hỗ trợ dễ dàng.", "Transparent rental information, easy support.");
        values.put("Quản lý phòng, hóa đơn, điện nước và yêu cầu hỗ trợ trong một nơi.",
                "Manage rooms, invoices, utilities, and support requests in one place.");

        values.put("Chào mừng trở lại", "Welcome back");
        values.put("Đăng nhập để tiếp tục với SmartRent.", "Log in to continue with SmartRent.");
        values.put("Email", "Email");
        values.put("Mật khẩu", "Password");
        values.put("Ghi nhớ tôi", "Remember me");
        values.put("Quên mật khẩu?", "Forgot password?");
        values.put("Chưa có tài khoản?", "Do not have an account?");
        values.put("Đăng ký miễn phí", "Register for free");
        values.put("Tạo tài khoản khách thuê", "Create tenant account");
        values.put("Họ và tên", "Full name");
        values.put("Số điện thoại", "Phone number");
        values.put("Tạo tài khoản", "Create account");
        values.put("Đã có tài khoản?", "Already have an account?");
        values.put("Quên mật khẩu - SmartRent", "Forgot password - SmartRent");
        values.put("Đặt lại mật khẩu - SmartRent", "Reset password - SmartRent");
        values.put("Gửi mã xác nhận", "Send verification code");
        values.put("Quay lại đăng nhập", "Back to login");
        values.put("Mã xác nhận", "Verification code");
        values.put("Xác nhận mã", "Verify code");
        values.put("Mật khẩu mới", "New password");
        values.put("Nhập lại mật khẩu mới", "Confirm new password");
        values.put("Đổi mật khẩu", "Change password");
        values.put("Gửi lại mã", "Send code again");

        values.put("Tìm phòng trọ", "Find rental rooms");
        values.put("như ở nhà", "that feel like home");
        values.put("Tất cả khu vực", "All areas");
        values.put("Ngân sách", "Budget");
        values.put("Tất cả ngân sách", "All budgets");
        values.put("Dưới 5 triệu", "Under 5 million");
        values.put("Dưới 8 triệu", "Under 8 million");
        values.put("Dưới 12 triệu", "Under 12 million");
        values.put("Dưới 20 triệu", "Under 20 million");
        values.put("Đăng ký tài khoản", "Create account");
        values.put("Đăng nhập tài khoản", "Log in");
        values.put("Xem phòng trống", "Browse rooms");
        values.put("Tất cả", "All");
        values.put("Studio", "Studio");
        values.put("Phòng đôi", "Double room");
        values.put("Penthouse", "Penthouse");
        values.put("Cho thú cưng", "Pet-friendly");
        values.put("Đề xuất hôm nay", "Today suggestions");
        values.put("Phòng đang còn trống", "Available rooms");
        values.put("Tại sao chọn SmartRent", "Why SmartRent");
        values.put("Thuê phòng chưa bao giờ dễ đến vậy", "Renting has never been easier");

        values.put("Không gian khách thuê", "Tenant area");
        values.put("Xin chào,", "Hello,");
        values.put("Phòng đang thuê", "Active rooms");
        values.put("Cần thanh toán", "Amount due");
        values.put("Hóa đơn mở", "Open invoices");
        values.put("Thông tin thuê phòng", "Rental information");
        values.put("Đang thuê", "Renting");
        values.put("Cơ sở", "Property");
        values.put("Diện tích", "Area");
        values.put("Sức chứa", "Capacity");
        values.put("Phòng ngủ", "Bedrooms");
        values.put("Bắt đầu", "Start date");
        values.put("Đã thanh toán đến", "Paid until");
        values.put("Tiền cọc", "Deposit");
        values.put("Phí dịch vụ", "Service fee");
        values.put("Điện", "Electricity");
        values.put("Nước", "Water");
        values.put("Chi tiết", "Details");
        values.put("Thanh toán", "Payment");
        values.put("Hóa đơn gần đây", "Recent invoices");
        values.put("Xem tất cả", "View all");
        values.put("Điện nước", "Utilities");
        values.put("Chỉ số gần nhất", "Latest readings");
        values.put("Lịch sử chỉ số", "Reading history");

        values.put("Hóa đơn các tháng", "Monthly invoices");
        values.put("Tổng còn nợ", "Total debt");
        values.put("Đã thanh toán", "Paid");
        values.put("Đang quá hạn", "Overdue");
        values.put("Tháng", "Month");
        values.put("Năm", "Year");
        values.put("Trạng thái", "Status");
        values.put("Tất cả tháng", "All months");
        values.put("Tất cả năm", "All years");
        values.put("Tất cả trạng thái", "All statuses");
        values.put("Chưa thanh toán", "Unpaid");
        values.put("Đã trả một phần", "Partially paid");
        values.put("Quá hạn", "Overdue");
        values.put("Đã hủy", "Cancelled");
        values.put("Lọc", "Filter");
        values.put("Xóa lọc", "Clear filters");
        values.put("Tiền phòng", "Room rent");
        values.put("Tiền điện", "Electricity");
        values.put("Tiền nước", "Water");
        values.put("Dịch vụ / phí khác", "Services / other fees");
        values.put("Hạn thanh toán", "Due date");
        values.put("Tổng tiền", "Total amount");
        values.put("Xem chi tiết", "View details");

        values.put("Tài khoản", "Account");
        values.put("Hồ sơ khách thuê", "Tenant profile");
        values.put("Cá nhân", "Personal");
        values.put("Giấy tờ", "Documents");
        values.put("Bảo mật", "Security");
        values.put("Thông tin cá nhân", "Personal information");
        values.put("Ảnh đại diện", "Avatar");
        values.put("Chọn ảnh", "Choose image");
        values.put("Ngày sinh", "Date of birth");
        values.put("Giới tính", "Gender");
        values.put("Địa chỉ thường trú", "Permanent address");
        values.put("Lưu hồ sơ", "Save profile");

        values.put("Hỗ trợ và ticket", "Support and tickets");
        values.put("Tạo ticket", "Create ticket");
        values.put("Gọi nhanh", "Quick call");
        values.put("Tổng ticket", "Total tickets");
        values.put("Đang xử lý", "In progress");
        values.put("Đã hoàn tất", "Completed");
        values.put("Ticket của tôi", "My tickets");
        values.put("Xem chi tiết", "View details");
        values.put("Phản hồi", "Reply");
        values.put("Gửi phản hồi", "Send reply");
        values.put("Ticket mới", "New ticket");
        values.put("Tạo yêu cầu hỗ trợ", "Create support request");
        values.put("Tiêu đề *", "Title *");
        values.put("Loại yêu cầu", "Request type");
        values.put("Mức độ", "Priority");
        values.put("Mô tả *", "Description *");
        values.put("Hủy", "Cancel");
        values.put("Thiết bị", "Equipment");
        values.put("Máy lạnh", "Air conditioner");
        values.put("Internet", "Internet");
        values.put("Nội thất", "Furniture");
        values.put("An ninh", "Security");
        values.put("Khác", "Other");
        values.put("Bình thường", "Normal");
        values.put("Cần xử lý sớm", "High");
        values.put("Khẩn cấp", "Urgent");
        values.put("Thấp", "Low");

        return values;
    }
}
