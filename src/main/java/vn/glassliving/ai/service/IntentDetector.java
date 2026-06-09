package vn.glassliving.ai.service;

import org.springframework.stereotype.Service;
import vn.glassliving.ai.entity.AiConversation;

import java.text.Normalizer;
import java.util.Locale;

@Service
public class IntentDetector {

    public DetectedIntent detect(AiConversation.Role role, String message) {
        String text = normalize(message);
        if (text.isBlank()) {
            return DetectedIntent.of(AiIntent.UNKNOWN, "generalWebsiteGuide", false);
        }
        if (isUnsafeOrUnrelated(text)) {
            return DetectedIntent.outOfScope(AiIntent.OUT_OF_SCOPE);
        }
        if (role == AiConversation.Role.ADMIN) {
            return detectAdmin(text);
        }
        return detectCustomer(text);
    }

    private DetectedIntent detectCustomer(String text) {
        if (containsAny(text, "doanh thu", "tong doanh thu", "bao cao doanh thu",
                "thong ke he thong", "tong quan he thong", "khach thue can xu ly",
                "danh sach khach thue", "quan tri")) {
            return DetectedIntent.outOfScope(AiIntent.RESTRICTED_ADMIN_DATA);
        }
        if (isGeneralHelp(text)) {
            return DetectedIntent.of(AiIntent.GENERAL_HELP, "generalWebsiteGuide", false);
        }
        if (containsAny(text, "doi mat khau", "cap nhat ho so", "doi so dien thoai", "doi sdt",
                "cach sua tai khoan", "cach cap nhat tai khoan")) {
            return DetectedIntent.of(AiIntent.CUSTOMER_ACCOUNT, "accountGuide", false);
        }
        if (containsAny(text, "tai khoan", "ho so", "thong tin ca nhan", "profile")) {
            return DetectedIntent.of(AiIntent.CUSTOMER_ACCOUNT, "getMyProfile", true);
        }
        if (containsAny(text, "cach thanh toan", "huong dan thanh toan", "thanh toan qr",
                "cach quet qr", "qr", "chuyen khoan")) {
            return DetectedIntent.of(AiIntent.PAYMENT, "paymentGuide", false);
        }
        if (containsAny(text, "da chuyen tien", "kiem tra thanh toan", "lich su thanh toan")) {
            return DetectedIntent.of(AiIntent.PAYMENT, "getMyPayments", true);
        }
        if (containsAny(text, "cach gui ticket", "huong dan gui ticket", "tao ticket", "gui ticket")) {
            return DetectedIntent.of(AiIntent.TICKET, "ticketGuide", false);
        }
        if (containsAny(text, "ticket", "ho tro", "sua chua", "bao tri", "bao hong", "khieu nai", "phan hoi")) {
            return DetectedIntent.of(AiIntent.TICKET, "getMyTickets", true);
        }
        if (containsAny(text, "hoa don", "tien phong", "cong no", "da thanh toan",
                "chua thanh toan", "qua han")) {
            return DetectedIntent.of(AiIntent.INVOICE, "getMyInvoices", true);
        }
        if (containsAny(text, "dien nuoc", "tien dien", "tien nuoc", "chi so dien", "chi so nuoc", "chi so")) {
            return DetectedIntent.of(AiIntent.UTILITY, "getMyUtilityHistory", true);
        }
        if (containsAny(text, "lien he", "chu tro", "zalo", "so dien thoai", "sdt")) {
            return DetectedIntent.of(AiIntent.LANDLORD_CONTACT, "getLandlordContact", true);
        }
        if (containsAny(text, "phong trong", "phong con", "dat phong", "tim phong", "phong co san")) {
            return DetectedIntent.of(AiIntent.ROOM, "getAvailableRooms", true);
        }
        if (containsAny(text, "phong toi", "phong cua toi", "dang o", "dang thue", "can ho cua toi",
                "han phong", "han thue", "gia phong", "tien ich phong", "thong tin phong")) {
            return DetectedIntent.of(AiIntent.ROOM, "getMyRoom", true);
        }
        if (containsAny(text, "co so", "dia chi", "chi nhanh", "toa nha")) {
            return DetectedIntent.of(AiIntent.PROPERTY, "getMyRoomProperty", true);
        }
        if (containsAny(text, "thong bao", "tin nhan he thong", "notification")) {
            return DetectedIntent.of(AiIntent.NOTIFICATION, "getMyNotifications", true);
        }
        return DetectedIntent.of(AiIntent.UNKNOWN, "generalWebsiteGuide", false);
    }

    private DetectedIntent detectAdmin(String text) {
        if (isGeneralHelp(text)) {
            return DetectedIntent.of(AiIntent.GENERAL_HELP, "generalWebsiteGuide", false);
        }
        if (containsAny(text, "cach thanh toan", "huong dan thanh toan", "cach gui ticket",
                "huong dan gui ticket", "doi mat khau", "cap nhat ho so")) {
            return DetectedIntent.of(AiIntent.GENERAL_HELP, "generalWebsiteGuide", false);
        }
        if (containsAny(text, "hoa don", "chua thanh toan", "cong no", "qua han", "tre han")) {
            return DetectedIntent.of(AiIntent.INVOICE, "getInvoiceSummary", true);
        }
        if (containsAny(text, "ticket", "ho tro", "sua chua", "bao tri", "phan hoi")) {
            return DetectedIntent.of(AiIntent.TICKET, "getTicketSummary", true);
        }
        if (containsAny(text, "thanh toan", "payment", "giao dich", "da thu")) {
            return DetectedIntent.of(AiIntent.PAYMENT, "getPaymentSummary", true);
        }
        if (containsAny(text, "dien nuoc bat thuong", "bat thuong", "chi so", "dien nuoc")) {
            return DetectedIntent.of(AiIntent.UTILITY, "getUtilityAnomalies", true);
        }
        if (containsAny(text, "doanh thu", "tong quan", "dashboard", "he thong", "hom nay", "bao cao",
                "thong ke", "phong trong", "phong dang thue", "bao nhieu phong", "trang thai phong",
                "khach thue", "khach hang")) {
            return DetectedIntent.of(AiIntent.ADMIN_DASHBOARD, adminDashboardTool(text), true);
        }
        if (containsAny(text, "phong", "co so", "chi nhanh", "dia chi")) {
            return DetectedIntent.of(AiIntent.ROOM, "getRoomSummary", true);
        }
        return DetectedIntent.of(AiIntent.UNKNOWN, "generalWebsiteGuide", false);
    }

    private String adminDashboardTool(String text) {
        if (containsAny(text, "doanh thu", "da thu", "thu nhap")) return "getRevenueSummary";
        if (containsAny(text, "phong", "trang thai phong")) return "getRoomSummary";
        if (containsAny(text, "khach thue", "khach hang")) return "getCustomerSummary";
        return "getDashboardSummary";
    }

    private boolean isGeneralHelp(String text) {
        return containsAny(text, "cach dung", "cach su dung", "huong dan", "nut nay lam gi",
                "chuc nang nao", "co chuc nang gi", "co cac tien ich nao", "tien ich nao",
                "tien ich he thong", "chatbot lam duoc gi", "ban lam duoc gi", "tro ly lam duoc gi",
                "help", "tro giup", "xin chao", "hello");
    }

    private boolean isUnsafeOrUnrelated(String text) {
        return containsAny(text, "hack", "tan cong", "ddos", "danh cap", "lay mat khau nguoi khac",
                "xoa database", "pha he thong", "ma tuy", "vu khi", "lua dao", "noi dung khieu dam");
    }

    static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }
}
