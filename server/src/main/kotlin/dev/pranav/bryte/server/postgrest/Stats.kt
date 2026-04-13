package dev.pranav.bryte.server.postgrest

import dev.pranav.bryte.model.stats.AnalyticsTimelineRow
import dev.pranav.bryte.model.stats.FSRSState
import dev.pranav.bryte.model.stats.TopicAnalytics
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class FSRSRepository(val postgrest: PostgrestQueryBuilder) {
    suspend fun getByQuestionId(questionId: String): FSRSState? {
        return postgrest.select {
            filter { eq("question_id", questionId) }
        }.decodeSingleOrNull()
    }

    suspend fun getByTopicId(topicId: String): List<FSRSState> {
        return postgrest.select {
            filter { eq("topic_id", topicId) }
        }.decodeList()
    }

    suspend fun getBySessionId(sessionId: String): List<FSRSState> {
        return postgrest.select {
            filter { eq("session_id", sessionId) }
        }.decodeList()
    }

    suspend fun getOverdue(sessionId: String, nowISO: String): List<FSRSState> {
        return postgrest.select {
            filter {
                eq("session_id", sessionId)
                lte("next_review", nowISO)
            }
        }.decodeList()
    }

    suspend fun upsert(state: FSRSState): FSRSState? {
        return postgrest.upsert(state) {
            select()
        }.decodeSingleOrNull()
    }
}

class TopicAnalyticsRepository(val postgrest: PostgrestQueryBuilder) {
    suspend fun getByTopicId(topicId: String): TopicAnalytics? {
        return postgrest.select {
            filter { eq("topic_id", topicId) }
        }.decodeSingleOrNull()
    }

    suspend fun getBySessionId(sessionId: String): List<TopicAnalytics> {
        return postgrest.select {
            filter { eq("session_id", sessionId) }
        }.decodeList()
    }

    suspend fun upsert(analytics: TopicAnalytics): TopicAnalytics? {
        return postgrest.upsert(analytics) {
            select()
        }.decodeSingleOrNull()
    }
}

class AnalyticsTimelineRepository(val postgrest: PostgrestQueryBuilder) {
    suspend fun insert(row: AnalyticsTimelineRow): AnalyticsTimelineRow? {
        return postgrest.insert(row) {
            select()
        }.decodeSingleOrNull()
    }

    suspend fun getBySession(sessionId: String): List<AnalyticsTimelineRow> {
        return postgrest.select {
            filter { eq("session_id", sessionId) }
        }.decodeList()
    }
}

class FSRSStateDelegate(private val supabase: SupabaseClient) : ReadOnlyProperty<Any?, FSRSRepository> {
    private val table by lazy { supabase.postgrest.from("fsrs_states") }
    override fun getValue(thisRef: Any?, property: KProperty<*>) = FSRSRepository(table)
}

class TopicAnalyticsDelegate(private val supabase: SupabaseClient) : ReadOnlyProperty<Any?, TopicAnalyticsRepository> {
    private val table by lazy { supabase.postgrest.from("topic_analytics") }
    override fun getValue(thisRef: Any?, property: KProperty<*>) = TopicAnalyticsRepository(table)
}

class AnalyticsTimelineDelegate(private val supabase: SupabaseClient) : ReadOnlyProperty<Any?, AnalyticsTimelineRepository> {
    private val table by lazy { supabase.postgrest.from("analytics_timeline") }
    override fun getValue(thisRef: Any?, property: KProperty<*>) = AnalyticsTimelineRepository(table)
}

fun SupabaseClient.fsrsStates() = FSRSStateDelegate(this)
fun SupabaseClient.topicAnalytics() = TopicAnalyticsDelegate(this)
fun SupabaseClient.analyticsTimeline() = AnalyticsTimelineDelegate(this)