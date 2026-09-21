package eu.kanade.presentation.reader

import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.PopupWindow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

/** A non-touchable window keeps reader chrome out of both readers' PixelCopy snapshots. */
@Composable
internal fun ReaderStatusLayer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val host = LocalView.current
    val parentComposition = rememberCompositionContext()
    val currentContent = rememberUpdatedState(content)
    val view = remember(host, parentComposition) {
        ComposeView(host.context).apply {
            setParentCompositionContext(parentComposition)
            setViewTreeLifecycleOwner(host.findViewTreeLifecycleOwner())
            setViewTreeSavedStateRegistryOwner(host.findViewTreeSavedStateRegistryOwner())
            setContent { currentContent.value() }
        }
    }
    val popup = remember(view) {
        PopupWindow(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, false).apply {
            isTouchable = false
            isClippingEnabled = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            animationStyle = 0
            elevation = 0f
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
    }
    val previousBounds = remember(popup) { Rect() }
    DisposableEffect(popup) {
        onDispose {
            popup.dismiss()
            view.disposeComposition()
        }
    }
    Box(
        modifier.fillMaxSize().onGloballyPositioned { coordinates ->
            if (!host.isAttachedToWindow) return@onGloballyPositioned
            val bounds = coordinates.boundsInWindow()
            val screen = IntArray(2)
            val window = IntArray(2)
            host.getLocationOnScreen(screen)
            host.getLocationInWindow(window)
            val left = bounds.left.roundToInt() + screen[0] - window[0]
            val top = bounds.top.roundToInt() + screen[1] - window[1]
            val width = bounds.width.roundToInt()
            val height = bounds.height.roundToInt()
            if (width <= 0 || height <= 0) return@onGloballyPositioned
            val next = Rect(left, top, left + width, top + height)
            if (popup.isShowing) {
                if (next != previousBounds) popup.update(left, top, width, height)
            } else {
                popup.width = width
                popup.height = height
                popup.showAtLocation(host, Gravity.TOP or Gravity.START, left, top)
            }
            // The separate window must retain the reader's screenshot protection.
            val parentFlags = (host.rootView.layoutParams as? WindowManager.LayoutParams)?.flags ?: 0
            val popupRoot = view.rootView
            val params = popupRoot.layoutParams as? WindowManager.LayoutParams
            if (params != null) {
                val secureFlag = WindowManager.LayoutParams.FLAG_SECURE
                val flags = (params.flags and secureFlag.inv()) or (parentFlags and secureFlag)
                if (flags != params.flags) {
                    params.flags = flags
                    host.context.getSystemService(WindowManager::class.java).updateViewLayout(popupRoot, params)
                }
            }
            previousBounds.set(next)
        },
    )
}
