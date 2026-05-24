-- ===================================================================
-- Glass Living - V11 seed additional amenities for the 3-group UI
-- ===================================================================
INSERT INTO amenity (id, code, name, icon, sort_order, category) VALUES
  -- Furniture
  ('22222222-0000-0000-0000-000000000001', 'BED',          'Giường',                'bed',          20, 'FURNITURE'),
  ('22222222-0000-0000-0000-000000000002', 'WARDROBE',     'Tủ quần áo',            'archive',      21, 'FURNITURE'),
  ('22222222-0000-0000-0000-000000000003', 'DESK',         'Bàn làm việc',          'pen-tool',     22, 'FURNITURE'),
  ('22222222-0000-0000-0000-000000000004', 'TV',           'TV',                    'tv',           23, 'FURNITURE'),
  ('22222222-0000-0000-0000-000000000005', 'SOFA',         'Sofa',                  'armchair',     24, 'FURNITURE'),
  ('22222222-0000-0000-0000-000000000006', 'INDUCTION',    'Bếp từ',                'flame',        25, 'FURNITURE'),
  -- Utilities
  ('22222222-0000-0000-0000-000000000010', 'WINDOW',       'Cửa sổ',                'square',       30, 'UTILITY'),
  ('22222222-0000-0000-0000-000000000011', 'MEZZANINE',    'Gác lửng',              'layers',       31, 'UTILITY'),
  ('22222222-0000-0000-0000-000000000012', 'PRIVATE_WC',   'WC riêng',              'bath',         32, 'UTILITY'),
  ('22222222-0000-0000-0000-000000000013', 'PRIVATE_KITCHEN','Bếp riêng',           'utensils',     33, 'UTILITY'),
  ('22222222-0000-0000-0000-000000000014', 'FINGERPRINT',  'Khóa vân tay',          'fingerprint',  34, 'UTILITY'),
  ('22222222-0000-0000-0000-000000000015', 'PARK_MOTOR',   'Chỗ để xe máy',         'bike',         35, 'UTILITY'),
  ('22222222-0000-0000-0000-000000000016', 'PARK_CAR',     'Chỗ để ô tô',           'car',          36, 'UTILITY'),
  -- Rules
  ('22222222-0000-0000-0000-000000000020', 'COOK_OK',      'Cho phép nấu ăn',       'chef-hat',     40, 'RULE'),
  ('22222222-0000-0000-0000-000000000021', 'OVERNIGHT',    'Cho khách qua đêm',     'moon',         41, 'RULE'),
  ('22222222-0000-0000-0000-000000000022', 'NO_CURFEW',    'Không giờ giới nghiêm', 'clock',        42, 'RULE')
ON CONFLICT (code) DO NOTHING;
