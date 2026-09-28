package eu.kanade.tachiyomi.ui.player.controls.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize

/**
 * Lets pointer events that hit this layout also reach the siblings drawn underneath it.
 *
 * Compose stops hit testing at the topmost sibling that was hit, so an overlay with its own pointer
 * input hides everything below it. Sharing is decided per layout, so this goes on the overlay that
 * is the direct sibling of the layout that should keep receiving events.
 */
fun Modifier.sharePointerInputWithSiblings(): Modifier = this then SharePointerInputWithSiblingsElement

private data object SharePointerInputWithSiblingsElement :
    ModifierNodeElement<SharePointerInputWithSiblingsNode>() {
    override fun create() = SharePointerInputWithSiblingsNode()

    override fun update(node: SharePointerInputWithSiblingsNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "sharePointerInputWithSiblings"
    }
}

private class SharePointerInputWithSiblingsNode : Modifier.Node(), PointerInputModifierNode {
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) = Unit

    override fun onCancelPointerInput() = Unit

    override fun sharePointerInputWithSiblings() = true
}
