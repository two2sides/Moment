package com.example.moment.ui.page

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.moment.R
import com.example.moment.data.entity.TASK_TYPE_APP_USAGE
import com.example.moment.data.entity.Task

class TaskCardAdapter(
    private val onTaskClick: (Task) -> Unit,
    private val onSubmitManualTask: (Task) -> Unit
) : RecyclerView.Adapter<TaskCardAdapter.TaskViewHolder>() {

    private val items = mutableListOf<Task>()

    fun submitList(tasks: List<Task>) {
        items.clear()
        items.addAll(tasks)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task_card, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(items[position], onTaskClick, onSubmitManualTask)
    }

    override fun getItemCount(): Int = items.size

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvType: TextView = itemView.findViewById(R.id.tvType)
        private val tvReward: TextView = itemView.findViewById(R.id.tvReward)
        private val tvDetail: TextView = itemView.findViewById(R.id.tvDetail)
        private val btnSubmitManual: Button = itemView.findViewById(R.id.btnSubmitManual)

        fun bind(
            task: Task,
            onTaskClick: (Task) -> Unit,
            onSubmitManualTask: (Task) -> Unit
        ) {
            val isAppTask = task.taskType == TASK_TYPE_APP_USAGE
            val context = itemView.context
            tvTitle.text = task.title
            tvType.text = context.getString(
                R.string.task_type_format,
                if (isAppTask) context.getString(R.string.task_type_app_usage) else context.getString(R.string.task_type_manual_submit)
            )
            tvReward.text = context.getString(R.string.reward_minutes_format, task.rewardMinutes)
            tvDetail.text = if (isAppTask) {
                context.getString(
                    R.string.task_detail_usage_format,
                    UiTimeFormatters.formatDurationMmSs(task.currentProgressSeconds),
                    UiTimeFormatters.formatDurationMmSs(task.targetSeconds),
                    task.targetPackage ?: "-"
                )
            } else {
                buildString {
                    if (!task.note.isNullOrBlank()) {
                        append(context.getString(R.string.task_note_preview_format, task.note.take(24)))
                        append("\n")
                    }
                    append(context.getString(R.string.task_created_at_format, UiTimeFormatters.formatDateTime(task.createdAt)))
                }
            }

            if (isAppTask) {
                btnSubmitManual.visibility = View.GONE
                btnSubmitManual.setOnClickListener(null)
            } else {
                btnSubmitManual.visibility = View.VISIBLE
                btnSubmitManual.text = context.getString(R.string.complete_and_submit)
                btnSubmitManual.setOnClickListener { onSubmitManualTask(task) }
            }

            itemView.setOnClickListener { onTaskClick(task) }
        }
    }
}

