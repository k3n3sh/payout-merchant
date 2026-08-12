package co.kp.merchantpayout

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import co.kp.merchantpayout.ui.home.HomeScreen
import co.kp.merchantpayout.ui.payout.PayoutFlow
import co.kp.merchantpayout.ui.theme.CheckoutTheme
import co.kp.merchantpayout.ui.transactions.TransactionsSheet
import dagger.hilt.android.AndroidEntryPoint

// Why FragmentActivity? Biometric prompt via FragmentManager, ComponentActivity alone would crash at prompt.authenticate().

@AndroidEntryPoint
class HomeActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CheckoutTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    var showTransactions by rememberSaveable { mutableStateOf(false) }
                    var showPayout by rememberSaveable { mutableStateOf(false) }

                    if (showPayout) {
                        PayoutFlow(
                            onExit = { showPayout = false },
                            modifier = Modifier.padding(inner),
                        )
                    } else {
                        HomeScreen(
                            onShowAllActivity = { showTransactions = true },
                            onStartPayout = { showPayout = true },
                            modifier = Modifier.padding(inner),
                        )
                        if (showTransactions) {
                            TransactionsSheet(onDismiss = { showTransactions = false })
                        }
                    }
                }
            }
        }
    }
}
