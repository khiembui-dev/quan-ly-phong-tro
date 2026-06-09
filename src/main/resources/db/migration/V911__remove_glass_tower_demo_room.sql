DELETE FROM room
WHERE code = 'GT-B-502'
   OR slug = 'penthouse-glass-tower-502';

UPDATE property
SET total_rooms = 1
WHERE slug = 'glass-tower';
