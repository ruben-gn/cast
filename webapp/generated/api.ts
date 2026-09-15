// Generated from Kotlin shared-models -- do not edit manually

export interface AddPodcastRequest {
  feed: string
}
export interface CreateSeriesRuleRequest {
  name: string
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
  seriesName: string | null
  podcastId: string | null
  podcastName: string | null
  podcastImage: string | null
}
export interface EpisodeEndedMessage {
  type: 'ended'
  episodeId: string
}
export interface GetPlaybackStateMessage {
  type: 'get'
  episodeId: string
}
export interface PlaybackStateResponse {
  type: 'state'
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
export interface StartPlaybackMessage {
  type: 'start'
  episodeId: string
  startPositionMs: number
}
export interface UpdateProgressMessage {
  type: 'update'
  episodeId: string
  progressMs: number
  updatedAt: number | null
}
