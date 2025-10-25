# Issue #4: Auto-Complete Search Bar Implementation Guide

## Overview
Implement an auto-complete search feature on the start page that allows users to search for teams and players with instant feedback and navigation.

## Requirements
- **Debounce**: 1 second of inactivity after keystroke before filtering results
- **Search Scope**: Team names and player names only
- **Results**: Display up to 5 closest matches in a dropdown
- **Navigation**: Clicking a match redirects to that team/player's detail page
- **UX**: Fast, responsive, minimal lag

## Recommended Architecture: Hybrid Approach (Option #4)

**Strategy**: Pinia store with cached data + periodic backend sync

**Rationale**:
- **Performance**: Client-side filtering is instant (no network delay)
- **Scalability**: ~800 players + 32 teams = manageable dataset (~50KB)
- **User Experience**: No lag during typing, instant dropdown updates
- **Data Freshness**: Sync with backend on app load and after data updates
- **Reduced Backend Load**: No queries on every keystroke

---

## Backend Agent Tasks

### 1. Create Search DTO
**File**: `backend/src/main/java/com/nhl/whoshotbackend/dto/SearchResultDTO.java`

```java
package com.nhl.whoshotbackend.dto;

public class SearchResultDTO {
    private String type; // "PLAYER" or "TEAM"
    private String id; // playerId or teamCode
    private String name; // player full name or team name
    private String secondaryInfo; // player position or team abbreviation
    private String imageUrl; // headshot or logo
    private String season; // for context

    // Constructor, getters, setters
}
```

### 2. Create Search Endpoint
**File**: `backend/src/main/java/com/nhl/whoshotbackend/controller/SearchController.java`

**Endpoint**: `GET /api/search/all`

**Query Parameter**: `season` (optional, defaults to current season)

**Response**: Array of SearchResultDTO containing:
- All active players (firstName + lastName, position, headshot, playerId)
- All teams (fullName, teamCode, logo, abbreviation)

**Notes**:
- Return lightweight objects (names, IDs, images only)
- Sort alphabetically by name
- Filter by current season to keep dataset small
- No need for pagination (small dataset)

### 3. Repository Methods
Add methods to existing repositories:

**PlayerRepository**:
```java
@Query("SELECT new com.nhl.whoshotbackend.dto.SearchResultDTO('PLAYER', " +
       "p.id, CONCAT(p.firstName, ' ', p.lastName), p.position, p.headshotUrl, p.seasonId) " +
       "FROM Player p WHERE p.seasonId = :seasonId ORDER BY p.lastName, p.firstName")
List<SearchResultDTO> findAllForSearch(@Param("seasonId") String seasonId);
```

**TeamRepository**:
```java
@Query("SELECT new com.nhl.whoshotbackend.dto.SearchResultDTO('TEAM', " +
       "t.teamCode, t.fullName, t.teamAbbrev, t.logoUrl, t.seasonId) " +
       "FROM Team t WHERE t.seasonId = :seasonId ORDER BY t.fullName")
List<SearchResultDTO> findAllForSearch(@Param("seasonId") String seasonId);
```

### 4. Search Service (Optional)
If preferred, create a service to combine results:

**File**: `backend/src/main/java/com/nhl/whoshotbackend/service/SearchService.java`

**Method**: `List<SearchResultDTO> getAllSearchableItems(String seasonId)`
- Combine player and team results
- Return sorted list

---

## Frontend Agent Tasks

### 1. Create Search Store (Pinia)
**File**: `frontend/src/stores/searchStore.js`

**State**:
- `searchData` (array of all searchable items)
- `isLoaded` (boolean)
- `lastUpdated` (timestamp)

**Actions**:
- `loadSearchData()` - Fetch from backend and cache
- `searchItems(query)` - Filter cached data client-side
- `clearCache()` - Reset cache

**Client-Side Search Logic**:
- Case-insensitive substring matching on name
- Return up to 5 best matches
- Prioritize matches at start of name
- Fuzzy matching optional (consider using fuse.js if needed)

### 2. Create SearchBar Component
**File**: `frontend/src/components/SearchBar.vue`

**Props**: None (standalone component)

**Features**:
- Input field with placeholder "Search players or teams..."
- Debounce implementation (1 second)
- Dropdown results list (max 5 items)
- Each result shows:
  - Image (player headshot or team logo)
  - Name (bold)
  - Secondary info (position or team abbreviation)
  - Type indicator (optional badge: "Player" or "Team")
- Keyboard navigation (arrow keys, enter to select)
- Click outside to close dropdown
- ESC key to close dropdown

