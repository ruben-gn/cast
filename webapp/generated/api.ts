// Generated from Kotlin shared-models -- do not edit manually

export interface AddPodcastRequest {
  feed: string
}
export interface EpisodeDetailDto {
  id: string
  title: string
  description: string
  audioUrl: string
  duration: string | null
  durationMs: number | null
  publishedAt: string | null
  played: boolean
  progressMs: number
  podcastId: string | null
  podcastName: string | null
  podcastImage: string | null
}
export interface PlaybackStateResponse {
  type: string
  episodeId: string
  progressMs: number
  played: boolean
}
export interface PodcastDetailDto {
  id: string
  url: string
  name: string
  image: string
  listening: boolean
  created: string
  episodes: EpisodeDetailDto[]
}
export interface PodcastSummaryDto {
  id: string
  url: string
  name: string
  image: string
  listening: boolean
  created: string
  latestEpisodeAt: string
}
export interface ReorderQueueRequest {
  episodeIds: string[]
}
export interface SettingsDto {
  hidePlayed: boolean
  recentListeningOnly: boolean
}
