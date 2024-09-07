package com.example.testingmyapp

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

class RegistrationActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var nameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var caregiverCodeEditText: EditText
    private lateinit var nextButton: Button
    private lateinit var userTypeRadioGroup: RadioGroup
    private var selectedUserType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge experience
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_registration)

        // Apply window insets to adjust padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Firebase Auth and Firestore
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Reference UI elements
        nameEditText = findViewById(R.id.name)
        emailEditText = findViewById(R.id.regEmail)
        passwordEditText = findViewById(R.id.regPass)
        caregiverCodeEditText = findViewById(R.id.caregiverCodeEditText)
        nextButton = findViewById(R.id.regButtonTohome)
        userTypeRadioGroup = findViewById(R.id.userTypeRadioGroup)

        // Handle RadioGroup selection
        userTypeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedUserType = when (checkedId) {
                R.id.caregiverRadioButton -> "Caregiver"
                R.id.adminlyRadioButton -> "adminly"
                R.id.elderRadioButton -> "Elder"
                else -> null
            }

            // Show or hide the caregiver code field based on selection
            caregiverCodeEditText.visibility = if (selectedUserType == "Caregiver" || selectedUserType == "Elder") {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        // Handle sign-up
        nextButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val name = nameEditText.text.toString().trim()
            val caregiverCode = caregiverCodeEditText.text.toString().trim()

            // Input validation
            if (TextUtils.isEmpty(email)) {
                emailEditText.error = "Email is required"
                return@setOnClickListener
            }

            if (TextUtils.isEmpty(password)) {
                passwordEditText.error = "Password is required"
                return@setOnClickListener
            }

            if (TextUtils.isEmpty(name)) {
                nameEditText.error = "Name is required"
                return@setOnClickListener
            }

            if (selectedUserType == null) {
                Toast.makeText(this, "Please select a user type.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if ((selectedUserType == "Caregiver" || selectedUserType == "Elder") && TextUtils.isEmpty(caregiverCode)) {
                caregiverCodeEditText.error = "Code is required"
                return@setOnClickListener
            }

            // Log the registration attempt
            Log.d("Registration", "Attempting to register user with email: $email as $selectedUserType")

            // Create user with Firebase Authentication
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser?.uid
                        Log.d("Registration", "User created successfully with UID: $userId")

                        if (userId != null) {
                            when (selectedUserType) {
                                "adminly" -> createAdminlyUser(userId, email, name)
                                "Caregiver" -> linkUserToAdminly(caregiverCode, userId, "Caregiver", name, email)
                                "Elder" -> linkUserToAdminly(caregiverCode, userId, "Elder", name, email)
                            }
                        } else {
                            Log.e("Registration", "User ID is null after creation.")
                        }
                    } else {
                        handleRegistrationError(task.exception)
                    }
                }
        }

        findViewById<TextView>(R.id.loginRedirectText).setOnClickListener {
            goToLogin()
        }
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    // Function to generate a unique code
    private fun generateUniqueCode(): String {
        val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6)
            .map { characters.random() }
            .joinToString("")
    }

    private fun createAdminlyUser(userId: String, email: String, name: String) {
        val adminlyCode = generateUniqueCode()
        val medicineScheduleId = firestore.collection("medicine_schedules").document().id

        val adminlyUser = hashMapOf(
            "name" to name,
            "email" to email,
            "userType" to "adminly",
            "caregiverCode" to adminlyCode,
            "medicineScheduleId" to medicineScheduleId,
            "caregivers" to emptyList<String>(),
            "elders" to emptyList<String>()
        )

        firestore.collection("users").document(userId).set(adminlyUser)
            .addOnSuccessListener {
                Log.d("Firestore", "Adminly account created and stored successfully.")
                Toast.makeText(
                    baseContext,
                    "Adminly account created successfully.",
                    Toast.LENGTH_SHORT
                ).show()

                // Navigate to HomeActivity or other fragment
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("fragment", "HomeeFragment")
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Failed to store adminly user details.", e)
                Toast.makeText(
                    baseContext,
                    "Failed to store adminly user details.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun linkUserToAdminly(code: String, userId: String, userType: String, name: String, email: String) {
        Log.d("UserLink", "Attempting to link $userType with code: $code")

        firestore.collection("users")
            .whereEqualTo("caregiverCode", code)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Log.e("UserLink", "Invalid code: No adminly user found.")
                    Toast.makeText(
                        baseContext,
                        "Invalid code.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addOnSuccessListener
                }

                val adminlyUserId = result.documents[0].id
                val adminlyUser = result.documents[0].data

                Log.d("UserLink", "Found adminly user with UID: $adminlyUserId")

                // Create linked user (either Caregiver or Elder)
                val user = hashMapOf(
                    "name" to name,
                    "email" to email,
                    "userType" to userType,
                    "caregiverCode" to code,
                    "medicineScheduleId" to adminlyUser?.get("medicineScheduleId") as String?,
                )

                firestore.collection("users").document(userId).set(user)
                    .addOnSuccessListener {
                        Log.d("Firestore", "$userType account created and stored successfully.")
                        Toast.makeText(
                            baseContext,
                            "$userType account created successfully.",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Update adminly user's linked users list (caregivers or elders)
                        if (userType == "Caregiver") {
                            updateAdminlyUsersList(adminlyUserId, userId, "caregivers")
                        } else if (userType == "Elder") {
                            updateAdminlyUsersList(adminlyUserId, userId, "elders")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("FirestoreUpdate", "Failed to store $userType details.", e)
                        Toast.makeText(
                            baseContext,
                            "Failed to store $userType details.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("UserLink", "Failed to fetch adminly user.", e)
                Toast.makeText(
                    baseContext,
                    "Error fetching adminly user.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun updateAdminlyUsersList(adminlyUserId: String, userId: String, listName: String) {
        val updateData = mapOf(
            listName to FieldValue.arrayUnion(userId)
        )

        firestore.collection("users").document(adminlyUserId)
            .update(updateData)
            .addOnSuccessListener {
                Log.d("FirestoreUpdate", "Adminly user's $listName updated with $userId.")
                Toast.makeText(
                    baseContext,
                    "User linked to adminly successfully.",
                    Toast.LENGTH_SHORT
                ).show()

                // Navigate to HomeActivity or other fragment
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("fragment", "HomeeFragment")
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreUpdate", "Failed to update adminly user's $listName.", e)
                Toast.makeText(
                    baseContext,
                    "Failed to update adminly user's $listName.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun handleRegistrationError(exception: Exception?) {
        Log.e("Registration", "Registration failed.", exception)

        when (exception) {
            is FirebaseAuthUserCollisionException -> {
                Toast.makeText(
                    this,
                    "This email is already registered.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            else -> {
                Toast.makeText(
                    this,
                    "Registration failed: ${exception?.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
