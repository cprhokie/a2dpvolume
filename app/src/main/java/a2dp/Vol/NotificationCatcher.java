package a2dp.Vol;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Jim on 1/18/2016.
 * This class replaces the old accessibility service for reading notifications. The old way was
 * deprecated in API 18. This service listens for notifications and sends them to the text reader
 * in service.java to be read out when devices are connected.
 */
public class NotificationCatcher extends NotificationListenerService {

    private static final String LOGTAG = "NotifCatcher";
    private static String[] packages;
    //private MyApplication application;
    SharedPreferences preferences;
//    List<LastNotificationTime> notList = new ArrayList<>();
    private List<String> apps1 = new ArrayList<>();

    public NotificationCatcher() {
        super();
    }

    private static AtomicInteger nextId = new AtomicInteger(0);
    private int id;

    private static final ArrayList<String> lastKeys = new ArrayList<>();

    @Override
    public void onCreate() {

        this.id = nextId.incrementAndGet();
        Log.d(LOGTAG,"NotificationCatcher.create id = " + id);

        MyApplication application = (MyApplication) this.getApplication();

        preferences = PreferenceManager.getDefaultSharedPreferences(application);

        IntentFilter reloadmessage = new IntentFilter("a2dp.vol.Reload");
        this.registerReceiver(reloadprefs, reloadmessage);

//        IntentFilter clearFilter = new IntentFilter("a2dp.Vol.Clear");
//        this.registerReceiver(clear, clearFilter);
        LoadPrefs();

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.d(LOGTAG,"NotificationCatcher.onDestroy id = " + id);
        unregisterReceiver(reloadprefs);
//        unregisterReceiver(clear);
        super.onDestroy();
    }

//    @Override
//    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
//        // if api level < 21, do nothing
//        Log.d(LOGTAG,"build SDK_INT = " + Build.VERSION.SDK_INT);
//        if (Build.VERSION.SDK_INT < 21) {
//            onNotificationPosted(sbn);
//            return;
//        }
//
//        super.onNotificationPosted(sbn,rankingMap);
//        //super.onNotificationPosted(sbn, rankingMap);
//        //Toast.makeText(application, "reading notification", Toast.LENGTH_LONG).show();
//        Log.d(LOGTAG,"onNotificationPosted 2");
//    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {

        Log.d(LOGTAG,"onNotificationPosted id = " + id);

        if (!a2dp.Vol.service.hasConnections()) {
            Log.d(LOGTAG,"no connections, ignore notification");
            return;
        }

        // Toast.makeText(application, "reading notification", Toast.LENGTH_LONG).show();
        boolean validPackage = false;
        for (String p : packages) {
            //Log.d(LOGTAG,"p = " + p);
            if (p.equalsIgnoreCase(sbn.getPackageName())) {
                validPackage = true;
            }
        }

