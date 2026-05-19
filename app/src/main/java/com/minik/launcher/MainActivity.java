package com.minik.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {

    private static final String EMPTY_MESSAGE = "No launchable apps found.";
    private static final String OPEN_ERROR_MESSAGE = "App could not be opened.";
    private static final String REMOVE_ERROR_MESSAGE = "App could not be removed.";

    private final List<ResolveInfo> apps = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();

    private ListView listView;
    private LabelAdapter adapter;
    private PackageManager packageManager;
    private boolean receiverRegistered;
    private boolean emptyStateShown;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadApps();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        packageManager = getPackageManager();

        listView = new ListView(this);
        listView.setBackgroundColor(Color.WHITE);
        setContentView(listView);

        adapter = new LabelAdapter(this, labels);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> launchApp(position));
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            uninstallApp(position);
            return true;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerPackageReceiver();
        loadApps();
    }

    @Override
    protected void onStop() {
        unregisterPackageReceiver();
        super.onStop();
    }

    private void registerPackageReceiver() {
        if (receiverRegistered) {
            return;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packageReceiver, filter);
        }

        receiverRegistered = true;
    }

    private void unregisterPackageReceiver() {
        if (!receiverRegistered) {
            return;
        }

        unregisterReceiver(packageReceiver);
        receiverRegistered = false;
    }

    private void loadApps() {
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolvedApps = packageManager.queryIntentActivities(launcherIntent, 0);
        if (resolvedApps == null) {
            resolvedApps = Collections.emptyList();
        }

        Collections.sort(resolvedApps, new ResolveInfo.DisplayNameComparator(packageManager));

        apps.clear();
        labels.clear();

        String ownPackageName = getPackageName();
        for (ResolveInfo info : resolvedApps) {
            ActivityInfo activityInfo = info.activityInfo;
            if (activityInfo == null || activityInfo.packageName == null || activityInfo.name == null) {
                continue;
            }

            if (ownPackageName.equals(activityInfo.packageName)) {
                continue;
            }

            apps.add(info);

            CharSequence label = info.loadLabel(packageManager);
            labels.add(label == null || label.length() == 0
                    ? activityInfo.packageName
                    : label.toString());
        }

        adapter.notifyDataSetChanged();

        if (labels.isEmpty()) {
            if (!emptyStateShown) {
                Toast.makeText(this, EMPTY_MESSAGE, Toast.LENGTH_LONG).show();
                emptyStateShown = true;
            }
        } else {
            emptyStateShown = false;
        }
    }

    private void launchApp(int position) {
        ActivityInfo activityInfo = getActivityInfo(position);
        if (activityInfo == null) {
            Toast.makeText(this, OPEN_ERROR_MESSAGE, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent launchIntent = packageManager.getLaunchIntentForPackage(activityInfo.packageName);
        if (launchIntent == null) {
            launchIntent = new Intent(Intent.ACTION_MAIN);
            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            launchIntent.setClassName(activityInfo.packageName, activityInfo.name);
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        try {
            startActivity(launchIntent);
        } catch (RuntimeException exception) {
            Toast.makeText(this, OPEN_ERROR_MESSAGE, Toast.LENGTH_SHORT).show();
        }
    }

    private void uninstallApp(int position) {
        ActivityInfo activityInfo = getActivityInfo(position);
        if (activityInfo == null) {
            Toast.makeText(this, REMOVE_ERROR_MESSAGE, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent removeIntent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + activityInfo.packageName));
        try {
            startActivity(removeIntent);
        } catch (RuntimeException exception) {
            Toast.makeText(this, REMOVE_ERROR_MESSAGE, Toast.LENGTH_SHORT).show();
        }
    }

    private ActivityInfo getActivityInfo(int position) {
        if (position < 0 || position >= apps.size()) {
            return null;
        }

        ResolveInfo info = apps.get(position);
        if (info == null) {
            return null;
        }

        return info.activityInfo;
    }

    private static final class LabelAdapter extends ArrayAdapter<String> {

        LabelAdapter(Context context, List<String> labels) {
            super(context, android.R.layout.simple_list_item_1, labels);
        }

        @Override
        public TextView getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
            TextView view = (TextView) super.getView(position, convertView, parent);
            view.setTextColor(Color.BLACK);
            view.setPadding(32, 24, 32, 24);
            return view;
        }
    }
}
