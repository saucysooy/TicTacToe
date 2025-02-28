package com.example.tictactoemain

import android.content.res.Configuration
import android.icu.text.SymbolTable
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
import androidx.compose.runtime.DisposableEffect
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
                val gameViewModel: GameViewModel by viewModels()
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
                                LobbyScreen(playerId = playerId!!, challengeManager = challengeManager, navController = navController, gameViewModel = gameViewModel)
                            }
                        }

                        composable("gameScreen/{gameId}/{playerId}") { backStackEntry ->
                            val gameId = backStackEntry.arguments?.getString("gameId")
                            val playerId = backStackEntry.arguments?.getString("playerId")
                            if (gameId != null && playerId != null) {
                                GameScreen(boardState = gameViewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

data class Game(
    val boardState: Map<String, List<String>> = mapOf(
        "0" to listOf("", "", ""),
        "1" to listOf("", "", ""),
        "2" to listOf("", "", "")
    ),
    val currentPlayerTurn: String = "",
    val gameId: String = "",
    val gameStatus: String = "ongoing",
    val player1: String = "",
    val player2: String = "",
    val player1Name: String = "",
    val player2Name: String = "",
)

data class Player (
    val available: Boolean = true,
    val playerId: String = "",
    val playerName: String = "",
)

@Composable
fun GameScreen(boardState: GameViewModel) {
    val borderColor = MaterialTheme.colorScheme.onSurface
    val currentPlayer = if (boardState.currentPlayer.value == "X") boardState.playerName.value else boardState.opponentName.value
    val game = boardState.game.value

    Log.d("GameScreen", "GameScreen When they loaded in: $game")

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Current Player's Turn: $currentPlayer")
        Spacer(Modifier.height(16.dp))
        for (y in 0..2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                for (x in 0..2) {
                    val cellValue = game.boardState[y.toString()]?.get(x) ?: ""
                    Box(
                        Modifier
                            .size(100.dp)
                            .border(1.dp, borderColor)
                            .clickable {
                                Log.d("GameScreen", "Square clicked: ($y, $x)")
                                boardState.makeMove(y, x)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = cellValue)
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
                val player = Player( available = true ,playerId = playerId, playerName = playerName)
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
fun LobbyScreen(playerId: String, challengeManager: ChallengeManager, navController: NavHostController, gameViewModel: GameViewModel) {
    val db = Firebase.firestore
    val playerList = remember { MutableStateFlow<List<Player>>(emptyList()) }

    // retrieves the list of players from the database
    db.collection("players").addSnapshotListener { value, error ->
        if (error != null) {
            return@addSnapshotListener
        }

        if (value != null) {
            playerList.value = value.toObjects()
        }
    }

    val playerCount by playerList.asStateFlow().collectAsStateWithLifecycle()

    //challenge State
    val challenge = challengeManager.challenge.value
    var showDialog by remember { mutableStateOf(false) }

    //start listening for challenges when the screen is displayed
    challengeManager.listenForChallengeUpdates(gameViewModel)
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
            items(playerCount.filter { it.playerId != playerId }) { player ->
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
                        challengeManager.sendChallenge(player.playerId)
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
                    challengeManager.acceptChallenge(challenge.challengeId, gameViewModel)
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