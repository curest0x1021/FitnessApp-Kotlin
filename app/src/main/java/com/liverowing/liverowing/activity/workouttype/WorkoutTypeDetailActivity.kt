package com.liverowing.liverowing.activity.workouttype

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.design.internal.NavigationMenu
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.view.Menu
import android.view.MenuItem
import com.bumptech.glide.Glide
import com.liverowing.liverowing.R
import com.liverowing.liverowing.activity.race.RaceActivity
import com.liverowing.liverowing.model.parse.WorkoutType
import com.liverowing.liverowing.service.messages.ProgramWorkout
import io.github.yavski.fabspeeddial.SimpleMenuListenerAdapter
import kotlinx.android.synthetic.main.activity_workout_type_detail.*
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.EventBus



class WorkoutTypeDetailActivity : AppCompatActivity() {
    private lateinit var mSectionsPagerAdapter: SectionsPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_type_detail)

        setSupportActionBar(a_workout_type_grid_toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mSectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager)
        a_workout_type_detail_container.adapter = mSectionsPagerAdapter
        a_workout_type_detail_container.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(a_workout_type_detail_tabs))
        a_workout_type_detail_tabs.addOnTabSelectedListener(TabLayout.ViewPagerOnTabSelectedListener(a_workout_type_detail_container))

        a_workout_type_detail_fab.setMenuListener(object: SimpleMenuListenerAdapter() {
            override fun onPrepareMenu(navigationMenu: NavigationMenu?): Boolean {
                startActivity(Intent(this@WorkoutTypeDetailActivity, RaceActivity::class.java))
                return true
            }
        })

    }

    public override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    public override fun onStop() {
        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    @Subscribe(sticky = true)
    fun onReceiveWorkoutType(workoutType: WorkoutType) {
        supportActionBar?.title = workoutType.name
        if (workoutType.image?.url != null) {
            Glide.with(this).load(workoutType.image?.url).into(a_workout_type_detail_image)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_workout_type_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                supportFinishAfterTransition()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    inner class SectionsPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {
        override fun getItem(position: Int): Fragment {
            return when (position) {
                0 -> WorkoutTypeDetailsFragment()
                1 -> WorkoutTypeLeadersAndStatsFragment()
                else -> Fragment()
            }
        }

        override fun getCount(): Int {
            return 2
        }
    }

}
