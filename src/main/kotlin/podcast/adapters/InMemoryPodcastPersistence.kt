package podcast.adapters

import podcast.core.model.Podcast
import podcast.core.port.PodcastPersistence
import java.time.Instant

class InMemoryPodcastPersistence : PodcastPersistence {

    //    private val storage = mutableMapOf<String, Podcast>()
    private val storage = mutableMapOf<String, Podcast>(
        "8d458250-e277-44bf-979a-47c4ba821701" to Podcast(
            "8d458250-e277-44bf-979a-47c4ba821701",
            "https://app.springcast.fm/podcast-xml/19300",
            "Elke Week van EW Magazine",
            "https://app.springcast.fm/storage/artwork/6336/19300/1CoMMd6QRLXoMLC2tRTddWJLXW3L98eMxQIdHPIR.jpg",
            Instant.parse("2026-04-13T20:53:52.447Z")
        ),
        "a97e6514-72fd-4af1-b774-1f00a261efd0" to Podcast(
            "a97e6514-72fd-4af1-b774-1f00a261efd0",
            "https://feed.petje.af/tpopodcast?membership=50bfcb3e-810d-4b4d-b90a-88f075d66959",
            "Brussen & Veelo Vrijdag (🔓)",
            "https://images.petjeaf.com/_/public/page/68e93823-b083-4c94-80cd-935f8007f2e2/3a5c4032-d6eb-45c0-bf60-ae7343de7a5d.jpg",
            Instant.parse("2026-04-13T20:53:52.655279Z")
        ),
        "26a311a8-9df7-4031-b203-da3a4544fead" to Podcast(
            "26a311a8-9df7-4031-b203-da3a4544fead",
            "https://www.omnycontent.com/d/playlist/fdd7ab40-270d-4a1e-a257-acd200da1324/8ea050bf-b0b4-4652-8276-ae030139c572/b13b3bf4-7788-4ddc-921e-ae030139c580/podcast.rss",
            "Afhameren met Wouter de Winther",
            "https://www.omnycontent.com/d/programs/fdd7ab40-270d-4a1e-a257-acd200da1324/8ea050bf-b0b4-4652-8276-ae030139c572/image.jpg?t=1736416045&size=Large",
            Instant.parse("2026-04-13T20:53:53.046738Z")
        ),
        "951c4fc2-1436-43f9-a350-e0740cc3d27f" to Podcast(
            "951c4fc2-1436-43f9-a350-e0740cc3d27f",
            "https://feeds.redcircle.com/2ff6b0e7-2516-44e6-a53c-26f8a68e5565",
            "Lord of the Rings Lorecast - J.R.R. Tolkien's World & Writings Explained",
            "https://media.redcircle.com/images/2024/11/10/4/882e82dd-a402-4081-b8f6-071b911b9819_7-4e2f-a846-76c015fe90da_lotr_lorecast_logo_v3.jpg",
            Instant.parse("2026-04-13T20:53:54.375361Z")
        ),
        "f92a1069-44f0-49d2-9cb1-25cab7dbc62f" to Podcast(
            "f92a1069-44f0-49d2-9cb1-25cab7dbc62f",
            "https://rss.buzzsprout.com/1918040.rss",
            "Politieke Popcorncast",
            "https://storage.buzzsprout.com/a746ybyczwwwfmxsjx1gwakejaj7?.jpg",
            Instant.parse("2026-04-13T20:53:54.492744Z")
        ),
        "b3d59e89-0bc0-457a-861c-d5d90109183c" to Podcast(
            "b3d59e89-0bc0-457a-861c-d5d90109183c",
            "https://www.omnycontent.com/d/playlist/33dbd2dc-d464-471d-9feb-abae00330078/ed6f6972-a49d-4979-be1d-abae00cd3a92/70ce1b8c-8e0d-4029-9f69-abae00cd3a97/podcast.rss",
            "Tweakers Podcast",
            "https://www.omnycontent.com/d/playlist/33dbd2dc-d464-471d-9feb-abae00330078/ed6f6972-a49d-4979-be1d-abae00cd3a92/70ce1b8c-8e0d-4029-9f69-abae00cd3a97/image.jpg?t=1588336034&size=Large",
            Instant.parse("2026-04-13T20:53:55.453547Z")
        ),
        "45c0f633-9136-440f-9ed1-04e791519264" to Podcast(
            "45c0f633-9136-440f-9ed1-04e791519264",
            "https://subscribers.transistor.fm/5f2acfb484502b",
            "EDFM",
            "https://img.transistorcdn.com/MCp7NUnTXiarYjr8598jw6H-ZmWy9_MnK8qWDl8D2qw/rs:fill:0:0:1/w:1400/h:1400/q:60/mb:500000/aHR0cHM6Ly9pbWct/dXBsb2FkLXByb2R1/Y3Rpb24udHJhbnNp/c3Rvci5mbS9lZjA2/ZDg3ODVhMzk2Nzkw/ODEzYzEzZTYwZjA4/ZTczOS5wbmc.jpg",
            Instant.parse("2026-04-13T20:53:55.665984Z")
        ),
        "52f40980-bc2f-410f-99a8-182e454f2d07" to Podcast(
            "52f40980-bc2f-410f-99a8-182e454f2d07",
            "https://feed.petje.af/narejongens?membership=1c2c93b8-424b-462a-b61f-ec04fa392a9a",
            "Bellen met Bassie (🔓)",
            "https://images.petjeaf.com/_/public/page/a83fb5de-f1f9-47c5-ad52-f435f3ed2913/5293d692-bcb0-417f-b975-36af38b43704.jpeg",
            Instant.parse("2026-04-13T20:53:55.799614Z")
        ),
        "516b3b59-3151-4f62-82c7-e4da6fb8318c" to Podcast(
            "516b3b59-3151-4f62-82c7-e4da6fb8318c",
            "https://www.omnycontent.com/d/playlist/fdd7ab40-270d-4a1e-a257-acd200da1324/a6e37ce6-10f5-43f1-928c-b0a100c71692/0aa42add-53e0-4d3a-abdc-b0a100c7191d/podcast.rss",
            "Het Haags Kwartiertje",
            "https://www.omnycontent.com/d/programs/fdd7ab40-270d-4a1e-a257-acd200da1324/a6e37ce6-10f5-43f1-928c-b0a100c71692/image.jpg?t=1698250532&size=Large",
            Instant.parse("2026-04-13T20:53:56.479848Z")
        ),
        "6ecb0675-dd0b-484d-9b31-9498324082a0" to Podcast(
            "6ecb0675-dd0b-484d-9b31-9498324082a0",
            "https://feeds.acast.com/public/shows/61deed94f2acc80013aab8aa",
            "A Podcast Of Unnecessary Detail",
            "https://assets.pippa.io/shows/61deed94f2acc80013aab8aa/1642016839531-085b43f714b1b178839bc5d1685948d7.jpeg",
            Instant.parse("2026-04-13T20:53:56.596016Z")
        ),
        "6595f1a2-322e-4181-9a76-4fd711f4e497" to Podcast(
            "6595f1a2-322e-4181-9a76-4fd711f4e497",
            "https://www.omnycontent.com/d/playlist/e73c998e-6e60-432f-8610-ae210140c5b1/ee4336cb-155f-4488-90e0-b1400134e40e/77e6a3a7-290d-4a82-8164-b14001353ef2/podcast.rss",
            "Money Stuff: The Podcast",
            "https://www.omnycontent.com/d/programs/e73c998e-6e60-432f-8610-ae210140c5b1/ee4336cb-155f-4488-90e0-b1400134e40e/image.jpg?t=1711565317&size=Large",
            Instant.parse("2026-04-13T20:53:56.902258Z")
        ),
        "bf4c93e8-9469-4b97-a128-fd22f14153a3" to Podcast(
            "bf4c93e8-9469-4b97-a128-fd22f14153a3",
            "https://www.omnycontent.com/d/playlist/8257a063-6be9-42fa-b892-acd4013b1255/b78de77c-664f-4baa-a146-ae6800769bd8/f87f2967-1a80-4fca-b9f6-ae680076e49e/podcast.rss",
            "FD Achter Gesloten Deuren",
            "https://www.omnycontent.com/d/playlist/8257a063-6be9-42fa-b892-acd4013b1255/b78de77c-664f-4baa-a146-ae6800769bd8/f87f2967-1a80-4fca-b9f6-ae680076e49e/image.jpg?t=1650466220&size=Large",
            Instant.parse("2026-04-13T20:53:57.157123Z")
        ),
        "abe7f355-2ac1-424f-b8d0-8d30e77a4a4b" to Podcast(
            "abe7f355-2ac1-424f-b8d0-8d30e77a4a4b",
            "https://anchor.fm/s/5b473960/podcast/rss",
            "Boze Geesten | Open Geesten ",
            "https://d3t3ozftmdmh3i.cloudfront.net/production/podcast_uploaded_nologo/15213944/15213944-1718886973066-014e90587e3c3.jpg",
            Instant.parse("2026-04-13T20:53:57.345298Z")
        ),
        "6ce02322-4a21-4333-9fde-87872fe66334" to Podcast(
            "6ce02322-4a21-4333-9fde-87872fe66334",
            "https://rss2.flightcast.com/pmgqiszts7kfhopzaq8el6yw.xml",
            "The Standup with ThePrimeagen",
            "https://assets.flightcast.com/workspaces/ibql3yufneslfscullre41ud/uploads/t2vvNI7xLFdztN6MOXVH0tWL-_twPEP3.png",
            Instant.parse("2026-04-13T20:53:57.473810Z")
        ),
        "f82ff00b-dfc9-446f-8638-0ff750cd302c" to Podcast(
            "f82ff00b-dfc9-446f-8638-0ff750cd302c",
            "https://channels.podcastfeed.nl/3fe8cab0-7cd1-11eb-8563-27273e3e7591/feed.xml?_ga=2.187136296.1876743407.1614852030-31680426.1614852030",
            "Nare Jongens Podcast",
            "https://cdn.podcastfeed.nl/3fe8cab0-7cd1-11eb-8563-27273e3e7591/3fe9d830-7cd1-11eb-8e66-e9bb82b8858f.jpg?1767699367",
            Instant.parse("2026-04-13T20:53:57.680701Z")
        ),
        "300a872f-2883-43c2-b1f8-954a6d49f942" to Podcast(
            "300a872f-2883-43c2-b1f8-954a6d49f942",
            "https://feeds.soundcloud.com/users/soundcloud:users:1086140401/sounds.rss",
            "Wynia's Week",
            "https://i1.sndcdn.com/avatars-nwCoapiDfRkZeUSX-UkuUjw-original.jpg",
            Instant.parse("2026-04-13T20:53:57.988802Z")
        ),
        "27cd38c9-947a-4b36-ac95-11ffa8886fc3" to Podcast(
            "27cd38c9-947a-4b36-ac95-11ffa8886fc3",
            "https://anchor.fm/s/fb29b160/podcast/rss",
            "The Top Shelf",
            "https://d3t3ozftmdmh3i.cloudfront.net/staging/podcast_uploaded_nologo/42038136/42038136-1726601723823-d03bbde20d083.jpg",
            Instant.parse("2026-04-13T20:53:58.075739Z")
        ),
        "bb1d2187-ea6c-41f8-bd14-27757aeae07a" to Podcast(
            "bb1d2187-ea6c-41f8-bd14-27757aeae07a",
            "https://feeds.transistor.fm/aws-morning-brief",
            "Last Week In AWS Podcast",
            "https://img.transistorcdn.com/EmuTjwJxvmRz4FP7pqX3AFReImqwX7k59Mme30xON4Y/rs:fill:0:0:1/w:1400/h:1400/q:60/mb:500000/aHR0cHM6Ly9pbWct/dXBsb2FkLXByb2R1/Y3Rpb24udHJhbnNp/c3Rvci5mbS80MDRk/OGFjYTIxMWE1MjQy/YWRkZDhiMDJmMzMy/MDQyMi5wbmc.jpg",
            Instant.parse("2026-04-13T20:53:58.415860Z")
        ),
        "1dcdd56f-6de2-4868-8902-e86e6328d9c8" to Podcast(
            "1dcdd56f-6de2-4868-8902-e86e6328d9c8",
            "https://www.omnycontent.com/d/playlist/61ee9ca4-a1b2-4660-9651-b2b70035edf5/cd00b4a2-3577-451b-beed-b2f100fbfad7/9d13575e-9e2c-4b45-b23c-b2f100fbfae4/podcast.rss",
            "Wat een Week!",
            "https://www.omnycontent.com/d/playlist/61ee9ca4-a1b2-4660-9651-b2b70035edf5/cd00b4a2-3577-451b-beed-b2f100fbfad7/9d13575e-9e2c-4b45-b23c-b2f100fbfae4/image.jpg?t=1748963852&size=Large",
            Instant.parse("2026-04-13T20:53:58.880176Z")
        ),
        "bb2fa036-c8d4-4aa0-99fe-94de60711845" to Podcast(
            "bb2fa036-c8d4-4aa0-99fe-94de60711845",
            "https://podcast.npo.nl/feed/de-spindoctors.xml",
            "De Spindoctors",
            "https://podcast.npo.nl/data/thumb/de-spindoctors.1400.2c2e5475b41e5392215eda22c4f22bff.jpg",
            Instant.parse("2026-04-13T20:53:59.057630Z")
        ),
        "02605281-41e2-406c-86c2-e659e9ff206d" to Podcast(
            "02605281-41e2-406c-86c2-e659e9ff206d",
            "https://anchor.fm/s/f8e28404/podcast/rss",
            "Brussen en Veelo Podcast",
            "https://d3t3ozftmdmh3i.cloudfront.net/staging/podcast_uploaded_nologo/41655945/41655945-1723183924135-9a2ac9275e498.jpg",
            Instant.parse("2026-04-13T20:53:59.174860Z")
        ),
        "9dd76fe7-6902-40c9-a301-4767bda58b6f" to Podcast(
            "9dd76fe7-6902-40c9-a301-4767bda58b6f",
            "https://rss.libsyn.com/shows/112428/destinations/628353.xml",
            "CoRecursive: Coding Stories",
            "https://static.libsyn.com/p/assets/d/7/a/5/d7a5a500931246e3/Coding_Stories.png",
            Instant.parse("2026-04-13T20:54:00.049517Z")
        ),
        "0efe4c1e-2f5b-485b-b0ab-e43304ba412a" to Podcast(
            "0efe4c1e-2f5b-485b-b0ab-e43304ba412a",
            "https://www.omnycontent.com/d/playlist/61ee9ca4-a1b2-4660-9651-b2b70035edf5/7a0fa413-eff8-429e-846d-b2f100f2b38e/a5100176-262d-49c6-a8f1-b2f100f2b411/podcast.rss",
            "Victor Duidt TV",
            "https://www.omnycontent.com/d/playlist/61ee9ca4-a1b2-4660-9651-b2b70035edf5/7a0fa413-eff8-429e-846d-b2f100f2b38e/a5100176-262d-49c6-a8f1-b2f100f2b411/image.jpg?t=1751732186&size=Large",
            Instant.parse("2026-04-13T20:54:00.856957Z")
        ),
        "dc655d8a-90bf-4de3-b012-7d435d392173" to Podcast(
            "dc655d8a-90bf-4de3-b012-7d435d392173",
            "https://anchor.fm/s/fe1f1fb8/podcast/rss",
            "De GeenStijl Podcast",
            "https://d3t3ozftmdmh3i.cloudfront.net/staging/podcast_uploaded_nologo/42534526/42534526-1732638876692-c27f91b3e675e.jpg",
            Instant.parse("2026-04-13T20:54:00.928983Z")
        ),
        "2fe91ed1-36f1-4168-a29c-e843ee617571" to Podcast(
            "2fe91ed1-36f1-4168-a29c-e843ee617571",
            "https://api.substack.com/feed/podcast/458709.rss",
            "The Pragmatic Engineer",
            "https://substackcdn.com/feed/podcast/458709/7de65f806a917987a235da999c014f7c.jpg",
            Instant.parse("2026-04-13T20:54:01.065712Z")
        )
    )

    override fun save(podcast: Podcast) = podcast.let { storage[podcast.id] = podcast }

    override fun findAll() = storage.values.toList()

    override fun findByUrl(url: String) = storage.values.find { it.url == url }
}