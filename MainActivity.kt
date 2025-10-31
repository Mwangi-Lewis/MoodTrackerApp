package com.example.moodtrackerapp

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.net.URLDecoder
import androidx.compose.runtime.setValue

enum class ThemeMode { Light, Dark, Custom }
class MainActivity : ComponentActivity() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If you ALWAYS want to see the login first:
        // FirebaseAuth.getInstance().signOut()

        setContent {
            // Persist selected theme across rotation/process death
            var themeMode by rememberSaveable { mutableStateOf(ThemeMode.Light) }

            // Use the app theme defined in AppTheme.kt
            MoodTrackerTheme(mode = themeMode) {
                val nav = rememberNavController()

                NavHost(navController = nav, startDestination = Dest.SignIn.route) {

                    // ───────── Sign In ─────────
                    composable(Dest.SignIn.route) {
                        SignInScreen(
                            onCreateAccountClick = { nav.navigate(Dest.CreateAccount.route) },
                            onSuccess = {
                                nav.navigate(Dest.Home.route) {
                                    popUpTo(Dest.SignIn.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    // ───────── Create Account ─────────
                    composable(Dest.CreateAccount.route) {
                        CreateAccountScreen(
                            onSignInClick = {
                                nav.navigate(Dest.SignIn.route) {
                                    popUpTo(Dest.CreateAccount.route) { inclusive = true }
                                }
                            },
                            onAccountCreated = {
                                nav.navigate(Dest.Home.route) {
                                    popUpTo(Dest.CreateAccount.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    // ───────── Home (keeps optional mood/conf query) ─────────
                    composable(
                        route = Dest.Home.route + "?mood={mood}&conf={conf}",
                        arguments = listOf(
                            navArgument("mood") { type = NavType.StringType; defaultValue = "" },
                            navArgument("conf") { type = NavType.FloatType; defaultValue = 0f }
                        )
                    ) { backStackEntry ->
                        val rawMood = backStackEntry.arguments?.getString("mood").orEmpty()
                        val mood = try { URLDecoder.decode(rawMood, "UTF-8") } catch (_: Exception) { rawMood }
                        val conf = backStackEntry.arguments?.getFloat("conf") ?: 0f

                        HomeScreen(
                            moodFromCamera = mood.takeIf { it.isNotBlank() },
                            confFromCamera = conf.takeIf { conf > 0f },
                            onCalendar = { nav.navigate(Dest.EntryRecords.route) },
                            onTips     = { nav.navigate(Dest.Tips.route) },
                            onProfile  = { nav.navigate(Dest.Profile.route) },
                            onLogMood  = { nav.navigate(Dest.LogMood.route) }
                        )
                    }

                    // ───────── Log Mood ─────────
                    composable(Dest.LogMood.route) {
                        MoodLogScreen(onBack = { nav.popBackStack() })
                    }

                    // ───────── Other screens ─────────
                    composable(Dest.EntryRecords.route) {
                        EntryRecordsScreen(onBack = { nav.popBackStack() })
                    }
                    composable(Dest.Tips.route) {
                        TipsScreen(onBack = { nav.popBackStack() })
                    }
                    composable(Dest.Profile.route) {
                        // Pass current theme + a setter so Profile can change it
                        ProfileScreen(
                            onBack = { nav.popBackStack() },
                            currentTheme = themeMode,
                            onChangeTheme = { newMode -> themeMode = newMode }
                        )
                    }
                }
            }
        }
    }
}