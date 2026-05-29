package queue

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import queue.adapters.persistence.SQLiteQueuePersistence
import queue.core.ports.QueuePersistence
import queue.core.usecase.DequeueEpisode
import queue.core.usecase.AddEpisodeAt
import queue.core.usecase.AddEpisodeFirst
import queue.core.usecase.AddEpisodeLast
import queue.core.usecase.GetQueue
import queue.core.usecase.ReorderQueue

fun Application.installQueueModule(
    queuePersistence: QueuePersistence? = null
) {
    dependencies {
        provide<QueuePersistence> { queuePersistence ?: SQLiteQueuePersistence(resolve()) }

        provide<GetQueue> { GetQueue(resolve()) }
        provide<AddEpisodeLast> { AddEpisodeLast(resolve()) }
        provide<AddEpisodeFirst> { AddEpisodeFirst(resolve()) }
        provide<AddEpisodeAt> { AddEpisodeAt(resolve()) }
        provide<DequeueEpisode> { DequeueEpisode(resolve()) }
        provide<ReorderQueue> { ReorderQueue(resolve()) }
    }
}