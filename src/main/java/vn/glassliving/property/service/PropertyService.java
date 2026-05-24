package vn.glassliving.property.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.util.SlugUtil;
import vn.glassliving.property.dto.PropertyForm;
import vn.glassliving.property.entity.Property;
import vn.glassliving.property.repository.PropertyRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyRepository propertyRepository;

    @Transactional
    public Property create(UUID ownerId, PropertyForm form) {
        String baseSlug = SlugUtil.slugify(form.getName());
        String slug = ensureUniqueSlug(baseSlug);

        Property p = Property.builder()
                .ownerId(ownerId)
                .name(form.getName().trim())
                .slug(slug)
                .description(form.getDescription())
                .addressLine(form.getAddressLine().trim())
                .district(form.getDistrict().trim())
                .city(form.getCity().trim())
                .totalRooms(0)
                .electricUnit(nz(form.getElectricUnit(), "4000"))
                .waterUnit(nz(form.getWaterUnit(), "25000"))
                .serviceFeeDefault(nz(form.getServiceFeeDefault(), "0"))
                .internetFee(nz(form.getInternetFee(), "0"))
                .garbageFee(nz(form.getGarbageFee(), "0"))
                .managementFee(nz(form.getManagementFee(), "0"))
                .billingDayDefault(form.getBillingDayDefault() != null ? form.getBillingDayDefault() : (short) 1)
                .extraFees(buildExtraFees(form))
                .build();
        return propertyRepository.save(p);
    }

    @Transactional
    public Property update(UUID ownerId, UUID id, PropertyForm form) {
        Property p = propertyRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Cơ sở"));
        if (!p.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu cơ sở này.");
        }
        p.setName(form.getName().trim());
        p.setAddressLine(form.getAddressLine().trim());
        p.setDistrict(form.getDistrict().trim());
        p.setCity(form.getCity().trim());
        p.setDescription(form.getDescription());
        if (form.getElectricUnit() != null)      p.setElectricUnit(form.getElectricUnit());
        if (form.getWaterUnit() != null)         p.setWaterUnit(form.getWaterUnit());
        if (form.getServiceFeeDefault() != null) p.setServiceFeeDefault(form.getServiceFeeDefault());
        if (form.getInternetFee() != null)       p.setInternetFee(form.getInternetFee());
        if (form.getGarbageFee() != null)        p.setGarbageFee(form.getGarbageFee());
        if (form.getManagementFee() != null)     p.setManagementFee(form.getManagementFee());
        if (form.getBillingDayDefault() != null) p.setBillingDayDefault(form.getBillingDayDefault());
        p.setExtraFees(buildExtraFees(form));
        return propertyRepository.save(p);
    }

    @Transactional
    public void delete(UUID ownerId, UUID id) {
        Property p = propertyRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Cơ sở"));
        if (!p.getOwnerId().equals(ownerId)) {
            throw BusinessException.forbidden("Bạn không sở hữu cơ sở này.");
        }
        if (p.getTotalRooms() != null && p.getTotalRooms() > 0) {
            throw BusinessException.conflict("Cơ sở vẫn còn " + p.getTotalRooms() +
                    " phòng. Hãy xóa hoặc chuyển phòng trước khi xóa cơ sở.");
        }
        propertyRepository.delete(p);  // soft-delete via @SQLDelete
    }

    @Transactional(readOnly = true)
    public PropertyForm toForm(Property p) {
        PropertyForm f = new PropertyForm();
        f.setId(p.getId().toString());
        f.setName(p.getName());
        f.setAddressLine(p.getAddressLine());
        f.setDistrict(p.getDistrict());
        f.setCity(p.getCity());
        f.setDescription(p.getDescription());
        f.setElectricUnit(p.getElectricUnit());
        f.setWaterUnit(p.getWaterUnit());
        f.setServiceFeeDefault(p.getServiceFeeDefault());
        f.setInternetFee(p.getInternetFee());
        f.setGarbageFee(p.getGarbageFee());
        f.setManagementFee(p.getManagementFee());
        f.setBillingDayDefault(p.getBillingDayDefault());
        if (p.getExtraFees() != null) {
            List<String> names = new ArrayList<>(p.getExtraFees().size());
            List<BigDecimal> amounts = new ArrayList<>(p.getExtraFees().size());
            for (Property.ExtraFee fee : p.getExtraFees()) {
                names.add(fee.getName());
                amounts.add(fee.getAmount());
            }
            f.setExtraFeeNames(names);
            f.setExtraFeeAmounts(amounts);
        }
        return f;
    }

    private String ensureUniqueSlug(String base) {
        String safeBase = (base == null || base.isBlank()) ? "co-so" : base;
        String slug = safeBase;
        int n = 1;
        while (slugTaken(slug)) {
            n++;
            slug = safeBase + "-" + n;
            if (n > 100) break;
        }
        return slug;
    }

    private boolean slugTaken(String slug) {
        return propertyRepository.findAll().stream()
                .anyMatch(p -> p.getSlug() != null && p.getSlug().equals(slug));
    }

    private static BigDecimal nz(BigDecimal v, String fallback) {
        return v != null ? v : new BigDecimal(fallback);
    }

    /** Pair extraFeeNames[i] with extraFeeAmounts[i] from the form, dropping blanks. */
    private static List<Property.ExtraFee> buildExtraFees(vn.glassliving.property.dto.PropertyForm form) {
        List<String> names = form.getExtraFeeNames();
        List<BigDecimal> amounts = form.getExtraFeeAmounts();
        if (names == null || amounts == null) return new ArrayList<>();
        int n = Math.min(names.size(), amounts.size());
        List<Property.ExtraFee> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String name = names.get(i);
            BigDecimal amount = amounts.get(i);
            if (name == null || name.isBlank()) continue;
            out.add(new Property.ExtraFee(name.trim(), amount != null ? amount : BigDecimal.ZERO));
        }
        return out;
    }
}
