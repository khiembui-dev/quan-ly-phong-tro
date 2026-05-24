package vn.glassliving.property.dto;

import jakarta.validation.constraints.NotBlank;
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

    // Tariffs (defaults for new rooms)
    @NotNull @PositiveOrZero
    private BigDecimal electricUnit;

    @NotNull @PositiveOrZero
    private BigDecimal waterUnit;

    @NotNull @PositiveOrZero
    private BigDecimal serviceFeeDefault;

    @PositiveOrZero
    private BigDecimal internetFee;

    @PositiveOrZero
    private BigDecimal garbageFee;

    @PositiveOrZero
    private BigDecimal managementFee;

    private Short billingDayDefault;

    /** Optional named extra service fees from the dynamic UI block ("Chi phí dịch vụ"). */
    private List<String>     extraFeeNames   = new ArrayList<>();
    private List<BigDecimal> extraFeeAmounts = new ArrayList<>();

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
    public void setName(String name) { this.name = name; }
    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String addressLine) { this.addressLine = addressLine; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getElectricUnit() { return electricUnit; }
    public void setElectricUnit(BigDecimal v) { this.electricUnit = v; }
    public BigDecimal getWaterUnit() { return waterUnit; }
    public void setWaterUnit(BigDecimal v) { this.waterUnit = v; }
    public BigDecimal getServiceFeeDefault() { return serviceFeeDefault; }
    public void setServiceFeeDefault(BigDecimal v) { this.serviceFeeDefault = v; }
    public BigDecimal getInternetFee() { return internetFee; }
    public void setInternetFee(BigDecimal v) { this.internetFee = v; }
    public BigDecimal getGarbageFee() { return garbageFee; }
    public void setGarbageFee(BigDecimal v) { this.garbageFee = v; }
    public BigDecimal getManagementFee() { return managementFee; }
    public void setManagementFee(BigDecimal v) { this.managementFee = v; }
    public Short getBillingDayDefault() { return billingDayDefault; }
    public void setBillingDayDefault(Short v) { this.billingDayDefault = v; }
    public List<String> getExtraFeeNames() { return extraFeeNames; }
    public void setExtraFeeNames(List<String> v) { this.extraFeeNames = v != null ? v : new ArrayList<>(); }
    public List<BigDecimal> getExtraFeeAmounts() { return extraFeeAmounts; }
    public void setExtraFeeAmounts(List<BigDecimal> v) { this.extraFeeAmounts = v != null ? v : new ArrayList<>(); }
}