**Styling**:
- Match existing app theme
- Dropdown appears below search input
- Highlight hover/selected item
- Loading state during initial cache load
- Empty state ("No results found")

**Navigation**:
- On click/enter: Navigate to:
  - Players: `/player/:playerId`
  - Teams: `/team/:teamCode`
- Close dropdown after navigation

### 3. Add SearchBar to Start Page
**File**: `frontend/src/views/StartPage.vue` (or main dashboard)

**Placement**:
- Prominent position at top of page
- Centered or top-right corner
- Above existing hot tables

**Initialization**:
- Load search data when component mounts
- Show loading indicator if data not yet cached

### 4. API Integration
**File**: `frontend/src/composables/useApi.js`

Add method:
```javascript
const getSearchData = async (season = null) => {
  const endpoint = season
    ? `/search/all?season=${season}`
    : '/search/all'
  return await fetchData(endpoint) || []
}
```

### 5. Debounce Utility (if not exists)
**File**: `frontend/src/utils/debounce.js` (or add to SearchBar component)

```javascript
export function debounce(fn, delay) {
  let timeoutId
  return function (...args) {
    clearTimeout(timeoutId)
    timeoutId = setTimeout(() => fn.apply(this, args), delay)
  }
}
```

---

## Implementation Checklist

### Backend
- [ ] Create SearchResultDTO with all required fields
- [ ] Add search query methods to PlayerRepository
- [ ] Add search query methods to TeamRepository
- [ ] Create SearchController with `/api/search/all` endpoint
- [ ] (Optional) Create SearchService to combine results
- [ ] Test endpoint returns all players and teams
- [ ] Verify image URLs are included in response

### Frontend
- [ ] Create Pinia search store with caching
- [ ] Implement client-side search with fuzzy matching
- [ ] Create SearchBar component with debounce (1 second)
- [ ] Implement dropdown with max 5 results
- [ ] Add keyboard navigation (arrows, enter, ESC)
- [ ] Add click-outside-to-close behavior
- [ ] Style dropdown to match app theme
- [ ] Implement navigation to player/team pages
- [ ] Add SearchBar to start page
- [ ] Test search performance with full dataset
- [ ] Add loading and empty states

---

## Testing Scenarios

1. **Initial Load**: Search data loads from backend on app start
2. **Fast Typing**: Debounce prevents excessive filtering during typing
3. **Exact Match**: Searching "Connor McDavid" shows Connor McDavid first
4. **Partial Match**: Searching "McD" shows McDavid and other matches
5. **Team Search**: Searching "Oilers" shows Edmonton Oilers
6. **No Results**: Searching "xyz123" shows "No results found"
7. **Navigation**: Clicking a result navigates to correct page
8. **Keyboard Nav**: Arrow keys navigate, enter selects, ESC closes
9. **Performance**: Search feels instant with no lag
10. **Cache Refresh**: Data stays fresh after backend sync

---

## Technical Decisions Summary

**Data Fetching**: Hybrid approach (Pinia cache + backend sync)
- ✅ Best user experience (instant search)
- ✅ Minimal backend load (one query on load)
- ✅ Small dataset size (~800 items)
- ✅ Easy to keep in sync

**Debounce**: 1 second (as specified)
- Applied to search filtering, not API calls (since we use cache)

**Result Limit**: 5 items (as specified)
- Keeps dropdown compact and scannable

**Search Algorithm**: Client-side substring matching
- Can enhance with fuzzy matching (fuse.js) if needed
- Prioritize matches at start of string

---

## Notes for Coordination

- **Backend**: Focus on creating lightweight search endpoint
- **Frontend**: Focus on UX - smooth debounce, keyboard nav, styling
- **Integration**: Frontend loads search data once on app start
- **Future Enhancement**: Auto-refresh cache when data sync completes
- **Data Size**: ~800 players + 32 teams = ~50KB (very manageable for client-side)

---

## Example User Flow

1. User lands on start page
2. Search data loads silently in background (Pinia store)
3. User types "mcd" in search bar
4. After 1 second of inactivity, dropdown shows:
   - Connor McDavid (C) [with headshot]
   - Connor McDonald (D) [with headshot]
   - McDonagh, Ryan (D) [with headshot]
5. User clicks "Connor McDavid"
6. App navigates to `/player/8478402`
7. Dropdown closes

---

## Questions for Coordination Review

1. Should we implement fuzzy matching or stick with simple substring?
2. Should search be case-sensitive or case-insensitive? (Recommend insensitive)
3. Should we show team affiliation for players in dropdown? (Optional)
4. Should we cache multiple seasons or just current? (Recommend current only)
5. When should cache refresh? (Recommend: on app load + after data sync)

