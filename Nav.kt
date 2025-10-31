package com.example.moodtrackerapp

sealed class Dest(val route: String) {
    data object SignIn : Dest("signIn")
    data object CreateAccount : Dest("createAccount")
    data object Home : Dest("home")
    data object EntryRecords : Dest("entryRecords")
    data object Tips : Dest("tips")
    data object Profile : Dest("profile")

    data object LogMood : Dest(route = "logMood")

}