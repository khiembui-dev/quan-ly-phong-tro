-- ===================================================================
-- Glass Living - V10 group amenities into 3 categories
-- FURNITURE / UTILITY / RULE — chip groups in admin form
-- ===================================================================
ALTER TABLE amenity
    ADD COLUMN category VARCHAR(24) NOT NULL DEFAULT 'OTHER';
ALTER TABLE amenity
    ADD CONSTRAINT amenity_category_chk
        CHECK (category IN ('FURNITURE', 'UTILITY', 'RULE', 'OTHER'));

-- Categorise existing amenities (codes from V2)
UPDATE amenity SET category = 'FURNITURE' WHERE code IN
    ('AIR_CON', 'WATER_HEATER', 'WASHING', 'FRIDGE', 'KITCHEN');
UPDATE amenity SET category = 'UTILITY' WHERE code IN
    ('WIFI', 'PARKING', 'SECURITY_24', 'CCTV', 'POOL', 'GYM',
     'BALCONY', 'ELEVATOR');
UPDATE amenity SET category = 'RULE' WHERE code IN ('PET');
