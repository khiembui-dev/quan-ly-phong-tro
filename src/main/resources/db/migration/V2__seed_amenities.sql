-- Seed amenities (idempotent — uses fixed UUIDs based on hash of code).
INSERT INTO amenity (id, code, name, icon, sort_order) VALUES
  ('11111111-0000-0000-0000-000000000001', 'WIFI',         'Wi-Fi tốc độ cao',     'wifi',          1),
  ('11111111-0000-0000-0000-000000000002', 'AIR_CON',      'Máy lạnh',             'wind',          2),
  ('11111111-0000-0000-0000-000000000003', 'WATER_HEATER', 'Máy nước nóng',        'flame',         3),
  ('11111111-0000-0000-0000-000000000004', 'WASHING',      'Máy giặt',             'washing-machine', 4),
  ('11111111-0000-0000-0000-000000000005', 'FRIDGE',       'Tủ lạnh',              'refrigerator',  5),
  ('11111111-0000-0000-0000-000000000006', 'KITCHEN',      'Bếp + tủ bếp',         'utensils',      6),
  ('11111111-0000-0000-0000-000000000007', 'PARKING',      'Chỗ để xe',            'car',           7),
  ('11111111-0000-0000-0000-000000000008', 'SECURITY_24',  'Bảo vệ 24/7',          'shield',        8),
  ('11111111-0000-0000-0000-000000000009', 'CCTV',         'Camera an ninh',       'cctv',          9),
  ('11111111-0000-0000-0000-00000000000a', 'POOL',         'Hồ bơi chung',         'waves',        10),
  ('11111111-0000-0000-0000-00000000000b', 'GYM',          'Phòng gym',            'dumbbell',     11),
  ('11111111-0000-0000-0000-00000000000c', 'BALCONY',      'Ban công',             'building',     12),
  ('11111111-0000-0000-0000-00000000000d', 'PET',          'Cho phép thú cưng',    'paw-print',    13),
  ('11111111-0000-0000-0000-00000000000e', 'ELEVATOR',     'Thang máy',            'arrow-up-down',14);
