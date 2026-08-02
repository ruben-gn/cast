# Catalog View Toggle & Sort — Design Spec

## Overview

Add a list/grid view toggle and sort control to the Android catalog screen, with podcasts split into two sections by listening status.

## Sort Options

Two options, applied within each section:

- **Name** — alphabetical (`name.lowercase()`)
- **Recently updated** — by `updated` ISO string descending (lexicographic sort is correct for ISO-8601)

Default: Name.

## Sections

Podcasts are always split into two groups regardless of view mode:

1. **Listening** — `podcast.listening == true`
2. **Not listening** — `podcast.listening == false`

The "Not listening" section is only rendered when non-empty. Sorting applies independently within each section.

## ViewModel (`CatalogViewModel`)

Two new state fields:

```kotlin
enum class ViewMode { Grid, List }
enum class SortBy { Name, Updated }

var viewMode: ViewMode by mutableStateOf(ViewMode.Grid)
    private set
var sortBy: SortBy by mutableStateOf(SortBy.Name)
    private set

fun toggleViewMode() { viewMode = if (viewMode == ViewMode.Grid) ViewMode.List else ViewMode.Grid }
fun setSortBy(sort: SortBy) { sortBy = sort }
```

A private extension function sorts a list by the current `SortBy`:

```kotlin
private fun List<PodcastSummaryDto>.sorted() = when (sortBy) {
    SortBy.Name -> sortedBy { it.name.lowercase() }
    SortBy.Updated -> sortedByDescending { it.updated }
}
```

Computed properties (derived from `uiState` when `Success`):

```kotlin
val listeningPodcasts: List<PodcastSummaryDto>
    get() = (uiState as? UiState.Success)?.data.orEmpty().filter { it.listening }.sorted()

val notListeningPodcasts: List<PodcastSummaryDto>
    get() = (uiState as? UiState.Success)?.data.orEmpty().filter { !it.listening }.sorted()
```

`sortBy` and `viewMode` are Compose `mutableStateOf`, so reads inside a `@Composable` trigger recomposition automatically when either changes.

## UI (`CatalogScreen`)

### TopAppBar

Add `TopAppBar` to the `Scaffold`. Actions (right side):

1. **Sort icon** (`Icons.Default.SwapVert` or similar) — opens a `DropdownMenu` with two items:
   - "Name" — checkmark when active
   - "Recently updated" — checkmark when active
2. **View toggle icon** — `Icons.Default.GridView` when in List mode (tap to go Grid), `Icons.AutoMirrored.Default.List` when in Grid mode (tap to go List)

FAB (Add podcast) remains unchanged.

### Grid view (`ViewMode.Grid`)

`LazyVerticalGrid(columns = GridCells.Fixed(3))` with:

- Full-span section header via `item(span = { GridItemSpan(maxLineSpan) })` for "Listening" label
- Grid items for listening podcasts
- Full-span header for "Not listening" (only if non-empty)
- Grid items for not-listening podcasts (only if non-empty)

Section header: `Text` with `labelMedium` style, horizontal padding 8dp, vertical padding 6dp.

### List view (`ViewMode.List`)

`LazyColumn` with:

- `stickyHeader` for "Listening"
- Items for listening podcasts — each row: 48dp square artwork (`RoundedCornerShape(6.dp)`) + column with name (`bodyMedium`) + `updated` string (`labelSmall`, secondary color)
- `stickyHeader` for "Not listening" (only if non-empty)
- Items for not-listening podcasts (only if non-empty)
- `HorizontalDivider` between rows

Sticky header background matches surface color so it's opaque when scrolling.

### Skeleton (`CatalogScreenSkeleton`)

No change needed — skeleton shows before data loads and doesn't reflect sections or view mode.

## Scope

- View mode and sort are in-memory only (reset on process death). No DataStore persistence.
- The "Not listening" toggle on individual grid cards is unchanged.
- No changes to backend, shared models, or repository.
