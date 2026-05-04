export interface Podcast {
  id: string
  name: string
  image: string
  url: string
  created: string
  updated: string
}

export interface Episode {
  id: string
  title: string
  description: string
  audioUrl: string
  duration?: string
  publishedAt?: string
}

export interface PodcastDetail extends Podcast {
  episodes: Episode[]
}
