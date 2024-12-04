package com.example.tictactoemain

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class GameViewModel : ViewModel(){

    // Using _ to separate between private and public read-able variables

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

            // Switch the player
            _currentPlayer.value = if (_currentPlayer.value == "X") "O" else "X"
        }
    }
}