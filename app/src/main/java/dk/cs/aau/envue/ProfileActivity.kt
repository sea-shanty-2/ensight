package dk.cs.aau.envue

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.facebook.Profile
import com.facebook.login.LoginManager
import com.squareup.picasso.Picasso
import dk.cs.aau.envue.shared.GatewayClient
import kotlinx.android.synthetic.main.activity_profile.*
import dk.cs.aau.envue.transformers.CircleTransform

class ProfileActivity : AppCompatActivity() {
    companion object {
        internal const val SET_INTERESTS_REQUEST = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val profileQuery = ProfileQuery.builder().build()

        GatewayClient.query(profileQuery).enqueue(object : ApolloCall.Callback<ProfileQuery.Data>() {
            override fun onResponse(response: Response<ProfileQuery.Data>) {
                val profile = response.data()?.accounts()?.me()

                if (profile != null) {
                    onProfileFetch(profile!!)
                }
                else {
                    TODO("Handle null response")
                }
            }

            override fun onFailure(e: ApolloException) = onProfileFetchFailure(e)

        })

        // register log out button listener
        logOutButton.setOnClickListener { this.onLogOut() }
        // register change interests button listener
        interestsButton.setOnClickListener { this.onChangeInterests() }



    }

    private fun onProfileFetch(profile: ProfileQuery.Me) {
        runOnUiThread {
            profileNameView.text = profile.displayName()
        }
    }

    private fun onProfileFetchFailure(e: ApolloException) {
        runOnUiThread {

            AlertDialog
                .Builder(this)
                .setMessage(e.message)
                .setNegativeButton("log out") { _, _ -> startActivity(Intent(this, LoginActivity::class.java)) }
                .setPositiveButton("return") { _, _ -> finish() }
                .create()
        }
    }

    private fun onLogOut() {
        val profile = Profile.getCurrentProfile()

        AlertDialog.Builder(this)
            .setMessage( resources.getString(R.string.com_facebook_loginview_logged_in_as, profile.name))
            .setCancelable(true)
            .setPositiveButton(R.string.com_facebook_loginview_log_out_button) { _: DialogInterface, _: Int -> run{
                LoginManager.getInstance().logOut()
                startActivity(Intent(this, LoginActivity::class.java))
            }}
            .create()
            .show()
    }

    private fun onChangeInterests() {
        val curint: CharSequence = currentInterestsView.text
        val intent = Intent(this, InterestsActivity::class.java)
        intent.putExtra(resources.getString(R.string.current_interests_key), curint)
        startActivityForResult(intent, SET_INTERESTS_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            SET_INTERESTS_REQUEST ->
                if (resultCode == Activity.RESULT_OK) {
                    currentInterestsView.text = data?.getStringExtra(resources.getString(R.string.interests_response_key))
                }
        }
    }

}