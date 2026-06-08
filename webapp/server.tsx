import {Hono} from 'hono'
import {serveStatic} from 'hono/bun'
import {Layout} from './components/Layout'
import {PodcastList} from './components/PodcastList'
import {PodcastDetail} from './components/PodcastDetail'
import {QueuePage, QueueList} from './components/QueuePage'
import {RecentPage} from './components/RecentPage'
import {SettingsPage} from './components/SettingsPage'
import {NowPlaying} from './components/NowPlaying'
import {EpisodeDetail} from './components/EpisodeDetail'
import type {Podcast, PodcastDetail as PodcastDetailType, Episode} from './types'

const KOTLIN_API = process.env.KOTLIN_API ?? 'http://localhost:8100'
const WS_API = KOTLIN_API.replace(/^http/, 'ws')

const app = new Hono()

app.use('/static/*', serveStatic({root: './'}))

app.get('/', async (c) => {
    const res = await fetch(`${KOTLIN_API}/api/episodes/recent`)
    if (!res.ok) return new Response('', {status: res.status})
    const episodes: Episode[] = await res.json()
    const isHtmx = c.req.header('HX-Request') === 'true'
    const content = <RecentPage episodes={episodes}/>

    if (isHtmx) {
        return c.html(
            <div id="content-container">
                <div class="page-content">{content}</div>
            </div>
        )
    }
    return c.html(
        <Layout title="Recent — Cast">{content}</Layout>
    )
})

app.get('/podcasts', async (c) => {
    const podcasts: Podcast[] = await fetch(`${KOTLIN_API}/api/podcasts`).then(r => r.json())
    const isHtmx = c.req.header('HX-Request') === 'true'
    const content = <PodcastList podcasts={podcasts}/>

    if (isHtmx) {
        return c.html(
            <div id="content-container">
                <div class="page-content">{content}</div>
            </div>
        )
    }
    return c.html(
        <Layout title="Cast">{content}</Layout>
    )
})

app.post('/podcasts', async (c) => {
    const body = await c.req.parseBody()
    const url = body['url'] as string

    const res = await fetch(`${KOTLIN_API}/api/podcasts`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({feed: url}),
    })

    if (!res.ok) {
        return new Response('', {status: res.status})
    }

    const podcasts: Podcast[] = await fetch(`${KOTLIN_API}/api/podcasts`).then(r => r.json())
    const isHtmx = c.req.header('HX-Request') === 'true'
    const content = <PodcastList podcasts={podcasts}/>

    if (isHtmx) {
        return c.html(
            <div id="content-container">
                <div class="page-content">{content}</div>
            </div>
        )
    }
    return c.redirect('/podcasts')
})

app.post('/podcasts/import', async (c) => {
    const body = await c.req.parseBody()
    const file = body['opml'] as File

    const formData = new FormData()
    formData.append('opml', file)

    const res = await fetch(`${KOTLIN_API}/api/podcasts/import`, {method: 'POST', body: formData})

    if (!res.ok) {
        return new Response('', {status: res.status})
    }

    const podcasts: Podcast[] = await fetch(`${KOTLIN_API}/api/podcasts`).then(r => r.json())
    const isHtmx = c.req.header('HX-Request') === 'true'
    const content = <PodcastList podcasts={podcasts}/>

    if (isHtmx) {
        return c.html(
            <div id="content-container">
                <div class="page-content">{content}</div>
            </div>
        )
    }
    return c.redirect('/podcasts')
})

app.get('/now-playing', async (c) => {
    const isHtmx = c.req.header('HX-Request') === 'true'
    const content = <NowPlaying/>
    if (isHtmx) {
        return c.html(
            <div id="content-container">
                <div class="page-content">{content}</div>
            </div>
        )
    }
    return c.html(<Layout title="Now Playing — Cast">{content}</Layout>)
})

