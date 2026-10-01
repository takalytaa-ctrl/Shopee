package com.example.shopeeautotool

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

class AutoClickService : AccessibilityService() {

    private enum class State { SCAN_AND_SELECT, RELOAD_PAGE, PLACE_ORDER, FINISHED }
    private var currentState = State.SCAN_AND_SELECT
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // Điều kiện mã cần lọc
    private val targetPercent = "30%"
    private val targetMaxDiscount = listOf("2 triệu", "2tr", "2000k")
    private val excludeDiscount = listOf("3 triệu", "3tr", "3000k")

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return

        when (currentState) {
            State.SCAN_AND_SELECT -> {
                val voucherSelected = scanAndSelectCorrectVoucher(rootNode)
                if (voucherSelected) {
                    clickNodeByText(rootNode, "Đồng ý") || clickNodeByText(rootNode, "Áp dụng")
                    currentState = State.PLACE_ORDER
                } else {
                    currentState = State.RELOAD_PAGE
                    reloadVoucherList()
                }
            }
            State.PLACE_ORDER -> {
                if (clickNodeByText(rootNode, "Đặt hàng")) {
                    currentState = State.FINISHED
                }
            }
            else -> {}
        }
    }

    private fun scanAndSelectCorrectVoucher(rootNode: AccessibilityNodeInfo): Boolean {
        val allNodes = ArrayList<AccessibilityNodeInfo>()
        findAllNodes(rootNode, allNodes)

        for (node in allNodes) {
            val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
            val hasPercent = text.contains(targetPercent, ignoreCase = true)
            val hasTargetMax = targetMaxDiscount.any { text.contains(it, ignoreCase = true) }
            val hasExcluded = excludeDiscount.any { text.contains(it, ignoreCase = true) }

            if (hasPercent && hasTargetMax && !hasExcluded) {
                if (clickParentOrSelf(node)) return true
            }
        }
        return false
    }

    private fun reloadVoucherList() {
        scope.launch {
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(300)
            rootInActiveWindow?.let { root ->
                clickNodeByText(root, "Shopee Voucher") || clickNodeByText(root, "Chọn hoặc nhập mã")
            }
            delay(400)
            currentState = State.SCAN_AND_SELECT
        }
    }

    private fun clickNodeByText(rootNode: AccessibilityNodeInfo, text: String): Boolean {
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (clickParentOrSelf(node)) return true
        }
        return false
    }

    private fun clickParentOrSelf(node: AccessibilityNodeInfo): Boolean {
        var temp: AccessibilityNodeInfo? = node
        while (temp != null) {
            if (temp.isClickable) {
                temp.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            temp = temp.parent
        }
        return false
    }

    private fun findAllNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        list.add(node)
        for (i in 0 until node.childCount) {
            findAllNodes(node.getChild(i), list)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
