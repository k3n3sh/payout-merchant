package co.kp.merchantpayout.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.kp.merchantpayout.data.MerchantRepository
import co.kp.merchantpayout.data.PayoutRefresher
import co.kp.merchantpayout.domain.Currency
import co.kp.merchantpayout.domain.DomainError
import co.kp.merchantpayout.domain.MerchantActivity
import co.kp.merchantpayout.domain.MerchantSnapshot
import co.kp.merchantpayout.domain.Money
import co.kp.merchantpayout.domain.Outcome
import co.kp.merchantpayout.ui.theme.CheckoutTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── UI state ──────────────────────────────────────────────────────────────
sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Content(val snapshot: MerchantSnapshot) : HomeUiState()
    data class Error(val error: DomainError) : HomeUiState()
}

// was 2 view model before but /api/merchant give both so one is enough.
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MerchantRepository,
    private val refresher: PayoutRefresher,
) : ViewModel() {

    private val stateFlow = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = stateFlow.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            refresher.refreshTrigger.collect {
                refresh()
            }
        }
    }

    fun refresh() {
        stateFlow.value = HomeUiState.Loading
        viewModelScope.launch {
            stateFlow.value = when (val result = repository.getSnapshot()) {
                is Outcome.Ok -> HomeUiState.Content(result.value)
                is Outcome.Err -> HomeUiState.Error(result.error)
            }
        }
    }
}

// ─── Composing ──────────────────────────────────

@Composable
fun HomeScreen(
    onShowAllActivity: () -> Unit,
    onStartPayout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        actions = HomeActions(
            onShowAllActivity = onShowAllActivity,
            onStartPayout = onStartPayout,
            onRetry = { viewModel.refresh() },
        ),
        modifier = modifier,
    )
}

// group the callbacks so the composable signature stay short.
data class HomeActions(
    val onShowAllActivity: () -> Unit,
    val onStartPayout: () -> Unit,
    val onRetry: () -> Unit,
)

@Composable
private fun HomeContent(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CheckoutTheme.colors.canvas)
            .padding(16.dp),
    ) {
        if (state is HomeUiState.Loading)
            LoadingBlock()

        if (state is HomeUiState.Content)
            ContentBlock(
                snapshot = state.snapshot,
                onShowAllActivity = actions.onShowAllActivity,
                onStartPayout = actions.onStartPayout,
            )

        if (state is HomeUiState.Error)
            ErrorBlock(error = state.error, onRetry = actions.onRetry)

    }
}

@Composable
private fun LoadingBlock() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = CheckoutTheme.colors.brand)
    }
}

@Composable
private fun ContentBlock(
    snapshot: MerchantSnapshot,
    onShowAllActivity: () -> Unit,
    onStartPayout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Merchant home",
            style = CheckoutTheme.typography.title,
            color = CheckoutTheme.colors.textPrimary,
        )

        BalanceCard(snapshot)

        Button(
            onClick = onStartPayout,
            modifier = Modifier.fillMaxWidth(),
            shape = CheckoutTheme.shapes.button,
            colors = ButtonDefaults.buttonColors(
                containerColor = CheckoutTheme.colors.brand,
                contentColor = CheckoutTheme.colors.onBrand,
            ),
        ) {
            Text(text = "Send payout", style = CheckoutTheme.typography.subtitle)
        }

        HorizontalDivider(color = CheckoutTheme.colors.outline)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Recent activity",
                style = CheckoutTheme.typography.subtitle,
                color = CheckoutTheme.colors.textPrimary,
            )
            OutlinedButton(onClick = onShowAllActivity) {
                Text(text = "Show more", color = CheckoutTheme.colors.brand)
            }
        }

        if (snapshot.recentActivity.isEmpty()) {
            Text(
                text = "No activity yet.",
                style = CheckoutTheme.typography.body,
                color = CheckoutTheme.colors.textSecondary,
            )
        }

        for (activity in snapshot.recentActivity)
            ActivityRow(activity)

    }
}

@Composable
private fun BalanceCard(snapshot: MerchantSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CheckoutTheme.shapes.card,
        colors = CardDefaults.cardColors(
            containerColor = CheckoutTheme.colors.brandContainer,
            contentColor = CheckoutTheme.colors.textPrimary,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BalanceRow(
                label = "Available",
                amount = snapshot.availableBalance.format(),
                big = true,
            )
            BalanceRow(
                label = "Pending",
                amount = snapshot.pendingBalance.format(),
                big = false,
            )
        }
    }
}

@Composable
private fun BalanceRow(label: String, amount: String, big: Boolean) {
    val amountStyle = if (big)
        CheckoutTheme.typography.amount
     else
        CheckoutTheme.typography.subtitle

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = CheckoutTheme.typography.label,
            color = CheckoutTheme.colors.textSecondary,
        )
        Text(
            text = amount,
            style = amountStyle,
            color = CheckoutTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun ActivityRow(activity: MerchantActivity) {
    val amountColor: Color = if (activity.amount.isNegative)
        CheckoutTheme.colors.danger
    else
        CheckoutTheme.colors.textPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = activity.description,
            style = CheckoutTheme.typography.body,
            color = CheckoutTheme.colors.textPrimary,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = activity.amount.format(),
            style = CheckoutTheme.typography.subtitle,
            color = amountColor,
        )
    }
}

@Composable
private fun ErrorBlock(error: DomainError, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = errorToText(error),
            style = CheckoutTheme.typography.body,
            color = CheckoutTheme.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onRetry,
            shape = CheckoutTheme.shapes.button,
            colors = ButtonDefaults.buttonColors(
                containerColor = CheckoutTheme.colors.brand,
                contentColor = CheckoutTheme.colors.onBrand,
            ),
        ) {
            Text(text = "Retry")
        }
    }
}

private fun errorToText(error: DomainError): String {
    if (error is DomainError.Network)
        return "No connection. Check your internet and try again."

    if (error is DomainError.ServiceUnavailable)
        return "The service is temporarily unavailable. Try again shortly."

    if (error is DomainError.InsufficientFunds)
        return "Insufficient funds."

    if (error is DomainError.Http)
        return "Something went wrong (code " + error.statusCode + ")."

    return "Something unexpected happened."
}
