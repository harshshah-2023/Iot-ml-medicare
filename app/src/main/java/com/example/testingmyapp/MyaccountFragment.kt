package com.example.testingmyapp

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MyaccountFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        (activity as AppCompatActivity).supportActionBar?.hide()
        val view = inflater.inflate(R.layout.fragment_myaccount, container, false)

        val forgotPasswordTextView = view.findViewById<TextView>(R.id.forgot_password_textview)
        forgotPasswordTextView.setOnClickListener {
            val intent = Intent(activity, ForegtpasswordActivity::class.java)
            startActivity(intent)
        }

        val emailTextView = view.findViewById<TextView>(R.id.emailTextView)
        val usernameTextView = view.findViewById<TextView>(R.id.usernameTextView)
        val codeTextView = view.findViewById<TextView>(R.id.CodeCargiver)
        val user = FirebaseAuth.getInstance().currentUser

        user?.let {
            val email = it.email
            emailTextView.text = email

            // Retrieve user details from Firestore
            val uid = it.uid
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val username = document.getString("name")
                        val userType = document.getString("userType")
                        val code = document.getString("caregiverCode")

                        usernameTextView.text = username

                        // Show code only if the user is adminly
                        if (userType == "adminly") {
                            codeTextView.text = code
                            codeTextView.visibility = View.VISIBLE
                        } else {
                            codeTextView.visibility = View.GONE
                        }
                    } else {
                        usernameTextView.text = "No username found"
                    }
                }
                .addOnFailureListener { e ->
                    usernameTextView.text = "Error retrieving user details"
                }
        }

        return view
    }

    companion object {
        @JvmStatic
        fun newInstance() =
            MyaccountFragment().apply {
                // You can pass other arguments if needed
                arguments = Bundle()
            }
    }
}
