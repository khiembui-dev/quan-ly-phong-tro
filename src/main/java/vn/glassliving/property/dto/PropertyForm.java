package vn.glassliving.property.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Backing object for create/edit property form (admin).
 * Tariff defaults populate every new room created under this property.
 */
public class PropertyForm {

    private String id;                     // null = create, set = edit

    @NotBlank @Size(max = 160)
    private String name;

    @NotBlank @Size(max = 240)
    private String addressLine;

    @NotBlank @Size(max = 80)
    private String district;

    @NotBlank @Size(max = 80)
    private String city;

    @Size(max = 4000)
    private String description;

    // Create-page aliases. The current database still stores address as
    // addressLine + district + city, so these values are normalized in service.
    private String propertyName;
    private String streetAddress;
    private String provinceCode;
    private String provinceName;
    private String districtCode;
    private String districtName;
    private String wardCode;
    private String wardName;
    private String fullAddress;

    // Tariffs (defaults for new rooms)
    @NotNull(message = "Vui lòng nhập số tiền.")
    @PositiveOrZero(message = "Số tiền phải là số nguyên không âm.")
    @Digits(integer = 8, fraction = 0, message = "Số tiền phải là số nguyên không âm.")
    private BigDecimal electricUnit;

    @NotNull(message = "Vui lòng nhập số tiền.")
    @PositiveOrZero(message = "Số tiền phải là số nguyên không âm.")
    @Digits(integer = 8, fraction = 0, message = "Số tiền phải là số nguyên không âm.")
    private BigDecimal waterUnit;

    @NotNull(message = "Vui lòng nhập số tiền.")
    @PositiveOrZero(message = "Số tiền phải là số nguyên không âm.")
    @Digits(integer = 14, fraction = 0, message = "Số tiền phải là số nguyên không âm.")
    private BigDecimal serviceFeeDefault;

    @PositiveOrZero(message = "Số tiền phải là số nguyên không âm.")
    @Digits(integer = 14, fraction = 0, message = "Số tiền phải là số nguyên không âm.")
    private BigDecimal internetFee;

    @PositiveOrZero(message = "Số tiền phải là số nguyên không âm.")
    @Digits(integer = 14, fraction = 0, message = "Số tiền phải là số nguyên không âm.")
    private BigDecimal garbageFee;

    @PositiveOrZero(message = "Số tiền phải là số nguyên không âm.")
    @Digits(integer = 14, fraction = 0, message = "Số tiền phải là số nguyên không âm.")
    private BigDecimal managementFee;

    @NotNull(message = "Ngày phát hành hóa đơn phải từ 1 đến 31")
    @Min(value = 1, message = "Ngày phát hành hóa đơn phải từ 1 đến 31")
    @Max(value = 31, message = "Ngày phát hành hóa đơn phải từ 1 đến 31")
    private Short billingDayDefault;

    /** Optional named extra service fees from the dynamic UI block ("Chi phí dịch vụ"). */
    private List<String> extraFeeNames = new ArrayList<>();
    private List<@PositiveOrZero(message = "Số tiền phải là số nguyên không âm.")
            @Digits(integer = 14, fraction = 0, message = "Số tiền phải là số nguyên không âm.") BigDecimal> extraFeeAmounts = new ArrayList<>();
    @Valid
    private List<ExtraFeeInput> extraFees = new ArrayList<>();

