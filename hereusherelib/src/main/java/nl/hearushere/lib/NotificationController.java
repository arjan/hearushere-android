package nl.hearushere.lib;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.session.MediaSession;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.media.session.MediaSessionCompat;

import nl.hearushere.lib.data.Walk;

/**
 * Handles the audio notifications
 * Created by Arjan Scherpenisse on 5-5-15.
 */
public class NotificationController {
    public static final int NOTIFICATION_ID = 1001;
    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String ACTION_START = "ACTION_START";


    private AudioWalkService parent;
    private MediaSession mSession;
    private Walk mLastWalk;
    private Walk mCurrentWalk;

    public NotificationController(AudioWalkService parent) {
        this.parent = parent;
        if (Build.VERSION.SDK_INT >= 21) {
            initMediaSession();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void initMediaSession() {
        mSession = new MediaSession(parent.getApplicationContext(), "sample session");
        mSession.setActive(true);
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                System.out.println("play");
            }

            @Override
            public void onPause() {
                super.onPause();
                System.out.println("pause");
            }
        });
    }

    public void hideNotification() {
        ((NotificationManager) parent.getSystemService(Context.NOTIFICATION_SERVICE))
                .cancel(NOTIFICATION_ID);
    }
    public void updateServiceNotification() {
        ((NotificationManager) parent.getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(NOTIFICATION_ID, buildServiceNotification());
    }

    public Notification buildServiceNotification() {
        Intent startIntent = new Intent(parent, parent.getMainActivity());
        startIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        int INTENT_ACTIVITY_ID = 1002;
        PendingIntent p = PendingIntent.getActivity(parent, INTENT_ACTIVITY_ID,
                startIntent, 0);

        if (Build.VERSION.SDK_INT < 21) {
            return buildNotificationPreLollipop(p);
        }

        return buildMediaNotification(p);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private Notification buildMediaNotification(PendingIntent p) {
        Notification.Builder builder = new Notification.Builder(parent)
                .setOngoing(true)
                .setSmallIcon(parent.getStatIcon())
                .setLargeIcon(BitmapFactory.decodeResource(parent.getResources(), parent.getAppIcon()))
                .setContentIntent(p)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(
                        mCurrentWalk == null ? parent.getString(parent.getAppName())
                                : mCurrentWalk.getTitle())
                .setContentText(
                        parent.getString(mCurrentWalk == null ? R.string.notification_not_started_text
                                : R.string.notification_progress_text_small));

        boolean hasAction = false;
        if (mCurrentWalk != null) {
            builder.setTicker(parent.getString(R.string.notification_progress_text_small));
            builder.addAction(generateAction(android.R.drawable.ic_media_pause, "Stop", ACTION_STOP));
            hasAction = true;
        } else {
            if (mLastWalk != null) {
                builder.addAction(generateAction(android.R.drawable.ic_media_play, "Start", ACTION_START));
                hasAction = true;
            }
        }
        if (hasAction) {
            builder.setStyle(new Notification.MediaStyle()
                    .setShowActionsInCompactView(0 /* #1: pause button */)
                    .setMediaSession(mSession.getSessionToken()));
        }

        return builder.build();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private Notification.Action generateAction(int icon, String title, String intentAction) {
        Intent intent = new Intent(parent.getApplicationContext(), parent.getAudioService());
        intent.setAction(intentAction);
        PendingIntent pendingIntent = PendingIntent.getService(parent.getApplicationContext(), 1, intent, 0);
        return new Notification.Action.Builder(icon, title, pendingIntent).build();
    }

    private Notification buildNotificationPreLollipop(PendingIntent p) {
        return new NotificationCompat.Builder(parent)
                .setOngoing(true)
                .setSmallIcon(parent.getStatIcon())
                .setLargeIcon(BitmapFactory.decodeResource(parent.getResources(), parent.getAppIcon()))
                .setContentIntent(p)
                .setTicker(parent.getString(R.string.notification_progress_text_small))
                .setContentTitle(
                        mCurrentWalk == null ? parent.getString(parent.getAppName())
                                : mCurrentWalk.getTitle())
                .setContentText(
                        parent.getString(mCurrentWalk == null ? R.string.notification_not_started_text
                                : R.string.notification_progress_text)).build();
    }


    @SuppressLint("NewApi")
    public void unbind() {
        if (mSession != null) {
            mSession.release();
        }
    }

    public void setLastWalk(Walk lastWalk) {
        this.mLastWalk = lastWalk;
    }

    public void setCurrentWalk(Walk currentWalk) {
        this.mCurrentWalk = currentWalk;
    }
}
