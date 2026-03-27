package com.example.moment.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.moment.R
import com.example.moment.ui.page.CompletedTasksFragment
import com.example.moment.ui.page.HomeFragment
import com.example.moment.ui.page.SettingsFragment
import com.example.moment.ui.page.StatisticsFragment
import com.example.moment.ui.page.TasksFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        if (savedInstanceState == null) {
            switchTo(HomeFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home -> switchTo(HomeFragment())
                R.id.menu_tasks -> switchTo(TasksFragment())
                R.id.menu_completed -> switchTo(CompletedTasksFragment())
                R.id.menu_statistics -> switchTo(StatisticsFragment())
                R.id.menu_settings -> switchTo(SettingsFragment())
                else -> false
            }
        }
    }

    private fun switchTo(fragment: Fragment): Boolean {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        return true
    }
}