package fr.free.nrw.commons.explore.depictions

import android.annotation.SuppressLint
import android.content.Context
import androidx.preference.PreferenceManager
import fr.free.nrw.commons.contributions.MainActivity
import fr.free.nrw.commons.di.CommonsApplicationModule
import fr.free.nrw.commons.mwapi.Binding
import fr.free.nrw.commons.mwapi.SparqlResponse
import fr.free.nrw.commons.settings.Prefs
import fr.free.nrw.commons.upload.depicts.DepictsInterface
import fr.free.nrw.commons.upload.structure.depictions.DepictedItem
import fr.free.nrw.commons.upload.structure.depictions.get
import fr.free.nrw.commons.wikidata.WikidataProperties
import fr.free.nrw.commons.wikidata.model.DepictSearchItem
import io.reactivex.Single
import org.wikipedia.wikidata.DataValue
import org.wikipedia.wikidata.Entities
import org.wikipedia.wikidata.Statement_partial
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Depicts Client to handle custom calls to Commons Wikibase APIs
 */
@Singleton
class DepictsClient @Inject constructor(private val depictsInterface: DepictsInterface) {

    /**
     * Search for depictions using the search item
     * @return list of depicted items
     */
    fun searchForDepictions(query: String?, limit: Int, offset: Int): Single<List<DepictedItem>> {
        val language = Locale.getDefault().language
        return depictsInterface.searchForDepicts(query, "$limit", language, language, "$offset")
            .map { it.search.joinToString("|", transform = DepictSearchItem::id) }
            .mapToDepictions()
    }

    fun getEntities(ids: String): Single<Entities> {
        return depictsInterface.getEntities(ids)
    }

    fun toDepictions(sparqlResponse: Single<SparqlResponse>): Single<List<DepictedItem>> {
        return sparqlResponse.map {
            it.results.bindings.joinToString("|", transform = Binding::id)
        }.mapToDepictions()
    }

    /**
     * Fetches Entities from ids ex. "Q1233|Q546" and converts them into DepictedItem
     */
    @SuppressLint("CheckResult")
    private fun Single<String>.mapToDepictions() =
        flatMap(::getEntities)
            .map { entities ->
                entities.entities().values.map { entity ->
                    mapToDepictItem(entity)
                }
            }

    /**
     * Convert different entities into DepictedItem
     */
    private fun mapToDepictItem(entity: Entities.Entity): DepictedItem {
        return if (entity.descriptions().byLanguageOrFirstOrEmpty() == "") {
            val instanceOfIDs = entity[WikidataProperties.INSTANCE_OF]
                .toIds()
            if (instanceOfIDs.isNotEmpty()) {
                val entities: Entities = getEntities(instanceOfIDs[0]).blockingGet()
                val nameAsDescription = entities.entities().values.first().labels()
                    .byLanguageOrFirstOrEmpty()
                DepictedItem(
                    entity,
                    entity.labels().byLanguageOrFirstOrEmpty(),
                    nameAsDescription
                )
            } else {
                DepictedItem(
                    entity,
                    entity.labels().byLanguageOrFirstOrEmpty(),
                    ""
                )
            }
        } else {
            print("entity.description: " + entity.descriptions().byLanguageOrFirstOrEmpty())
            print("Entity labels: " + entity.labels())
            DepictedItem(
                entity,
                entity.labels().byLanguageOrFirstOrEmpty(),
                entity.descriptions().byLanguageOrFirstOrEmpty()
            )
        }
    }


    /**
     * Get App Ui language from sharedPreferences
     */
    fun getSavedLanguage(context: Context): String? {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString(Prefs.APP_UI_LANGUAGE, "en")
    }


    /**
     * Tries to get Entities.Label by default language from the map.
     * If that returns null, Tries to retrieve first element from the map.
     * If that still returns null, function returns "".
     */
    private fun Map<String, Entities.Label>.byLanguageOrFirstOrEmpty() =
        let {
            val language = getSavedLanguage(MainActivity.contextOfApplication)
            print("byLanguageOr: $language")
            it[language] ?: it.values.firstOrNull() }?.value() ?: ""

    /**
     * returns list of id ex. "Q2323" from Statement_partial
     */
    private fun List<Statement_partial>?.toIds(): List<String> {
        return this?.map { it.mainSnak.dataValue }
            ?.filterIsInstance<DataValue.EntityId>()
            ?.map { it.value.id }
            ?: emptyList()
    }
}