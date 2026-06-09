package vn.glassliving.room.controller;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.ui.ExtendedModelMap;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.automation.repository.AutomationSettingRepository;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomImageRepository;
import vn.glassliving.room.repository.RoomRepository;
import vn.glassliving.room.service.RoomService;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomWebControllerHomeTest {

    @Test
    void homeRendersLandingPageWithAtMostEightAvailableRoomsWithoutFilters() {
        RoomRepository roomRepository = mock(RoomRepository.class);
        RoomService roomService = new RoomService(roomRepository);
        Room room = Room.builder()
                .code("A101")
                .title("Studio A101")
                .type(Room.RoomType.STUDIO)
                .district("Quận 1")
                .status(Room.RoomStatus.AVAILABLE)
                .priceMonthly(new BigDecimal("5000000"))
                .build();
        when(roomRepository.search(
                eq(Room.RoomStatus.AVAILABLE),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(6);
                    assertThat(pageable.getPageSize()).isEqualTo(8);
                    return new PageImpl<>(List.of(room), PageRequest.of(0, 8), 1);
                });

        RoomWebController controller = new RoomWebController(
                roomService,
                mock(PropertyRepository.class),
                mock(UserRepository.class),
                mock(RoomImageRepository.class),
                mock(AutomationSettingRepository.class)
        );
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.home("Quận 1", "STUDIO", new BigDecimal("5000000"), true, model);

        assertThat(view).isEqualTo("customer/home");
        assertThat(model.get("rooms")).asList().containsExactly(room);
        assertThat(model.get("totalRooms")).isEqualTo(1L);
        assertThat(model.get("district")).isNull();
        assertThat(model.get("type")).isNull();
        assertThat(model.get("maxPrice")).isNull();
        assertThat(model.get("petAllowed")).isNull();
    }
}
