package com.example.postureguard.ui.util

import androidx.compose.ui.graphics.Color
import com.example.postureguard.posture.model.PostureState
import com.example.postureguard.ui.theme.Alert
import com.example.postureguard.ui.theme.AlertPale
import com.example.postureguard.ui.theme.InkSoft
import com.example.postureguard.ui.theme.Lavender
import com.example.postureguard.ui.theme.LavenderPale
import com.example.postureguard.ui.theme.Mist
import com.example.postureguard.ui.theme.Teal
import com.example.postureguard.ui.theme.TealPale
import com.example.postureguard.ui.theme.Warm
import com.example.postureguard.ui.theme.WarmPale

fun postureStateColor(state: PostureState): Color = when (state) {
    PostureState.NORMAL -> Teal
    PostureState.SUSPECTED_FORWARD_HEAD,
    PostureState.SUSPECTED_HEAD_DOWN,
    PostureState.SUSPECTED_HEAD_DOWN_FORWARD,
    PostureState.SUSPECTED_TORSO_LEAN,
    -> Warm
    PostureState.FORWARD_HEAD,
    PostureState.HEAD_DOWN,
    PostureState.HEAD_DOWN_FORWARD,
    PostureState.TORSO_LEAN,
    -> Alert
    PostureState.PAUSED -> Lavender
    else -> InkSoft
}

fun postureStateContainer(state: PostureState): Color = when (state) {
    PostureState.NORMAL -> TealPale
    PostureState.SUSPECTED_FORWARD_HEAD,
    PostureState.SUSPECTED_HEAD_DOWN,
    PostureState.SUSPECTED_HEAD_DOWN_FORWARD,
    PostureState.SUSPECTED_TORSO_LEAN,
    -> WarmPale
    PostureState.FORWARD_HEAD,
    PostureState.HEAD_DOWN,
    PostureState.HEAD_DOWN_FORWARD,
    PostureState.TORSO_LEAN,
    -> AlertPale
    PostureState.PAUSED -> LavenderPale
    else -> Mist
}
