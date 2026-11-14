package com.parsfilo.contentapp.feature.auth.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parsfilo.contentapp.core.designsystem.component.AppButton
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.feature.auth.R

enum class AuthPurpose(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val valueRes: Int,
) {
    MESSAGES(R.string.auth_title, R.string.auth_description, R.string.auth_value_short),
    SUBSCRIPTION(
        R.string.auth_subscription_title,
        R.string.auth_subscription_description,
        R.string.auth_subscription_value_short,
    ),
}

@Composable
fun AuthRoute(
    viewModel: AuthViewModel = hiltViewModel(),
    purpose: AuthPurpose = AuthPurpose.MESSAGES,
    onBackClick: () -> Unit = {},
    onSignInSuccess: () -> Unit
) {
    val isSignedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    LaunchedEffect(isSignedIn) {
        if (isSignedIn) {
            onSignInSuccess()
        }
    }

    AuthScreen(
        isLoading = isLoading,
        errorMessage = errorMessage,
        onSignInClick = viewModel::signIn,
        onErrorDismissed = viewModel::clearError,
        purpose = purpose,
        onBackClick = onBackClick,
    )
}

@Composable
fun AuthScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onSignInClick: (Activity) -> Unit,
    onErrorDismissed: () -> Unit,
    purpose: AuthPurpose = AuthPurpose.MESSAGES,
    onBackClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val snackbarHostState = remember { SnackbarHostState() }
    val dimens = LocalDimens.current

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onErrorDismissed()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = dimens.space32, vertical = dimens.space24),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (purpose == AuthPurpose.SUBSCRIPTION) {
                TextButton(
                    onClick = onBackClick,
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.size(dimens.space8))
                    Text(stringResource(R.string.auth_subscription_back_to_plans))
                }
            }
            val headerSpacing = when (purpose) {
                AuthPurpose.SUBSCRIPTION -> dimens.space16
                AuthPurpose.MESSAGES -> dimens.space48 + dimens.space8
            }
            Spacer(modifier = Modifier.height(headerSpacing))

            Icon(
                imageVector = Icons.Filled.Email,
                contentDescription = null,
                modifier = Modifier.size(dimens.space48 + dimens.space24),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(dimens.space24))

            Text(
                text = stringResource(purpose.titleRes),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(dimens.space4))

            Text(
                text = stringResource(purpose.descriptionRes),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(dimens.space10))

            Text(
                text = stringResource(purpose.valueRes),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(dimens.space28))

            AppButton(
                text = stringResource(R.string.auth_sign_in_google),
                onClick = { activity?.let(onSignInClick) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && activity != null
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(dimens.iconSm),
                        strokeWidth = dimens.space2,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.size(dimens.space8))
                }
                Text(stringResource(R.string.auth_sign_in_google))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
