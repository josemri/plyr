package com.plyr.desktop.playlists

import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.components.Titulo

@Composable
fun CreatePlaylistView(controller: PlaylistsController, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    Titulo(Translations.get("create_playlist"))

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(Translations.get("playlist_name")) },
        modifier = Modifier.fillMaxWidth().testTag("create_playlist_name")
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = desc,
        onValueChange = { desc = it },
        label = { Text(Translations.get("description_optional")) },
        modifier = Modifier.fillMaxWidth().testTag("create_playlist_desc")
    )
    Spacer(modifier = Modifier.height(8.dp))

    ActionButton(
        data = ActionButtonData(
            text = Translations.get("save"),
            enabled = name.isNotBlank(),
            onClick = { controller.createPlaylist(name, desc) },
        ),
        modifier = Modifier.testTag("create_playlist_confirm")
    )
    Spacer(modifier = Modifier.height(8.dp))
    ActionButton(
        data = ActionButtonData(text = Translations.get("cancel"), onClick = { controller.hideCreate() }),
        modifier = Modifier.testTag("create_playlist_cancel")
    )
}
