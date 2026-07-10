package dev.sadr.atlas.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sadr.atlas.AppConfig
import dev.sadr.atlas.BuildConfig
import dev.sadr.atlas.R
import dev.sadr.atlas.core.CoreNativeManager
import dev.sadr.atlas.ui.theme.AccentBlue
import dev.sadr.atlas.ui.theme.AccentBlueDeep
import dev.sadr.atlas.ui.theme.V2Theme
import dev.sadr.atlas.util.Utils

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            V2Theme {
                AboutScreen(
                    version = "v${BuildConfig.VERSION_NAME}",
                    coreVersion = CoreNativeManager.getLibVersion(),
                    appId = BuildConfig.APPLICATION_ID,
                    onBack = { finish() },
                    onUrlClick = { Utils.openUri(this, it) }
                )
            }
        }
    }
}

@Composable
fun AboutScreen(
    version: String,
    coreVersion: String,
    appId: String,
    onBack: () -> Unit,
    onUrlClick: (String) -> Unit
) {
    AtlasSubScreen(
        title = stringResource(R.string.title_about),
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(AccentBlue, AccentBlueDeep))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = version,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
                SectionLabel(text = "Info")
                SectionCard {
                    SettingRow(
                        icon = Icons.Rounded.Shield,
                        title = "Core version",
                        subtitle = coreVersion
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.Rounded.Fingerprint,
                        title = "Application ID",
                        subtitle = appId
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                SectionLabel(text = "Links")
                SectionCard {
                    SettingRow(
                        icon = Icons.Rounded.Code,
                        title = stringResource(R.string.title_source_code),
                        onClick = { onUrlClick(AppConfig.APP_URL) }
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.Rounded.Policy,
                        title = stringResource(R.string.title_privacy_policy),
                        onClick = { onUrlClick(AppConfig.APP_PRIVACY_POLICY) }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
