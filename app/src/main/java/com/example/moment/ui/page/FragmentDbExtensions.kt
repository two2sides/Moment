package com.example.moment.ui.page

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.moment.data.AppDatabase
import com.example.moment.data.dao.TaskDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun <T> Fragment.runDbThenUi(
    dbWork: suspend (TaskDao) -> T,
    uiWork: (T) -> Unit
) {
    viewLifecycleOwner.lifecycleScope.launch {
        val dao = AppDatabase.getDatabase(requireContext()).taskDao()
        val result = withContext(Dispatchers.IO) { dbWork(dao) }
        uiWork(result)
    }
}

