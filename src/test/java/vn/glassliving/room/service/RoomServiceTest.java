package vn.glassliving.room.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomServiceTest {

    @Test
    void searchCustomerRemovesKnownDemoRoomsFromBookingCatalog() {
        RoomRepository roomRepository = mock(RoomRepository.class);
        RoomService roomService = new RoomService(roomRepository);
        Room demo = availableRoom("GT-B-502", "penthouse-glass-tower-502");
        Room real = availableRoom("PP3", "phong-pp3-pp3");

        when(roomRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(demo, real)));

        List<Room> rooms = roomService.searchCustomer(
                null, null, null, null, null, null, null, null, 60);

        assertThat(rooms).extracting(Room::getCode).containsExactly("PP3");
    }

    private static Specification<Room> anySpecification() {
        return any();
    }

    private static Room availableRoom(String code, String slug) {
        Room room = Room.builder()
                .ownerId(UUID.randomUUID())
                .propertyId(UUID.randomUUID())
                .code(code)
                .slug(slug)
                .title("Phong " + code)
                .type(Room.RoomType.STUDIO)
                .areaSqm(new BigDecimal("25"))
                .priceMonthly(new BigDecimal("5000000"))
                .depositAmount(new BigDecimal("5000000"))
                .serviceFee(BigDecimal.ZERO)
                .electricUnit(new BigDecimal("4000"))
                .waterUnit(new BigDecimal("25000"))
                .status(Room.RoomStatus.AVAILABLE)
                .district("Quan 1")
                .city("TP.HCM")
                .build();
        room.setId(UUID.randomUUID());
        return room;
    }
}
