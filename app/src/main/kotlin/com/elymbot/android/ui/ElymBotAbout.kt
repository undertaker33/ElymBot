package com.elymbot.android.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.elymbot.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ProjectUrl = "https://github.com/undertaker33/ElymBot"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AboutPage(back: () -> Unit) {
    val context = LocalContext.current
    val version by produceState<String?>(null, context) {
        value = withContext(Dispatchers.IO) { context.installedVersion() }
    }
    val license = stringResource(R.string.elym_about_mit)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 65.dp).padding(start = 5.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundAction(R.drawable.elym_back, stringResource(R.string.elym_back), back)
            Text(
                stringResource(R.string.elym_about), color = Ink, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() },
            )
        }
        Column(
            Modifier.fillMaxWidth().then(LocalPageBodyMotion.current)
                .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)
                .padding(top = 36.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painterResource(R.mipmap.icon), contentDescription = null,
                modifier = Modifier.size(106.dp).clip(RoundedCornerShape(26.dp)),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.app_name), color = Ink, fontSize = 30.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                version?.let { AboutChip(stringResource(R.string.elym_about_version, it), Accent, AccentInk) }
                AboutChip(license, Soft, Ink)
            }
            Spacer(Modifier.height(40.dp))

            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                Section(stringResource(R.string.elym_about_links)) {
                    ListRow(
                        stringResource(R.string.elym_about_repository), R.drawable.elym_sliders,
                        { context.openProjectLink(ProjectUrl) }, subtitle = ProjectUrl.removePrefix("https://"),
                    )
                    HorizontalDivider(Modifier.padding(start = 57.dp, end = 12.dp), color = Line)
                    ListRow(
                        stringResource(R.string.elym_about_releases), R.drawable.elym_box,
                        { context.openProjectLink("$ProjectUrl/releases") },
                    )
                    HorizontalDivider(Modifier.padding(start = 57.dp, end = 12.dp), color = Line)
                    ListRow(
                        stringResource(R.string.elym_about_license), R.drawable.elym_info,
                        { context.openProjectLink("$ProjectUrl/blob/main/LICENSE") },
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutChip(label: String, background: Color, foreground: Color) {
    Text(
        label, color = foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(background, CircleShape).padding(horizontal = 13.dp, vertical = 5.dp),
    )
}

@Suppress("DEPRECATION")
private fun Context.installedVersion(): String? = try {
    packageManager.getPackageInfo(packageName, 0).versionName
} catch (_: PackageManager.NameNotFoundException) {
    null
}

private fun Context.openProjectLink(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.elym_about_browser_unavailable, Toast.LENGTH_SHORT).show()
    }
}
