package com.teamnak.nakassist

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

object TextInjector {

    fun inject(service: AssistAccessibilityService, text: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val inputNode = findEditableNode(root)
        root.recycle()

        if (inputNode != null) {
            inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val clipboard = service.getSystemService(android.content.ClipboardManager::class.java)
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("reply", text))
            val success = inputNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            inputNode.recycle()
            return success
        }
        return false
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        node ?: return null
        if (node.isEditable && node.isFocused) return node
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val result = findEditableNode(node.getChild(i))
            if (result != null) return result
        }
        return null
    }
}
