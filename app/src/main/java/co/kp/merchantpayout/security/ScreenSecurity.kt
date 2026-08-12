package co.kp.merchantpayout.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import co.kp.merchantpayout.ui.theme.CheckoutTheme
import kotlinx.coroutines.delay

// ─── FLAG_SECURE + API 34 callback ────────────────────────────────────

// call this at the top of each payout screen. it turn FLAG_SECURE on while the screen
// is visible, and clear it when user leave. also register a screenshot callback (API 34+)
// as a safety net — show a Toast if user try to screenshot.
@Composable
fun ScreenSecurityEffect() {
    val context = LocalContext.current
    val activity = remember(context) { findActivity(context) }
    if (activity == null)
        return


    // FLAG_SECURE — the primary protection. Android refuse to render pixels for
    // screenshot, screen recording, cast.
    DisposableEffect(activity) {
        val window = activity.window
        if (window != null)
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        onDispose {
            val w = activity.window
            if (w != null)
                w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // API 34+ callback. FLAG_SECURE normally block the capture so this dont fire,
    // but if flag ever get stripped it show a Toast.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        DisposableEffect(activity) {
            val callback = object : Activity.ScreenCaptureCallback {
                override fun onScreenCaptured() {
                    Toast.makeText(
                        context,
                        "Screenshots are disabled here to keep your financial details private.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            val executor = ContextCompat.getMainExecutor(context)
            var registered = false
            try {
                activity.registerScreenCaptureCallback(executor, callback)
                registered = true
            } catch (t: Throwable) {
                // huhh jail breaked ? permission stripped ? ROM refuse? FLAG_SECURE is still on so we still safe.
            }
            onDispose {
                if (registered) {
                    try {
                        activity.unregisterScreenCaptureCallback(callback)
                    } catch (t: Throwable) {
                        // ignore
                    }
                }
            }
        }
    }
}

// ───  2-second warning banner ──────────────────────────────────────

// small info banner at the top of payout screens. fade out after 2 seconds so it dont
// take space forever.
@Composable
fun PayoutSecurityBanner(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(2000)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(CheckoutTheme.colors.brandContainer)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = "Financial details screen, screenshots not allowed",
                style = CheckoutTheme.typography.label,
                color = CheckoutTheme.colors.textPrimary,
            )
        }
    }
}

// walk the Context chain to find the Activity.
private fun findActivity(context: Context): Activity? {
    var current: Context = context
    while (true) {
        if (current is Activity)
            return current

        if (current is ContextWrapper)
            current = current.baseContext
        else
            return null
    }
}