package com.itantra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itantra.app.core.model.Language
import com.itantra.app.tts.TtsManager
import com.itantra.app.ui.theme.ITantraTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ITantraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TtsTestScreen()
                }
            }
        }
    }
}

@Composable
fun TtsTestScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ttsManager = remember { TtsManager(context) }
    val state by ttsManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    remember {
        ttsManager.initialize()
        true
    }

    val testPhrases = listOf(
        Language.ENGLISH to "Hello, this is a test of the text to speech engine.",
        Language.HINDI to "नमस्ते, यह एक परीक्षण है।",
        Language.MALAYALAM to "ഹലോ, ഇതൊരു പരീക്ഷണമാണ്.",
        Language.BENGALI to "নমস্কার, এটি একটি পরীক্ষা।",
        Language.KANNADA to "ನಮಸ್ಕಾರ, ಇದು ಒಂದು ಪರೀಕ್ಷೆ.",
        Language.MARATHI to "नमस्कार, ही एक चाचणी आहे.",
        Language.TAMIL to "வணக்கம், இது ஒரு சோதனை.",
        Language.TELUGU to "నమస్కారం, ఇది ఒక పరీక్ష.",
        Language.GUJARATI to "નમસ્તે, આ એક પરીક્ષણ છે.",
        Language.ODIA to "ନମସ୍କାର, ଏହା ଏକ ପରୀକ୍ଷା।"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        item {
            Text(text = "TTS State: $state", modifier = Modifier.padding(bottom = 12.dp))
        }
        items(testPhrases) { (language, phrase) ->
            Button(
                onClick = {
                    scope.launch {
                        ttsManager.speak(phrase, language)
                    }
                },
                modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
            ) {
                Text("Speak ${language.name}")
            }
        }
    }
}
