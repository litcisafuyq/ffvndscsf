package com.example

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.a17.assist.ffvn.ui.AboutFragment
import com.a17.assist.ffvn.ui.ConfigFragment
import com.a17.assist.ffvn.ui.HomeFragment
import com.a17.assist.ffvn.ui.SetupFragment
import com.example.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBar.updatePadding(top = bars.top + dp(10))
            binding.bottomNav.updatePadding(bottom = bars.bottom)
            insets
        }

        binding.viewPager.adapter = MainPagerAdapter(this)
        binding.viewPager.offscreenPageLimit = 3

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val menuId = when (position) {
                    0 -> R.id.tab_home
                    1 -> R.id.tab_setup
                    2 -> R.id.tab_config
                    else -> R.id.tab_about
                }
                if (binding.bottomNav.selectedItemId != menuId) {
                    binding.bottomNav.selectedItemId = menuId
                }
            }
        })

        binding.bottomNav.setOnItemSelectedListener { item ->
            val page = when (item.itemId) {
                R.id.tab_home -> 0
                R.id.tab_setup -> 1
                R.id.tab_config -> 2
                R.id.tab_about -> 3
                else -> 0
            }
            if (binding.viewPager.currentItem != page) {
                binding.viewPager.setCurrentItem(page, true)
            }
            true
        }
    }

    fun navigateToTab(index: Int) {
        binding.viewPager.setCurrentItem(index.coerceIn(0, 3), true)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private class MainPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 4
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> HomeFragment()
                1 -> SetupFragment()
                2 -> ConfigFragment()
                else -> AboutFragment()
            }
        }
    }
}