        if (validPackage) {
            new Readit().execute(sbn);
        } else {
            //Log.d(LOGTAG,"not valid package for reading notifications");
        }
    }

    @Override
    public void onListenerConnected() {
        //super.onListenerConnected();
        Log.d(LOGTAG,"onListenerConnected id = " + id);
    }

    private static String createKey(StatusBarNotification sbn) {
        String key = sbn.getKey();
        Notification n = sbn.getNotification();
        if (n != null) {
            key = key + "|" + n.tickerText;
            key = key + "|" + n.when;
        }
        return key;
    }

    private class Readit extends AsyncTask<StatusBarNotification, Integer, Long> {

        @Override
        protected Long doInBackground(StatusBarNotification... params) {

            Log.d(LOGTAG,"in Readit task");

            StatusBarNotification sbn = params[0];

            String str = "";
            ApplicationInfo appInfo;
            PackageManager pm = getPackageManager();
            String pack = sbn.getPackageName();
            try {
                appInfo = pm.getApplicationInfo(pack, 0);
            } catch (NameNotFoundException e1) {
                //Toast.makeText(application, "problem getting app info", Toast.LENGTH_LONG).show();
                appInfo = null;
            }
            String appName = (String) (appInfo != null ? pm
                    .getApplicationLabel(appInfo) : pack);

            // abort if we can't get the notification
            Notification notification = sbn.getNotification();
            if (notification == null) {
                return null;
            }

            String key = createKey(sbn);
            Log.d(LOGTAG,"key = " + key);
            if (lastKeys.contains(key)) {
                Log.d(LOGTAG,"ignore because key already seen in last 30");
                return null;
            } else {
                lastKeys.add(key);
                while (lastKeys.size() > 30) {
                    lastKeys.remove(0);
                }
            }

            Log.d(LOGTAG,"lastKeys.size = " + lastKeys.size());

            // get the time this notification was posted
            Long when = notification.when;

//            boolean ignoreRepeatsFromSamePackage = false;
//
//            if (ignoreRepeatsFromSamePackage) {
//                // create an item out of the new data
//                LastNotificationTime item = new LastNotificationTime(pack, when);
//                Boolean found = false;
//                for (int i = 0; i < notList.size(); i++) {
//                    LastNotificationTime element = notList.get(i);
//                    if (element.getPackageName().equals(pack)) {
//                        if ((element.getTime() + 1000) < when) {
//                            notList.set(i, item);  // if the package sent a new notification update the last time
//                        } else {
//                            return null; // if this is not new, exit here to stop repeating notifications
//                        }
//                        found = true;
//                    }
//                }
//                if (!found) {
//                    Log.d(LOGTAG, "addItem " + item);
//                    notList.add(item); // if this is a new package posting, add it to the list
//                }
//            }

            // add the app name to the string to be read
            str += appName + ", ";
            Log.d(LOGTAG,"str = " + str);

            // get the ticker text of this notification and add that to the message string
            String ticker = "";
            if (notification.tickerText != null) {
                ticker = sbn.getNotification().tickerText.toString();
                Log.d(LOGTAG,"ticker = " + ticker);
            }

            String temp = "";

            // these apps use the TickerText properly
            if (apps1.contains(pack)) {
                Log.d(LOGTAG,"uses TickerText properly?");
                if (ticker != null) {
                    str += ticker;
                } else {
                    Log.d(LOGTAG,"ticker is null");
                    return null;
                }
            } else {

                // get the lines of the notification

                Bundle bun = notification.extras;
                if (!bun.isEmpty()) {
                    CharSequence[] lines = bun.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
                    if (lines != null) {
                        if (lines.length > 0) {
                            for (CharSequence line : lines) {
                                if (line != null) {
                                    if (line.length() > 1) {
                                        temp = line.toString();
                                        Log.d(LOGTAG, "extra_text_lines = " + temp);
                                    }
                                }
                            }
                        }
                    }
                }

                // get the text string to see if there is something in it
                String text = "";
                if (bun.getString(Notification.EXTRA_TEXT) != null) {
                    if (!bun.getString(Notification.EXTRA_TEXT).isEmpty()) {
                        text = bun.getString(Notification.EXTRA_TEXT);
                        Log.d(LOGTAG,"extra_text = " + text);

                        if (pack.equalsIgnoreCase("com.google.android.apps.messaging")) {
                            if (ticker != null) {
                                if (ticker.endsWith(text)) {
                                    Log.d(LOGTAG,"ignore extra text since it is already part of ticker");
                                    text = "";
                                }
                            }
                        }
                    }
                }

                // figure out which have valid strings and which we want to communicate
                if (ticker.length() > 1) {
                    if (ticker.equalsIgnoreCase(temp) || temp.length() < 1) {
                        str += ticker;
                    } else {
                        str += ticker + ", " + temp;
                    }
                }

                Log.d(LOGTAG,"str = " + str);

                if (!text.isEmpty()) {
                    Log.d(LOGTAG,"text is not empty");
                    if (text.equalsIgnoreCase(temp) || temp.isEmpty()) {
                        str += text;
                    } else {
                        str += text + ", " + temp;
                    }
                }

                Log.d(LOGTAG,"str = " + str);

                //if there is no ticker or strings then ignore it.
                if (temp.isEmpty() && ticker.isEmpty() && text.isEmpty()) {
                    Log.d(LOGTAG,"no strings or ticker");
                    return null;
                }

                if (pack == "com.google.android.apps.fireball") { // Google Allo handling
                    if (ticker != null) {
                        str = appName + ", " + ticker + ", " + text;
                    }else {
                        return null;
                    }
                }
            }

            Log.d(LOGTAG,"str = " + str);

            // read out the message by sending it to the service
            if (str.length() > 0) {
                Log.d(LOGTAG,"send broadcast to read the message");
                Intent intent = new Intent();
                intent.setAction(Actions.READ_MESSAGE);
                intent.putExtra("message", str);
                LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(getApplication());
                lbm.sendBroadcast(intent);
            } else {
                Log.d(LOGTAG,"text to read is empty!");
            }

            return null;
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.d(LOGTAG,"onNotificationRemoved");
    }

    public void LoadPrefs() {

        String packagelist = preferences
                .getString("packages",
                        "com.google.android.talk,com.android.email,com.android.calendar");
        packages = packagelist.split(",");

        apps1.add("com.google.android.talk");
        apps1.add("com.skype.raider");
    }

    private final BroadcastReceiver reloadprefs = new BroadcastReceiver() {

        @Override
        public void onReceive(Context arg0, Intent arg1) {
            LoadPrefs();
        }
    };

//    private final BroadcastReceiver clear = new BroadcastReceiver() {
//
//        @Override
//        public void onReceive(Context arg0, Intent arg1) {
//            notList.clear();
//        }
//    };

    /*This class stores the packages that have posted notifications, and the last time they posted
    * it is used to make sure notifications are not read multiple times.*/

//    private class LastNotificationTime {
//        private String packageName;
//        private Long nottime;
//
//        public LastNotificationTime(String packageName, Long nottime) {
//            this.packageName = packageName;
//            this.nottime = nottime;
//        }
//
//        public Long getTime() {
//            return nottime;
//        }
//
////        public void setNottime(Long nottime) {
////            this.nottime = nottime;
////        }
//
//        public String getPackageName() {
//            return packageName;
//        }
//
//        public void setPackageName(String packageName) {
//            this.packageName = packageName;
//        }
//    }

}


