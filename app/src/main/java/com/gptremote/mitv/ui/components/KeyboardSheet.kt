package com.gptremote.mitv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.ui.theme.MiOrange
import com.gptremote.mitv.ui.theme.RemoteSurface
import com.gptremote.mitv.ui.theme.TextPrimary
import com.gptremote.mitv.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardSheet(
    onSendText: (String) -> Unit,
    onButtonPress: (RemoteButton) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var liveTyping by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = RemoteSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TV Keyboard",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Live typing",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Switch(
                        checked = liveTyping,
                        onCheckedChange = { liveTyping = it }
                    )
                }
            }

            OutlinedTextField(
                value = draft,
                onValueChange = { updated ->
                    if (liveTyping) {
                        if (updated.length > draft.length && updated.startsWith(draft)) {
                            onSendText(updated.substring(draft.length))
                        } else if (updated.length < draft.length) {
                            repeat(draft.length - updated.length) {
                                onButtonPress(RemoteButton.BACKSPACE)
                            }
                        }
                    }
                    draft = updated
                },
                label = { Text("Type search query, URL, or password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (!liveTyping && draft.isNotEmpty()) {
                            onSendText(draft)
                            draft = ""
                        }
                        onButtonPress(RemoteButton.ENTER)
                    }
                ),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (!liveTyping && draft.isNotEmpty()) {
                                onSendText(draft)
                                draft = ""
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send Text",
                            tint = MiOrange
                        )
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { onButtonPress(RemoteButton.BACKSPACE) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Backspace")
                }
                FilledTonalButton(
                    onClick = { onSendText(" ") },
                    modifier = Modifier.weight(1.4f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Space")
                }
                FilledTonalButton(
                    onClick = { onButtonPress(RemoteButton.SEARCH) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search")
                }
                Button(
                    onClick = {
                        if (!liveTyping && draft.isNotEmpty()) {
                            onSendText(draft)
                            draft = ""
                        }
                        onButtonPress(RemoteButton.ENTER)
                    },
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MiOrange)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardReturn, contentDescription = "Enter")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Number Pad",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            val numRows = listOf(
                listOf(RemoteButton.DIGIT_1 to "1", RemoteButton.DIGIT_2 to "2", RemoteButton.DIGIT_3 to "3"),
                listOf(RemoteButton.DIGIT_4 to "4", RemoteButton.DIGIT_5 to "5", RemoteButton.DIGIT_6 to "6"),
                listOf(RemoteButton.DIGIT_7 to "7", RemoteButton.DIGIT_8 to "8", RemoteButton.DIGIT_9 to "9"),
                listOf(RemoteButton.ESCAPE to "ESC", RemoteButton.DIGIT_0 to "0", RemoteButton.ENTER to "GO")
            )
            numRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (btn, label) ->
                        FilledTonalButton(
                            onClick = { onButtonPress(btn) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(label, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
