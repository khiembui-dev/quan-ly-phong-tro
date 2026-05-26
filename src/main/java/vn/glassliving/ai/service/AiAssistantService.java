package vn.glassliving.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.glassliving.ai.entity.AiQuery;
import vn.glassliving.ai.repository.AiQueryRepository;

import java.util.List;
import java.util.UUID;

/**
 * Mock AI service. Returns canned responses based on simple keyword matches.
 * When `app.ai.enabled=true`, will be replaced by a real Anthropic Claude client.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantService {

    private final AiQueryRepository aiQueryRepository;

    @Value("${app.ai.enabled:false}")
    private boolean enabled;

    @Value("${app.ai.anthropic.model-haiku}")
    private String model;

    public String chat(UUID userId, String prompt, String context) {
        long start = System.currentTimeMillis();
        String reply = enabled ? callRealApi(prompt, context) : mockReply(prompt, context);
        long duration = System.currentTimeMillis() - start;

        try {
            aiQueryRepository.save(AiQuery.builder()
                    .userId(userId)
                    .prompt(prompt)
                    .response(reply)
                    .model(model)
                    .durationMs((int) duration)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to save AI query history", e);
        }
        return reply;
    }

    public List<AiQuery> history(UUID userId) {
        return aiQueryRepository.findTop10ByUserIdOrderByCreatedAtDesc(userId);
    }

    private String callRealApi(String prompt, String context) {
        // TODO: integrate Anthropic Java SDK or HTTP client
        return mockReply(prompt, context);
    }

    private String mockReply(String prompt, String context) {
        String p = prompt == null ? "" : prompt.toLowerCase();
        boolean isAdmin = "ADMIN".equals(context);

        if (p.contains("doanh thu")) {
            return isAdmin
                    ? "Doanh thu tháng này: 142.500.000₫ (+12.4% so với tháng trước).\n" +
                      "Top 3 cơ sở: Glass Tower (58tr), Riverside (45tr), Lotus House (39tr).\n" +
                      "Dự báo Q2: ~430-460tr nếu tỉ lệ lấp đầy giữ ở mức 88%."
                    : "Tổng chi phí thuê + dịch vụ tháng này của bạn: 7.260.000₫. Hóa đơn còn 1 chưa thanh toán.";
        }
        if (p.contains("hết hạn") || p.contains("sắp hết")) {
            return "Tháng tới có 2 phòng sắp đến hạn thanh toán:\n" +
                   "• HD-202503-0012 — phòng LH-201 — kết thúc 31/05/2026 (nên gửi email gia hạn ngay).\n" +
                   "• HD-202504-0028 — phòng RS-101 — kết thúc 14/06/2026.";
        }
        if (p.contains("sửa") || p.contains("bảo trì")) {
            return "Phòng LH-303 đang trạng thái BẢO TRÌ. Yêu cầu hiện tại: thay máy nước nóng, sơn lại tường. " +
                   "Ước tính 2.500.000₫, hoàn thành sau 5 ngày.";
        }
        if (p.contains("trễ") || p.contains("quá hạn")) {
            return "Top khách trễ hạn (tích lũy):\n" +
                   "1) Khách Nguyễn V.A — INV-202604-0099 trễ 3 ngày — 7.195.000₫.\n" +
                   "2) Khách Trần T.B — trễ 1 hóa đơn tháng 4 — 6.800.000₫.\n" +
                   "Nên gửi nhắc nhở qua email + SMS.";
        }
        if (p.contains("email") || p.contains("soạn")) {
            return "Gợi ý nội dung email tăng giá:\n\n" +
                   "Kính gửi quý khách thuê,\n" +
                   "Do chi phí dịch vụ và bảo trì tăng, kể từ ngày 01/07/2026, giá thuê sẽ điều chỉnh +5%. " +
                   "Giá hiện tại được giữ nguyên đến hết kỳ đã thanh toán. Mọi thắc mắc vui lòng liên hệ.\n\n" +
                   "Trân trọng,\nSmartRent";
        }
        if (p.contains("thú cưng") || p.contains("pet")) {
            return "Hiện có 2 phòng cho phép thú cưng: Riverside Q7 205 (7.8tr/tháng) và Glass Tower B-502 (penthouse 18tr). " +
                   "Cả 2 đều có ban công và gần công viên.";
        }
        if (p.contains("studio") && p.contains("đôi")) {
            return "So sánh nhanh:\n" +
                   "• Studio: 1 không gian mở, 22-32m², 1 người ở, giá 6-9tr.\n" +
                   "• Phòng đôi: 1 phòng ngủ tách bếp/khách, 22-30m², 2 người ở, giá 6.5-9tr.\n" +
                   "Studio hợp người độc thân; phòng đôi hợp cặp đôi/2 người ở chung.";
        }
        if (p.contains("q1") || p.contains("quận 1")) {
            return "Phòng Quận 1 dưới 10 triệu hiện có:\n" +
                   "• Studio Glass Tower 301 — 8.5tr — 28m² — view trung tâm.\n" +
                   "Bạn muốn mình lọc thêm theo tiêu chí gì (m², ban công, thú cưng…)?";
        }
        return isAdmin
                ? "Tôi có thể giúp bạn phân tích doanh thu, tìm phòng trễ hạn, dự báo, soạn email khách thuê. Hỏi cụ thể nhé!"
                : "Mình có thể giúp tìm phòng theo khu vực, ngân sách, tiện nghi, hoặc giải thích hóa đơn. Bạn cần gì?";
    }
}
