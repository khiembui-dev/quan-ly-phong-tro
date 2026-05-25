package vn.glassliving.room.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class RoomForm {

    private String id;

    @NotNull
    private String propertyId;

    @Size(max = 32)
    private String code;          // optional, auto-generated if blank

    @Size(max = 200)
    private String title;         // optional, defaults to "Phòng " + code

    @Size(max = 4000)
    private String description;

    @Size(max = 24)
    private String type;          // optional, defaults to STUDIO

    private Short floor;

    @NotNull @Positive
    private BigDecimal areaSqm;   // ONLY mandatory field

    private Short bedrooms;
    private Short bathrooms;
    private Short maxOccupants;

    @PositiveOrZero
    private BigDecimal priceMonthly;

    @PositiveOrZero
    private BigDecimal depositAmount;

    @PositiveOrZero
    private BigDecimal serviceFee;

    /** Override property tariff when true. */
    private boolean inheritTariff = true;

    @PositiveOrZero
    private BigDecimal electricUnit;

    @PositiveOrZero
    private BigDecimal waterUnit;

    @Size(max = 240)
    private String addressLine;

    private String status;        // AVAILABLE/OCCUPIED/MAINTENANCE/HIDDEN

    /** When status=OCCUPIED, the tenant user UUID who is renting. */
    private String currentTenantId;

    /** Inclusive date until which the tenant has paid/covered room usage. */
    private LocalDate currentTenantPaidUntil;

    /** Selected amenity codes (from chip groups). */
    private List<String> amenityCodes = new ArrayList<>();

    /** Custom extra fees (parking, elevator, etc.). */
    private List<String>     extraFeeNames   = new ArrayList<>();
    private List<BigDecimal> extraFeeAmounts = new ArrayList<>();

    /** User-defined amenities (parallel arrays — one per chip the user typed in). */
    private List<String> customAmenityNames      = new ArrayList<>();
    private List<String> customAmenityCategories = new ArrayList<>();

    public RoomForm() {
        this.bedrooms = 1;
        this.bathrooms = 1;
        this.maxOccupants = 2;
        this.serviceFee = BigDecimal.ZERO;
        this.status = "AVAILABLE";
        this.type = "STUDIO";
        this.priceMonthly = BigDecimal.ZERO;
        this.depositAmount = BigDecimal.ZERO;
    }

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getPropertyId() { return propertyId; }
    public void setPropertyId(String v) { this.propertyId = v; }
    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public Short getFloor() { return floor; }
    public void setFloor(Short v) { this.floor = v; }
    public BigDecimal getAreaSqm() { return areaSqm; }
    public void setAreaSqm(BigDecimal v) { this.areaSqm = v; }
    public Short getBedrooms() { return bedrooms; }
    public void setBedrooms(Short v) { this.bedrooms = v; }
    public Short getBathrooms() { return bathrooms; }
    public void setBathrooms(Short v) { this.bathrooms = v; }
    public Short getMaxOccupants() { return maxOccupants; }
    public void setMaxOccupants(Short v) { this.maxOccupants = v; }
    public BigDecimal getPriceMonthly() { return priceMonthly; }
    public void setPriceMonthly(BigDecimal v) { this.priceMonthly = v; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal v) { this.depositAmount = v; }
    public BigDecimal getServiceFee() { return serviceFee; }
    public void setServiceFee(BigDecimal v) { this.serviceFee = v; }
    public boolean isInheritTariff() { return inheritTariff; }
    public void setInheritTariff(boolean v) { this.inheritTariff = v; }
    public BigDecimal getElectricUnit() { return electricUnit; }
    public void setElectricUnit(BigDecimal v) { this.electricUnit = v; }
    public BigDecimal getWaterUnit() { return waterUnit; }
    public void setWaterUnit(BigDecimal v) { this.waterUnit = v; }
    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String v) { this.addressLine = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getCurrentTenantId() { return currentTenantId; }
    public void setCurrentTenantId(String v) { this.currentTenantId = v; }
    public LocalDate getCurrentTenantPaidUntil() { return currentTenantPaidUntil; }
    public void setCurrentTenantPaidUntil(LocalDate v) { this.currentTenantPaidUntil = v; }
    public List<String> getAmenityCodes() { return amenityCodes; }
    public void setAmenityCodes(List<String> v) { this.amenityCodes = v != null ? v : new ArrayList<>(); }
    public List<String> getExtraFeeNames() { return extraFeeNames; }
    public void setExtraFeeNames(List<String> v) { this.extraFeeNames = v != null ? v : new ArrayList<>(); }
    public List<BigDecimal> getExtraFeeAmounts() { return extraFeeAmounts; }
    public void setExtraFeeAmounts(List<BigDecimal> v) { this.extraFeeAmounts = v != null ? v : new ArrayList<>(); }
    public List<String> getCustomAmenityNames() { return customAmenityNames; }
    public void setCustomAmenityNames(List<String> v) { this.customAmenityNames = v != null ? v : new ArrayList<>(); }
    public List<String> getCustomAmenityCategories() { return customAmenityCategories; }
    public void setCustomAmenityCategories(List<String> v) { this.customAmenityCategories = v != null ? v : new ArrayList<>(); }
}