    public PropertyForm() {
        this.electricUnit       = new BigDecimal("4000");
        this.waterUnit          = new BigDecimal("25000");
        this.serviceFeeDefault  = BigDecimal.ZERO;
        this.internetFee        = BigDecimal.ZERO;
        this.garbageFee         = BigDecimal.ZERO;
        this.managementFee      = BigDecimal.ZERO;
        this.billingDayDefault  = 1;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; this.propertyName = name; }
    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String addressLine) { this.addressLine = addressLine; this.streetAddress = addressLine; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; this.districtName = district; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; this.provinceName = city; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPropertyName() { return propertyName != null ? propertyName : name; }
    public void setPropertyName(String propertyName) { this.propertyName = propertyName; this.name = propertyName; }
    public String getStreetAddress() { return streetAddress != null ? streetAddress : addressLine; }
    public void setStreetAddress(String streetAddress) { this.streetAddress = streetAddress; this.addressLine = streetAddress; }
    public String getProvinceCode() { return provinceCode; }
    public void setProvinceCode(String provinceCode) { this.provinceCode = provinceCode; }
    public String getProvinceName() { return provinceName != null ? provinceName : city; }
    public void setProvinceName(String provinceName) { this.provinceName = provinceName; this.city = provinceName; }
    public String getDistrictCode() { return districtCode; }
    public void setDistrictCode(String districtCode) { this.districtCode = districtCode; }
    public String getDistrictName() { return districtName != null ? districtName : district; }
    public void setDistrictName(String districtName) { this.districtName = districtName; this.district = districtName; }
    public String getWardCode() { return wardCode; }
    public void setWardCode(String wardCode) { this.wardCode = wardCode; }
    public String getWardName() { return wardName; }
    public void setWardName(String wardName) { this.wardName = wardName; }
    public String getFullAddress() { return fullAddress; }
    public void setFullAddress(String fullAddress) { this.fullAddress = fullAddress; }
    public BigDecimal getElectricUnit() { return electricUnit; }
    public void setElectricUnit(BigDecimal v) { this.electricUnit = v; }
    public BigDecimal getElectricityPrice() { return electricUnit; }
    public void setElectricityPrice(BigDecimal v) { this.electricUnit = v; }
    public BigDecimal getWaterUnit() { return waterUnit; }
    public void setWaterUnit(BigDecimal v) { this.waterUnit = v; }
    public BigDecimal getWaterPrice() { return waterUnit; }
    public void setWaterPrice(BigDecimal v) { this.waterUnit = v; }
    public BigDecimal getServiceFeeDefault() { return serviceFeeDefault; }
    public void setServiceFeeDefault(BigDecimal v) { this.serviceFeeDefault = v; }
    public BigDecimal getServiceFee() { return serviceFeeDefault; }
    public void setServiceFee(BigDecimal v) { this.serviceFeeDefault = v; }
    public BigDecimal getInternetFee() { return internetFee; }
    public void setInternetFee(BigDecimal v) { this.internetFee = v; }
    public BigDecimal getGarbageFee() { return garbageFee; }
    public void setGarbageFee(BigDecimal v) { this.garbageFee = v; }
    public BigDecimal getTrashFee() { return garbageFee; }
    public void setTrashFee(BigDecimal v) { this.garbageFee = v; }
    public BigDecimal getManagementFee() { return managementFee; }
    public void setManagementFee(BigDecimal v) { this.managementFee = v; }
    public Short getBillingDayDefault() { return billingDayDefault; }
    public void setBillingDayDefault(Short v) { this.billingDayDefault = v; }
    public Short getInvoiceDay() { return billingDayDefault; }
    public void setInvoiceDay(Short v) { this.billingDayDefault = v; }
    public List<String> getExtraFeeNames() { return extraFeeNames; }
    public void setExtraFeeNames(List<String> v) { this.extraFeeNames = v != null ? v : new ArrayList<>(); }
    public List<BigDecimal> getExtraFeeAmounts() { return extraFeeAmounts; }
    public void setExtraFeeAmounts(List<BigDecimal> v) { this.extraFeeAmounts = v != null ? v : new ArrayList<>(); }
    public List<ExtraFeeInput> getExtraFees() { return extraFees; }
    public void setExtraFees(List<ExtraFeeInput> v) { this.extraFees = v != null ? v : new ArrayList<>(); }

    public static class ExtraFeeInput {
        private String name;
        @PositiveOrZero(message = "Số tiền phải là số nguyên không âm.")
        @Digits(integer = 14, fraction = 0, message = "Số tiền phải là số nguyên không âm.")
        private BigDecimal amount;
        private String cycle;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getCycle() { return cycle; }
        public void setCycle(String cycle) { this.cycle = cycle; }
    }
}
