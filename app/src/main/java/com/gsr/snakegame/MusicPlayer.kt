package com.gsr.snakegame

import android.content.Context
import android.media.MediaPlayer

object MusicPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false

    var isPlaying: Boolean = false
        private set

    fun toggleMusic(context: Context) {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(context, R.raw.game_music)
            mediaPlayer?.isLooping = true
            isPrepared = true
        }

        if (isPlaying) {
            mediaPlayer?.pause()
        } else {
            if (isPrepared) {
                mediaPlayer?.start()
            }
        }
        isPlaying = !isPlaying
    }

    fun stopMusic() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        isPrepared = false
    }
}
