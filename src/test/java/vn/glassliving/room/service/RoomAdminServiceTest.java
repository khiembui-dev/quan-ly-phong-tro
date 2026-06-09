package vn.glassliving.room.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.common.storage.LocalUploadService;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.dto.RoomForm;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.AmenityRepository;
import vn.glassliving.room.repository.RoomImageRepository;
import vn.glassliving.room.repository.RoomRepository;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomAdminServiceTest {

    private final RoomRepository roomRepository = mock(RoomRepository.class);
    private final PropertyRepository propertyRepository = mock(PropertyRepository.class);
    private final AmenityRepository amenityRepository = mock(AmenityRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoomImageRepository roomImageRepository = mock(RoomImageRepository.class);
    private final LocalUploadService localUploadService = new LocalUploadService("target/test-uploads");
    private final RoomAdminService service = new RoomAdminService(
            roomRepository,
            propertyRepository,
            amenityRepository,
            userRepository,
            roomImageRepository,
            localUploadService
    );

    @Test
    void updateInheritingRoomDoesNotPersistPropertyFeesAsPrivateFixedCosts() {
        UUID ownerId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Room room = baseRoom(ownerId, propertyId, roomId);
        room.setInheritTariff(true);
        room.setExtraFees(List.of(new Property.ExtraFee("Internet", new BigDecimal("100000"))));
        Property property = baseProperty(ownerId, propertyId);
        property.setInternetFee(new BigDecimal("100000"));
        property.setGarbageFee(new BigDecimal("50000"));
        property.setManagementFee(new BigDecimal("70000"));
        property.setExtraFees(List.of(new Property.ExtraFee("Gửi xe", new BigDecimal("150000"))));
        RoomForm form = baseForm(propertyId);
        form.setInheritTariff(true);
        form.setExtraFeeNames(List.of());
        form.setExtraFeeAmounts(List.of());

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Room updated = service.update(ownerId, roomId, form, new MultipartFile[0]);

        assertThat(updated.isInheritTariff()).isTrue();
        assertThat(updated.getExtraFees()).isEmpty();
    }

    @Test
    void editFormForInheritingRoomDoesNotExposeLegacyInheritedFeesAsPrivateFixedCosts() {
        UUID ownerId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Room room = baseRoom(ownerId, propertyId, roomId);
        room.setInheritTariff(true);
        room.setExtraFees(List.of(new Property.ExtraFee("Internet", new BigDecimal("100000"))));

        when(roomImageRepository.findByRoomIdOrderBySortOrderAsc(roomId)).thenReturn(List.of());

        RoomForm form = service.toForm(room);

        assertThat(form.isInheritTariff()).isTrue();
        assertThat(form.getExtraFeeNames()).isEmpty();
        assertThat(form.getExtraFeeAmounts()).isEmpty();
    }

    @Test
    void updateCustomRoomOnlyPersistsCompletePrivateFixedCosts() {
        UUID ownerId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Room room = baseRoom(ownerId, propertyId, roomId);
        Property property = baseProperty(ownerId, propertyId);
        RoomForm form = baseForm(propertyId);
        form.setInheritTariff(false);
        form.setExtraFeeNames(List.of("Internet", "Thiếu tiền", "Phí 0", ""));
        form.setExtraFeeAmounts(Arrays.asList(new BigDecimal("100000"), null, BigDecimal.ZERO, new BigDecimal("90000")));

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Room updated = service.update(ownerId, roomId, form, new MultipartFile[0]);

        assertThat(updated.isInheritTariff()).isFalse();
        assertThat(updated.getExtraFees())
                .extracting(Property.ExtraFee::getName)
                .containsExactly("Internet");
        assertThat(updated.getExtraFees())
                .extracting(Property.ExtraFee::getAmount)
                .containsExactly(new BigDecimal("100000"));
    }

    private static Room baseRoom(UUID ownerId, UUID propertyId, UUID roomId) {
        Room room = Room.builder()
                .ownerId(ownerId)
                .propertyId(propertyId)
                .code("P101")
                .slug("phong-p101")
                .title("Phong P101")
                .type(Room.RoomType.STUDIO)
                .areaSqm(new BigDecimal("25"))
                .bedrooms((short) 1)
                .bathrooms((short) 1)
                .maxOccupants((short) 2)
                .priceMonthly(new BigDecimal("5000000"))
                .depositAmount(new BigDecimal("5000000"))
                .serviceFee(BigDecimal.ZERO)
                .electricUnit(new BigDecimal("4000"))
                .waterUnit(new BigDecimal("25000"))
                .status(Room.RoomStatus.AVAILABLE)
                .district("Quan 1")
                .city("TP.HCM")
                .build();
        room.setId(roomId);
        return room;
    }

    private static Property baseProperty(UUID ownerId, UUID propertyId) {
        Property property = Property.builder()
                .ownerId(ownerId)
                .name("Test")
                .slug("test")
                .description("Test")
                .addressLine("12 Test")
                .district("Quan 1")
                .city("TP.HCM")
                .totalRooms(1)
                .serviceFeeDefault(BigDecimal.ZERO)
                .electricUnit(new BigDecimal("4000"))
                .waterUnit(new BigDecimal("25000"))
                .internetFee(BigDecimal.ZERO)
                .garbageFee(BigDecimal.ZERO)
                .managementFee(BigDecimal.ZERO)
                .build();
        property.setId(propertyId);
        return property;
    }

    private static RoomForm baseForm(UUID propertyId) {
        RoomForm form = new RoomForm();
        form.setPropertyId(propertyId.toString());
        form.setCode("P101");
        form.setTitle("Phong P101");
        form.setType("STUDIO");
        form.setAreaSqm(new BigDecimal("25"));
        form.setBedrooms((short) 1);
        form.setBathrooms((short) 1);
        form.setMaxOccupants((short) 2);
        form.setPriceMonthly(new BigDecimal("5000000"));
        form.setDepositAmount(new BigDecimal("5000000"));
        form.setServiceFee(BigDecimal.ZERO);
        form.setElectricUnit(new BigDecimal("4000"));
        form.setWaterUnit(new BigDecimal("25000"));
        form.setStatus("AVAILABLE");
        return form;
    }
}
