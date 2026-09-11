package com.pantrypal.chatbot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrypal.chatbot.data.ChatMessage
import com.pantrypal.chatbot.data.ChatRepository
import com.pantrypal.chatbot.data.Role
import kotlin.coroutines.cancellation.CancellationException
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

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                inputText = "",
            )
        }

        dispatch(userMessage, historyForRequest)
    }

    /**
     * Resends a message that previously failed, without re-adding it to the
     * message list — [userMessageId] must already be present. Its failed
     * caption ("Unable to send message. Try again") triggers this.
     */
    fun retryMessage(userMessageId: String) {
        if (_uiState.value.isSending) return

        val messages = _uiState.value.messages
        val index = messages.indexOfFirst { it.id == userMessageId }
        if (index == -1) return

        val userMessage = messages[index]
        val historyForRequest = messages.subList(0, index).toList()

        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { m ->
                    if (m.id == userMessageId) m.copy(failed = false) else m
                },
            )
        }

        dispatch(userMessage, historyForRequest)
    }

    private fun dispatch(userMessage: ChatMessage, historyForRequest: List<ChatMessage>) {
        val assistantMessage = ChatMessage(role = Role.ASSISTANT, content = "")

        _uiState.update {
            it.copy(
                messages = it.messages + assistantMessage,
                isSending = true,
            )
        }

        viewModelScope.launch {
            try {
                repository.sendMessage(userMessage.content, historyForRequest).collect { chunk ->
                    appendToMessage(assistantMessage.id, chunk)
                }
            } catch (e: CancellationException) {
                // Not a failure — e.g. the ViewModel was cleared mid-request.
                // Rethrow so structured concurrency can actually cancel us;
                // catching this below would swallow cancellation silently.
                throw e
            } catch (e: Exception) {
                markFailed(userMessage.id, assistantMessage.id)
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

    // Drops the (incomplete) assistant placeholder and flags the user's
    // message as failed so the UI can show a "Try again" caption under it.
    private fun markFailed(userMessageId: String, assistantMessageId: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages
                    .filterNot { it.id == assistantMessageId }
                    .map { m -> if (m.id == userMessageId) m.copy(failed = true) else m },
            )
        }
    }
}
