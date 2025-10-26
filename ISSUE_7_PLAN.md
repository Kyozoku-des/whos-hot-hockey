# Implementation Plan: Issue #7 - User Favorites Functionality

## Overview
Implement client-side user favorites functionality allowing users to save up to 10 favorite teams and/or players, with GDPR-compliant cookie consent.

## Requirements Analysis

### 1. Favorites Storage (Client-Side)
- Store user favorites using **localStorage** (recommended over cookies)
- Support both teams and players
- Maximum 10 favorites total (mixed teams and players)
- Persist across browser sessions

### 2. Cookie Consent Banner
- Display consent banner on first visit
- Save user consent preference (accept/decline)
- If declined: use sessionStorage (cleared on browser close)
- If accepted: use localStorage (persists across sessions)
- Make consent banner dismissible and respect user choice

### 3. UI Presentation
- Create "My Favorites" expandable card
- Position as first card on start page (above other tables)
- Match existing card styling and expandable behavior
- Show favorites in grid format similar to other player/team tables

### 4. Maximum Limit
- Enforce 10-item maximum
- Show clear feedback when limit reached
- Display count: "Favorites (X/10)"

### 5. Favorite/Unfavorite Actions
- Add star/heart icon to player and team items
- Toggle favorite on/off with visual feedback
- Prevent adding when at 10-item limit

---

## Technical Architecture

### Storage Strategy Decision: localStorage

**Why localStorage over cookies:**
- ✅ Larger capacity (5MB vs 4KB for cookies)
- ✅ Doesn't transmit with every HTTP request (better performance)
- ✅ Simpler API for storing complex objects
- ✅ Still requires user consent under GDPR
- ✅ Persistent across sessions (when consented)

**Data Structure:**
```javascript
{
  consent: {
    given: boolean,
    timestamp: string (ISO date)
  },
  favorites: [
    {
      id: string, // playerId or teamCode
      type: "PLAYER" | "TEAM",
      name: string,
      imageUrl: string,
      secondaryInfo: string, // position or team abbreviation
      addedAt: string (ISO date)
    }
  ]
}
```

---

## Frontend Implementation Tasks

### Phase 1: Storage & Consent Infrastructure

#### 1.1 Create Consent Banner Component
**File:** `frontend/src/components/CookieConsent.vue`

**Features:**
- Display banner at bottom of screen on first visit
- Two buttons: "Accept" and "Decline"
- Dismissible with X button (counts as decline)
- Styling matches app theme
- Slide-in animation

**Implementation:**
```vue
<template>
  <div v-if="showBanner" class="cookie-consent-banner">
    <div class="banner-content">
      <p>
        We use browser storage to save your favorite teams and players.
        Would you like to enable this feature?
      </p>
      <div class="banner-actions">
        <button @click="accept" class="btn-accept">Accept</button>
        <button @click="decline" class="btn-decline">Decline</button>
      </div>
    </div>
    <button @click="decline" class="btn-close">✕</button>
  </div>
</template>
```

#### 1.2 Create Favorites Storage Composable
**File:** `frontend/src/composables/useFavorites.js`

**Features:**
- Check consent status
- Add/remove favorites
- Get all favorites
- Check if item is favorited
- Enforce 10-item limit
- Handle localStorage/sessionStorage based on consent

**Key Methods:**
```javascript
export function useFavorites() {
  const hasConsent = () => { ... }
  const addFavorite = (item) => { ... }
  const removeFavorite = (id) => { ... }
  const isFavorited = (id) => { ... }
  const getFavorites = () => { ... }
  const canAddMore = () => { ... }
  const getFavoritesCount = () => { ... }
  const clearAll = () => { ... }
}
```

### Phase 2: UI Components

#### 2.1 Create Favorites Table Component
**File:** `frontend/src/components/FavoritesTable.vue`

**Features:**
- Display favorited teams and players in grid
- Click to navigate to team/player page
- Remove button (unfavorite) on each item
- Empty state: "No favorites yet. Click the ★ icon on any player or team to add them here!"
- Show count: "My Favorites (X/10)"
- Support for both player and team items

