# Feature Specification: Team Color Themes

**Feature Branch**: `claude/every-32-teams-colors-opdpwi`
**Created**: 2026-10-13
**Status**: Draft
**Input**: User description: "I'd like to set up themes based on every 32 teams' color schemes. Prepare for this with requirements and fetch every 32 teams' color schemes in the form of hex codes."

## Context

The Settings page already has a **Theme** selector, but it is disabled and offers only `Retro` ("More themes coming later"). The choice is stored with the other settings (localStorage after consent, sessionStorage if declined). The whole site takes its colours from a small set of shared colour tokens (page background, card background, primary text, highlight text, border). This feature adds one theme per NHL club, built from that club's brand colours. The colour source data is in [`team-colors.json`](team-colors.json), and how it was gathered is in [`research.md`](research.md).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Pick my team's theme (Priority: P1)

A fan opens Settings, picks their club (e.g. "Edmonton Oilers") from the Theme selector, and the whole site switches to that club's colours straight away. They do not need to reload the page.

**Why this priority**: This is the feature. Each of the other stories refines it.

**Independent Test**: Enable the selector, pick any team and check that the home, player, team, and info pages all use that team's palette.

**Acceptance Scenarios**:

1. **Given** the Retro theme is active, **When** the visitor selects a team in Settings, **Then** every page uses that team's colours within the same view, with no reload.
2. **Given** a team theme is selected, **When** the visitor selects `Retro`, **Then** the site returns exactly to today's look.
3. **Given** the selector is opened, **When** the visitor looks at the options, **Then** all 32 current clubs are listed by full name, in a predictable order (alphabetical or grouped by division), with `Retro` first.

---

### User Story 2 - Theme is remembered (Priority: P1)

A visitor who chose a team theme sees it again the next time they open the site, under the same storage rules as their other settings.

**Why this priority**: A theme that resets on every visit makes the choice pointless.

**Independent Test**: Choose a team, accept storage consent, close and reopen the browser, and check that the theme is still applied on first paint.

**Acceptance Scenarios**:

1. **Given** consent was given and a team theme was chosen, **When** the visitor returns later, **Then** the team theme is applied before content is shown, with no visible flash of the Retro colours.
2. **Given** consent was declined, **When** the visitor closes the tab, **Then** the next visit starts on Retro, matching how the other settings behave.
3. **Given** saved settings name a theme that no longer exists (e.g. a renamed or relocated club), **When** the site loads, **Then** it falls back to Retro silently.

---

### User Story 3 - Everything stays readable (Priority: P1)

Whatever team is chosen, text, tables, hot and cold indicators, graphs, and buttons stay legible and distinguishable. This includes clubs whose colours are light (Utah's Mountain Blue, Boston's Gold) or close to each other (Toronto, Tampa Bay).

**Why this priority**: Brand palettes are not designed as UI palettes. Without contrast rules, several clubs would produce unreadable pages.

**Independent Test**: For every one of the 32 themes, check each text and background pair the site uses against the contrast thresholds in SC-002. Visually confirm that the hot and cold markers and the graph lines remain distinguishable.

**Acceptance Scenarios**:

1. **Given** any team theme, **When** body text is shown on a page or card background, **Then** it meets the SC-002 contrast threshold.
2. **Given** a team whose brand colour is too light or too dark to serve as a background, **When** the theme is built, **Then** the colour is used as an accent (borders, highlights, headings) instead, and the backgrounds stay dark enough to carry text.
3. **Given** any team theme, **When** hot and cold markers, win and loss values, or graph series are shown, **Then** they remain distinguishable from each other and from the theme accent. For example, the hot flame must not disappear on a red-themed page.

---

### User Story 4 - Theme follows my favorite team (Priority: P3)

A visitor who has favorited a team can choose "Match my favorite team" so the theme follows that team.

**Why this priority**: This is a convenience on top of US1. It is not needed for a first release.

**Independent Test**: Favorite one team, choose "Match my favorite team" and check that the theme matches that team.

**Acceptance Scenarios**:

1. **Given** exactly one favorited team, **When** "Match my favorite team" is chosen, **Then** that team's theme applies.
2. **Given** zero or several favorited teams, **When** that option is chosen, **Then** [NEEDS CLARIFICATION: fall back to Retro, or use the first favorited team?].

---

### Edge Cases

