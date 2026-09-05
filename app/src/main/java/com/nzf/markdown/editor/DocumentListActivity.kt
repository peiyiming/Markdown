package com.nzf.markdown.editor

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.nzf.markdown.R
import com.nzf.markdown.document.DocumentStore
import java.io.File
import java.text.DateFormat

/** First functional workspace for the Markdown product MVP. */
class DocumentListActivity : AppCompatActivity() {

    private lateinit var store: DocumentStore
    private lateinit var documentContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        store = DocumentStore(this)
        setContentView(R.layout.activity_document_list)
        documentContainer = findViewById(R.id.document_container)
        findViewById<Button>(R.id.btn_new_document).setOnClickListener { createDocument() }
    }

    override fun onResume() {
        super.onResume()
        renderDocuments()
    }

    private fun createDocument() {
        val file = store.create("Untitled")
        openEditor(file)
    }

    private fun renderDocuments() {
        documentContainer.removeAllViews()
        val documents = store.list()
        if (documents.isEmpty()) {
            val empty = TextView(this).apply {
                text = "还没有文档\n从一个想法开始吧。"
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#6B6B66"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(24, 80, 24, 80)
            }
            documentContainer.addView(empty)
            return
        }
        documents.forEach { file -> documentContainer.addView(documentView(file)) }
    }

    private fun documentView(file: File): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(R.drawable.qp_document_item)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            isClickable = true
            isFocusable = true
            setOnClickListener { openEditor(file) }
        }

        val title = TextView(this).apply {
            text = file.nameWithoutExtension
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(Color.parseColor("#1C1C1A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val preview = TextView(this).apply {
            val content = store.read(file).trim().replace("\n", " ")
            text = if (content.isEmpty()) "空白文档" else content
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(Color.parseColor("#6B6B66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(8), 0, 0)
        }

        val meta = TextView(this).apply {
            text = "最近编辑 · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(file.lastModified())
            setTextColor(Color.parseColor("#A0A09A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(12), 0, 0)
        }

        card.addView(title)
        card.addView(preview)
        card.addView(meta)
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }
        return card
    }

    private fun openEditor(file: File) {
        startActivity(Intent(this, MarkdownEditorActivity::class.java).apply {
            putExtra(MarkdownEditorActivity.EXTRA_DOCUMENT_PATH, file.absolutePath)
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
