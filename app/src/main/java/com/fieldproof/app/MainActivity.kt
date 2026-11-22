package com.fieldproof.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fieldproof.app.ui.FieldProofNav
import com.fieldproof.app.ui.theme.FieldProofTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FieldProofTheme {
                FieldProofNav()
            }
        }
    }
}
