package koharia.epub

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

internal class SwipePageTurnGate(
    context: Context,
    private val onVerticalSwipe: (Boolean) -> Boolean = { false },
    private val blockPageTurns: () -> Boolean,
) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var blocked = false
    private var multiTouch = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            startX = event.x
            startY = event.y
            blocked = false
            multiTouch = false
        }
        if (event.pointerCount > 1) multiTouch = true
        if (!blocked && !multiTouch && blockPageTurns() && event.actionMasked == MotionEvent.ACTION_MOVE &&
            abs(event.x - startX) > slop
        ) {
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            try {
                super.dispatchTouchEvent(cancel)
            } finally {
                cancel.recycle()
            }
            blocked = true
        }
        if (blocked) return true
        val handled = super.dispatchTouchEvent(event)
        if (!multiTouch && event.actionMasked == MotionEvent.ACTION_UP &&
            abs(event.y - startY) > slop * 3 && abs(event.y - startY) > abs(event.x - startX) * 2
        ) {
            if (onVerticalSwipe(event.y < startY)) return true
        }
        return handled
    }
}
