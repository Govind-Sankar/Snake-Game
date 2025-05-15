package com.gsr.snakegame

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gsr.snakegame.ui.theme.SnakeGameTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScoreStorage.prefs = getSharedPreferences("snake_game_prefs", Context.MODE_PRIVATE)
        enableEdgeToEdge()
        setContent {
            SnakeGameTheme (
                darkTheme = false,
                dynamicColor = false
            ) {
                MyGameApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MusicPlayer.stopMusic()
    }
}

data class State(val food: Pair<Int, Int>, val snake: List<Pair<Int, Int>>)

@OptIn(ExperimentalCoroutinesApi::class)
class Game(
    private val scope: CoroutineScope,
    private val onGameOver: (score: Int) -> Unit
)
{
    private var isRunning = true

    private val mutableState: MutableStateFlow<State> =
        MutableStateFlow(State(food = Pair(5, 5), snake = listOf(Pair(7, 7))))
    val state: Flow<State> = mutableState

    private val inputQueue = Channel<Pair<Int, Int>>(capacity = Channel.UNLIMITED)
    private var currentMove = Pair(1, 0)

    fun enqueueMove(direction: Pair<Int, Int>) {
        scope.launch {
            inputQueue.send(direction)
        }
    }

    init {
        scope.launch {
            var snakeLength = 4
            while (isRunning) {
                delay(150)

                // Apply the next valid direction from the queue
                while (!inputQueue.isEmpty) {
                    val nextMove = inputQueue.receive()
                    if (nextMove != Pair(-currentMove.first, -currentMove.second)) {
                        currentMove = nextMove
                        break
                    }
                }

                mutableState.update { current ->
                    val newPosition = current.snake.first().let { pos ->
                        Pair(
                            (pos.first + currentMove.first + BOARD_SIZE) % BOARD_SIZE,
                            (pos.second + currentMove.second + BOARD_SIZE) % BOARD_SIZE
                        )
                    }

                    if (current.snake.contains(newPosition)) {
                        val score = current.snake.size - 4
                        isRunning = false
                        onGameOver(score)
                        return@update current
                    }

                    val grew = newPosition == current.food
                    if (grew) snakeLength++
                    val newFood = if (grew) {
                        var pos: Pair<Int, Int>
                        do {
                            pos = Pair(Random.nextInt(BOARD_SIZE), Random.nextInt(BOARD_SIZE))
                        } while (current.snake.contains(pos))
                        pos
                    } else current.food

                    current.copy(
                        food = newFood,
                        snake = listOf(newPosition) + current.snake.take(snakeLength - 1)
                    )
                }
            }
        }
    }

    companion object {
        const val BOARD_SIZE = 16
    }
}

object ScoreStorage {
    private const val SCORE_KEY = "HighScore"
    lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("SnakeGamePrefs", Context.MODE_PRIVATE)
    }

    fun saveScore(score: Int) {
        val currentHigh = loadScore()
        if (score > currentHigh) {
            prefs.edit().putInt(SCORE_KEY, score).apply()
        }
    }

    fun loadScore(): Int {
        return prefs.getInt(SCORE_KEY, 0)
    }
}

