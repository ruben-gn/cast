# Episode context model — design

Date: 2026-08-21. Status: implemented.

## Problem

`GET /api/podcasts/{id}` returns every episode with `seriesName = null`, while the same
episodes on `/api/episodes/recent`, `/api/episodes/{id}` and `/api/queue` carry a resolved
name. The field is not optional-because-absent; it is **overloaded**, and carries two
different meanings on the same wire type:

- on recent / queue / episode detail — *this episode matched no series rule*
- on podcast detail — *nobody looked*

No client can distinguish them.

### It was never a decision

- `58a2a58` ("Series rule endpoints and seriesName on the recent feed") added `seriesName`
  to `EpisodeDetailDto`. Every construction site had to fill it; the recent feed got the
  real value, the rest got `null`.
- `fdd6336` ("Resolve series names on the episode detail and queue endpoints **too**")
  retrofitted two of the three remaining sites. Podcast detail was not touched.
- No test asserts the current behaviour. The `seriesName shouldBe null` assertions in
  `GetEpisodeDetailTests`, `GetQueueDetailTests`, `FindRecentUnplayedEpisodesTests` and
  `AppTest` all cover a different case — an episode matching *no rule*. The podcast-detail
  path is simply unasserted.

### It is latent, not live

Nothing groups by series on a podcast page: Android's `PodcastDetailScreen` has no series
handling, and only `RecentScreen` calls `groupIntoRows`. The webapp groups nowhere at all
(see [`../webapp-parity-gaps.md`](../webapp-parity-gaps.md)). The bug surfaces the moment
either client groups on a podcast page — which is arguably where grouping is most useful.

## Why it happened

Two parallel application models, where one is a literal field-for-field prefix of the other:

```kotlin
EpisodeWithPlayback(episode, progressMs, played)
EpisodeInContext   (episode, progressMs, played, podcastName, podcastImage, seriesName)
```

`GetPodcastDetail` returns the first, the other three use cases return the second. That
split is defensible in itself — on a podcast page the podcast is the parent of the
response, so per-episode podcast fields look redundant.

The mistake is that `seriesName` got filed under "podcast context" and swept along with
`podcastName` / `podcastImage`, when it is an independent axis that podcast detail wants
just as much as the other flows.

`api/PodcastApi.kt` then flattens both models into the single `EpisodeDetailDto` through
two `episodeDetailDto` overloads, and the `EpisodeWithPlayback` one hardcodes
`seriesName = null` (`PodcastApi.kt:158`) to make the shape fit. The lie is introduced in
the mapper, not in the model.

## Decision

**Fill the field; do not split the wire type.**

Splitting `EpisodeDetailDto` into a full and a reduced variant was considered and rejected.
Of its 13 fields, **12 are already correctly populated on both paths** — the podcast-detail
overload fills `podcastId`, `podcastName` and `podcastImage` explicitly from the parent
podcast. `seriesName` is the only discrepancy. Splitting would mean a breaking wire change
for Android (which deserializes `PodcastDetailDto.episodes` as `EpisodeDetailDto[]`),
against the DTO convention that exists so a new client can read an older server's JSON, and
would leave two near-identical 12-field types to keep in step forever — all to avoid
resolving one field.

**Collapse the two application models instead.** `GetPodcastDetail` returns
`EpisodeInContext` like the other three. Because the API already fills the podcast fields
from the parent, the JSON shape does not change at all; only `seriesName` changes from a
constant `null` to a resolved value. Both `EpisodeWithPlayback` and the second mapper
overload then disappear.

After this there is exactly one episode model in the application layer, one mapper to
`EpisodeDetailDto`, and `null` means one thing everywhere: *no series rule matched*.

## Components

1. **`application/usecase/GetPodcastDetail.kt`** — inject `ListSeriesRules`; build
   `EpisodeInContext` with `podcastName`/`podcastImage` from the already-fetched parent
   podcast and `seriesName = rules.matchSeriesName(episode.podcastId, episode.title)`.
   (`GetEpisodeDetail` already imports `ListSeriesRules`, so the Konsist rule that the
   `application` layer may not import ports is unaffected — it is a use case, not a port.)
2. **`application/model/PodcastWithPlayback.kt`** — `episodes: List<EpisodeInContext>`.
3. **`application/model/EpisodeWithPlayback.kt`** — delete. Its only users are
   `GetPodcastDetail`, `PodcastWithPlayback` and `PodcastApi`.
4. **`api/PodcastApi.kt`** — delete the four-argument `episodeDetailDto` overload
   (lines 143–161) and let `podcastDetailDto` map through `episodeDetailDto(EpisodeInContext)`.
5. **`application/ApplicationModule.kt`** — pass the new dependency when constructing
   `GetPodcastDetail`.

No change to `shared-models`, so no `webapp/generated/api.ts` regeneration.

## Wire compatibility

Additive and safe in both directions. The DTO is untouched, so field presence and types are
unchanged; `seriesName` simply stops being constantly `null` on one endpoint. Clients
already accept a string there. An older client against a newer server ignores the value it
was not using; a newer client against an older server sees `null`, which is exactly the
behaviour it has today.

Android needs no change and no release — it starts receiving names whenever it next talks
to an updated server.

## Testing

- **`GetPodcastDetailTests`** — a podcast with a matching series rule yields episodes
  carrying the name; an episode matching no rule yields `null`. This is the assertion that
  is missing today and is the reason the gap survived a retrofit.
- **`AppTest`** — end-to-end over `GET /api/podcasts/{id}`, mirroring the existing recent-feed
  series coverage around `AppTest.kt:320`.
- Existing `seriesName shouldBe null` assertions elsewhere stay valid — they cover the
  no-rule case, whose meaning is unchanged.

## Deliberately out of scope

**The wider enrichment duplication.** All four use cases hand-roll the same playback
decoration (`state?.progressMs ?: 0`, `state?.played ?: false`), and the `hidePlayed` filter
is implemented independently in `GetPodcastDetail` and `GetQueueDetail`, with a third
"drop played episodes" variant in `FindRecentUnplayedEpisodes`. The missing concept is a
collaborator that turns `List<Episode>` into `List<EpisodeInContext>`, owning the playback,
podcast and series lookups.

Deferred rather than dismissed, for two reasons:

- **It is compiler-guarded.** `EpisodeInContext` is a data class and the project bans
  default parameters, so adding a field breaks every construction site loudly. The failure
  mode this spec fixes came from a site that bypassed the model entirely — which the
  collapse above removes.
- **It is not a pure move.** Two use cases need playback state *before* assembly, to filter
  on it; a resolver that hides the lookup forces filtering the enriched list instead
  (cheap here, but a real change in shape). And `FindRecentUnplayedEpisodes` additionally
  filters on `podcast.listening`, which `EpisodeInContext` does not carry — so either it
  keeps its own `listPodcasts`, or the model grows a field only one filter needs.

Worth doing when a fourth flow needs the same enrichment, not before.

**Series grouping on the podcast detail screen.** Filling the field is a prerequisite, but
whether either client should collapse a podcast's episodes into series stacks is a separate
UI decision. For the webapp it belongs with the other parity work in
[`../webapp-parity-gaps.md`](../webapp-parity-gaps.md); for Android it would be new work on
`PodcastDetailScreen`.
