package co.kp.merchantpayout.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.insertSeparators
import androidx.paging.map
import co.kp.merchantpayout.data.ActivityRepository
import co.kp.merchantpayout.data.PayoutRefresher
import co.kp.merchantpayout.domain.MerchantActivity
import co.kp.merchantpayout.ui.theme.CheckoutTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

// ─── Date label helper ────────────────────────────────────────────────────

// Instant is lifesaver here "Today", "Yesterday" or date
// well pass clock here for testing so no flake testing

class DateLabelProvider @Inject constructor(private val clock: Clock, private val zone: ZoneId) {

    fun headerFor(instant: Instant): String {
        val today = LocalDate.now(clock.withZone(zone))
        val date = instant.atZone(zone).toLocalDate()
        if (date == today)
            return "Today"

        if (date == today.minusDays(1))
            return "Yesterday"

        return date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()))
    }

    fun rowDateFor(instant: Instant): String {
        val date = instant.atZone(zone).toLocalDate()
        return date.format(DateTimeFormatter.ofPattern("dd MM yyyy", Locale.getDefault()))
    }
}

// ─── List item ────────────────────────────────────────────────────────────

// list mix header row and data row. sealed class so LazyColumn can pick which one to draw.
sealed class ActivityListItem {
    data class Header(val label: String) : ActivityListItem()
    data class Row(val activity: MerchantActivity) : ActivityListItem()
}

// ─── ViewModel ────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repository: ActivityRepository,
    val dateLabels: DateLabelProvider,
    refresher: PayoutRefresher,
) : ViewModel() {

    // flatMapLatest bit is a bit ugly but works — will clean up in follow up PR.
    // rebuild pager fresh from api when the shared counter change (payout happen).
    val items: Flow<PagingData<ActivityListItem>> = refresher.refreshTrigger
        .flatMapLatest { buildItems() }
        .cachedIn(viewModelScope)

    // take paging data from repo, wrap each MerchantActivity as a Row, then insert Header
    // rows whenever the day change (Today, Yesterday, or a date).
    private fun buildItems(): Flow<PagingData<ActivityListItem>> {
        return repository.pagedActivity().map { pagingData ->
            val withRows = pagingData.map { activity -> ActivityListItem.Row(activity) }
            withRows.insertSeparators { before, after ->
                if (after == null) {
                    null
                } else {
                    val afterLabel = dateLabels.headerFor(after.activity.occurredAt)
                    val beforeLabel: String? = if (before is ActivityListItem.Row)
                        dateLabels.headerFor(before.activity.occurredAt)
                    else
                        null

                    if (afterLabel != beforeLabel)
                        ActivityListItem.Header(afterLabel)
                     else
                        null

                }
            }
        }
    }
}

// ─── Composable ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    // skipPartiallyExpanded — sheet open full height. simpler for our list.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val items = viewModel.items.collectAsLazyPagingItems()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = CheckoutTheme.shapes.card,
        containerColor = CheckoutTheme.colors.surface,
        modifier = modifier,
    ) {
        TransactionsContent(items = items, dateLabels = viewModel.dateLabels)
    }
}

@Composable
private fun TransactionsContent(
    items: LazyPagingItems<ActivityListItem>,
    dateLabels: DateLabelProvider,
) {
    val refreshState = items.loadState.refresh
    val appendState = items.loadState.append

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "All activity",
            style = CheckoutTheme.typography.title,
            color = CheckoutTheme.colors.textPrimary,
        )

        // pick which block to show based on refresh state.
        if (refreshState is LoadState.Loading)
            InitialLoadingBlock()
        else if (refreshState is LoadState.Error)
            ErrorBlock(refreshState.error) { items.retry() }
        else if (items.itemCount == 0)
            EmptyBlock()
        else
            ActivityListBlock(items = items, appendState = appendState, dateLabels = dateLabels)

    }
}

@Composable
private fun InitialLoadingBlock() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = CheckoutTheme.colors.brand)
    }
}

@Composable
private fun EmptyBlock() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No activity to show yet.",
            style = CheckoutTheme.typography.body,
            color = CheckoutTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun ErrorBlock(error: Throwable, onRetry: () -> Unit) {
    val message: String = if (error is IOException)
        "No connection. Check your internet and try again."
    else
        "Couldn't load activity. Please try again."

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
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

@Composable
private fun ActivityListBlock(
    items: LazyPagingItems<ActivityListItem>,
    appendState: LoadState,
    dateLabels: DateLabelProvider,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        // give the append spinner some breathing room from the sheet bottom edge.
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // key each item by a stable id so scrolling and append dont re-compose everything.
        items(
            count = items.itemCount,
            key = { index ->
                when (val item = items.peek(index)) {
                    is ActivityListItem.Row -> "row:" + item.activity.id
                    is ActivityListItem.Header -> "header:" + item.label
                    else -> "placeholder:$index"
                }
            },
        ) { index ->
            val item = items[index]
            if (item is ActivityListItem.Header)
                HeaderRow(item.label)
            else if (item is ActivityListItem.Row)
                ActivityRow(item.activity, dateLabels)
        }

        // small spinner or error at the bottom when loading next page.
        if (appendState is LoadState.Loading) {
            item {
                AppendLoadingRow()
            }
        }
        if (appendState is LoadState.Error) {
            item {
                AppendErrorRow(onRetry = { items.retry() })
            }
        }
    }
}

@Composable
private fun HeaderRow(label: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Text(
            text = label,
            style = CheckoutTheme.typography.label,
            color = CheckoutTheme.colors.textSecondary,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        HorizontalDivider(color = CheckoutTheme.colors.outline)
    }
}

@Composable
private fun ActivityRow(activity: MerchantActivity, dateLabels: DateLabelProvider) {
    val amountColor: Color = if (activity.amount.isNegative)
        CheckoutTheme.colors.danger
    else
        CheckoutTheme.colors.textPrimary

    val rowDate = dateLabels.rowDateFor(activity.occurredAt)
    // small label showing kind (Payout, Deposit) — first letter uppercase.
    val kindLabel = activity.kind.name.lowercase().replaceFirstChar { it.uppercase() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CheckoutTheme.colors.surface)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.padding(end = 8.dp)) {
            Text(
                text = activity.description,
                style = CheckoutTheme.typography.subtitle,
                color = CheckoutTheme.colors.textPrimary,
            )
            Text(
                text = "$kindLabel  ·  $rowDate",
                style = CheckoutTheme.typography.label,
                color = CheckoutTheme.colors.textSecondary,
            )
        }
        Text(
            text = activity.amount.format(),
            style = CheckoutTheme.typography.subtitle,
            color = amountColor,
        )
    }
}

@Composable
private fun AppendLoadingRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // size() lock both width and height so the spinner draw round, not oval.
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = CheckoutTheme.colors.brand,
        )
    }
}

@Composable
private fun AppendErrorRow(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Couldn't load more.",
            style = CheckoutTheme.typography.body,
            color = CheckoutTheme.colors.textPrimary,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(start = 12.dp),
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