@SuppressLint("ContextCastToActivity")
@Composable
fun MyGameApp() {
    val navController = rememberNavController()
    var lastScore by rememberSaveable { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    var game by remember { mutableStateOf<Game?>(null) }
    NavHost(navController = navController, startDestination = "title") {
        composable("title") {
            val activity = LocalContext.current as? Activity
            BackHandler {
                activity?.finish()
            }
            TitleScreen(onPlay = {
                game = Game(
                    scope = coroutineScope,
                    onGameOver = { score ->
                        ScoreStorage.saveScore(score)
                        lastScore = score
                        navController.navigate("gameover")
                    }
                )
                navController.navigate("play")
            })
        }
        composable("play") {
            BackHandler {
                navController.popBackStack("title", inclusive = false)
            }
            game?.let { Snake(it) }
        }
        composable("gameover") {
            BackHandler {
                navController.popBackStack("title", inclusive = false)
            }
            val highScore = remember { ScoreStorage.loadScore() }
            GameOverScreen(score = lastScore, highScore = highScore, navController)
        }
    }

}

@Composable
fun TitleScreen(onPlay: () -> Unit) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(MusicPlayer.isPlaying) }
    Column (
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xFFDDDDEA))
                .fillMaxWidth()
                .fillMaxHeight(.075f)
                .padding(top = 40.dp, start = 15.dp)
        ) {
            Row {
                IconButton(onClick = {
                    MusicPlayer.toggleMusic(context)
                    isPlaying = MusicPlayer.isPlaying
                }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.MusicNote else Icons.Default.MusicOff,
                        contentDescription = if (isPlaying) "Music On" else "Music Off",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth() .fillMaxHeight(0.9f)
                .background(Color(0xFFDDDDEA))
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Snake Game",
                fontSize = 40.sp,
                modifier = Modifier.padding(bottom = 32.dp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            //Spacer(modifier = Modifier.height(1.dp))
            Button(
                onClick = onPlay,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = Color.LightGray,
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary
                ),
            ) {
                Text("Play")
            }
        }
        Box(
            modifier = Modifier
                .background(Color(0xFFDDDDEA))
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(top = 10.dp, bottom = 10.dp, start = 120.dp)
        ){
            Text("A Game by Govind Sankar!", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
        }
    }
}

@Composable
fun GameOverScreen(score: Int, highScore: Int, navController: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFDDDDEA))
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Game Over!!", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Your Score: $score", fontSize = 24.sp)
        Text("High Score: $highScore", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { navController.navigate("title") }) {
            Text("Back to Title")
        }
    }
}

@Composable
fun Snake(game: Game) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFDDDDEA)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GameBoard(game)
        Buttons {
            game.enqueueMove(it)
//            game.move = it
        }
    }
}

@Composable
fun GameBoard(game: Game) {
    val state = game.state.collectAsState(initial = null)
    state.value?.let {
        Board(it)
    }
}

@Composable
fun Buttons(onDirectionChange: (Pair<Int, Int>) -> Unit) {
    val buttonSize = Modifier.size(84.dp)
    Column (
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp)
    ) {
        Button(
            onClick = { onDirectionChange(Pair(0, -1)) },
            shape = RectangleShape,
            modifier = buttonSize,
            colors = ButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = Color.White,
                disabledContainerColor = MaterialTheme.colorScheme.tertiary,
                disabledContentColor = Color.White,
            )
        ) {
            Icon(Icons.Default.KeyboardArrowUp, null)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row {
            Button(
                onClick = { onDirectionChange(Pair(-1, 0)) },
                shape = RectangleShape,
                modifier = buttonSize,
                colors = ButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.tertiary,
                    disabledContentColor = Color.White,
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null)
            }
            Spacer(modifier = Modifier.width(94.dp))
            Button(
                onClick = { onDirectionChange(Pair(1, 0)) },
                shape = RectangleShape,
                modifier = buttonSize,
                colors = ButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.tertiary,
                    disabledContentColor = Color.White,
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = { onDirectionChange(Pair(0, 1)) },
            shape = RectangleShape,
            modifier = buttonSize,
            colors = ButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = Color.White,
                disabledContainerColor = MaterialTheme.colorScheme.tertiary,
                disabledContentColor = Color.White,
            )
        ) {
            Icon(Icons.Default.KeyboardArrowDown, null)
        }
    }
}

@Composable
fun Board(state: State) {
    Column (
        modifier = Modifier.padding(vertical = 30.dp)
    ){
        Text(text = "\t\tScore: ${state.snake.size - 4}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.tertiary)
        BoxWithConstraints(
            Modifier
                .padding(16.dp)
                .background(Color(0xFFAB95C5))
        ) {
            val tileSize = maxWidth / Game.BOARD_SIZE
            Box(
                Modifier
                    .size(maxWidth)
                    .border(2.dp, Color.Black)
            )
            Box(
                Modifier
                    .offset(x = tileSize * state.food.first, y = tileSize * state.food.second)
                    .size(tileSize)
                    .background(Color.Black, CircleShape)
            )
            state.snake.forEach {
                Box(
                    Modifier
                        .offset(x = tileSize * it.first, y = tileSize * it.second)
                        .size(tileSize)
                        //.padding(1.dp)
                        .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SnakePreview() {
    SnakeGameTheme {
        MyGameApp()
    }
}