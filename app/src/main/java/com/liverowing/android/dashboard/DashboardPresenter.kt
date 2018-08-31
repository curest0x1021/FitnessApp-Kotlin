package com.liverowing.android.dashboard

import com.liverowing.android.base.EventBusPresenter
import com.liverowing.android.model.messages.DeviceConnected
import com.liverowing.android.model.messages.DeviceDisconnected
import com.liverowing.android.model.parse.User
import com.liverowing.android.model.parse.WorkoutType
import com.parse.ParseException
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class DashboardPresenter : EventBusPresenter<DashboardView>() {
    fun loadDashboard() {
        loadFeaturedWorkouts()
        loadPopularWorkouts()
        loadRecentWorkouts()
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onDeviceConnectedMainThread(data: DeviceConnected) {
        ifViewAttached { it.deviceConnected(data.device) }
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onDeviceDisconnectedMainThread(data: DeviceDisconnected) {
        ifViewAttached { it.deviceDisconnected(data.device) }
    }

    private fun loadFeaturedWorkouts() {
        val query = WorkoutType.featuredWorkouts()

        ifViewAttached { it.featuredWorkoutsLoading() }
        query.findInBackground { objects, e ->
            if (e !== null) {
                if (e.code != ParseException.CACHE_MISS) {
                    ifViewAttached { it.featuredWorkoutsError(e) }
                }
            } else {
                ifViewAttached {
                    it.featuredWorkoutsLoaded(objects)
                }
            }
        }
    }

    fun updateFeaturedWorkouts(workouts: MutableList<WorkoutType>, selectedFeaturedUsers: MutableList<User>) {

        var featuredWorkouts = mutableListOf<WorkoutType>()
        val featuredUsers = mutableListOf<User>()

        if (selectedFeaturedUsers.size == 0) {

            var maxRotationRank = 0
            var userIds = mutableListOf<String>()

            workouts.forEach { item ->

                // add 1 of the most recent featuredUser
                if (!userIds.contains(item.createdBy!!.objectId)) {

                    // set order by rotationRank
                    val rank = item.createdBy!!.rotationRank ?: 0
                    if (rank > maxRotationRank) {
                        maxRotationRank = rank
                        featuredUsers.add(0, item.createdBy!!)
                    } else {
                        featuredUsers.add(item.createdBy!!)
                    }
                    userIds.add(item.createdBy!!.objectId)

                    // add 1 of the most recent workoutType from each featuredUser
                    featuredWorkouts.add(item)
                }
            }

        } else {

            // get featuredWorkoutTypes by filtering
            val selectedIds = selectedFeaturedUsers.map { item -> item.objectId }

            workouts.forEach { item ->
                if (selectedIds.contains(item.createdBy!!.objectId)) {
                    featuredWorkouts.add(item)
                }
            }
        }

        ifViewAttached {
            it.featuredWorkoutsUpdated(featuredWorkouts, featuredUsers)
        }
    }

    private fun loadPopularWorkouts() {
        val query = WorkoutType.popularWorkouts()
        ifViewAttached { it.popularWorkoutsLoading() }
        query.findInBackground { objects, e ->
            if (e !== null) {
                if (e.code != ParseException.CACHE_MISS) {
                    ifViewAttached { it.popularWorkoutsError(e) }
                }
            } else {
                ifViewAttached {
                    it.popularWorkoutsLoaded(objects)
                }
            }
        }
    }

    private fun loadRecentWorkouts() {
        val query = WorkoutType.recentWorkouts()

        ifViewAttached { it.recentWorkoutsLoading() }
        query.findInBackground { objects, e ->
            if (e !== null) {
                if (e.code != ParseException.CACHE_MISS) {
                    ifViewAttached { it.recentWorkoutsError(e) }
                }
            } else {
                ifViewAttached {
                    var recents = mutableListOf<WorkoutType>()
                    var ids = mutableListOf<String>()
                    objects.forEach { workout ->
                        if (workout.workoutType != null  && !ids.contains(workout.workoutType!!.objectId)) {
                            recents.add(workout.workoutType!!)
                            ids.add(workout.workoutType!!.objectId)
                        }
                        if (recents.size == 15) {
                            return@forEach
                        }
                    }
                    it.recentWorkoutsLoaded(recents)
                }
            }
        }
    }

}
