package vn.glassliving.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.glassliving.ai.dto.GeminiRequest;
import vn.glassliving.ai.entity.AiConversation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiIntentPlanner {

    private static final Map<String, ToolDefinition> CUSTOMER_TOOLS = customerTools();
    private static final Map<String, ToolDefinition> ADMIN_TOOLS = adminTools();

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public Optional<DetectedIntent> plan(AiConversation.Role role, String message) {
        if (role == null || message == null || message.isBlank()) {
            return Optional.empty();
        }
        GeminiRequest request = new GeminiRequest(
                GeminiRequest.Content.system(systemPrompt(role)),
                java.util.List.of(GeminiRequest.Content.user(message.trim())),
                new GeminiRequest.GenerationConfig(0.0, 350)
        );
        return geminiClient.generate(request)
                .flatMap(answer -> parsePlan(role, answer));
    }

    private Optional<DetectedIntent> parsePlan(AiConversation.Role role, String answer) {
        String json = extractJson(answer);
        if (json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            String toolName = text(root.path("toolName"));
            String intentText = text(root.path("intent"));
            if (toolName.isBlank() && !intentText.isBlank()) {
                toolName = defaultToolForIntent(role, intentText);
            }
            Map<String, ToolDefinition> allowedTools = allowedTools(role);
            ToolDefinition definition = allowedTools.get(toolName);
            if (definition == null) {
                log.info("Gemini intent planner rejected role={} toolName={}", role, safeToolName(toolName));
                return Optional.empty();
            }
            return Optional.of(DetectedIntent.of(definition.intent(), definition.toolName(), definition.requiresData()));
        } catch (Exception ex) {
            log.info("Gemini intent planner returned non-JSON or invalid schema: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private String systemPrompt(AiConversation.Role role) {
        String allowed = allowedTools(role).values().stream()
                .map(tool -> "- %s | intent=%s | requiresData=%s | %s".formatted(
                        tool.toolName(), tool.intent(), tool.requiresData(), tool.description()))
                .collect(Collectors.joining("\n"));
        return """
                You are SmartRent AI routing planner.
                Role: %s

                Choose exactly one allowed backend tool for the user's message.
                Return JSON only, no markdown:
                {"intent":"INVOICE","toolName":"getMyInvoices","requiresData":true}

                Allowed tools for this role:
                %s

                Security rule:
                - CUSTOMER can only use CUSTOMER tools and their own data.
                - ADMIN can use ADMIN tools.
                - Never use these admin-only tools for CUSTOMER: getRevenueSummary, getDashboardSummary, getCustomerSummary, getUnpaidInvoices, getOverdueInvoices, getRoomSummary, searchCustomer, getTicketSummary, getPaymentSummary, getUtilityAnomalies.
                - If no data is needed, choose generalWebsiteGuide, paymentGuide, ticketGuide, or accountGuide when appropriate.
                - If unsure, choose generalWebsiteGuide.
                """.formatted(role, allowed);
    }

    private Map<String, ToolDefinition> allowedTools(AiConversation.Role role) {
        return role == AiConversation.Role.ADMIN ? ADMIN_TOOLS : CUSTOMER_TOOLS;
    }

    private String defaultToolForIntent(AiConversation.Role role, String intentText) {
        AiIntent intent;
        try {
            intent = AiIntent.valueOf(intentText.trim());
        } catch (IllegalArgumentException ex) {
            return "";
        }
        return allowedTools(role).values().stream()
                .filter(tool -> tool.intent() == intent)
                .map(ToolDefinition::toolName)
                .findFirst()
                .orElse("");
    }

    private static Map<String, ToolDefinition> customerTools() {
        Map<String, ToolDefinition> tools = new LinkedHashMap<>();
        put(tools, AiIntent.GENERAL_HELP, "generalWebsiteGuide", false, "Explain SmartRent user features and navigation.");
        put(tools, AiIntent.CUSTOMER_ACCOUNT, "accountGuide", false, "Guide profile, password, and personal information updates.");
        put(tools, AiIntent.CUSTOMER_ACCOUNT, "getMyProfile", true, "Read the current customer's profile.");
        put(tools, AiIntent.ROOM, "getMyRoom", true, "Read the current customer's active room and paid-until date.");
        put(tools, AiIntent.ROOM, "getAvailableRooms", true, "List available public rooms for booking.");
        put(tools, AiIntent.AVAILABLE_ROOMS, "getAvailableRooms", true, "List available public rooms for booking.");
        put(tools, AiIntent.PROPERTY, "getMyRoomProperty", true, "Read property and address for the customer's current room.");
        put(tools, AiIntent.INVOICE, "getMyInvoices", true, "Read the current customer's invoices and debt.");
        put(tools, AiIntent.INVOICE_DETAIL, "getMyInvoiceDetail", true, "Read one invoice by id when the user includes an invoice UUID.");
        put(tools, AiIntent.PAYMENT, "paymentGuide", false, "Explain QR bank transfer payment steps.");
        put(tools, AiIntent.PAYMENT, "getMyPayments", true, "Read the current customer's payment history.");
        put(tools, AiIntent.UTILITY, "getMyUtilityHistory", true, "Read electric and water reading history for the customer's room.");
        put(tools, AiIntent.TICKET, "ticketGuide", false, "Explain how to create and follow support tickets.");
        put(tools, AiIntent.TICKET, "getMyTickets", true, "Read the current customer's support tickets.");
        put(tools, AiIntent.CREATE_TICKET, "ticketGuide", false, "Guide ticket creation; writing data requires explicit backend flow.");
        put(tools, AiIntent.NOTIFICATION, "getMyNotifications", true, "Read current customer's notifications.");
        put(tools, AiIntent.LANDLORD_CONTACT, "getLandlordContact", true, "Read landlord or owner contact for current room.");
        return Collections.unmodifiableMap(tools);
    }

    private static Map<String, ToolDefinition> adminTools() {
        Map<String, ToolDefinition> tools = new LinkedHashMap<>();
        put(tools, AiIntent.GENERAL_HELP, "generalWebsiteGuide", false, "Explain SmartRent admin features and navigation.");
        put(tools, AiIntent.ADMIN_DASHBOARD, "getDashboardSummary", true, "Read admin dashboard totals.");
        put(tools, AiIntent.ADMIN_DASHBOARD, "getCustomerSummary", true, "Summarize active customers and occupancy.");
        put(tools, AiIntent.REVENUE_SUMMARY, "getRevenueSummary", true, "Summarize revenue and unpaid debt.");
        put(tools, AiIntent.ROOM, "getRoomSummary", true, "Summarize room availability and occupancy.");
        put(tools, AiIntent.ROOM_STATUS, "getRoomSummary", true, "Summarize room status.");
        put(tools, AiIntent.INVOICE, "getInvoiceSummary", true, "Summarize invoice counts, paid invoices, pending invoices, overdue invoices, and debt.");
        put(tools, AiIntent.UNPAID_INVOICES, "getUnpaidInvoices", true, "List recent unpaid invoices.");
        put(tools, AiIntent.OVERDUE_INVOICES, "getOverdueInvoices", true, "List recent overdue invoices.");
        put(tools, AiIntent.CUSTOMER_SEARCH, "searchCustomer", true, "Search active customers by name, phone, email, or room keyword.");
        put(tools, AiIntent.TICKET_SUMMARY, "getTicketSummary", true, "Summarize tickets and recent support requests.");
        put(tools, AiIntent.TICKET, "getTicketSummary", true, "Summarize tickets and recent support requests.");
        put(tools, AiIntent.PAYMENT_SUMMARY, "getPaymentSummary", true, "Summarize paid, pending, and overdue payment amounts.");
        put(tools, AiIntent.PAYMENT, "getPaymentSummary", true, "Summarize payment status.");
        put(tools, AiIntent.UTILITY_ANOMALY, "getUtilityAnomalies", true, "Summarize electric and water readings for current month.");
        put(tools, AiIntent.UTILITY, "getUtilityAnomalies", true, "Summarize electric and water readings.");
        return Collections.unmodifiableMap(tools);
    }

    private static void put(Map<String, ToolDefinition> tools,
                            AiIntent intent,
                            String toolName,
                            boolean requiresData,
                            String description) {
        tools.put(toolName, new ToolDefinition(intent, toolName, requiresData, description));
    }

    private static String extractJson(String answer) {
        if (answer == null || answer.isBlank()) {
            return "";
        }
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        return answer.substring(start, end + 1).trim();
    }

    private static String text(JsonNode node) {
        return node != null && node.isTextual() ? node.asText().trim() : "";
    }

    private static String safeToolName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "blank";
        }
        return toolName.length() <= 80 ? toolName : toolName.substring(0, 80);
    }

    private record ToolDefinition(AiIntent intent, String toolName, boolean requiresData, String description) {}
}
