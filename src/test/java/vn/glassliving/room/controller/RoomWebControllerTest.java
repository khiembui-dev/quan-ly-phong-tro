package vn.glassliving.room.controller;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.test.util.ReflectionTestUtils;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.automation.entity.AutomationSetting;
import vn.glassliving.automation.repository.AutomationSettingRepository;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;
import vn.glassliving.room.repository.RoomImageRepository;
import vn.glassliving.room.service.RoomService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomWebControllerTest {

    @Test
    void roomDetailUsesSupportSettingsContactAndDoesNotExposeReviews() {
        RoomRepository roomRepository = mock(RoomRepository.class);
        RoomService roomService = new RoomService(roomRepository);
        PropertyRepository propertyRepository = mock(PropertyRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        RoomImageRepository imageRepository = mock(RoomImageRepository.class);
        AutomationSettingRepository settingRepository = mock(AutomationSettingRepository.class);
        RoomWebController controller = new RoomWebController(
                roomService,
                propertyRepository,
                userRepository,
                imageRepository,
                settingRepository
        );
        ReflectionTestUtils.setField(controller, "supportPhone", "0909999999");
        ReflectionTestUtils.setField(controller, "supportEmail", "fallback@example.test");
        ReflectionTestUtils.setField(controller, "supportZalo", "0908888888");

        UUID roomId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        Room room = Room.builder()
                .ownerId(ownerId)
                .propertyId(propertyId)
                .code("P101")
                .slug("phong-p101")
                .title("Phong P101")
                .type(Room.RoomType.STUDIO)
                .areaSqm(new BigDecimal("25"))
                .priceMonthly(new BigDecimal("5000000"))
                .depositAmount(new BigDecimal("5000000"))
                .serviceFee(BigDecimal.ZERO)
                .electricUnit(new BigDecimal("4000"))
                .waterUnit(new BigDecimal("25000"))
                .status(Room.RoomStatus.AVAILABLE)
                .addressLine("12 Test")
                .district("Quan 1")
                .city("TP.HCM")
                .build();
        room.setId(roomId);
        User owner = User.builder()
                .email("owner@example.test")
                .fullName("Owner")
                .phone("0900000000")
                .build();
        owner.setId(ownerId);
        Property property = Property.builder()
                .ownerId(ownerId)
                .name("Test Property")
                .slug("test-property")
                .description("Test")
                .addressLine("12 Test")
                .district("Quan 1")
                .city("TP.HCM")
                .totalRooms(1)
                .build();
        property.setId(propertyId);
        AutomationSetting setting = AutomationSetting.builder()
                .ownerId(ownerId)
                .contactEmail("support@example.test")
                .contactZalo("0911111111")
                .build();

        when(roomRepository.findBySlug("phong-p101")).thenReturn(Optional.of(room));
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(roomRepository.findSimilar(eq(roomId), eq("Quan 1"), any())).thenReturn(List.of());
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(settingRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(setting));
        when(settingRepository.findFirstByContactEmailIsNotNullOrContactZaloIsNotNullOrderByUpdatedAtDesc())
                .thenReturn(Optional.empty());
        when(imageRepository.findByRoomIdOrderBySortOrderAsc(roomId)).thenReturn(List.of());

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.roomDetail("phong-p101", model);

        assertThat(view).isEqualTo("customer/room-detail");
        assertThat(model.get("contactPhone")).isEqualTo("0911111111");
        assertThat(model.get("contactPhoneUrl")).isEqualTo("tel:0911111111");
        assertThat(model.get("contactZaloUrl")).isEqualTo("https://zalo.me/0911111111");
        assertThat(model.get("contactEmail")).isEqualTo("support@example.test");
        assertThat(model.asMap()).doesNotContainKey("reviews");
    }
}
