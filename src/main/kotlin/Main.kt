fun main(args: Array<String>) {
    val scrollPane = JScrollPane(postsList)
    scrollPane.verticalScrollBar.addAdjustmentListener { e ->
        if (!e.valueIsAdjusting) {
            val extent = scrollPane.viewport.extentSize.height
            val maximum = scrollPane.verticalScrollBar.maximum
            val position = e.value + extent

            if (position >= maximum) {
                loadMorePosts()
            }
        }
    }

}