**Layout:**
```
┌─────────────────────────────────────────┐
│  My Favorites (3/10)                    │
│  ┌──────┐  ┌──────┐  ┌──────┐          │
│  │ ★    │  │ ★    │  │ ★    │          │
│  │ Logo │  │ Logo │  │ Logo │          │
│  │ Name │  │ Name │  │ Name │          │
│  │  🗑   │  │  🗑   │  │  🗑   │          │
│  └──────┘  └──────┘  └──────┘          │
└─────────────────────────────────────────┘
```

#### 2.2 Add Favorite Toggle to Existing Components
**Files to modify:**
- `frontend/src/components/TopPointsTable.vue`
- `frontend/src/components/PointStreaksTable.vue`
- `frontend/src/components/HottestPlayersTable.vue`
- `frontend/src/components/TeamStandingsTable.vue`
- `frontend/src/components/TeamWinStreaksTable.vue`
- `frontend/src/components/TeamLossStreaksTable.vue`

**Changes:**
- Add star icon button to each player/team item
- Toggle between filled ★ (favorited) and outline ☆ (not favorited)
- Show tooltip on hover: "Add to favorites" or "Remove from favorites"
- Disable when limit reached (with tooltip: "Maximum 10 favorites")

**Example modification:**
```vue
<div class="player-item">
  <button
    class="favorite-btn"
    @click.stop="toggleFavorite(player)"
    :disabled="!canFavorite(player)"
    :title="getFavoriteTooltip(player)"
  >
    {{ isFavorited(player.playerId) ? '★' : '☆' }}
  </button>
  <!-- existing player/team content -->
</div>
```

### Phase 3: Integration

#### 3.1 Update Start Page
**File:** `frontend/src/views/StartPage.vue`

**Changes:**
- Import FavoritesTable component
- Import CookieConsent component
- Position FavoritesTable as first expandable card
- Show CookieConsent banner at bottom

**Layout Order:**
1. Search Bar
2. **My Favorites (NEW)** - Only show if user has favorites
3. Who's Hot
4. Top Points
5. Point Streaks
6. Team Standings
7. Win Streaks
8. Loss Streaks

#### 3.2 Update Team/Player Detail Pages
**Files:**
- `frontend/src/views/TeamPage.vue`
- `frontend/src/views/PlayerPage.vue`

**Changes:**
- Add favorite button in header section
- Display filled star if already favorited
- Allow toggle from detail pages

---

## Implementation Steps

### Step 1: Consent & Storage Foundation
1. Create `useFavorites.js` composable with all storage logic
2. Create `CookieConsent.vue` component
3. Test consent flow and storage switching (localStorage vs sessionStorage)

### Step 2: Favorites Display
1. Create `FavoritesTable.vue` component
2. Test with mock data
3. Integrate into StartPage as first card

### Step 3: Add Favorite Toggles
1. Add favorite buttons to all player table components
2. Add favorite buttons to all team table components
3. Test adding/removing favorites
4. Test 10-item limit enforcement

### Step 4: Detail Pages Enhancement
1. Add favorite button to TeamPage header
2. Add favorite button to PlayerPage header
3. Test navigation and state persistence

### Step 5: Polish & Testing
1. Add animations and visual feedback
2. Test edge cases (full storage, consent changes)
3. Test across different browsers
4. Verify GDPR compliance

---

## Backend Implementation Tasks

**None required** - This feature is entirely client-side.

The backend already provides all necessary data through existing endpoints:
- `/search/all` - Returns all teams and players with required info
- Individual team/player endpoints - For detail pages

---

## File Changes Summary

### New Files
```
frontend/src/components/CookieConsent.vue
frontend/src/components/FavoritesTable.vue
frontend/src/composables/useFavorites.js
```

### Modified Files
```
frontend/src/views/StartPage.vue
frontend/src/views/TeamPage.vue
frontend/src/views/PlayerPage.vue
frontend/src/components/TopPointsTable.vue
frontend/src/components/PointStreaksTable.vue
frontend/src/components/HottestPlayersTable.vue
frontend/src/components/TeamStandingsTable.vue
frontend/src/components/TeamWinStreaksTable.vue
frontend/src/components/TeamLossStreaksTable.vue
```

---

## User Stories & Acceptance Criteria

### User Story 1: Consent Banner
**As a** first-time visitor
**I want to** see a cookie consent banner
**So that** I can choose whether to enable favorites storage

**Acceptance Criteria:**
- ✅ Banner appears on first visit only
- ✅ Banner has clear messaging about what's being stored
- ✅ Accept button enables localStorage
- ✅ Decline button uses sessionStorage only
- ✅ Choice is remembered for future visits
- ✅ Banner is dismissible

