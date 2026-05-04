import {Hono} from 'hono'
import {serveStatic} from 'hono/bun'
import {Layout} from './components/Layout'
import {PodcastList} from './components/PodcastList'
import {PodcastDetail} from './components/PodcastDetail'
import type {Podcast, PodcastDetail as PodcastDetailType} from './types'

const KOTLIN_API = process.env.KOTLIN_API ?? 'http://localhost:8100'
const WS_API = KOTLIN_API.replace(/^http/, 'ws')

const app = new Hono()

app.use('/static/*', serveStatic({root: './'}))

app.get('/', (c) => c.redirect('/podcasts'))

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
