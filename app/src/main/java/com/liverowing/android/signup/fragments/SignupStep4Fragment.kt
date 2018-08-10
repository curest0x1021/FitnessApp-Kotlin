package com.liverowing.android.activity.login

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.liverowing.android.R
import com.liverowing.android.signup.fragments.BaseStepFragment
import com.liverowing.android.signup.fragments.ResultListener
import kotlinx.android.synthetic.main.fragment_signup_4.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

class SignupStep4Fragment(override var listener: ResultListener) : BaseStepFragment() {

    var birthday: String = ""
        get() = a_signup_birthday.text.toString()

    var myCalendar: Calendar = Calendar.getInstance()

    var userName: String = ""
        set(value) {
            field = value
            a_signup_username_textview.text = value
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_signup_4, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dateSetListener = DatePickerDialog.OnDateSetListener { view, year, month, dayOfMonth ->
            myCalendar.set(year, month, dayOfMonth)
            updateBirthday()
        }

        a_signup_birthday.setOnClickListener {
            DatePickerDialog(activity, dateSetListener, myCalendar.get(Calendar.YEAR), myCalendar.get(Calendar.MONTH), myCalendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        btn_signup_profile_picture.setOnClickListener {

        }
    }

    private fun updateBirthday() {
        val pattern = "MM/dd/yyyy"
        var simpleDateFormat = SimpleDateFormat(pattern, Locale.US)
        a_signup_birthday.setText(simpleDateFormat.format(myCalendar.time))
    }

    override fun checkValidation() {
        if (birthday.isEmpty()) {
            a_signup_birthday.requestFocus()
            a_signup_birthday.error = "Empty birthday!"
        } else {
            var data = HashMap<String, String>()
            data.put("birthday", birthday)
            listener.onResultListener(true, data)
        }
    }

}