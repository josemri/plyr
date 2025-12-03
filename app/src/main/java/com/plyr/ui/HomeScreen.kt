package com.plyr.ui

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.plyr.ui.components.*
import com.plyr.ui.theme.*
import com.plyr.utils.Config
import com.plyr.utils.Translations

// Nuevos imports para el icono de settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings

@Composable
fun HomeScreen(
    context: Context,
    onNavigateToScreen: (Screen) -> Unit
) {
    var backPressedTime by remember { mutableLongStateOf(0L) }
    var showExitMessage by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    // Obtener idioma actual para forzar recomposición
    var currentLanguage by remember { mutableStateOf(Config.getLanguage(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            val newLanguage = Config.getLanguage(context)
            if (newLanguage != currentLanguage) {
                currentLanguage = newLanguage
            }
        }
    }

    // List of ASCII arts (add your own manually)
    val asciiArts = listOf(
        """
                 __          
          ____  / /_  _______
         / __ \/ / / / / ___/
        / /_/ / / /_/ / /    
 ______/ .___/_/\__, /_/     
/_____/_/      /____/        
        """,
        """
             _            
       _ __ | |_   _ _ __ 
      | '_ \| | | | | '__|
      | |_) | | |_| | |   
 _____| .__/|_|\__, |_|   
|_____|_|      |___/      
        """,
        """
    ___       ___       ___       ___   
   /\  \     /\__\     /\__\     /\  \  
  /::\  \   /:/  /    |::L__L   /::\  \ 
 /::\:\__\ /:/__/     |:::\__\ /::\:\__\
 \/\::/  / \:\  \     /:;;/__/ \;:::/  /
    \/__/   \:\__\    \/__/     |:\/__/ 
             \/__/               \|__|  
        """,
        """
 ______   __         __  __     ______    
/\  == \ /\ \       /\ \_\ \   /\  == \   
\ \  _-/ \ \ \____  \ \____ \  \ \  __<   
 \ \_\    \ \_____\  \/\_____\  \ \_\ \_\ 
  \/_/     \/_____/   \/_____/   \/_/ /_/ 
        """,
        """
 ▄▄▄·▄▄▌   ▄· ▄▌▄▄▄  
▐█ ▄███•  ▐█▪██▌▀▄ █·
 ██▀·██▪  ▐█▌▐█▪▐▀▀▄ 
▐█▪·•▐█▌▐▌ ▐█▀·.▐█•█▌
.▀   .▀▀▀   ▀ • .▀  ▀
        """,
        """

█ ▄▄  █    ▀▄    ▄ █▄▄▄▄ 
█   █ █      █  █  █  ▄▀ 
█▀▀▀  █       ▀█   █▀▀▌  
█     ███▄    █    █  █  
 █        ▀ ▄▀       █   
  ▀                 ▀    
        """,
        """
      ____  __   _  _  ____ 
     (  _ \(  ) ( \/ )(  _ \
 ___  )___/ )(__ \  /  )   /
(___)(__)  (____)(__) (_)\_)
        """,
        """
▄▄▄▄  █ ▄   ▄  ▄▄▄ 
█   █ █ █   █ █    
█▄▄▄▀ █  ▀▀▀█ █    
█     █ ▄   █      
▀        ▀▀▀       
        """,
        """
               (             
               )\ (     (    
        `  )  ((_))\ )  )(   
        /(/(   _ (()/( (()\  
       ((_)_\ | | )(_)) ((_) 
       | '_ \)| || || || '_| 
 _____ | .__/ |_| \_, ||_|   
|_____||_|        |__/       
        """,
        """
             .__                 
      ______ |  | ___.__._______ 
      \____ \|  |<   |  |\_  __ \
      |  |_> >  |_\___  | |  | \/
 _____|   __/|____/ ____| |__|   
/_____/__|        \/             
        """,
        """
   _______   ___       ___  ___  _______   
  |   __ "\ |"  |     |"  \/"  |/"      \  
  (. |__) :)||  |      \   \  /|:        | 
  |:  ____/ |:  |       \\  \/ |_____/   ) 
  (|  /      \  |___    /   /   //      /  
 /|__/ \    ( \_|:  \  /   /   |:  __   \  
(_______)    \_______)|___/    |__|  \___) 
        """,
    )
    // Select a random ASCII art on each composition
    val selectedAscii = remember { asciiArts.random() }

    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime > 2000) {
            backPressedTime = currentTime
            showExitMessage = true
            CoroutineScope(Dispatchers.Main).launch {
                delay(2000)
                showExitMessage = false
            }
        } else {
            (context as? Activity)?.finish()
        }
    }

    PlyrScreenContainer {
        val verticalScrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScrollState),
            contentAlignment = Alignment.Center
        ) {
            // Icono de configuración en la esquina superior derecha (fuera del Column)
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigateToScreen(Screen.CONFIG)
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = Translations.get(context, "settings"),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ASCII art and title
                val horizontalScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(horizontalScrollState),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = selectedAscii,
                        fontFamily = FontFamily.Monospace,
                        style = PlyrTextStyles.commandTitle().copy(
                            fontSize = 12.sp,
                            lineHeight = 13.sp
                        ),
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(bottom = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(50.dp))

                // Crear botones usando ActionButtonData con formato < texto >
                val buttons = listOf(
                    ActionButtonData(
                        text = "< ${Translations.get(context, "home_search")} >",
                        color = MaterialTheme.colorScheme.primary, // antes Color(0xFFE74C3C)
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToScreen(Screen.SEARCH)
                        }
                    ),
                    ActionButtonData(
                        text = "< ${Translations.get(context, "home_playlists")} >",
                        color = MaterialTheme.colorScheme.primary, // antes Color(0xFF3498DB)
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToScreen(Screen.PLAYLISTS)
                        }
                    ),
                    ActionButtonData(
                        text = "< ${Translations.get(context, "home_queue")} >",
                        color = MaterialTheme.colorScheme.primary, // antes Color(0xFF2ECC71)
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToScreen(Screen.QUEUE)
                        }
                    ),
                    ActionButtonData(
                        text = "< ${Translations.get(context, "home_local")} >",
                        color = MaterialTheme.colorScheme.primary, // antes Color(0xFFF39C12)
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToScreen(Screen.LOCAL)
                        }
                    )
                )

                // Usar ActionButtonsGroup en modo vertical y centrado
                ActionButtonsGroup(
                    buttons = buttons,
                    isHorizontal = false,
                    spacing = 12.dp,
                    modifier = Modifier
                        .wrapContentWidth()
                        .padding(bottom = 16.dp)
                )

                if (showExitMessage) {
                    Spacer(modifier = Modifier.height(24.dp))
                    PlyrErrorText(
                        text = Translations.get(context, "exit_message"),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}
