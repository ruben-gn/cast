package series

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import series.adapters.persistence.SQLiteSeriesRulePersistence
import series.core.ports.SeriesRulePersistence
import series.core.usecase.CreateSeriesRule
import series.core.usecase.DeleteSeriesRule
import series.core.usecase.ListSeriesRules

fun Application.installSeriesModule(persistence: SeriesRulePersistence? = null) {
    dependencies {
        provide<SeriesRulePersistence> { persistence ?: SQLiteSeriesRulePersistence(resolve()) }
        provide<CreateSeriesRule> { CreateSeriesRule(resolve(), resolve()) }
        provide<DeleteSeriesRule> { DeleteSeriesRule(resolve()) }
        provide<ListSeriesRules> { ListSeriesRules(resolve()) }
    }
}
