package vn.glassliving.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.glassliving.booking.dto.CreateBookingRequest;
import vn.glassliving.booking.entity.Booking;
import vn.glassliving.booking.repository.BookingRepository;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.notification.service.NotificationService;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final NotificationService notificationService;

    @Transactional
    public Booking create(UUID tenantUserId, CreateBookingRequest req) {
        Room room = roomRepository.findById(req.roomId()).orElseThrow(() -> BusinessException.notFound("Phòng"));
        if (room.getStatus() != Room.RoomStatus.AVAILABLE) {
            throw BusinessException.conflict("Phòng không còn trống.");
        }
        BigDecimal rent = room.getPriceMonthly();
        BigDecimal deposit = room.getDepositAmount();
        BigDecimal serviceFee = room.getServiceFee();
        BigDecimal total = rent.add(deposit).add(serviceFee);

        String code = "BK-" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-"
                + String.format("%04d", (int) (Math.random() * 9000) + 1000);

        Booking b = Booking.builder()
                .code(code)
                .tenantUserId(tenantUserId)
                .roomId(room.getId())
                .ownerId(room.getOwnerId())
                .status(Booking.BookingStatus.PENDING_PAYMENT)
                .occupation(req.occupation())
                .moveInDate(req.moveInDate())
                .durationMonths(req.duration() != null ? req.duration() : (short) 12)
                .occupantCount(req.occupants() != null ? req.occupants() : (short) 1)
                .hasPet(Boolean.TRUE.equals(req.hasPet()))
                .note(req.note())
                .rentMonthly(rent)
                .depositAmount(deposit)
                .serviceFee(serviceFee)
                .totalDue(total)
                .paymentMethod(req.paymentMethod() != null ? Booking.PaymentMethod.valueOf(req.paymentMethod()) : null)
                .expiredAt(OffsetDateTime.now().plusHours(24))
                .build();

        b = bookingRepository.save(b);

        notificationService.create(room.getOwnerId(), "BOOKING_NEW",
                "Yêu cầu đặt phòng mới",
                "Có yêu cầu đặt phòng " + room.getCode() + " mã " + code,
                "/admin/tenants");

        return b;
    }
}
