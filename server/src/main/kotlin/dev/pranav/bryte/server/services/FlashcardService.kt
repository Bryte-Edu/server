package dev.pranav.bryte.server.services

import dev.pranav.bryte.FlashcardService
import dev.pranav.bryte.model.card.Flashcard
import dev.pranav.bryte.model.session.Session
import dev.pranav.bryte.server.ai.FlashcardGenerator
import dev.pranav.bryte.server.util.ext.documentChunks
import dev.pranav.bryte.server.util.ext.flashcards
import dev.pranav.bryte.server.util.ext.supabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


class FlashcardServiceImpl(val session: Session) : FlashcardService {

    private var generator: FlashcardGenerator
    private val flashcards by supabase.flashcards()

    init {
        val chunks by supabase.documentChunks()

        generator = FlashcardGenerator(
            session,
            chunks,
            flashcards
        )
    }


    override suspend fun flashcardsByTopic(topicId: String): List<Flashcard> {
        return flashcards.getByTopicId(topicId)
    }

    override fun flashcards(): Flow<Flashcard> = flow {
        if (!generator.exhausted) {
            generator.generateFlashcards().collect { emit(it) }
        }
    }
}
