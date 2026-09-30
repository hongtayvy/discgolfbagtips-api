-- Curated plastic reference data.
--
-- DiscIt publishes molds and flight numbers but not plastics, and plastic is half of how a disc
-- actually flies: the same mold in a base blend and a premium blend are different discs to a
-- player. `stability_shift` is applied to a mold's (turn + fade) to get the flight the player sees.
-- Positive = flies more overstable than the published numbers.
insert into plastic_type (brand, name, slug, family, stability_shift, durability, grip, description) values

-- Innova
('Innova', 'DX', 'dx', 'BASE', -0.3, 1, 4, 'Innova''s base blend: cheap, grippy and quick to season. A DX driver beats in to a noticeably more understable flight within a few dozen throws, which is exactly why players buy them for turnover and roller shots.'),
('Innova', 'Pro', 'pro', 'MID', 0.0, 3, 4, 'A durable mid-grade blend with a slightly tacky surface. Seasons in slowly and holds a straighter flight than Champion in the same mold.'),
('Innova', 'Champion', 'champion', 'PREMIUM', 0.4, 5, 2, 'Innova''s clear premium plastic. Very durable and slick; Champion runs typically fly a touch more overstable than the published numbers and keep that flight for years.'),
('Innova', 'Star', 'star', 'GRIPPY_PREMIUM', 0.1, 4, 4, 'Premium durability with far more grip than Champion. Star runs fly close to the published numbers and season in gradually rather than suddenly.'),
('Innova', 'GStar', 'gstar', 'GUMMY', -0.2, 4, 5, 'A softer, more flexible Star. Grips well in the cold and the wet and plays slightly more understable than standard Star.'),
('Innova', 'Halo Star', 'halo-star', 'PREMIUM', 0.5, 5, 3, 'Star with a denser rim ring. The extra rim mass makes Halo runs measurably more overstable and more wind resistant than the same mold in standard Star.'),
('Innova', 'Champion Glow', 'champion-glow', 'GLOW', 0.5, 5, 2, 'Glow additive stiffens the Champion blend, pushing the flight further overstable. A common night-round and headwind choice.'),
('Innova', 'Blizzard Champion', 'blizzard-champion', 'SPECIALTY', -0.4, 4, 2, 'Microbubble-lightened Champion. Very light weights carry further for slower arms but turn over easily and are unreliable in wind.'),

-- Discraft
('Discraft', 'Pro-D', 'pro-d', 'BASE', -0.3, 1, 5, 'Discraft''s base blend. Excellent grip straight out of the box, seasons quickly, and is the classic cheap putter and beat-in midrange plastic.'),
('Discraft', 'X', 'x', 'MID', -0.1, 3, 4, 'A mid-grade opaque blend that holds its flight longer than Pro-D while keeping most of the grip.'),
('Discraft', 'Z', 'z', 'PREMIUM', 0.4, 5, 2, 'Discraft''s clear premium plastic. Stiff, slick and very durable; Z runs hold their original flight and read slightly more overstable than the numbers suggest.'),
('Discraft', 'ESP', 'esp', 'GRIPPY_PREMIUM', 0.1, 4, 4, 'Premium durability with a soft, grippy surface. ESP flies close to the published numbers and seasons in gradually toward straight and then understable.'),
('Discraft', 'Big Z', 'big-z', 'PREMIUM', 0.2, 5, 3, 'A thicker, slightly softer Z. Durable and stable-holding, with more grip than standard Z.'),
('Discraft', 'Jawbreaker', 'jawbreaker', 'BASE', 0.1, 2, 5, 'A chalky, extremely grippy putting blend. Tacky in the hand and stiff off the ground; a favourite for putters that must not slide off the chains.'),
('Discraft', 'Titanium', 'titanium', 'PREMIUM', 0.5, 5, 3, 'Titanium (Ti) is a stiff, dense premium blend that plays noticeably more overstable and is a common headwind and forehand choice.'),
('Discraft', 'Z Glow', 'z-glow', 'GLOW', 0.5, 5, 2, 'Glow-additive Z. Stiffer than standard Z and more overstable in flight.'),
('Discraft', 'CryZtal', 'cryztal', 'PREMIUM', 0.5, 5, 2, 'The clearest and stiffest Z variant. Very durable and among the most overstable-playing runs of any given mold.'),

