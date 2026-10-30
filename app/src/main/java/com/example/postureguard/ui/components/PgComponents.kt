package com.example.postureguard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.postureguard.ui.theme.Hairline
import com.example.postureguard.ui.theme.Ink
import com.example.postureguard.ui.theme.InkSoft
import com.example.postureguard.ui.theme.Mist
import com.example.postureguard.ui.theme.SurfaceWhite
import com.example.postureguard.ui.theme.Teal
import com.example.postureguard.ui.theme.TealDeep
import com.example.postureguard.ui.theme.TealPale

@Composable
fun PageScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Mist)
            .statusBarsPadding()
            .navigationBarsPadding()
            .then(
                if (scrollable) {
                    Modifier.verticalScroll(rememberScrollState())
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (onBack != null) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = InkSoft,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("返回", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp,
                ),
                color = Ink,
            )
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
            }
        }
        content()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun PgCard(
    modifier: Modifier = Modifier,
    containerColor: Color = SurfaceWhite,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun PgPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Teal,
            contentColor = SurfaceWhite,
            disabledContainerColor = TealPale,
            disabledContentColor = TealDeep.copy(alpha = 0.5f),
        ),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
fun PgQuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TealPale,
            contentColor = TealDeep,
            disabledContainerColor = Mist,
            disabledContentColor = InkSoft.copy(alpha = 0.5f),
        ),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
fun PgSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = InkSoft,
    )
}

@Composable
fun PgSwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SurfaceWhite,
                checkedTrackColor = Teal,
                uncheckedThumbColor = SurfaceWhite,
                uncheckedTrackColor = Hairline,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun <T> PgChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(value) },
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) TealPale else Mist,
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = if (isSelected) TealDeep else InkSoft,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun PgPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(shape = RoundedCornerShape(999.dp), color = containerColor) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

@Composable
fun PgInfoRow(label: String, value: String, valueColor: Color = Ink) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = valueColor,
        )
    }
}

@Composable
fun PgMetricTile(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = InkSoft)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = accent,
            )
            if (unit.isNotEmpty()) {
                Text(
                    unit,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft,
                )
            }
        }
    }
}

@Composable
fun PgDivider() {
    HorizontalDivider(color = Hairline, thickness = 1.dp)
}

@Composable
fun PgNote(text: String, containerColor: Color = Mist, contentColor: Color = InkSoft) {
    Surface(shape = RoundedCornerShape(16.dp), color = containerColor) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
        )
    }
}

@Composable
fun PgBarRow(
    label: String,
    durationMs: Long,
    maxMs: Long,
    color: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = InkSoft)
            Text(
                com.example.postureguard.ui.util.formatCompactDuration(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = Ink,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Mist, RoundedCornerShape(999.dp)),
        ) {
            val fraction = if (maxMs <= 0) 0f else (durationMs.toFloat() / maxMs).coerceIn(0f, 1f)
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .background(color, RoundedCornerShape(999.dp)),
                )
            }
        }
    }
}

@Composable
fun PgEmptyState(title: String, detail: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun PgNotice(
    title: String,
    detail: String,
    containerColor: Color,
    contentColor: Color,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(shape = RoundedCornerShape(18.dp), color = containerColor) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
            )
            Text(detail, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.85f))
            if (actionLabel != null && onAction != null) {
                Text(
                    actionLabel,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable(onClick = onAction),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
            }
        }
    }
}
