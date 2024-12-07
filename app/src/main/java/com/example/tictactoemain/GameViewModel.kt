package com.example.tictactoemain

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class GameViewModel : ViewModel(){

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

    private val _boardState = mutableStateListOf<MutableList<String>>(
        mutableListOf("", "", ""),
        mutableListOf("", "", ""),
        mutableListOf("", "", "")
    )
    val boardState: List<List<String>> = _boardState

    private val _currentPlayer = mutableStateOf("X")
    val currentPlayer: State<String> = _currentPlayer

    private val _gameStatus = mutableStateOf("ongoing")
    val gameStatus: State<String> = _gameStatus

    private val _winner = mutableStateOf<String>("")
    val winner: State<String?> = _winner

    fun makeMove(row: Int, col: Int) {
        if (_boardState[row][col] == "" && _gameStatus.value == "ongoing") {
            // Create a new list with the updated row
            val newRow = _boardState[row].toMutableList()
            newRow[col] = _currentPlayer.value
            _boardState[row] = newRow

            checkWin()

            // Switch the player if the game is still ongoing
            if (_gameStatus.value == "ongoing") {
                _currentPlayer.value = if (_currentPlayer.value == "X") "O" else "X"
            }
        }
    }

    private fun checkWin() {
        for (combination in WinningCombination.values()) {
            val firstCell = getCellValue(combination.indices[0])

            // Check if the first cell is not empty and all cells in the combination match the first cell
            if (firstCell.isNotEmpty() && combination.indices.all { index ->
                    getCellValue(index) == firstCell
                }) {
                _winner.value = firstCell
                _gameStatus.value = "completed"
                return
            }
        }
        // Check if the game is a draw after checking for a win
        checkDraw()
    }

    private fun checkDraw() {
        var isDraw = true
        for (row in _boardState) {
            for (cell in row) {
                if (cell.isEmpty()) {
                    isDraw = false
                    break // No need to check further if an empty cell is found
                }
            }
            if (!isDraw) break // Exit the outer loop if an empty cell was found
        }

        if (isDraw) {
            _gameStatus.value = "draw"
        }
    }

    private fun getCellValue(index: Int): String {
        return when (index) {
            0 -> _boardState[0][0]
            1 -> _boardState[0][1]
            2 -> _boardState[0][2]
            3 -> _boardState[1][0]
            4 -> _boardState[1][1]
            5 -> _boardState[1][2]
            6 -> _boardState[2][0]
            7 -> _boardState[2][1]
            8 -> _boardState[2][2]
            else -> ""
        }
    }
}