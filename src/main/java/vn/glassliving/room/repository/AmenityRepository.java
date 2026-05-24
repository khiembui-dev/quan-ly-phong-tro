package vn.glassliving.room.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.glassliving.room.entity.Amenity;

import java.util.List;
import java.util.UUID;

public interface AmenityRepository extends JpaRepository<Amenity, UUID> {
    List<Amenity> findAllByOrderBySortOrderAsc();

    List<Amenity> findByCategoryOrderBySortOrderAsc(Amenity.Category category);

    List<Amenity> findByCodeIn(java.util.Collection<String> codes);
}
