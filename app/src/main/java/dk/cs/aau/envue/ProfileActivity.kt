package dk.cs.aau.envue

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.support.text.emoji.EmojiCompat
import android.support.text.emoji.bundled.BundledEmojiCompatConfig
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.widget.ListView
import com.facebook.Profile
import com.facebook.login.LoginManager
import com.google.gson.GsonBuilder
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_profile.*
import dk.cs.aau.envue.transformers.CircleTransform
import dk.cs.aau.envue.utility.EmojiIcon
import dk.cs.aau.envue.workers.BroadcastCategoryListAdapter
import kotlinx.android.synthetic.main.activity_initialize_broadcast.*

class ProfileActivity : AppCompatActivity() {
    companion object {
        internal val SET_INTERESTS_REQUEST = 0
        internal val INTERESTS_RESPONSE_KEY = "interests"
        internal val CURRENT_INTERESTS_KEY = "current interests"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val profile = Profile.getCurrentProfile()

        // register log out button listener
        logOutButton.setOnClickListener { this.onLogOut() }
        // register change interests button listener
        interestsButton.setOnClickListener { this.onChangeInterests() }
        testButton.setOnClickListener { this.onTestNotification() }

        val profilePicture = profile.getProfilePictureUri(1024, 1024)
        val profileName = profile.name

        Picasso
            .get()
            .load(profilePicture)
            .placeholder(R.drawable.ic_profile_picture_placeholder)
            .error(R.drawable.ic_profile_picture_placeholder)
            .resize(256, 256)
            .transform(CircleTransform())
            .into(profilePictureView)

        profileNameView.text = profileName
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
        startActivityForResult(Intent(this, InterestsActivity::class.java).putExtra(CURRENT_INTERESTS_KEY, currentInterestsView.text), SET_INTERESTS_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            SET_INTERESTS_REQUEST ->
                if (resultCode == Activity.RESULT_OK) {
                    currentInterestsView.text = data?.getStringExtra(INTERESTS_RESPONSE_KEY)
                }
        }
    }

    private fun onTestNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val icon: Bitmap = BitmapFactory.decodeResource(this.getResources(), R.drawable.dummy)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
        var builder = NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
            .setSmallIcon(R.drawable.ic_live_tv_48dp)
            .setContentTitle("Notification!!!")
            .setContentText("This is a notification")
            .setLargeIcon(icon)
            .setStyle(NotificationCompat.BigPictureStyle()
                .bigPicture(icon)
                .bigLargeIcon(null))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Set the intent that will fire when the user taps the notification
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            // notificationId is a unique int for each notification that you must define
            notify(1, builder.build())
        }



    }

}