app.get('/episodes/:id', async (c) => {
    const id = c.req.param('id')
    const res = await fetch(`${KOTLIN_API}/api/episodes/${encodeURIComponent(id)}`)
    if (!res.ok) return c.notFound()
    const episode: Episode = await res.json()
    const isHtmx = c.req.header('HX-Request') === 'true'
    const content = <EpisodeDetail episode={episode}/>
    if (isHtmx) {
        return c.html(
            <div id="content-container">
                <div class="page-content">{content}</div>
            </div>
        )
    }
    return c.html(<Layout title={episode.title}>{content}</Layout>)
})

app.get('/queue', async (c) => {
    const res = await fetch(`${KOTLIN_API}/api/queue`)
    if (!res.ok) return new Response('', {status: res.status})
    const episodes: Episode[] = await res.json()
    const isHtmx = c.req.header('HX-Request') === 'true'
    const content = <QueuePage episodes={episodes}/>

    if (isHtmx) {
        return c.html(
            <div id="content-container">
                <div class="page-content">{content}</div>
            </div>
        )
    }
    return c.html(
        <Layout title="Queue — Cast">{content}</Layout>
    )
})

app.post('/queue/:id', async (c) => {
    const id = encodeURIComponent(c.req.param('id'))
    const res = await fetch(`${KOTLIN_API}/api/queue/${id}`, {method: 'POST'})
    if (!res.ok) return new Response('', {status: res.status})
    const episodes: Episode[] = await res.json()
    return c.html(<QueueList episodes={episodes}/>)
})

app.delete('/queue/:id', async (c) => {
    const id = encodeURIComponent(c.req.param('id'))
    const res = await fetch(`${KOTLIN_API}/api/queue/${id}`, {method: 'DELETE'})
    if (!res.ok) return new Response('', {status: res.status})
    const episodes: Episode[] = await res.json()
    return c.html(<QueueList episodes={episodes}/>)
})

app.get('/api/queue', async (c) => {
    const res = await fetch(`${KOTLIN_API}/api/queue`)
    return new Response(res.body, {
        status: res.status,
        headers: {'Content-Type': res.headers.get('content-type') ?? 'application/json'},
    })
})

app.put('/api/queue', async (c) => {
    const body = await c.req.text()
    const res = await fetch(`${KOTLIN_API}/api/queue`, {
        method: 'PUT',
        headers: {'Content-Type': 'application/json'},
        body,
    })
    return new Response(res.body, {
        status: res.status,
        headers: {'Content-Type': res.headers.get('content-type') ?? 'application/json'},
    })
})

app.post('/api/podcasts/:id/played', async (c) => {
    const id = encodeURIComponent(c.req.param('id'))
    const res = await fetch(`${KOTLIN_API}/api/podcasts/${id}/played`, {method: 'POST'})
    return new Response(null, {status: res.status})
})

app.post('/api/podcasts/:id/listening', async (c) => {
    const id = encodeURIComponent(c.req.param('id'))
    await fetch(`${KOTLIN_API}/api/podcasts/${id}/listening`, {method: 'POST'})
    const podcasts: Podcast[] = await fetch(`${KOTLIN_API}/api/podcasts`).then(r => r.json())
    return c.html(<PodcastList podcasts={podcasts}/>)
})

app.delete('/api/podcasts/:id/listening', async (c) => {
    const id = encodeURIComponent(c.req.param('id'))
    await fetch(`${KOTLIN_API}/api/podcasts/${id}/listening`, {method: 'DELETE'})
    const podcasts: Podcast[] = await fetch(`${KOTLIN_API}/api/podcasts`).then(r => r.json())
    return c.html(<PodcastList podcasts={podcasts}/>)
})

app.post('/podcasts/:id/listening', async (c) => {
    const id = encodeURIComponent(c.req.param('id'))
    await fetch(`${KOTLIN_API}/api/podcasts/${id}/listening`, {method: 'POST'})
    const detail: PodcastDetailType = await fetch(`${KOTLIN_API}/api/podcasts/${id}`).then(r => r.json())
    return c.html(
        <div id="content-container">
            <div class="page-content"><PodcastDetail podcast={detail} episodes={detail.episodes}/></div>
        </div>
    )
})

