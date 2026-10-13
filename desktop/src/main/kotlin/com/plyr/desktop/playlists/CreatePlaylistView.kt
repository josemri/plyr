package com.plyr.desktop.playlists

import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.components.Titulo

@Composable
fun CreatePlaylistView(controller: PlaylistsController, onBack: () -> Unit) {
    val name = remember { mutableStateOf("") }
    val desc = remember { mutableStateOf("") }
    Titulo("Create playlist")

    OutlinedTextField(
        value = name.value,
        onValueChange = { name.value = it },
        label = { Text("Name") },
        modifier = Modifier.fillMaxWidth().testTag("create_playlist_name")
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = desc.value,
        onValueChange = { desc.value = it },
        label = { Text("Description") },
        modifier = Modifier.fillMaxWidth().testTag("create_playlist_desc")
    )
    Spacer(modifier = Modifier.height(8.dp))

    ActionButton(
        data = ActionButtonData(
            text = "Create",
            onClick = { controller.hideCreate() }
        ),
        modifier = Modifier.testTag("create_playlist_confirm")
    )
    Spacer(modifier = Modifier.height(0.dp))
    ActionButton(
        data = ActionButtonData(text = "Cancel", onClick = { controller.hideCreate() }),
        modifier = Modifier.testTag("create_playlist_cancel")
    )
}
