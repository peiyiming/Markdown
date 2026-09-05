package com.nzf.markdown

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.File
import java.text.DateFormat
import java.util.Date

class DocumentAdapter(
    private val onOpen: (File) -> Unit,
    private val onManage: (File) -> Unit
) : RecyclerView.Adapter<DocumentAdapter.Holder>() {
    private val items = mutableListOf<File>()

    fun submit(files: List<File>) {
        items.clear()
        items.addAll(files)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_document, parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], onOpen, onManage)
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(file: File, onOpen: (File) -> Unit, onManage: (File) -> Unit) {
            itemView.findViewById<TextView>(R.id.tv_document_name).text = file.nameWithoutExtension
            itemView.findViewById<TextView>(R.id.tv_document_meta).text =
                DateFormat.getDateTimeInstance().format(Date(file.lastModified()))
            itemView.setOnClickListener { onOpen(file) }
            itemView.setOnLongClickListener {
                onManage(file)
                true
            }
        }
    }
}
