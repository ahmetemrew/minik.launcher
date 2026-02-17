package com.minik.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {

    private ListView listView;
    private AppAdapter adapter;
    private PackageManager pm;
    private List<ResolveInfo> apps;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadApps();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        listView = new ListView(this);
        listView.setBackgroundColor(Color.TRANSPARENT);
        setContentView(listView);

        pm = getPackageManager();
        loadApps();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            ResolveInfo info = adapter.getItem(position);
            launchApp(info);
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            ResolveInfo info = adapter.getItem(position);
            showContextMenu(info);
            return true;
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(packageReceiver);
    }

    private void loadApps() {
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        apps = pm.queryIntentActivities(intent, 0);
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                return String.CASE_INSENSITIVE_ORDER.compare(
                        a.loadLabel(pm).toString(),
                        b.loadLabel(pm).toString()
                );
            }
        });

        adapter = new AppAdapter(this, apps, pm);
        listView.setAdapter(adapter);
    }

    private void launchApp(ResolveInfo info) {
        if (info != null) {
            ComponentName name = new ComponentName(
                    info.activityInfo.applicationInfo.packageName,
                    info.activityInfo.name
            );
            Intent launchIntent = new Intent(Intent.ACTION_MAIN);
            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            launchIntent.setComponent(name);
            startActivity(launchIntent);
        }
    }

    private void showContextMenu(final ResolveInfo info) {
        String[] options = {"App Info", "Uninstall"};
        new AlertDialog.Builder(this)
                .setTitle(info.loadLabel(pm))
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + info.activityInfo.packageName));
                        startActivity(intent);
                    } else if (which == 1) {
                        String packageName = info.activityInfo.packageName;
                        try {
                            Uri packageUri = Uri.parse("package:" + packageName);
                            Intent intent = new Intent(Intent.ACTION_DELETE, packageUri);
                            startActivity(intent);
                        } catch (Exception e) {
                            // Silent fail or minimal error handling for release
                        }
                    }
                })
                .show();
    }

    private static class AppAdapter extends ArrayAdapter<ResolveInfo> {
        private final PackageManager pm;

        public AppAdapter(Context context, List<ResolveInfo> apps, PackageManager pm) {
            super(context, 0, apps);
            this.pm = pm;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view;
            if (convertView == null) {
                view = new TextView(getContext());
                view.setTextSize(16);
                view.setPadding(20, 20, 20, 20);
                view.setTextColor(Color.WHITE);
                view.setCompoundDrawablePadding(20);
            } else {
                view = (TextView) convertView;
            }

            ResolveInfo item = getItem(position);
            if (item != null) {
                view.setText(item.loadLabel(pm));
                Drawable icon = item.loadIcon(pm);
                icon.setBounds(0, 0, 96, 96);
                view.setCompoundDrawables(icon, null, null, null);
            }

            return view;
        }
    }
}
