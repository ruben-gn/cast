// Generated from Kotlin shared-models -- do not edit manually

export interface AddPodcastRequest {
  feed: string
}
export interface EpisodeDto {
  id: string
  title: string
  description: string
  audioUrl: string
  duration: string | null
  publishedAt: string | null
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
  created: string
  updated: string
  episodes: EpisodeDto[]
}
export interface PodcastSummaryDto {
  id: string
  url: string
  name: string
  image: string
  created: string
  updated: string
}
