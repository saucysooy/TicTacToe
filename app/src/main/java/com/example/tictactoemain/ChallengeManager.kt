package com.example.tictactoemain

import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavHostController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.UUID

class ChallengeManager(
    private val db: FirebaseFirestore,
    private val playerId: String,
    private val navController: NavHostController
) {

    data class Challenge(
        var challengeId: String = "",
        var challengerId: String = "",
        var challengedId: String = "",
        var status: String = "pending", // "pending", "accepted", "denied"
        var gameId: String = ""
    )

    private val _challenge = mutableStateOf<Challenge?>(null)
    val challenge: MutableState<Challenge?> = _challenge

    private var challengeListener: ListenerRegistration? = null

    fun sendChallenge(challengedId: String) {
        val challengeId = UUID.randomUUID().toString()
        val gameId = UUID.randomUUID().toString()
        val challenge = Challenge(
            challengeId = challengeId,
            challengerId = playerId,
            challengedId = challengedId,
            gameId = gameId
        )

        db.collection("challenges").document(challengeId)
            .set(challenge)
            .addOnSuccessListener {
                Log.d("ChallengeManager", "Challenge sent successfully!")
            }
            .addOnFailureListener { e ->
                Log.e("ChallengeManager", "Error sending challenge", e)
            }
    }

    fun listenForChallengeUpdates(gameManager: GameViewModel) {
        db.collection("challenges")
            .whereEqualTo("challengerId", playerId)  // only listen to challenges the player sent
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null && !snapshot.isEmpty) {
                    for (doc in snapshot.documents) {
                        val status = doc.getString("status")
                        val gameId = doc.getString("gameId")
                        if (status == "accepted" && gameId != null) {
                            navController.navigate("gameScreen/$gameId/$playerId")
                            gameManager.initializeGame(gameId, playerId)
                        }
                    }
                }
            }
    }

    fun listenToChallenges() {
        challengeListener = db.collection("challenges")
            .whereEqualTo("challengedId", playerId)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("ChallengeManager", "Listen to challenges failed.", error)
                    return@addSnapshotListener
                }

                for (doc in snapshots!!.documentChanges) {
                    val challenge = doc.document.toObject(Challenge::class.java)
                    _challenge.value = challenge
                }
            }
    }

    fun acceptChallenge(challengeId: String) {
        val challenge = challenge.value
        if (challenge != null) {
            db.collection("challenges").document(challengeId)
                .update("status", "accepted")
                .addOnSuccessListener {
                    Log.d("ChallengeManager", "Challenge accepted!")
                }
                .addOnFailureListener { e ->
                    Log.e("ChallengeManager", "Error accepting challenge", e)
                }
        }
    }

    fun denyChallenge(challengeId: String) {
        db.collection("challenges").document(challengeId)
            .update("status", "denied")
            .addOnSuccessListener {
                Log.d("ChallengeManager", "Challenge denied!")
                _challenge.value = null
            }
            .addOnFailureListener { e ->
                Log.e("ChallengeManager", "Error denying challenge", e)
            }
    }
    // remove the challenge listener when the screen is closed or when they are in game maybe?
    fun removeChallengeListener() {
        challengeListener?.remove()
    }
}