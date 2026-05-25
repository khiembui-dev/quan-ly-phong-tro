package vn.glassliving.room.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import vn.glassliving.room.entity.Amenity;
import vn.glassliving.room.repository.AmenityRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AmenityService {
    private final AmenityRepository amenityRepository;

    @Cacheable("amenities")
    public List<Amenity> all() {
        return amenityRepository.findAllByOrderBySortOrderAsc();
    }
}