-- Dynamic Discs / Latitude 64 / Westside (Trilogy)
('Dynamic Discs', 'Prime', 'prime', 'BASE', -0.3, 1, 5, 'Trilogy base plastic. Grippy, inexpensive, and seasons fast; a common first-bag and beat-in-on-purpose choice.'),
('Dynamic Discs', 'Prime Burst', 'prime-burst', 'BASE', -0.3, 1, 5, 'Prime with a swirled colour burst. Flies and seasons the same as standard Prime.'),
('Dynamic Discs', 'Classic', 'classic', 'BASE', -0.2, 2, 5, 'A soft base putter blend that grabs the ground on landing and stays put on a downhill green.'),
('Dynamic Discs', 'Fuzion', 'fuzion', 'GRIPPY_PREMIUM', 0.1, 4, 4, 'Trilogy''s grippy premium blend. Durable, tacky, and true to the published numbers.'),
('Dynamic Discs', 'Lucid', 'lucid', 'PREMIUM', 0.3, 5, 2, 'Clear premium plastic. Durable and slick, and generally reads slightly more overstable than the same mold in Fuzion.'),
('Dynamic Discs', 'Lucid-X', 'lucid-x', 'PREMIUM', 0.4, 5, 2, 'A denser, stiffer Lucid run that holds an overstable flight for a long time.'),
('Dynamic Discs', 'Fuzion Orbit', 'fuzion-orbit', 'PREMIUM', 0.3, 5, 3, 'Fuzion with a denser rim ring. The added rim weight makes it fly more overstable and more wind resistant.'),
('Dynamic Discs', 'Supreme', 'supreme', 'PREMIUM', 0.3, 5, 3, 'A stiff, durable premium blend used for the more overstable-playing runs in the Trilogy lineup.'),
('Latitude 64', 'Opto', 'opto', 'PREMIUM', 0.3, 5, 2, 'Latitude 64''s clear premium plastic. Slick, very durable, and holds its flight; usually a touch more overstable than the numbers.'),
('Latitude 64', 'Gold', 'gold', 'GRIPPY_PREMIUM', 0.1, 4, 4, 'Premium durability with far more grip than Opto. Flies close to the published numbers.'),
('Latitude 64', 'Zero Hard', 'zero-hard', 'BASE', 0.1, 2, 4, 'A firm base putting blend. Predictable off the chains and stiff enough to skip on hardpan.'),
('Latitude 64', 'Zero Medium', 'zero-medium', 'BASE', -0.1, 2, 5, 'A medium-firm base putting blend with excellent grip and a soft landing.'),
('Latitude 64', 'Royal Grand', 'royal-grand', 'GRIPPY_PREMIUM', 0.1, 4, 5, 'A tacky premium blend with outstanding cold-weather and wet-weather grip.'),
('Westside Discs', 'VIP', 'vip', 'PREMIUM', 0.3, 5, 2, 'Westside''s clear premium blend. Durable, slick and stable-holding.'),
('Westside Discs', 'Tournament', 'tournament', 'GRIPPY_PREMIUM', 0.1, 4, 4, 'Grippy premium plastic that flies true to the published numbers.'),
('Westside Discs', 'BT Medium', 'bt-medium', 'GUMMY', -0.1, 3, 5, 'A gummy putter blend that grabs the basket and the ground. Popular for putting and touch approaches.'),

-- MVP / Axiom / Streamline
('MVP', 'Neutron', 'neutron', 'PREMIUM', 0.2, 5, 3, 'MVP''s standard premium blend. Durable, moderately grippy, and true to the published flight with a slow seasoning curve.'),
('MVP', 'Proton', 'proton', 'PREMIUM', 0.3, 5, 3, 'A stiffer premium blend that holds an overstable finish; a common wind and forehand choice in the MVP lineup.'),
('MVP', 'Plasma', 'plasma', 'PREMIUM', 0.4, 5, 2, 'A dense metal-flake premium blend. The extra rim mass reads as more overstable and more wind resistant.'),
('MVP', 'Electron', 'electron', 'BASE', -0.2, 2, 5, 'A soft base blend used mainly for putters and approach discs. Grippy and quick to season.'),
('MVP', 'Fission', 'fission', 'SPECIALTY', -0.3, 4, 3, 'Microbubble-lightened premium plastic. Lighter weights in a given mold carry further for slower arms and play more understable.'),
('MVP', 'Eclipse', 'eclipse', 'GLOW', 0.4, 5, 2, 'MVP''s glow premium blend. Stiff and durable, and generally more overstable than Neutron in the same mold.'),
('Axiom Discs', 'Neutron', 'axiom-neutron', 'PREMIUM', 0.2, 5, 3, 'Axiom''s standard premium blend, shared with MVP. Durable and true to the published flight.'),
('Axiom Discs', 'Prism Neutron', 'prism-neutron', 'PREMIUM', 0.2, 5, 3, 'Neutron with a contrasting rim. Flies as Neutron does, with the same slow seasoning curve.'),
('Streamline Discs', 'Neutron', 'streamline-neutron', 'PREMIUM', 0.2, 5, 3, 'Streamline''s premium blend, shared with MVP and Axiom.'),

