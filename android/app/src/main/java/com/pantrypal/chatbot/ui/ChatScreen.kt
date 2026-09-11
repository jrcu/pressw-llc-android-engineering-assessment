package com.pantrypal.chatbot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pantrypal.chatbot.ChatUiState
import com.pantrypal.chatbot.ChatViewModel
import com.pantrypal.chatbot.data.ChatMessage
import com.pantrypal.chatbot.data.Role
import com.pantrypal.chatbot.ui.theme.PantryPalTheme

/**
 * Flattens each [ChatMessage] into a `List<Any>` of Bundle-safe primitives so
 * the conversation survives process death, not just recomposition — a plain
 * [ChatViewModel] loses its state when the system kills the process, since a
 * fresh ViewModel is created on restart.
 */
private val ChatMessageListSaver: Saver<List<ChatMessage>, ArrayList<ArrayList<Any>>> = Saver(
    save = { messages ->
        ArrayList(messages.map { arrayListOf<Any>(it.id, it.role.name, it.content, it.isError) })
    },
    restore = { saved ->
        saved.map { fields ->
            ChatMessage(
                id = fields[0] as String,
                role = Role.valueOf(fields[1] as String),
                content = fields[2] as String,
                isError = fields[3] as Boolean,
            )
        }
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var savedMessages by rememberSaveable(stateSaver = ChatMessageListSaver) {
        mutableStateOf(emptyList())
    }
    // Runs once per (re)creation of this composable, including after the
    // process was killed and restored — restores history into what is then
    // a brand-new ViewModel. A no-op for a plain recomposition, since
    // restoreMessages() bails out once the ViewModel already has messages.
    LaunchedEffect(Unit) {
        viewModel.restoreMessages(savedMessages)
    }
    // Keeps the saveable snapshot in sync so it's ready to be written into
    // the next saved-instance-state Bundle.
    SideEffect {
        savedMessages = uiState.messages
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("PantryPal") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { innerPadding ->
        ChatContent(
            uiState = uiState,
            onInputChange = viewModel::onInputChange,
            onSend = viewModel::sendMessage,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@Composable
private fun ChatContent(
    uiState: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (uiState.messages.isEmpty()) {
            EmptyState(modifier = Modifier.weight(1f))
        } else {
            MessageList(
                messages = uiState.messages,
                isSending = uiState.isSending,
                modifier = Modifier.weight(1f),
            )
        }
        MessageInputBar(
            text = uiState.inputText,
            enabled = !uiState.isSending,
            onTextChange = onInputChange,
            onSend = onSend,
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Ask me what to cook, or what to do with what's in your fridge.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    isSending: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            val isPendingAssistantReply =
                message.role == Role.ASSISTANT && message.content.isBlank() && isSending
            MessageBubble(message = message, isThinking = isPendingAssistantReply)
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, isThinking: Boolean) {
    val isUser = message.role == Role.USER
    val backgroundColor = when {
        message.isError -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = backgroundColor,
            contentColor = contentColor,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            if (isThinking) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = contentColor,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Thinking…", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                }
            } else {
                Text(
                    text = remember(message.content) { message.content.toBoldAnnotatedString() },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

// The backend's model output uses markdown-style **bold** for emphasis (see
// recipe name formatting in the chatbot route's system prompt). Streaming
// means a trailing "**" may not have its closing pair yet, so unmatched
// markers are left as literal text until the rest of the chunk arrives.
private val boldMarkdownRegex = Regex("\\*\\*(.+?)\\*\\*")

private fun String.toBoldAnnotatedString(): AnnotatedString = buildAnnotatedString {
    var lastIndex = 0
    for (match in boldMarkdownRegex.findAll(this@toBoldAnnotatedString)) {
        append(substring(lastIndex, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(match.groupValues[1])
        }
        lastIndex = match.range.last + 1
    }
    append(substring(lastIndex))
}

@Composable
private fun MessageInputBar(
    text: String,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                enabled = enabled,
                placeholder = { Text("What do you want to cook?") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(
                    onSend = { if (text.isNotBlank()) onSend() },
                ),
                maxLines = 5,
            )
            Spacer(modifier = Modifier.size(8.dp))
            IconButton(
                onClick = onSend,
                enabled = enabled && text.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (enabled && text.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatContentPreview() {
    PantryPalTheme {
        ChatContent(
            uiState = ChatUiState(
                messages = listOf(
                    ChatMessage(role = Role.USER, content = "What can I make with chicken and rice?"),
                    ChatMessage(
                        role = Role.ASSISTANT,
                        content = "You could make a quick chicken and rice skillet with whatever vegetables you have on hand.",
                    ),
                ),
            ),
            onInputChange = {},
            onSend = {},
        )
    }
}
