package com.mistral.api


import com.mistral.api.models.ocr.FileChunk
import com.mistral.api.models.ocr.OcrRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@Serializable
data class DocumentAnnotation(
    val language: String?,
    val authors: List<String>?,
    val title: String?,
)

fun main() = runBlocking {
    val apiKey = "sk-xxx"
    MistralClient(apiKey = apiKey).use { client ->
        val models = client.models.listModels()
        println("Found ${models.data.size} models")
        println()

//        for (model in models.data) {
//            println("Model: ${model.id}")
//            println("  Created: ${model.created}")
//            println("  Owned by: ${model.owned_by}")
//            println("  Capabilities:")
//            model.capabilities?.let { caps ->
//                println("    Completion Chat: ${caps.completion_chat}")
//                println("    Completion FIM: ${caps.completion_fim}")
//                println("    Completion: ${caps.completion}")
//                println("    Embedding: ${caps.embedding}")
//                println("    Moderation: ${caps.moderation}")
//                println("    Function Calling: ${caps.function_calling}")
//                println("    Vision: ${caps.vision}")
//            }
//            println()
//        }

        // upload document.pdf to get a fileId first.
//        val fileItem = client.files.upload(
//            File("/Users/sandeeppurwar/Downloads/bryte/document.pdf"), "ocr"
//        )
//
//        println("Uploaded file: ${fileItem.id}, ${fileItem.filename}, $fileItem")


        val schemaJson = """
{
    "type": "object",
    "properties": {
        "language": { "type": "string", "description": "Detected language of the document" },
        "authors": { "type": "array", "items": { "type": "string" }, "description": "List of authors if detected" },
        "title": { "type": "string", "description": "Title of the document if detected" }
    },
    "required": ["language"]
}
        """.trimIndent()

        val request = OcrRequest(
            model = "mistral-ocr-latest",
            document = FileChunk(fileId = "9dfc251b-c1f4-4798-af7a-9a46f0766187"),
            includeImageBase64 = false,
//            documentAnnotationFormat = ResponseFormat(
//                json_schema = DocumentAnnotationSchema(
//                    name = "DocumentAnnotation",
//                    schema = Json.parseToJsonElement(schemaJson)
//                )
//            )
        )


        val response = client.ocr.recognize(request)


        println("Document Annotation: ${response.documentAnnotation}")
        println("OCR recognized ${response.pages.size} pages")
        println()

        response.pages.forEach { page ->
            println("Page ${page.index} (${page.dimensions?.width}x${page.dimensions?.height}):")
            println(page.markdown)
            println()
            page.images.forEach { image ->
                println("  Image ${image.id} at (${image.topLeftX},${image.topLeftY}) size ${image.imageBase64?.length}")
            }

            println("--------------------------------------------------")
            println()
        }

        // create a neat javafx app to show the images and text side by side.
        // with a scroll pane to scroll through the pages.


//        val req = ChatCompletionRequest(
//            model = models.data.firstOrNull()?.id ?: "mistral-small",
//            messages = listOf(ChatMessage("user", "Hello from Kotlin!"))
//        )
//        val resp = client.chat.createChatCompletion(req)
//        println(resp)
    }
}