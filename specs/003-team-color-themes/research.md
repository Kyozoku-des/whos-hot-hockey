# Research: Team Color Themes

## R1 — Source of the 32 team palettes

**Decision**: Keep the palettes in [`team-colors.json`](team-colors.json), keyed by NHL team abbreviation, with named colours (primary first).

**How the values were gathered (2026-10-13)**:

- The usual colour-reference sites (teamcolorcodes.com, teampalettes.com, encycolorpedia.com, Wikipedia, nhl.com) could not be reached from the build environment. The values were assembled from:
  - `stephenyeargin/hubot-hockey` `src/teams.json` (primary colour for all 32 clubs, including Utah Mammoth and Seattle Kraken; updated 2026-10).
  - `jimniels/teamcolors` `src/teams.json` (fuller palettes, but pre-2021: no SEA/UTA, and stale ANA/BUF/LAK/OTT).
  - Widely published club brand values (Pantone-derived hex codes as listed on team colour reference sites).
- Where the two datasets disagreed, the newer brand identity was preferred. The old values were for BUF navy `#002654` (pre-2020) and ANA `#F47A38` (pre-2024).

**Needs verification before release** (`"verify": true` in the JSON):

| Team | Why |
|------|-----|
| ANA | 2024 rebrand to orange/black/gold. Exact orange and gold hex codes vary between sources (`#FC4C02` vs `#CF4520`; gold `#85714D` vs `#B9975B`). |
| BUF | Royal blue `#003087` since 2020. Some sources still give `#002654` navy. |
| LAK | 2024 rebrand. Confirm whether gold returned as an accent. |
| OTT | Red is given as `#C52032` or `#DA1A32`, gold as `#C2912C` or `#B79257`. |
| UTA | Mountain Blue `#6CACE4` is confirmed by two sources. Rock Black and Salt White are given only as names, so `#000000` and `#FFFFFF` are assumed. |

## R2 — Contrast hot spots found in the data

Light primaries that cannot be backgrounds behind white text: `#6CACE4` (UTA), `#FFB81C` (BOS, NSH), `#FCB514` (PIT), `#B4975A` (VGK), `#F1BE48` (CGY gold), `#99D9D9` (SEA Ice Blue). Seven clubs use navy `#041E42` as a main colour. See the spec's Edge Cases section.
