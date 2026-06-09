package vn.glassliving.customer.profile;

import org.springframework.stereotype.Component;
import vn.glassliving.auth.entity.User;
import vn.glassliving.common.exception.BusinessException;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class CustomerProfileValidator {

    private static final Pattern VIETNAM_PHONE = Pattern.compile("^0\\d{9,10}$");
    private static final Pattern CCCD = Pattern.compile("^\\d{12}$");
    private static final Pattern CMND = Pattern.compile("^(\\d{9}|\\d{12})$");
    private static final Pattern PASSPORT = Pattern.compile("^[A-Z0-9]{6,12}$");

    public ValidProfile validateProfile(String fullName,
                                        String phone,
                                        LocalDate dob,
                                        String gender,
                                        String permanentAddress) {
        String cleanName = clean(fullName);
        if (cleanName == null || cleanName.length() < 2 || cleanName.length() > 120) {
            throw BusinessException.badRequest("Họ tên phải có từ 2 đến 120 ký tự.");
        }
        String cleanPhone = normalizePhone(phone);
        if (dob != null) {
            if (dob.isAfter(LocalDate.now())) {
                throw BusinessException.badRequest("Ngày sinh không được lớn hơn ngày hiện tại.");
            }
            if (dob.isBefore(LocalDate.of(1900, 1, 1))) {
                throw BusinessException.badRequest("Ngày sinh không hợp lệ.");
            }
        }
        return new ValidProfile(
                cleanName,
                cleanPhone,
                dob,
                parseGenderStrict(gender),
                validateLength(clean(permanentAddress), "Địa chỉ thường trú", 240)
        );
    }

    public ValidIdentity validateIdentity(String identityType,
                                          String identityNumber,
                                          LocalDate identityIssuedDate,
                                          String identityIssuedPlace,
                                          String permanentAddress) {
        User.IdentityType type = parseIdentityTypeStrict(identityType);
        String number = clean(identityNumber);
        if (number == null) {
            throw BusinessException.badRequest("Số giấy tờ không được để trống.");
        }
        number = number.replaceAll("[\\s.-]+", "").toUpperCase(Locale.ROOT);
        switch (type) {
            case CCCD -> {
                if (!CCCD.matcher(number).matches()) {
                    throw BusinessException.badRequest("CCCD phải gồm đúng 12 chữ số.");
                }
            }
            case CMND -> {
                if (!CMND.matcher(number).matches()) {
                    throw BusinessException.badRequest("CMND phải gồm 9 hoặc 12 chữ số.");
                }
            }
            case PASSPORT -> {
                if (!PASSPORT.matcher(number).matches()) {
                    throw BusinessException.badRequest("Hộ chiếu phải gồm 6 đến 12 ký tự chữ hoặc số.");
                }
            }
        }

        if (identityIssuedDate == null) {
            throw BusinessException.badRequest("Ngày cấp không được để trống.");
        }
        if (identityIssuedDate.isAfter(LocalDate.now())) {
            throw BusinessException.badRequest("Ngày cấp không được lớn hơn ngày hiện tại.");
        }
        if (identityIssuedDate.isBefore(LocalDate.of(1950, 1, 1))) {
            throw BusinessException.badRequest("Ngày cấp không hợp lệ.");
        }

        String place = validateRequiredLength(identityIssuedPlace, "Nơi cấp", 2, 160);
        String address = validateRequiredLength(permanentAddress, "Địa chỉ thường trú", 5, 240);
        return new ValidIdentity(type, number, identityIssuedDate, place, address);
    }

    public String normalizePhone(String phone) {
        String clean = clean(phone);
        if (clean == null) return null;
        String compact = clean.replaceAll("[\\s().-]+", "");
        if (compact.startsWith("+84")) {
            compact = "0" + compact.substring(3);
        } else if (compact.startsWith("84") && compact.length() >= 11) {
            compact = "0" + compact.substring(2);
        }
        if (!compact.matches("\\d+")) {
            throw BusinessException.badRequest("Số điện thoại chỉ được chứa chữ số và mã quốc gia +84.");
        }
        if (!VIETNAM_PHONE.matcher(compact).matches()) {
            throw BusinessException.badRequest("Số điện thoại không hợp lệ. Ví dụ: 0901234567.");
        }
        return compact;
    }

    private static User.Gender parseGenderStrict(String value) {
        String clean = clean(value);
        if (clean == null) return null;
        try {
            return User.Gender.valueOf(clean.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("Giới tính không hợp lệ.");
        }
    }

    private static User.IdentityType parseIdentityTypeStrict(String value) {
        String clean = clean(value);
        if (clean == null) {
            throw BusinessException.badRequest("Vui lòng chọn loại giấy tờ.");
        }
        try {
            return User.IdentityType.valueOf(clean.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("Loại giấy tờ không hợp lệ.");
        }
    }

    private static String validateRequiredLength(String value, String label, int min, int max) {
        String clean = clean(value);
        if (clean == null || clean.length() < min || clean.length() > max) {
            throw BusinessException.badRequest(label + " phải có từ " + min + " đến " + max + " ký tự.");
        }
        return clean;
    }

    private static String validateLength(String value, String label, int max) {
        if (value != null && value.length() > max) {
            throw BusinessException.badRequest(label + " không được vượt quá " + max + " ký tự.");
        }
        return value;
    }

    private static String clean(String value) {
        String clean = Objects.toString(value, "").trim();
        return clean.isBlank() ? null : clean;
    }

    public record ValidProfile(
            String fullName,
            String phone,
            LocalDate dob,
            User.Gender gender,
            String permanentAddress) {}

    public record ValidIdentity(
            User.IdentityType identityType,
            String identityNumber,
            LocalDate identityIssuedDate,
            String identityIssuedPlace,
            String permanentAddress) {}
}
