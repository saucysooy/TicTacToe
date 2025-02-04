package com.example.tictactoemain

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
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

    enum class GameStatus {
        ONGOING,
        COMPLETED,
        DRAW
    }

    private val _currentPlayer = mutableStateOf("X")
    val currentPlayer: State<String> = _currentPlayer

    private val _gameStatus = mutableStateOf(GameStatus.ONGOING)
    val gameStatus: State<GameStatus> = _gameStatus

    private val _winner = mutableStateOf<String>("")
    val winner: State<String?> = _winner

    private val db = Firebase.firestore
    private var gameId: String = ""
    private var playerId: String = ""

    private var gameListener: ListenerRegistration? = null

    private val _isMyTurn = mutableStateOf(true)
    val isMyTurn: State<Boolean> = _isMyTurn

    private val _game = mutableStateOf(Game())
    val game: State<Game> = _game

    private fun updateGameState(game: Game) {
        // Update current player
        _currentPlayer.value = game.currentPlayerTurn

        // Update game status
        _gameStatus.value = when (game.gameStatus) {
            "ongoing" -> GameStatus.ONGOING
            "completed" -> GameStatus.COMPLETED
            "draw" -> GameStatus.DRAW
            else -> GameStatus.ONGOING
        }

        // Update isMyTurn
        val mySymbol = if (playerId == game.player1) "X" else "O"
        _isMyTurn.value = game.currentPlayerTurn == mySymbol

        //update winner
        if (game.gameStatus == "completed") {
            _winner.value = if (game.currentPlayerTurn == "X") "O" else "X"
        } else if (game.gameStatus == "draw") {
            _winner.value = "draw"
        } else {
            _winner.value = ""
        }
    }

    fun makeMove(row: Int, col: Int) {
        Log.d("GameViewModel", "makeMove: called")
        Log.d("GameViewModel", "makeMove: gameId = $gameId")
        if (gameId.isEmpty()) {
            Log.e("GameViewModel", "makeMove: gameId is empty!")
            return
        }
        val boardState = _game.value.boardState
        val rowKey = row.toString()
        val currentRow = boardState[rowKey]
        Log.d("GameViewModel", "Is my turn: ${_isMyTurn.value}")
        if (_isMyTurn.value && currentRow?.getOrNull(col) == "" && _gameStatus.value == GameStatus.ONGOING) {
            Log.d("GameViewModel", "Making move at row $row, col $col")
            // Create a new list with the updated row
            val newBoardState = boardState.toMutableMap()
            val newRow = currentRow.toMutableList()
            newRow[col] = _currentPlayer.value
            newBoardState[rowKey] = newRow

            // Update the game state in Firestore
            val updatedGame = _game.value.copy(
                boardState = newBoardState,
                currentPlayerTurn = if (_currentPlayer.value == "X") "O" else "X",
                player1 = _game.value.player1,
                player2 = _game.value.player2,
                gameId = gameId
            )
            _game.value = updatedGame
            updateGameState(updatedGame)
            Log.d("GameViewModel", "updated game state: $updatedGame")

            checkWin(updatedGame)

            viewModelScope.launch {// making this tied to the lifecycle
                // just in case the user closes down the app ensuring the process stops on the firestore side
                db.collection("games").document(gameId)
                    .set(updatedGame)
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
                    gameStatus = "completed"
                )
                viewModelScope.launch {
                    db.collection("games").document(gameId)
                        .set(updatedGame)
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
        Log.d("GameViewModel", "checkDraw: Called")
        Log.d("GameViewModel", "checkDraw: gameId = $gameId")
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
                gameStatus = "draw"
            )
            viewModelScope.launch {
                db.collection("games").document(gameId)
                    .set(updatedGame)
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
        Log.d("GameViewModel", "initializeGame: Called with gameId = $gameId, playerId = $playerId")
        this.gameId = gameId
        this.playerId = playerId
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

                    val newGame = Game(
                        gameId = gameId,
                        player1 = player1,
                        player2 = player2,
                        currentPlayerTurn = "X"
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
                Log.d("GameViewModel", "Firestore listener triggered")
                if (e != null) {
                    Log.w("GameViewModel", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    Log.d("GameViewModel", "Firestore listener: Snapshot exists")
                    val game = snapshot.toObject(Game::class.java)
                    if (game != null) {
                        Log.d("GameViewModel", "Firestore listener: Game data received: $game")
                        updateGameState(game)
                    }
                } else {
                    Log.d("GameViewModel", "current data: null")
                }
            }
    }
}
