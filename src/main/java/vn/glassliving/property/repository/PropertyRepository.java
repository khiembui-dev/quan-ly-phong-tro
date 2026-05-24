package vn.glassliving.property.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.glassliving.property.entity.Property;

import java.util.List;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID> {
    List<Property> findByOwnerIdOrderByNameAsc(UUID ownerId);
}
