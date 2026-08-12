package co.kp.merchantpayout.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Color tokens ─────────────────────────────────────────────────────────

// keep the colors here by role (brand, danger) not by name (purple, red). if brand color
// change one day, we just swap one hex here and whole app pick it up.

data class CheckoutColors(
    val brand: Color,
    val onBrand: Color,
    val brandContainer: Color,
    val canvas: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val outline: Color,
    val danger: Color,
    val success: Color,
)

// TODO: get real Checkout brand token from design team. purple is placeholder.
val LightColors = CheckoutColors(
    brand = Color(0xFF7057FF),
    onBrand = Color(0xFFFFFFFF),
    brandContainer = Color(0xFFEEEAFF),
    canvas = Color(0xFFFAFAFC),
    surface = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF0B0B0F),
    textSecondary = Color(0xFF4A4A57),
    outline = Color(0xFFE3E3EA),
    danger = Color(0xFFF04438),
    success = Color(0xFF17B26A),
)

val DarkColors = CheckoutColors(
    brand = Color(0xFF9E8CFF),
    onBrand = Color(0xFF0B0B0F),
    brandContainer = Color(0xFF2A2255),
    canvas = Color(0xFF0B0B0F),
    surface = Color(0xFF16161C),
    textPrimary = Color(0xFFFAFAFC),
    textSecondary = Color(0xFFA8A8B3),
    outline = Color(0xFF2A2A34),
    danger = Color(0xFFF97066),
    success = Color(0xFF3CCB7F),
)

// ─── Typography ───────────────────────────────────────────────────────────

// text style by role — title, body, amount. easier to change size or weight in one place.
data class CheckoutTypography(
    val title: TextStyle,
    val subtitle: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val amount: TextStyle,
)

val AppTypography = CheckoutTypography(
    title = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    subtitle = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    body = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    ),
    label = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
    amount = TextStyle(
        fontSize = 26.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
    ),
)

// ─── Shapes ───────────────────────────────────────────────────────────────

// corner radius by role. card is bigger, button smaller. same rule — change once, apply everywhere.
data class CheckoutShapes(
    val card: Shape,
    val button: Shape,
    val input: Shape,
)

val AppShapes = CheckoutShapes(
    card = RoundedCornerShape(16.dp),
    button = RoundedCornerShape(12.dp),
    input = RoundedCornerShape(12.dp),
)

// ─── Composition local + accessor ─────────────────────────────────────────

// composition local mean every composable inside CheckoutTheme can read current color
// without we passing it as parameter to every function.
val LocalCheckoutColors = staticCompositionLocalOf<CheckoutColors> {
    error("CheckoutColors not set. did you forget to wrap content in CheckoutTheme?")
}

val LocalCheckoutTypography = staticCompositionLocalOf<CheckoutTypography> {
    error("CheckoutTypography not set.")
}

val LocalCheckoutShapes = staticCompositionLocalOf<CheckoutShapes> {
    error("CheckoutShapes not set.")
}

// this is how feature code read the theme. write CheckoutTheme.colors.brand or
// CheckoutTheme.typography.title in any composable.
object CheckoutTheme {

    val colors: CheckoutColors
        @Composable
        get() = LocalCheckoutColors.current

    val typography: CheckoutTypography
        @Composable
        get() = LocalCheckoutTypography.current

    val shapes: CheckoutShapes
        @Composable
        get() = LocalCheckoutShapes.current
}

// ─── The theme wrapper composable ─────────────────────────────────────────

// wrap MaterialTheme too so Material component like Button, Card still get color from
// somewhere. we map our token onto material's slot.
@Composable
fun CheckoutTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors: CheckoutColors
    if (dark) {
        colors = DarkColors
    } else {
        colors = LightColors
    }

    val materialScheme = buildMaterialScheme(colors, dark)

    CompositionLocalProvider(
        LocalCheckoutColors provides colors,
        LocalCheckoutTypography provides AppTypography,
        LocalCheckoutShapes provides AppShapes,
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            content = content,
        )
    }
}

// build material 3 color scheme from our token. only set the role we actually use, rest
// stay as material default.
private fun buildMaterialScheme(colors: CheckoutColors, dark: Boolean) =
    if (dark) {
        darkColorScheme(
            primary = colors.brand,
            onPrimary = colors.onBrand,
            primaryContainer = colors.brandContainer,
            background = colors.canvas,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            outline = colors.outline,
            error = colors.danger,
            onError = colors.onBrand,
        )
    } else {
        lightColorScheme(
            primary = colors.brand,
            onPrimary = colors.onBrand,
            primaryContainer = colors.brandContainer,
            background = colors.canvas,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            outline = colors.outline,
            error = colors.danger,
            onError = colors.onBrand,
        )
    }