### User Story 2: Add Favorites
**As a** user who accepted consent
**I want to** click a star icon on teams/players
**So that** I can save my favorites

**Acceptance Criteria:**
- ✅ Star icon visible on all team/player items
- ✅ Clicking toggles favorite status
- ✅ Visual feedback (filled vs outline star)
- ✅ Favorites persist across sessions
- ✅ Maximum 10 favorites enforced
- ✅ Clear error message when limit reached

### User Story 3: View Favorites
**As a** user with saved favorites
**I want to** see my favorites at the top of the start page
**So that** I can quickly access my preferred teams/players

**Acceptance Criteria:**
- ✅ "My Favorites" card appears first on start page
- ✅ Shows favorites count (X/10)
- ✅ Clicking favorite navigates to detail page
- ✅ Can remove favorites from the list
- ✅ Empty state message when no favorites
- ✅ Card hidden if no favorites

### User Story 4: Manage Favorites
**As a** user with favorites
**I want to** easily remove favorites
**So that** I can keep my list current

**Acceptance Criteria:**
- ✅ Remove button on each favorite in list
- ✅ Star button on detail pages
- ✅ Immediate visual feedback
- ✅ No confirmation needed (easily reversible)
- ✅ Count updates in real-time

---

## Testing Checklist

### Functional Testing
- [ ] Consent banner appears on first visit
- [ ] Accepting consent enables localStorage
- [ ] Declining consent uses sessionStorage
- [ ] Adding favorite updates UI immediately
- [ ] Removing favorite updates UI immediately
- [ ] 10-item limit enforced
- [ ] Limit warning shown clearly
- [ ] Favorites persist after page reload (with consent)
- [ ] Favorites cleared on browser close (without consent)
- [ ] Favorites table shows/hides based on content
- [ ] Clicking favorite navigates to detail page
- [ ] Star icons update across all components
- [ ] Empty state displays correctly

### Cross-Browser Testing
- [ ] Chrome/Edge (Chromium)
- [ ] Firefox
- [ ] Safari
- [ ] Mobile browsers

### Edge Cases
- [ ] What if localStorage is full?
- [ ] What if localStorage is disabled by browser?
- [ ] What if user clears browser data?
- [ ] What if same player/team appears in multiple tables?
- [ ] What if favorited player/team no longer exists?

---

## GDPR Compliance Notes

1. **Consent First**: Banner must appear before any localStorage use
2. **Clear Purpose**: Explain exactly what's being stored and why
3. **Easy Decline**: Declining should be as easy as accepting
4. **Reversible**: User can change consent preference later
5. **No Tracking**: We're only storing favorites, not tracking behavior
6. **Data Minimization**: Only store necessary fields (id, type, name, image)

---

## Future Enhancements (Not in Scope)

- Import/export favorites
- Sync favorites across devices (requires backend)
- Favorite groups/folders
- More than 10 favorites
- Favorite notes/comments
- Share favorites with others

---

## Agent Work Assignment

### Frontend Agent Tasks
All implementation work is frontend-only:
1. Create consent banner component
2. Create favorites storage composable
3. Create favorites table component
4. Add favorite toggles to all table components
5. Update start page layout
6. Add favorite buttons to detail pages
7. Test and polish

### Backend Agent Tasks
**No backend changes required** for this issue.

---

## Questions to Resolve

1. **Consent Management**: Should we add a settings page where users can change their consent preference later?
   - **Recommendation**: Add a small link in footer "Cookie Settings" that reopens consent banner

2. **Favorites Sync**: Should we consider backend storage for future cross-device sync?
   - **Recommendation**: Not for MVP. Can be added later as enhancement.

3. **Visual Design**: Star icon or heart icon for favorites?
   - **Recommendation**: Star (★/☆) is more universal for favorites

4. **Removal Confirmation**: Should we confirm before removing a favorite?
   - **Recommendation**: No confirmation needed - it's easily reversible

---

## Estimated Effort

**Total Frontend Effort:** 6-8 hours
- Consent banner: 1 hour
- Storage composable: 2 hours
- Favorites table: 2 hours
- Integration & toggles: 2-3 hours
- Testing & polish: 1 hour

**Backend Effort:** 0 hours (no changes needed)

**Total:** 6-8 hours of development time
