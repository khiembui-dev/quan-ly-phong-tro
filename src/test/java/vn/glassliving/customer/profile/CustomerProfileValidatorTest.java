package vn.glassliving.customer.profile;

import org.junit.jupiter.api.Test;
import vn.glassliving.auth.entity.User;
import vn.glassliving.common.exception.BusinessException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerProfileValidatorTest {

    private final CustomerProfileValidator validator = new CustomerProfileValidator();

    @Test
    void normalizesValidProfileInput() {
        CustomerProfileValidator.ValidProfile profile = validator.validateProfile(
                " Nguyen Van A ",
                " +84 901 234 567 ",
                LocalDate.of(1995, 1, 20),
                "MALE",
                " 12 Nguyen Hue "
        );

        assertThat(profile.fullName()).isEqualTo("Nguyen Van A");
        assertThat(profile.phone()).isEqualTo("0901234567");
        assertThat(profile.gender()).isEqualTo(User.Gender.MALE);
        assertThat(profile.permanentAddress()).isEqualTo("12 Nguyen Hue");
    }

    @Test
    void rejectsInvalidProfileValues() {
        assertThatThrownBy(() -> validator.validateProfile("A", "0901234567", null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Họ tên");

        assertThatThrownBy(() -> validator.validateProfile("Nguyen Van A", "12345", null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Số điện thoại");

        assertThatThrownBy(() -> validator.validateProfile("Nguyen Van A", null, LocalDate.now().plusDays(1), null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ngày sinh");

        assertThatThrownBy(() -> validator.validateProfile("Nguyen Van A", null, null, "ALIEN", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Giới tính");
    }

    @Test
    void validatesIdentityDocumentByType() {
        CustomerProfileValidator.ValidIdentity cccd = validator.validateIdentity(
                "CCCD",
                " 012345678901 ",
                LocalDate.of(2024, 5, 1),
                " Cục CSQLHC về TTXH ",
                " 12 Nguyen Hue "
        );

        assertThat(cccd.identityType()).isEqualTo(User.IdentityType.CCCD);
        assertThat(cccd.identityNumber()).isEqualTo("012345678901");
        assertThat(cccd.identityIssuedPlace()).isEqualTo("Cục CSQLHC về TTXH");

        assertThatThrownBy(() -> validator.validateIdentity("CCCD", "123456789", LocalDate.now(), "Ha Noi", "Dia chi"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CCCD");

        assertThatThrownBy(() -> validator.validateIdentity("PASSPORT", "ABC", LocalDate.now(), "Ha Noi", "Dia chi"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Hộ chiếu");

        assertThatThrownBy(() -> validator.validateIdentity("CCCD", "012345678901", LocalDate.now().plusDays(1), "Ha Noi", "Dia chi"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ngày cấp");
    }
}
