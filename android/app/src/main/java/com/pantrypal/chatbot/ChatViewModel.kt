package com.pantrypal.chatbot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrypal.chatbot.data.ChatMessage
import com.pantrypal.chatbot.data.ChatRepository
import com.pantrypal.chatbot.data.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
)

class ChatViewModel(
    private val repository: ChatRepository = ChatRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /**
     * Seeds message history restored via `rememberSaveable` (see ChatScreen)
     * into a freshly created ViewModel, e.g. after the process was killed
     * and recreated. A no-op once a conversation is already in progress, so
     * it's safe to call unconditionally on first composition.
     */
    fun restoreMessages(messages: List<ChatMessage>) {
        if (_uiState.value.messages.isNotEmpty() || messages.isEmpty()) return
        _uiState.update { it.copy(messages = messages) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isSending) return

        val historyForRequest = _uiState.value.messages
        val userMessage = ChatMessage(role = Role.USER, content = text)
        val assistantMessage = ChatMessage(role = Role.ASSISTANT, content = "")

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage + assistantMessage,
                inputText = "",
                isSending = true,
            )
        }

        viewModelScope.launch {
            try {
                repository.sendMessage(text, historyForRequest).collect { chunk ->
                    appendToMessage(assistantMessage.id, chunk)
                }
            } catch (e: Exception) {
                replaceMessage(
                    assistantMessage.id,
                    content = e.message ?: "Something went wrong. Please try again.",
                    isError = true,
                )
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    private fun appendToMessage(id: String, chunk: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { m ->
                    if (m.id == id) m.copy(content = m.content + chunk) else m
                },
            )
        }
    }

    private fun replaceMessage(id: String, content: String, isError: Boolean) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { m ->
                    if (m.id != id) return@map m
                    // Keep any partial answer that streamed in before the failure.
                    val newContent = if (m.content.isBlank()) content else "${m.content}\n\n$content"
                    m.copy(content = newContent, isError = isError)
                },
            )
        }
    }
}
