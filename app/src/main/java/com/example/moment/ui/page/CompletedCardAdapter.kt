package com.example.moment.ui.page

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.moment.R
import com.example.moment.data.entity.CompletedTask
import com.example.moment.data.entity.TASK_TYPE_APP_USAGE

class CompletedCardAdapter(
    private val onItemClick: (CompletedTask) -> Unit
) : RecyclerView.Adapter<CompletedCardAdapter.CompletedViewHolder>() {

    private val items = mutableListOf<CompletedTask>()

    fun submitList(records: List<CompletedTask>) {
        items.clear()
        items.addAll(records)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompletedViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_completed_card, parent, false)
        return CompletedViewHolder(view)
    }

    override fun onBindViewHolder(holder: CompletedViewHolder, position: Int) {
        holder.bind(items[position], onItemClick)
    }

    override fun getItemCount(): Int = items.size

    class CompletedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvType: TextView = itemView.findViewById(R.id.tvType)
        private val tvReward: TextView = itemView.findViewById(R.id.tvReward)
        private val tvCreatedAt: TextView = itemView.findViewById(R.id.tvCreatedAt)
        private val tvCompletedAt: TextView = itemView.findViewById(R.id.tvCompletedAt)
        private val tvNotePreview: TextView = itemView.findViewById(R.id.tvNotePreview)
        private val tvSubmittedText: TextView = itemView.findViewById(R.id.tvSubmittedText)
        private val ivSubmittedThumbnail: ImageView = itemView.findViewById(R.id.ivSubmittedThumbnail)

        fun bind(record: CompletedTask, onItemClick: (CompletedTask) -> Unit) {
            val isAppTask = record.taskTypeSnapshot == TASK_TYPE_APP_USAGE
            val context = itemView.context
            tvTitle.text = record.taskTitleSnapshot
            tvType.text = context.getString(
                R.string.task_type_format,
                if (isAppTask) context.getString(R.string.task_type_app_usage) else context.getString(R.string.task_type_manual_submit)
            )
            tvReward.text = context.getString(R.string.reward_minutes_label_format, record.rewardGrantedMinutes)
            tvCreatedAt.text = context.getString(
                R.string.created_time_format,
                UiTimeFormatters.formatDateTime(record.taskCreatedAtSnapshot)
            )
            tvCompletedAt.text = context.getString(
                R.string.completed_time_format,
                UiTimeFormatters.formatDateTime(record.completedAt)
            )

            if (record.taskNoteSnapshot.isNullOrBlank()) {
                tvNotePreview.visibility = View.GONE
            } else {
                tvNotePreview.visibility = View.VISIBLE
                tvNotePreview.text = context.getString(
                    R.string.task_note_preview_format,
                    record.taskNoteSnapshot.take(40)
                )
            }

            if (isAppTask) {
                tvSubmittedText.visibility = View.GONE
            } else {
                val previewText = MarkdownContentHelper.buildPreviewText(
                    markdown = record.submittedMarkdown,
                    fallbackText = record.submittedText,
                    maxLen = 50
                )
                if (previewText.isNullOrBlank()) {
                    tvSubmittedText.visibility = View.GONE
                } else {
                    tvSubmittedText.visibility = View.VISIBLE
                    tvSubmittedText.text = context.getString(
                        R.string.submitted_text_preview_format,
                        previewText
                    )
                }
            }

            val thumbnailUri = MarkdownContentHelper.extractFirstImageUri(
                markdown = record.submittedMarkdown,
                fallbackImageUri = record.submittedImageUri
            )
            if (isAppTask || thumbnailUri.isNullOrBlank()) {
                ivSubmittedThumbnail.visibility = View.GONE
                ivSubmittedThumbnail.setImageDrawable(null)
            } else {
                ivSubmittedThumbnail.visibility = View.VISIBLE
                ivSubmittedThumbnail.setImageURI(android.net.Uri.parse(thumbnailUri))
            }

            itemView.setOnClickListener { onItemClick(record) }
        }
    }
}