- **Light primaries** (UTA `#6CACE4`, BOS/NSH `#FFB81C`, PIT `#FCB514`, VGK `#B4975A`): white text on these fails contrast, so they can only be accents or need dark text.
- **Near-black primaries** (LAK `#111111`, PIT/NJD `#000000`): a theme built on these would look almost the same as Retro. The second colour has to carry the identity.
- **Near-identical palettes** (TOR `#00205B` vs TBL `#002868` vs VAN `#00205B`; EDM, FLA, NSH, WSH, WPG all use navy `#041E42`): themes should differ through their accent colours, not just a blue background.
- **Red-heavy themes** (DET, CAR, NJD, CGY, WSH, CHI, MTL): the site's existing red and orange meanings (hot, losses, ticker highlight) must stay distinguishable.
- **Rebrands and relocations** (Arizona → Utah 2024, Utah HC → Utah Mammoth 2025, Anaheim's 2024 orange rebrand, Buffalo's 2020 return to royal blue): updating a palette or renaming a club must be a data change in one place, and must not break saved settings (see US2 scenario 3).
- **Team logos** drawn on team-coloured backgrounds may lose contrast against their own colours (e.g. the Leafs logo on Leafs blue).
- **The pixel-art icons** (flame, snowflake, graph) have fixed colours and must stay visible on all 32 backgrounds.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The Theme selector in Settings MUST be enabled and offer `Retro` plus one theme for each of the 32 current NHL clubs.
- **FR-002**: Each team theme MUST be built from that club's brand colours as recorded in a single source data set keyed by the NHL team abbreviation (`ANA` … `WPG`, the same codes the NHL API and this app already use).
- **FR-003**: Each theme MUST define every colour token the site uses: page background, card background, primary text, highlight/secondary text, border, and accent. No component may fall back to Retro colours while a team theme is active.
- **FR-004**: Switching theme MUST take effect immediately on every page without a reload.
- **FR-005**: The chosen theme MUST be persisted and restored under the same consent rules as the existing settings (time zone, card order).
- **FR-006**: An unknown or stale saved theme value MUST fall back to `Retro` without an error.
- **FR-007**: The saved theme MUST be applied before first paint, so returning visitors do not see a flash of the Retro theme.
- **FR-008**: Every theme MUST meet the contrast thresholds in SC-002 for all text and background pairs. Brand colours that cannot meet them as a background MUST be used only as accents.
- **FR-009**: Status colours (hot and cold, wins and losses, streak highlights, graph series) MUST stay distinguishable in every theme, either by staying fixed or by being adjusted for each theme.
- **FR-010**: `Retro` MUST remain the default and MUST look identical to the current site.
- **FR-011**: The site's typography, layout and retro styling (pixel font, thick borders) MUST NOT change between themes. Only colours change.
- **FR-012**: The theme MUST be applied in the browser only. It MUST NOT require backend, API or database changes, and MUST NOT be sent to the server.
- **FR-013**: The Settings selector SHOULD show a small preview (e.g. a colour swatch) of each team's primary colours next to its name.
- **FR-014**: [NEEDS CLARIFICATION: theme intensity. Option A: team colours become the page and card backgrounds (bold, close to a team website). Option B: backgrounds stay dark and only borders, headings and highlights take the team colours (subtle, always readable). Recommendation: B, with A as a possible later "bold" variant.]

### Key Entities

- **Team palette**: one club's brand colours. Keyed by team abbreviation, it holds the full club name and an ordered list of named colours (name + hex), primary first. The source data is [`team-colors.json`](team-colors.json).
- **Theme**: a complete set of UI colour tokens (FR-003). `Retro` is hand-tuned. Each team theme is built from its team palette, following the contrast rules.
- **Theme setting**: the visitor's chosen theme identifier (`retro` or a team abbreviation, e.g. `edm`). It is stored with the existing settings.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 32 clubs plus Retro can be selected, and each applies across 100% of pages.
- **SC-002**: In all 33 themes, normal text meets a contrast ratio of at least 4.5:1, and large text and UI borders meet at least 3:1 (WCAG 2.2 AA). An automated check verifies this for every theme.
- **SC-003**: Switching theme updates the visible page in under 100 ms with no reload.
- **SC-004**: Returning visitors with a saved theme see no frame rendered in the Retro colours before their theme applies.
- **SC-005**: Adding, renaming, or recolouring a club touches only the team palette data and its tests.
- **SC-006**: The Retro theme passes a visual comparison with the current production site with no differences.

## Assumptions

- **A-001**: The colours in `team-colors.json` are a starting point gathered from public colour references (see `research.md`). Entries marked `"verify": true` (ANA, BUF, LAK, OTT, UTA) must be checked against current club brand material before release.
- **A-002**: Using club colours for a fan statistics site is acceptable. Team logos are already shown on the site, and this feature adds no new logo usage.
- **A-003**: Themes are visitor-chosen only. There are no per-page automatic themes (e.g. a team page in that team's colours). Per-page theming could be a separate feature.
- **A-004**: The 32-club list is the 2026–27 league (UTA = Utah Mammoth). Expansion clubs are added later by extending the data (SC-005).
- **A-005**: The existing dark, retro look is the design baseline, so team themes are dark-background variants unless FR-014 resolves otherwise.
