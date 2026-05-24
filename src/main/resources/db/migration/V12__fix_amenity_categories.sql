-- ===================================================================
-- Glass Living - V12 re-apply amenity categorisation
-- V10 ran but UPDATE statements didn't take effect on V2-seeded rows
-- in some runs (Flyway transaction edge case). Re-apply idempotently.
-- ===================================================================
UPDATE amenity SET category = 'FURNITURE' WHERE code IN
    ('AIR_CON', 'WATER_HEATER', 'WASHING', 'FRIDGE', 'KITCHEN',
     'BED', 'WARDROBE', 'DESK', 'TV', 'SOFA', 'INDUCTION');

UPDATE amenity SET category = 'UTILITY' WHERE code IN
    ('WIFI', 'PARKING', 'SECURITY_24', 'CCTV', 'POOL', 'GYM',
     'BALCONY', 'ELEVATOR',
     'WINDOW', 'MEZZANINE', 'PRIVATE_WC', 'PRIVATE_KITCHEN',
     'FINGERPRINT', 'PARK_MOTOR', 'PARK_CAR');

UPDATE amenity SET category = 'RULE' WHERE code IN
    ('PET', 'COOK_OK', 'OVERNIGHT', 'NO_CURFEW');
