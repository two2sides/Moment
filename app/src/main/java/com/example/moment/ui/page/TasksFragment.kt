package com.example.moment.ui.page

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.moment.R
import com.example.moment.data.AppDatabase
import com.example.moment.data.TimeManager
import com.example.moment.data.entity.CompletedTask
import com.example.moment.data.entity.Task
import com.example.moment.data.entity.TASK_TYPE_APP_USAGE
import com.example.moment.data.entity.TASK_TYPE_MANUAL_SUBMIT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

class TasksFragment : Fragment(R.layout.fragment_tasks) {

    private val uiHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isAdded) return
            loadTasks()
            uiHandler.postDelayed(this, 1000L)
        }
    }
    private val adapter = TaskCardAdapter(
        onTaskClick = { task ->
            TaskDetailDialogFragment.newInstance(task).show(childFragmentManager, "TaskDetailDialog")
        },
        onSubmitManualTask = { task ->
            openSubmitDialog(task)
        }
    )

    private lateinit var rvTasks: RecyclerView
    private lateinit var tvTasksEmpty: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvTasks = view.findViewById(R.id.rvTasks)
        tvTasksEmpty = view.findViewById(R.id.tvTasksEmpty)
        val btnCreateTask = view.findViewById<Button>(R.id.btnCreateTask)

        rvTasks.layoutManager = LinearLayoutManager(requireContext())
        rvTasks.adapter = adapter

        setupFragmentResultListeners()

        btnCreateTask.setOnClickListener {
            CreateTaskDialogFragment().show(childFragmentManager, "CreateTaskDialog")
        }

        loadTasks()
    }

    override fun onResume() {
        super.onResume()
        loadTasks()
        uiHandler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(refreshRunnable)
    }

    private fun saveCreatedTask(bundle: Bundle) {
        runDbThenUi(
            dbWork = { dao -> dao.insertTask(buildTaskFromBundle(bundle)) },
            uiWork = {
                Toast.makeText(requireContext(), getString(R.string.task_created), Toast.LENGTH_SHORT).show()
                loadTasks()
            }
        )
    }

    private fun openSubmitDialog(task: Task) {
        if (task.taskType != TASK_TYPE_MANUAL_SUBMIT) return

        SubmitTaskDialogFragment.newInstance(
            taskId = task.id,
            taskTitle = task.title
        ).show(childFragmentManager, "SubmitTaskDialog")
    }

    private fun submitManualTask(bundle: Bundle) {
        val taskId = bundle.getInt(SubmitTaskDialogFragment.KEY_TASK_ID)
        val submittedMarkdown = bundle.getString(SubmitTaskDialogFragment.KEY_SUBMITTED_MARKDOWN)
        val submittedText = bundle.getString(SubmitTaskDialogFragment.KEY_SUBMITTED_TEXT)
        val submittedImageUri = bundle.getString(SubmitTaskDialogFragment.KEY_SUBMITTED_IMAGE_URI)

        viewLifecycleOwner.lifecycleScope.launch {
            val context = requireContext()
            val dao = AppDatabase.getDatabase(context).taskDao()
            val task = withContext(Dispatchers.IO) { dao.getTaskById(taskId) }

            if (task == null || task.taskType != TASK_TYPE_MANUAL_SUBMIT) {
                Toast.makeText(context, getString(R.string.task_not_found_or_invalid), Toast.LENGTH_SHORT).show()
                return@launch
            }

            withContext(Dispatchers.IO) {
                dao.insertCompletedTask(
                    buildCompletedRecord(
                        task = task,
                        submittedMarkdown = submittedMarkdown,
                        submittedText = submittedText,
                        submittedImageUri = submittedImageUri
                    )
                )
                settleManualTask(dao, task)
            }

            TimeManager.addSeconds(context, task.rewardMinutes * 60)
            Toast.makeText(context, getString(R.string.task_submit_success), Toast.LENGTH_SHORT).show()
            loadTasks()
        }
    }

    private fun deleteActiveTask(taskId: Int) {
        runDbThenUi(
            dbWork = { dao -> dao.deleteTaskById(taskId) },
            uiWork = {
                Toast.makeText(requireContext(), getString(R.string.task_deleted), Toast.LENGTH_SHORT).show()
                loadTasks()
            }
        )
    }

    private fun openEditTaskDialog(taskId: Int) {
        runDbThenUi(
            dbWork = { dao -> dao.getTaskById(taskId) },
            uiWork = { task ->
                if (task == null) {
                    Toast.makeText(requireContext(), getString(R.string.task_not_found_or_invalid), Toast.LENGTH_SHORT).show()
                    return@runDbThenUi
                }
                EditTaskDialogFragment.newInstance(task)
                    .show(childFragmentManager, "EditTaskDialog")
            }
        )
    }

    private fun updateTaskFromEdit(bundle: Bundle) {
        val taskId = bundle.getInt(EditTaskDialogFragment.KEY_TASK_ID)
        val note = bundle.getString(EditTaskDialogFragment.KEY_NOTE)?.trim().orEmpty().ifBlank { null }
        val rewardMinutes = bundle.getInt(EditTaskDialogFragment.KEY_REWARD_MINUTES)
        val targetMinutes = bundle.getInt(EditTaskDialogFragment.KEY_TARGET_MINUTES)

        runDbThenUi(
            dbWork = { dao ->
                val task = dao.getTaskById(taskId)
                if (task == null) {
                    false
                } else {
                    val updated = if (task.taskType == TASK_TYPE_APP_USAGE) {
                        task.copy(
                            note = note,
                            rewardMinutes = rewardMinutes,
                            targetSeconds = targetMinutes * 60,
                            updatedAt = System.currentTimeMillis()
                        )
                    } else {
                        task.copy(
                            note = note,
                            rewardMinutes = rewardMinutes,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                    dao.updateTask(updated)
                    true
                }
            },
            uiWork = { success ->
                if (!success) {
                    Toast.makeText(requireContext(), getString(R.string.task_not_found_or_invalid), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.task_updated), Toast.LENGTH_SHORT).show()
                    loadTasks()
                }
            }
        )
    }

    private fun loadTasks() {
        runDbThenUi(
            dbWork = { dao -> dao.getActiveTasks() },
            uiWork = { tasks ->
                adapter.submitList(tasks)
                tvTasksEmpty.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
            }
        )
    }

    private fun setupFragmentResultListeners() {
        childFragmentManager.setFragmentResultListener(
            CreateTaskDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle -> saveCreatedTask(bundle) }

        childFragmentManager.setFragmentResultListener(
            SubmitTaskDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle -> submitManualTask(bundle) }

        childFragmentManager.setFragmentResultListener(
            TaskDetailDialogFragment.DELETE_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            deleteActiveTask(bundle.getInt(TaskDetailDialogFragment.KEY_TASK_ID))
        }

        childFragmentManager.setFragmentResultListener(
            TaskDetailDialogFragment.EDIT_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            openEditTaskDialog(bundle.getInt(TaskDetailDialogFragment.KEY_TASK_ID))
        }

        childFragmentManager.setFragmentResultListener(
            EditTaskDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            updateTaskFromEdit(bundle)
        }
    }

    private fun buildTaskFromBundle(bundle: Bundle): Task {
        val targetMinutes = bundle.getInt(CreateTaskDialogFragment.KEY_TARGET_MINUTES)
        return Task(
            title = bundle.getString(CreateTaskDialogFragment.KEY_TITLE).orEmpty(),
            note = bundle.getString(CreateTaskDialogFragment.KEY_NOTE)?.trim().orEmpty().ifBlank { null },
            taskType = bundle.getString(CreateTaskDialogFragment.KEY_TASK_TYPE).orEmpty(),
            targetPackage = bundle.getString(CreateTaskDialogFragment.KEY_TARGET_PACKAGE),
            targetSeconds = targetMinutes * 60,
            rewardMinutes = bundle.getInt(CreateTaskDialogFragment.KEY_REWARD_MINUTES),
            isRepeatable = bundle.getBoolean(CreateTaskDialogFragment.KEY_REPEATABLE),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun buildCompletedRecord(
        task: Task,
        submittedMarkdown: String?,
        submittedText: String?,
        submittedImageUri: String?
    ): CompletedTask {
        val normalizedMarkdown = submittedMarkdown?.trim().orEmpty().ifBlank { null }
        val normalizedText = submittedText?.trim().orEmpty().ifBlank {
            MarkdownContentHelper.stripMarkdownToPlainText(normalizedMarkdown)
        }
        val normalizedImageUri = submittedImageUri?.trim().orEmpty().ifBlank {
            MarkdownContentHelper.extractFirstImageUri(normalizedMarkdown)
        }

        return CompletedTask(
            taskId = task.id,
            taskTitleSnapshot = task.title,
            taskNoteSnapshot = task.note,
            taskTypeSnapshot = task.taskType,
            isRepeatableSnapshot = task.isRepeatable,
            submittedMarkdown = normalizedMarkdown,
            submittedText = normalizedText,
            submittedImageUri = normalizedImageUri,
            rewardGrantedMinutes = task.rewardMinutes,
            taskCreatedAtSnapshot = task.createdAt,
            completedAt = System.currentTimeMillis(),
            logicalDayKey = TimeManager.getCurrentLogicDayKey()
        )
    }

    private suspend fun settleManualTask(dao: com.example.moment.data.dao.TaskDao, task: Task) {
        if (task.isRepeatable) {
            dao.updateTask(task.copy(updatedAt = System.currentTimeMillis()))
        } else {
            dao.deleteTask(task)
        }
    }

}