app.delete('/podcasts/:id/listening', async (c) => {
    const id = encodeURIComponent(c.req.param('id'))
    await fetch(`${KOTLIN_API}/api/podcasts/${id}/listening`, {method: 'DELETE'})
    const detail: PodcastDetailType = await fetch(`${KOTLIN_API}/api/podcasts/${id}`).then(r => r.json())
    return c.html(
        <div id="content-container">
            <div class="page-content"><PodcastDetail podcast={detail} episodes={detail.episodes}/></div>
        </div>
    )
})

app.post('/api/episodes/:id/played', async (c) => {
    const id = encodeURIComponent(c.req.param('id'))
    const res = await fetch(`${KOTLIN_API}/api/episodes/${id}/played`, {method: 'POST'})
    return new Response(null, {status: res.status})
})

app.delete('/api/episodes/:id/played', async (c) => {
    const id = encodeURIComponent(c.req.param('id'))
    const res = await fetch(`${KOTLIN_API}/api/episodes/${id}/played`, {method: 'DELETE'})
    return new Response(null, {status: res.status})
})

app.get('/settings', async (c) => {
    const res = await fetch(`${KOTLIN_API}/api/settings`)
    if (!res.ok) return new Response('', {status: res.status})
    const settings = await res.json() as {hidePlayed: boolean, recentListeningOnly: boolean}
    const isHtmx = c.req.header('HX-Request') === 'true'
    const content = <SettingsPage hidePlayed={settings.hidePlayed} recentListeningOnly={settings.recentListeningOnly}/>

    if (isHtmx) {
        return c.html(
            <div id="content-container">
                <div class="page-content">{content}</div>
            </div>
        )
    }
    return c.html(
        <Layout title="Settings — Cast">{content}</Layout>
    )
})

app.post('/settings', async (c) => {
    const body = await c.req.parseBody()
    const hidePlayed = body['hidePlayed'] === 'on'
    const recentListeningOnly = body['recentListeningOnly'] === 'on'
    await fetch(`${KOTLIN_API}/api/settings`, {
        method: 'PUT',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({hidePlayed, recentListeningOnly}),
    })
    return new Response(null, {status: 204})
})

app.get('/podcasts/:id', async (c) => {
    const id = c.req.param('id')
    const res = await fetch(`${KOTLIN_API}/api/podcasts/${id}`)
    if (!res.ok) return c.notFound()

    const detail: PodcastDetailType = await res.json()
    const isHtmx = c.req.header('HX-Request') === 'true'
    const content = <PodcastDetail podcast={detail} episodes={detail.episodes}/>

    if (isHtmx) {
        return c.html(
            <div id="content-container">
                <div class="page-content">{content}</div>
            </div>
        )
    }
    return c.html(
        <Layout title={detail.name}>{content}</Layout>
    )
})

interface WsData {
    kotlinWs: WebSocket
    buffer: string[]
}

Bun.serve<WsData>({
    port: 3000,
    fetch(req, server) {
        const url = new URL(req.url)
        if (url.pathname === '/api/playback') {
            const kotlinWs = new WebSocket(`${WS_API}/api/playback`)
            const upgraded = server.upgrade(req, {data: {kotlinWs, buffer: []}})
            if (upgraded) return
            return new Response('WebSocket upgrade required', {status: 426})
        }
        return app.fetch(req)
    },
    websocket: {
        open(ws) {
            ws.data.kotlinWs.onopen = () => {
                for (const msg of ws.data.buffer) ws.data.kotlinWs.send(msg)
                ws.data.buffer = []
            }
            ws.data.kotlinWs.onmessage = (msg) => ws.send(msg.data as string)
            ws.data.kotlinWs.onclose = () => ws.close()
            ws.data.kotlinWs.onerror = () => ws.close()
        },
        message(ws, msg) {
            if (ws.data.kotlinWs.readyState === WebSocket.OPEN) {
                ws.data.kotlinWs.send(msg as string)
            } else {
                ws.data.buffer.push(msg as string)
            }
        },
        close(ws) {
            ws.data.kotlinWs.close()
        },
    },
})

console.log('Cast webapp running on http://localhost:3000')
