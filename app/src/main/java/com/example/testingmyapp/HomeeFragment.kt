package com.example.testingmyapp

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.database.*
import com.example.testingmyapp.model.MedicineScheduleItem

class HomeeFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var adapter: MedicineAdapter
    private val medicineList = mutableListOf<MedicineScheduleItem>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        (activity as AppCompatActivity).supportActionBar?.hide()
        val view = inflater.inflate(R.layout.fragment_homee, container, false)

        val recyclerView: RecyclerView = view.findViewById(R.id.medicine_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)

        adapter = MedicineAdapter(medicineList)
        recyclerView.adapter = adapter

        database = FirebaseDatabase.getInstance().getReference("medicine_schedules")

        fetchUserRoleAndLoadData()

        return view
    }

    private fun fetchUserRoleAndLoadData() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userRef = FirebaseFirestore.getInstance().collection("users").document(currentUser.uid)

            userRef.get().addOnSuccessListener { documentSnapshot ->
                Log.d("HomeeFragment", "Document snapshot: $documentSnapshot")

                val userType = documentSnapshot.getString("userType")
                Log.d("HomeeFragment", "User type: $userType")

                when (userType) {
                    "adminly" -> {
                        // User is an adminly Person
                        Log.d("HomeeFragment", "User is an adminly Person")
                        fetchMedicineSchedules(currentUser.uid)
                    }
                    "Elder" -> {
                        // User is an Elder
                        val caregiverCode = documentSnapshot.getString("caregiverCode")
                        if (caregiverCode != null) {
                            Log.d("HomeeFragment", "Caregiver code found for Elder: $caregiverCode")
                            fetchLinkedAdminUser(caregiverCode)
                        } else {
                            Log.e("HomeeFragment", "Caregiver code is null for Elder.")
                        }
                    }
                    "Caregiver" -> {
                        // User is a caregiver
                        val caregiverCode = documentSnapshot.getString("caregiverCode")
                        if (caregiverCode != null) {
                            fetchLinkedAdminlyPersonUid(caregiverCode)
                        } else {
                            Log.e("HomeeFragment", "Caregiver code is null.")
                        }
                    }
                    else -> {
                        Log.e("HomeeFragment", "Unknown user type.")
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("HomeeFragment", "Failed to load user data from Firestore.", e)
            }
        } else {
            Log.e("HomeeFragment", "User is not authenticated.")
        }
    }

    private fun fetchLinkedAdminUser(caregiverCode: String) {
        val usersRef = FirebaseFirestore.getInstance().collection("users")

        usersRef.whereEqualTo("caregiverCode", caregiverCode)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Log.e("HomeeFragment", "No adminly users found linked to this caregiver.")
                } else {
                    val adminlyUid = result.documents[0].id
                    Log.d("HomeeFragment", "Found adminly UID: $adminlyUid")
                    fetchMedicineSchedules(adminlyUid)
                }
            }
            .addOnFailureListener { e ->
                Log.e("HomeeFragment", "Failed to fetch adminly person data.", e)
            }
    }

    private fun fetchLinkedAdminlyPersonUid(caregiverCode: String) {
        val usersRef = FirebaseFirestore.getInstance().collection("users")

        usersRef.whereEqualTo("caregiverCode", caregiverCode)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Log.e("HomeeFragment", "No adminly users found linked to this caregiver.")
                } else {
                    val adminlyUid = result.documents[0].id
                    Log.d("HomeeFragment", "Found adminly UID: $adminlyUid")
                    fetchMedicineSchedules(adminlyUid)
                }
            }
            .addOnFailureListener { e ->
                Log.e("HomeeFragment", "Failed to fetch adminly person data.", e)
            }
    }

    private fun fetchMedicineSchedules(userId: String) {
        val userMedicineRef = database.child(userId)

        userMedicineRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                medicineList.clear() // Clear the list to ensure fresh data

                if (snapshot.exists()) {
                    for (dataSnapshot in snapshot.children) {
                        val medicineItem = dataSnapshot.getValue(MedicineScheduleItem::class.java)
                        if (medicineItem != null) {
                            medicineList.add(medicineItem)
                        }
                    }
                    Log.d("HomeeFragment", "Medicine schedules fetched: ${medicineList.size} items.")
                } else {
                    Log.e("HomeeFragment", "No medicine schedules found for user ID: $userId")
                }

                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeeFragment", "Failed to load medicine schedules.", error.toException())
            }
        })
    }
}
