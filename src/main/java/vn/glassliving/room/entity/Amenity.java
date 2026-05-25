package vn.glassliving.room.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "amenity")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Amenity {

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 40, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "icon", length = 40)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "category", nullable = false, length = 24)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Category category = Category.OTHER;

    public enum Category { FURNITURE, UTILITY, RULE, OTHER }
}
