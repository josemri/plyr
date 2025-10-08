package com.plyr.ui

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.plyr.ui.components.*
import com.plyr.ui.theme.*

@Composable
fun HomeScreen(
    context: Context,
    onNavigateToScreen: (Screen) -> Unit
) {
    var backPressedTime by remember { mutableLongStateOf(0L) }
    var showExitMessage by remember { mutableStateOf(false) }

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
        Box(modifier = Modifier.verticalScroll(verticalScrollState)) {
            // Center all content (ASCII art, title, buttons) vertically and horizontally
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
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
                    Spacer(modifier = Modifier.height(32.dp))
                    // Centered menu options
                    val options = listOf(
                        MenuOption(Screen.SEARCH, "search"),
                        MenuOption(Screen.PLAYLISTS, "playlists"),
                        MenuOption(Screen.QUEUE, "queue"),
                        MenuOption(Screen.CONFIG, "settings")
                    )
                    // Restore previous layout: buttons below ASCII, left-aligned but centered vertically
                    Spacer(modifier = Modifier.height(50.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.8f), // keep buttons visually centered horizontally
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start // left-align buttons
                    ) {
                        options.forEach { option ->
                            PlyrMenuOption(
                                text = option.title,
                                onClick = { onNavigateToScreen(option.screen) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    if (showExitMessage) {
                        Spacer(modifier = Modifier.height(24.dp))
                        PlyrErrorText(
                            text = "Press back again to exit",
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}
