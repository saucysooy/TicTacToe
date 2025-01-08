package com.example.tictactoemain

import android.content.ContentValues.TAG
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tictactoemain.ui.theme.TicTacToeMainTheme
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val boardState by viewModels<GameViewModel>()

        setContent {
            TicTacToeMainTheme {
                PlayerCreationScreen()
            }
        }
    }
}

data class Player (
    var available: Boolean = false,
    val id: String = "",
    var playerName: String = "",
)

@Composable
fun GameScreen(boardState: GameViewModel) {

    val borderColor = MaterialTheme.colorScheme.onSurface
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Current Player's Turn: Tom") /* placeholder, replace with actual player */
        Spacer(Modifier.height(16.dp))
        for (y in 0..2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                for (x in 0..2) {
                    Box(
                        Modifier
                            .size(100.dp)
                            .border(1.dp, borderColor)
                            .clickable {
                                boardState.makeMove(y, x)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(boardState.boardState[y][x])
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerCreationScreen(modifier: Modifier = Modifier){
    var playerName by remember { mutableStateOf("") }
    var playerNameError by remember { mutableStateOf("") }
    val db = Firebase.firestore


    Column (modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Tic Tac Toe", fontWeight = FontWeight.Bold, fontSize = 50.sp)
        Spacer(modifier = Modifier.height(75.dp))

        Text(text = "Player Name")
        Spacer(modifier = Modifier.height(25.dp))
        Box {
            TextField(value = playerName, onValueChange = { playerName = it }, placeholder = { Text("Enter player name") })
        }
        Text(text = playerNameError)
        Spacer(modifier = Modifier.height(25.dp))
        Button(onClick = {
            if (playerName.isEmpty()) {
                playerNameError = "Please enter a player name"
            }
            if (playerName.length > 15) {
                playerNameError = "Player name cannot be more than 15 characters"
            }
            if (playerName.isNotEmpty() && playerName.length <= 15) {
                Log.d(TAG, "Player name: $playerName")
                val player = hashMapOf(
                    "available" to true,
                    "playerID" to UUID.randomUUID().toString(),
                    "playerName" to playerName
                )
                db.collection("players").add(player)
                playerName = ""
                playerNameError = ""
            }
        }) {
            Text(text = "Connect")
        }
    }
}

@Composable
fun LobbyScreen() {
    val db = Firebase.firestore

    val playerList = remember { MutableStateFlow<List<Player>>(emptyList()) }

    // Retrieves the list of players from the database
    db.collection("players" ).addSnapshotListener{
        value, error ->
        if (error != null) {
            return@addSnapshotListener
        }

        if(value != null) {
            playerList.value = value.toObjects()
        }
    }

    val playerCount by playerList.asStateFlow().collectAsStateWithLifecycle()

    Column {
        Spacer(modifier = Modifier.height(75.dp))
        Text("Players in Lobby", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 30.sp, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(playerCount) { player -> /* placeholder */
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* Handle player selection */ }
                        .padding(8.dp)
                    ,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(player.playerName)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { /* Handle challenge */ }) {
                        Text("Challenge")
                    }
                }
            }
        }
    }
}


@Preview(
    showBackground = true,
    widthDp = 411,
    heightDp = 891,
)
@Preview(
    name = "Phone Preview",
    showBackground = true,
    widthDp = 411,
    heightDp = 891,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun GridPreview() {
    TicTacToeMainTheme {
        Surface {
            GameScreen(GameViewModel())
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 411,
    heightDp = 891,
)
@Preview(
    name = "Phone Preview",
    showBackground = true,
    widthDp = 411,
    heightDp = 891,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun PlayerCreationScreenPreview() {
    TicTacToeMainTheme {
        Surface {
            PlayerCreationScreen()
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 411,
    heightDp = 891,
)
@Preview(
    name = "Phone Preview",
    showBackground = true,
    widthDp = 411,
    heightDp = 891,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun LobbyScreenPreview() {
    TicTacToeMainTheme {
        Surface {
            LobbyScreen()
        }
    }
}