-- Discmania
('Discmania', 'C-Line', 'c-line', 'PREMIUM', 0.4, 5, 2, 'A stiff, dense premium blend. C-Line runs are the most overstable-playing of Discmania''s standard lineup and hold that flight for a long time.'),
('Discmania', 'S-Line', 's-line', 'PREMIUM', 0.3, 5, 2, 'A durable clear premium blend, slightly less stiff than C-Line but still stable-holding.'),
('Discmania', 'D-Line', 'd-line', 'BASE', -0.3, 1, 5, 'Discmania''s base blend. Grippy, cheap, and seasons quickly toward understable.'),
('Discmania', 'Neo', 'neo', 'GRIPPY_PREMIUM', 0.1, 4, 4, 'A grippy premium blend that flies true to the published numbers and seasons gradually.'),
('Discmania', 'Lux', 'lux', 'PREMIUM', 0.2, 5, 3, 'A premium putter and approach blend with a firm feel and a long flight life.'),

-- Prodigy, Kastaplast, Gateway and others
('Prodigy', '400', '400', 'PREMIUM', 0.2, 5, 3, 'Prodigy''s workhorse premium blend. Durable and true to the published flight.'),
('Prodigy', '400G', '400g', 'GRIPPY_PREMIUM', 0.2, 5, 5, 'A tacky version of 400 with outstanding grip in the wet and the cold.'),
('Prodigy', '500', '500', 'PREMIUM', 0.3, 5, 3, 'A firmer, more overstable-playing premium blend that resists turning over in wind.'),
('Prodigy', '300', '300', 'BASE', -0.2, 2, 4, 'Prodigy''s base blend. Seasons quickly and is a cheap way to get an understable version of a mold.'),
('Kastaplast', 'K1', 'k1', 'PREMIUM', 0.3, 5, 2, 'Kastaplast''s stiff premium blend. Very durable and holds an overstable finish.'),
('Kastaplast', 'K3', 'k3', 'BASE', -0.3, 2, 5, 'A grippy base blend that seasons quickly toward a straighter, more understable flight.'),
('Kastaplast', 'K1 Soft', 'k1-soft', 'GUMMY', -0.1, 4, 5, 'A gummy premium blend with excellent grip and a soft landing; popular for putters.'),
('Gateway', 'Wizard S', 'wizard-s', 'GUMMY', -0.1, 3, 5, 'Gateway''s soft putting blend. Very tacky, lands dead, and is a long-standing putting favourite.'),
('Mint Discs', 'Apex', 'apex', 'PREMIUM', 0.3, 5, 3, 'Mint''s premium blend. Durable and stable-holding.'),
('Mint Discs', 'Sublime', 'sublime', 'GRIPPY_PREMIUM', 0.1, 4, 5, 'A tacky premium blend with strong grip in wet conditions.'),
('Thought Space Athletics', 'Ethereal', 'ethereal', 'PREMIUM', 0.2, 5, 4, 'A durable premium blend with a grippy surface, true to the published numbers.'),
('Thought Space Athletics', 'Nebula Aura', 'nebula-aura', 'GUMMY', -0.1, 4, 5, 'A soft, gummy premium blend used for putters and approach discs.'),

-- Cross-brand fallbacks, used when the player names a generic blend or a brand we do not have listed.
('*', 'Base', 'base', 'BASE', -0.3, 1, 5, 'A generic base-grade blend: grippy, inexpensive, and quick to beat in toward a more understable flight.'),
('*', 'Premium', 'premium', 'PREMIUM', 0.3, 5, 2, 'A generic premium blend: durable and slick, holding its original flight for a long time and reading slightly more overstable.'),
('*', 'Glow', 'glow', 'GLOW', 0.4, 5, 3, 'A generic glow blend. Glow additive stiffens most plastics and pushes the flight more overstable.'),
('*', 'Soft', 'soft', 'GUMMY', -0.1, 3, 5, 'A generic soft or gummy blend: tacky in the hand, grabs the ground on landing, and holds grip when wet.'),
('*', 'Unspecified', 'unspecified', 'MID', 0.0, 3, 3, 'No plastic was given, so the mold''s published flight numbers are used unadjusted.');
