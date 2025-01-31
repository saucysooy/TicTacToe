package com.example.tictactoemain

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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.State
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TicTacToeMainTheme {
                val navController = rememberNavController()
                Surface {
                    var playerId by remember { mutableStateOf<String?>(null) }
                    NavHost(navController = navController, startDestination = "playerCreationScreen") {
                        composable(
                            route = "playerCreationScreen"
                        ) {
                            PlayerCreationScreen(
                                onPlayerCreated = { newPlayerId ->
                                    playerId = newPlayerId
                                    navController.navigate("lobbyScreen")
                                }
                            )
                        }
                        composable(route = "lobbyScreen") {
                            if (playerId != null) {
                                val challengeManager = ChallengeManager(Firebase.firestore, playerId!!, navController)
                                LobbyScreen(playerId = playerId!!, challengeManager = challengeManager, navController = navController, gameManager = GameViewModel())
                            }
                        }

                        composable("gameScreen/{gameId}/{playerId}") { backStackEntry ->
                            val gameId = backStackEntry.arguments?.getString("gameId")
                            val playerId = backStackEntry.arguments?.getString("playerId")
                            if (gameId != null && playerId != null) {
                                GameScreen(boardState = GameViewModel())
                            }
                        }
                    }
                }
            }
        }
    }
}

data class Game(
    var boardState: Map<String, List<String>> = mapOf(
        "0" to listOf("", "", ""),
        "1" to listOf("", "", ""),
        "2" to listOf("", "", "")
    ),
    var currentPlayerTurn: String = "X",
    var gameId: String = "",
    var gameStatus: String = "ongoing",
    var player1: String = "",
    var player2: String = "",

)

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
        Text("Current Player's Turn: ${boardState.currentPlayer.value}")
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
                        Text(boardState.game.value.boardState[y.toString()]?.get(x) ?: "")
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerCreationScreen(onPlayerCreated: (String) -> Unit,modifier: Modifier = Modifier){
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
                val playerId = UUID.randomUUID().toString()
                val player = Player( available = true ,id = playerId, playerName = playerName)
                db.collection("players").document(playerId).set(player)
                    .addOnSuccessListener {
                        Log.d("PlayerCreationScreen", "Player created successfully!")
                        onPlayerCreated(playerId)
                    }
                    .addOnFailureListener { e ->
                        Log.w("PlayerCreationScreen", "Error creating player", e)
                    }
            }
        }) {
            Text(text = "Connect")
        }
    }
}

@Composable
fun LobbyScreen(playerId: String, challengeManager: ChallengeManager, navController: NavHostController, gameManager: GameViewModel) {
    val db = Firebase.firestore
    val playerList = remember { MutableStateFlow<List<Player>>(emptyList()) }

    // retrieves the list of players from the database
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

    //challenge State
    val challenge = challengeManager.challenge.value
    var showDialog by remember { mutableStateOf(false) }

    challengeManager.listenForChallengeUpdates(gameManager)
    //start listening for challenges when the screen is displayed
    challengeManager.listenToChallenges()

    //show the dialog when a challenge is received
    showDialog = challenge != null

    Column {
        Spacer(modifier = Modifier.height(75.dp))
        Text(
            "Players in Lobby",
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(playerCount.filter { it.id != playerId }) { player ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* handle player selection, wont really need to do anything here */ }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(player.playerName)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        // send a challenge to the selected player
                        challengeManager.sendChallenge(player.id)
                    }) {
                        Text("Challenge")
                    }
                }
            }
        }
    }

    //show the dialog if a challenge is pending
    if (showDialog && challenge != null) {
        ChallengeDialog(
            challengerId = challenge.challengerId,
            onAccept = {
                val challenge = challengeManager.challenge.value
                if (challenge != null) {
                    gameManager.createGame(challenge.gameId, playerId)
                    challengeManager.acceptChallenge(challenge.challengeId)
                    navController.navigate("gameScreen/${challenge.gameId}/${playerId}")
                }
                showDialog = false
            },
            onDeny = {
                challengeManager.denyChallenge(challenge.challengeId)
                showDialog = false
            },
            onDismiss = {
                showDialog = false
            },
        )
    }
}

@Composable
fun ChallengeDialog(
    challengerId: String,
    onAccept: () -> Unit,
    onDeny: () -> Unit,
    onDismiss: () -> Unit,
) {
    val playerName = remember { mutableStateOf("") }

    val db = Firebase.firestore
    val playerDoc = db.collection("players").document(challengerId)
    playerDoc.get().addOnSuccessListener {
        val player = it.getString("playerName")
        playerName.value = player ?: ""
    }



    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Challenge Received") },
        text = { Text(text = "Challenged by: ${playerName.value}") },
        confirmButton = {
            Button(onClick = onAccept) {
                Text(text = "Accept")
            }
        },
        dismissButton = {
            Button(onClick = onDeny) {
                Text(text = "Deny")
            }
        }
    )
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
            GameScreen( boardState = GameViewModel())
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
            PlayerCreationScreen( onPlayerCreated = {})
        }
    }
}
/*
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
            LobbyScreen(playerId = "", ChallengeManager(Firebase.firestore, "", GameViewModel()), navController = rememberNavController())
        }
    }
}
 */