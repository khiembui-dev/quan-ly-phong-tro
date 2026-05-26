package vn.glassliving.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.contract.entity.Contract;
import vn.glassliving.contract.repository.ContractRepository;
import vn.glassliving.notification.service.NotificationService;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository contractRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public Contract create(UUID ownerId,
                           UUID roomId, String tenantEmail,
                           LocalDate startDate, short durationMonths,
                           BigDecimal rentMonthly, BigDecimal depositAmount,
                           BigDecimal serviceFee,
                           Short billingDay,
                           String extraTerms) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> BusinessException.notFound("Phòng"));
        if (!room.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu phòng này.");
        }
        if (room.getStatus() == Room.RoomStatus.OCCUPIED) {
            throw BusinessException.conflict("Phòng đã có người thuê. Hãy gỡ khách khỏi phòng trước.");
        }

        // Find or note: tenant must already be a registered user (we don't auto-create here).
        User tenant = userRepository.findByEmailIgnoreCase(tenantEmail.trim().toLowerCase())
                .orElseThrow(() -> BusinessException.badRequest(
                        "Khách thuê với email \"" + tenantEmail + "\" chưa đăng ký. Yêu cầu khách đăng ký trước, hoặc dùng \"Mời khách\"."));

        LocalDate endDate = startDate.plusMonths(durationMonths);

        String code = "HD-" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-"
                + String.format("%04d", (int) (Math.random() * 9000) + 1000);

        Contract c = Contract.builder()
                .code(code)
                .roomId(roomId)
                .ownerId(ownerId)
                .tenantUserId(tenant.getId())
                .startDate(startDate)
                .endDate(endDate)
                .durationMonths(durationMonths)
                .rentMonthly(rentMonthly != null ? rentMonthly : room.getPriceMonthly())
                .depositAmount(depositAmount != null ? depositAmount : room.getDepositAmount())
                .serviceFee(serviceFee != null ? serviceFee : room.getServiceFee())
                .electricUnit(room.getElectricUnit())
                .waterUnit(room.getWaterUnit())
                .billingDay(billingDay != null ? billingDay : (short) 1)
                .status(Contract.ContractStatus.ACTIVE)
                .extraTerms(extraTerms)
                .build();
        c = contractRepository.save(c);

        // Mark room as occupied + link tenant
        room.setStatus(Room.RoomStatus.OCCUPIED);
        room.setCurrentTenantId(tenant.getId());
        room.setCurrentTenantStartedOn(startDate);
        room.setCurrentTenantPaidUntil(startDate.plusMonths(1).minusDays(1));
        roomRepository.save(room);

        notificationService.create(tenant.getId(), "CONTRACT_NEW",
                "Hồ sơ thu tiền " + c.getCode() + " đã được tạo",
                "Bắt đầu " + startDate + " · " + durationMonths + " tháng · phòng " + room.getCode(),
                "/me/invoices");
        return c;
    }

    @Transactional
    public Contract terminate(UUID ownerId, UUID id, String reason, BigDecimal terminationFee) {
        Contract c = contractRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Hồ sơ thu tiền"));
        if (!c.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu hồ sơ thu tiền này.");
        }
        if (c.getStatus() == Contract.ContractStatus.TERMINATED || c.getStatus() == Contract.ContractStatus.EXPIRED) {
            throw BusinessException.conflict("Hồ sơ thu tiền đã kết thúc trước đó.");
        }
        c.setStatus(Contract.ContractStatus.TERMINATED);
        c.setTerminatedAt(OffsetDateTime.now());
        c.setTerminatedReason(reason);
        c.setEarlyTerminationFee(terminationFee);
        contractRepository.save(c);

        // Free up the room + clear tenant link
        roomRepository.findById(c.getRoomId()).ifPresent(r -> {
            r.setStatus(Room.RoomStatus.AVAILABLE);
            r.setCurrentTenantId(null);
            r.setCurrentTenantStartedOn(null);
            r.setCurrentTenantPaidUntil(null);
            roomRepository.save(r);
        });

        notificationService.create(c.getTenantUserId(), "CONTRACT_TERMINATED",
                "Hồ sơ thu tiền " + c.getCode() + " đã chấm dứt",
                reason != null ? reason : "Hồ sơ thu tiền được chấm dứt sớm.",
                "/me/invoices");
        return c;
    }
}
