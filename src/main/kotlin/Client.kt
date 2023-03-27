import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.swing.DefaultListModel
import javax.swing.JFrame
import javax.swing.JList
import javax.swing.SwingWorker

class RedditClientUI : JFrame("Reddit Client") {
    private val postsListModel = DefaultListModel<RedditPost>()
    private val postsList = JList(postsListModel)
    private val subredditComboBox = JComboBox(arrayOf("/r/aww", "/r/funny", "/r/gifs"))
    private val selectedPostPanel = JPanel()
    private val openLinkButton = JButton("Open Link")
    private val openCommentsButton = JButton("Open Comments")

    private val thumbnailScope = CoroutineScope(Dispatchers.IO)

    private val placeholderIcon = ImageIcon("placeholder.png")

    private val thumbnailCache = mutableMapOf<String, ImageIcon>()

    private fun loadThumbnail(url: String, onThumbnailLoaded: (ImageIcon?) -> Unit) {
        thumbnailScope.launch {
            val response = try {
                OkHttpClient().newCall(Request.Builder().url(url).build()).execute()
            } catch (e: IOException) {
                null
            }

            val image = response?.body?.byteStream()?.use { stream ->
                ImageIcon(ImageIO.read(stream))
            }

            thumbnailCache[url] = image
            withContext(Dispatchers.Swing) {
                onThumbnailLoaded(image)
            }
        }
    }
    fun getListCellRendererComponent(
        list: JList<out RedditPost>,
        value: RedditPost,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val panel = JPanel(BorderLayout())
        val thumbnailLabel = JLabel()

        panel.add(thumbnailLabel, BorderLayout.WEST)

        loadThumbnail(value.thumbnailUrl) { thumbnail ->
            thumbnailLabel.icon = thumbnail ?: placeholderIcon
            thumbnailLabel.repaint()
        }

        // Add the title label and other components to the panel

        return panel
    }
    private fun createUI(): JPanel {
        // create JList component
        val postList = JList<RedditPost>()
        postList.cellRenderer = PostCellRenderer()
        postList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        postList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1) {
                    val index = postList.locationToIndex(e.point)
                    val post = postList.model.getElementAt(index)
                    showPostPanel(post)
                }
            }
        })

        // create post panel
        val postPanel = JPanel()
        postPanel.isVisible = false
        postPanel.layout = BoxLayout(postPanel, BoxLayout.LINE_AXIS)
        postPanel.add(Box.createHorizontalGlue())
        postPanel.add(JButton("Open Link").apply {
            addActionListener {
                openBrowser(selectedPost!!.url)
            }
        })
        postPanel.add(JButton("Open Comments").apply {
            addActionListener {
                openBrowser(selectedPost!!.permalink)
            }
        })
        postPanel.add(Box.createHorizontalGlue())

        // create main panel
        val mainPanel = JPanel(BorderLayout())
        mainPanel.add(JScrollPane(postList), BorderLayout.CENTER)
        mainPanel.add(postPanel, BorderLayout.SOUTH)

        // load initial posts
        loadPosts()

        return mainPanel
    }
    private var selectedPost: RedditPost? = null

    private fun showPostPanel(post: RedditPost) {
        selectedPost = post
        postPanel.isVisible = true
    }
    private fun openBrowser(url: String) {
        Desktop.getDesktop().browse(URI(url))
    }



    init {
        // set up the posts list
        postsList.cellRenderer = PostListCellRenderer()
        val scrollPane = JScrollPane(postsList)
        add(scrollPane)

        // set up the subreddit combo box
        subredditComboBox.addActionListener {
            // load posts from the selected subreddit
            loadPosts(subredditComboBox.selectedItem as String)
        }
        add(subredditComboBox, BorderLayout.NORTH)

        // set up the selected post panel
        selectedPostPanel.layout = BoxLayout(selectedPostPanel, BoxLayout.LINE_AXIS)
        selectedPostPanel.add(openLinkButton)
        selectedPostPanel.add(openCommentsButton)
        selectedPostPanel.isVisible = false
        add(selectedPostPanel, BorderLayout.SOUTH)

        // set up the window
        pack()
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        isVisible = true
    }

    private fun loadPosts(subreddit: String) {
        object : SwingWorker<List<RedditPost>, Unit>() {
            override fun doInBackground(): List<RedditPost> {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://www.reddit.com$subreddit/hot.json")
                    .build()
                val response = client.newCall(request).execute()
                val json = response.body?.string()
                val redditPosts = Gson().fromJson(json, RedditPosts::class.java)
                return redditPosts.data.children.map { it.data }
            }

            override fun done() {
                postsListModel.clear()
                get().forEach {
                    postsListModel.addElement(it)
                }
            }
        }.execute()
    }

    private inner class PostListCellRenderer : ListCellRenderer<RedditPost> {
        private val panel = JPanel()
        private val titleLabel = JLabel()
        private val thumbnailLabel = JLabel()

        init {
            panel.layout = BoxLayout(panel, BoxLayout.LINE_AXIS)
            panel.add(thumbnailLabel)
            panel.add(titleLabel)
        }

        override fun getListCellRendererComponent(
            list: JList<out RedditPost>?,
            value: RedditPost?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            titleLabel.text = value?.title
            thumbnailLabel.icon = ImageIcon(value?.thumbnail ?: "placeholder.png")
            panel.background = if (isSelected) UIManager.getColor("List.selectionBackground") else UIManager.getColor("List.background")
            return panel
        }
    }

    data class RedditPosts(val data: RedditData)
    data class RedditData(val children: List<RedditPostWrapper>)
    data class RedditPostWrapper(val data: RedditPost)
    data class RedditPost(
        val title: String,
        val thumbnail: String?,
        val url: String,
        val permalink: String
    )
}


