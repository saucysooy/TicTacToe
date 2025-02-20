package com.example.tictactoemain

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {

    // Using _ to separate between private and public read-able variables

    enum class WinningCombination(val indices: List<Int>) {
        ROW_1(listOf(0, 1, 2)),
        ROW_2(listOf(3, 4, 5)),
        ROW_3(listOf(6, 7, 8)),
        COLUMN_1(listOf(0, 3, 6)),
        COLUMN_2(listOf(1, 4, 7)),
        COLUMN_3(listOf(2, 5, 8)),
        DIAGONAL_1(listOf(0, 4, 8)),
        DIAGONAL_2(listOf(2, 4, 6));
    }

    private val GAME_STATUS_ONGOING = "ongoing"
    private val GAME_STATUS_COMPLETED = "completed"
    private val GAME_STATUS_DRAW = "draw"

    private val _currentPlayer = mutableStateOf("X")
    val currentPlayer: State<String> = _currentPlayer

    private val _gameStatus = mutableStateOf(GAME_STATUS_ONGOING)
    val gameStatus: State<String> = _gameStatus

    private val _winner = mutableStateOf<String>("")
    val winner: State<String?> = _winner

    private val _isMyTurn = mutableStateOf(false)
    val isMyTurn: State<Boolean> = _isMyTurn

    private val _game = mutableStateOf(Game())
    val game: State<Game> = _game

    private val db = Firebase.firestore
    private var gameId: String = ""
    private var playerId: String = ""
    private var gameListener: ListenerRegistration? = null

    val playerName = mutableStateOf("")
    val opponentName = mutableStateOf("")

    private var isGameInitialized = false

    private fun updateGameState(game: Game) {

        _game.value = game

        _currentPlayer.value = game.currentPlayerTurn
        _gameStatus.value = game.gameStatus

        val mySymbol = if (playerId == game.player1) "X" else "O"
        val newIsMyTurn = game.currentPlayerTurn == mySymbol
        if (_isMyTurn.value != newIsMyTurn) {
            Log.d("GameViewModel", "updateGameState: isMyTurn changed from ${_isMyTurn.value} to $newIsMyTurn")
        }
        _isMyTurn.value = newIsMyTurn

        if (game.gameStatus == GAME_STATUS_COMPLETED) {
            _winner.value = if (game.currentPlayerTurn == "X") "O" else "X"
        } else if (game.gameStatus == GAME_STATUS_DRAW) {
            _winner.value = "draw"
        } else {
            _winner.value = ""
        }
    }

    fun makeMove(row: Int, col: Int) {
        if (!_isMyTurn.value || _gameStatus.value != GAME_STATUS_ONGOING) {
            Log.d("GameViewModel", "Not your turn!")
            return
        }

        val boardState = _game.value.boardState
        val rowKey = row.toString()
        val currentRow = boardState[rowKey]

        if (currentRow?.getOrNull(col) == "") {
            // Create a new list with the updated row
            val newBoardState = boardState.toMutableMap()
            val newRow = currentRow.toMutableList()
            newRow[col] = _currentPlayer.value
            newBoardState[rowKey] = newRow

            val nextPlayer = if (_currentPlayer.value == "X") "O" else "X"

            // Update the game state in Firestore
            val updatedGame = _game.value.copy(
                boardState = newBoardState,
                currentPlayerTurn = nextPlayer,
                gameId = gameId,
                player1 = game.value.player1,
                player2 = game.value.player2
            )
            _game.value = updatedGame
            updateGameState(updatedGame)
            Log.d("GameViewModel", "updated game state: $updatedGame")

            checkWin(updatedGame)

            viewModelScope.launch {// making this tied to the lifecycle
                // just in case the user closes down the app ensuring the process stops on the firestore side
                db.collection("games").document(gameId)
                    .set(updatedGame, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("GameViewModel", "Document successfully written!")
                    }
                    .addOnFailureListener { e ->
                        Log.w("GameViewModel", "Error writing document", e)
                    }
            }
        }
    }

    private fun checkWin(game: Game) {
        Log.d("GameViewModel", "checkWin: Called")
        Log.d("GameViewModel", "checkWin: gameId = $gameId")
        for (combination in WinningCombination.values()) {
            val firstCell = getCellValue(combination.indices[0], game)

            // Check if the first cell is not empty and all cells in the combination match the first cell
            if (firstCell.isNotEmpty() && combination.indices.all { index ->
                    getCellValue(index, game) == firstCell
                }) {
                // Update the game state in Firestore
                val updatedGame = game.copy(
                    gameStatus = GAME_STATUS_COMPLETED
                )
                viewModelScope.launch {
                    db.collection("games").document(gameId)
                        .set(updatedGame, SetOptions.merge())
                        .addOnSuccessListener {
                            Log.d("GameViewModel", "Document successfully written!")
                        }
                        .addOnFailureListener { e ->
                            Log.w("GameViewModel", "Error writing document", e)
                        }
                }
                return
            }
        }
        // Check if the game is a draw after checking for a win
        checkDraw(game)
    }

    private fun checkDraw(game: Game) {
        var isDraw = true
        for (row in game.boardState.values) {
            for (cell in row) {
                if (cell.isEmpty()) {
                    isDraw = false
                    break // No need to check further if an empty cell is found
                }
            }
            if (!isDraw) break // Exit the outer loop if an empty cell was found
        }

        if (isDraw) {
            // Update the game state in Firestore
            val updatedGame = game.copy(
                gameStatus = GAME_STATUS_DRAW
            )
            viewModelScope.launch {
                db.collection("games").document(gameId)
                    .set(updatedGame, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("GameViewModel", "Document successfully written!")
                    }
                    .addOnFailureListener { e ->
                        Log.w("GameViewModel", "Error writing document", e)
                    }
            }
        }
    }


    private fun getCellValue(index: Int, game: Game): String {
        return when (index) {
            0 -> game.boardState["0"]?.get(0) ?: ""
            1 -> game.boardState["0"]?.get(1) ?: ""
            2 -> game.boardState["0"]?.get(2) ?: ""
            3 -> game.boardState["1"]?.get(0) ?: ""
            4 -> game.boardState["1"]?.get(1) ?: ""
            5 -> game.boardState["1"]?.get(2) ?: ""
            6 -> game.boardState["2"]?.get(0) ?: ""
            7 -> game.boardState["2"]?.get(1) ?: ""
            8 -> game.boardState["2"]?.get(2) ?: ""
            else -> ""
        }
    }

    fun initializeGame(gameId: String, playerId: String) {

        if (isGameInitialized) return

        isGameInitialized = true

        Log.d("GameViewModel", "initializeGame: Called with gameId = $gameId, playerId = $playerId")
        this.gameId = gameId
        this.playerId = playerId

        db.collection("games").document(gameId).get().addOnSuccessListener {
            val game = it.toObject(Game::class.java)
            if (game != null) {
                _game.value = game

                playerName.value = game.player1Name
                opponentName.value = game.player2Name

            }
        }.addOnFailureListener {
            Log.d("GameViewModel", "initializeGame: Failed to get game")
        }
        startListeningForGameChanges()
    }

    fun createGame(challengeId: String, challengedId: String) {
        Log.d("GameViewModel", "createGame: Called")
        viewModelScope.launch {
            db.collection("challenges").document(challengeId).get().addOnSuccessListener {
                val challenge = it.toObject(ChallengeManager.Challenge::class.java)
                if (challenge != null) {
                    val gameId = challenge.gameId
                    val player1 = challenge.challengerId
                    val player2 = challengedId
                    var player1Name = ""
                    var player2Name = ""


                    db.collection("players").document(player1).get().addOnSuccessListener {
                        player1Name = it.getString("playerName") ?: ""
                        playerName.value = player1Name
                    }

                    db.collection("players").document(player2).get().addOnSuccessListener {
                        player2Name = it.getString("playerName") ?: ""
                        opponentName.value = player2Name
                    }

                    val newGame = Game(
                        currentPlayerTurn = "X",
                        gameId = gameId,
                        gameStatus = "ongoing",
                        player1 = player1,
                        player2 = player2,
                        player1Name = player1Name,
                        player2Name = player2Name

                    )
                    Log.d("GameViewModel", "createGame: gameId = $gameId")
                    db.collection("games").document(gameId).set(newGame)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.d("GameViewModel", "Document successfully written!")
                                initializeGame(gameId, challengedId)
                            } else {
                                Log.w("GameViewModel", "Error writing document")
                            }
                        }
                    Log.d("GameViewModel", "createGame: gameId = $gameId")
                }
            }
        }
    }

    private fun startListeningForGameChanges() {
        gameListener = db.collection("games").document(gameId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("GameViewModel", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val game = snapshot.toObject(Game::class.java)

                    Log.d("GameViewModel", "Received Firestore update: $game")

                    if (game != null) {
                        Log.d("GameViewModel", "Game data received: $game")
                        updateGameState(game)
                    }
                }
            }
    }
}