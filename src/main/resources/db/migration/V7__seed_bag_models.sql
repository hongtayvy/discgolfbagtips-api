-- Bag specs, hand-researched from manufacturer listings.
--
-- Deliberately incomplete rather than invented: where a maker does not publish a figure (Zuca does
-- not publish empty weights, several Innova bags omit them) the column is NULL and the carry-weight
-- calculation reports itself as partial. A plausible-looking guess would be worse than a gap,
-- because the whole point of the feature is telling someone what their load actually is.
--
-- Weights converted from published pounds at 453.592 g/lb. Capacities quoted as "30+" are stored at
-- the stated floor: under-promising means the fitting feature omits a bag that might have fit,
-- which is the safer error than recommending one that does not.
insert into bag_model (brand, model, slug, disc_capacity_min, disc_capacity_max, empty_weight_grams,
                       height_in, width_in, depth_in, carry_on_compliant, build_tier, bag_type, notes) values

-- GRIPeq — premium build, consistently published specs.
('GRIPeq', 'G-Series', 'g-series', 8, 12, 1089, 14.00, 12.00, 8.00, null, 'PREMIUM', 'BACKPACK',
 'Published 2.4 lb. Compact everyday carry.'),
('GRIPeq', 'CX1', 'cx1', 16, 16, 1769, 20.00, 15.00, 9.50, null, 'PREMIUM', 'BACKPACK',
 'Published 3.9 lb. Width expands 15-19in; the narrower figure is stored.'),
('GRIPeq', 'BX3', 'bx3', null, 18, 1905, null, null, null, null, 'PREMIUM', 'BACKPACK',
 'Published 4.2 lb. Dimensions not published.'),
('GRIPeq', 'AX6', 'ax6', null, 22, 2268, null, null, null, null, 'PREMIUM', 'BACKPACK',
 'Published 5 lb. Dimensions not published.'),
('GRIPeq', 'MB Line', 'mb-line', null, 28, null, 22.00, 14.00, 10.00, true, 'PREMIUM', 'BACKPACK',
 'Paul McBeth line. Carry-on compliant at 10x14x22in. Empty weight not published.'),

-- Innova — budget to mid, capacity usually published, weight rarely.
('Innova', 'Starter Bag', 'starter-bag', 6, 10, null, null, null, null, null, 'BUDGET', 'SHOULDER',
 'Entry-level. Empty weight not published.'),
('Innova', 'Standard Bag', 'standard-bag', 8, 12, null, null, null, null, null, 'BUDGET', 'SHOULDER',
 'Empty weight not published.'),
('Innova', 'Adventure Pack', 'adventure-pack', null, 25, 907, null, null, null, null, 'MID', 'BACKPACK',
 'Published 2 lb — unusually light for its capacity, but a lighter bag is not automatically a better swap; see build tier.'),
('Innova', 'Weekender Bag', 'weekender-bag', 6, 10, null, null, null, null, null, 'BUDGET', 'SHOULDER',
 'Empty weight not published.'),

-- Discraft — also resells GRIPeq-branded lines, which are listed under GRIPeq above.
('Discraft', 'Duffle Bag', 'duffle-bag', null, 32, null, 18.00, 9.00, 9.00, true, 'MID', 'DUFFLE',
 'Carry-on compliant at 9x9x18in. Empty weight not published.'),

-- Squatch — premium, large capacity.
('Squatch', 'Link', 'link', 30, 30, 1724, 19.50, 18.00, 9.00, null, 'PREMIUM', 'BACKPACK',
 'Published 3.8 lb, includes cooler. Maker states "30+"; stored at the floor.'),
('Squatch', 'Legend 3.0', 'legend-3-0', null, 40, null, null, null, null, null, 'PREMIUM', 'BACKPACK',
 'Capacity from published breakdown: 24 main + 4 per side pocket x2 + 6 putter + 2 flap = 40. Empty weight not published.'),

-- Zuca — carts rather than backpacks, so empty weight matters less to carry load.
('Zuca', 'Compact Disc Golf Rack', 'compact-disc-golf-rack', null, 15, null, null, null, null, null,
 'PREMIUM', 'CART', 'Cart insert. Empty weight not published; may need a support enquiry.'),
('Zuca', 'Disc Golf Cart', 'disc-golf-cart', null, 32, null, null, null, null, null, 'PREMIUM', 'CART',
 'Covers Infrared / Storm / Anaconda variants. Empty weight not published.');
