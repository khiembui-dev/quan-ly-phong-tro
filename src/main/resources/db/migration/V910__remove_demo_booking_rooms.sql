DELETE FROM notification
WHERE type = 'BOOKING_NEW'
  AND body ILIKE '%LH-303%';

DELETE FROM booking
WHERE room_id IN (
    SELECT id FROM room
    WHERE slug = 'studio-lotus-303'
);

DELETE FROM maintenance_ticket
WHERE room_id IN (
    SELECT id FROM room
    WHERE slug = 'riverside-q7-205'
);

DELETE FROM utility_reading
WHERE room_id IN (
    SELECT id FROM room
    WHERE slug = 'riverside-q7-205'
);

DELETE FROM room
WHERE slug IN (
    'penthouse-smartrent-tower-502',
    'studio-lotus-303',
    'riverside-q7-101',
    'riverside-q7-205'
);

UPDATE property
SET total_rooms = CASE slug
    WHEN 'smartrent-tower' THEN 1
    WHEN 'lotus-house' THEN 1
    WHEN 'riverside-q7' THEN 0
    ELSE total_rooms
END
WHERE slug IN ('smartrent-tower', 'lotus-house', 'riverside-q7');
