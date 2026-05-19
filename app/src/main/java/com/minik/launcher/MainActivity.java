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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {

    private ListView listView;
    private AppAdapter adapter;
    private PackageManager pm;

    private static class AppItem {
        String label;
        Drawable icon;
        String packageName;
        String className;

        AppItem(String label, Drawable icon, String packageName, String className) {
            this.label = label;
            this.icon = icon;
            this.packageName = packageName;
            this.className = className;
        }
    }

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
        listView.setDivider(null);
        listView.setDividerHeight(0);
        setContentView(listView);

        pm = getPackageManager();
        loadApps();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            AppItem item = adapter.getItem(position);
            launchApp(item);
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            AppItem item = adapter.getItem(position);
            showContextMenu(item);
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
        new Thread(() -> {
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> infos = pm.queryIntentActivities(intent, 0);
            
            List<AppItem> items = new ArrayList<>();
            for (ResolveInfo info : infos) {
                items.add(new AppItem(
                        info.loadLabel(pm).toString(),
                        info.loadIcon(pm),
                        info.activityInfo.packageName,
                        info.activityInfo.name
                ));
            }

            Collections.sort(items, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label));

            runOnUiThread(() -> {
                adapter = new AppAdapter(MainActivity.this, items);
                listView.setAdapter(adapter);
            });
        }).start();
    }

    private void launchApp(AppItem item) {
        if (item != null) {
            ComponentName name = new ComponentName(item.packageName, item.className);
            Intent launchIntent = new Intent(Intent.ACTION_MAIN);
            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            launchIntent.setComponent(name);
            startActivity(launchIntent);
        }
    }

    private void showContextMenu(final AppItem item) {
        String[] options = {"App Info", "Uninstall"};
        new AlertDialog.Builder(this)
                .setTitle(item.label)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + item.packageName));
                        startActivity(intent);
                    } else if (which == 1) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + item.packageName));
                            startActivity(intent);
                        } catch (Exception ignored) {}
                    }
                })
                .show();
    }

    private static class AppAdapter extends ArrayAdapter<AppItem> {
        public AppAdapter(Context context, List<AppItem> items) {
            super(context, 0, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view;
            if (convertView == null) {
                view = new TextView(getContext());
                view.setTextSize(16);
                view.setTextColor(Color.WHITE);
                view.setCompoundDrawablePadding(24);
                view.setGravity(android.view.Gravity.CENTER_VERTICAL);
                view.setPadding(32, 0, 32, 0);
            } else {
                view = (TextView) convertView;
            }

            int parentHeight = parent.getHeight();
            if (parentHeight > 0) {
                int itemHeight = parentHeight / 11;
                ViewGroup.LayoutParams lp = view.getLayoutParams();
                if (lp == null) {
                    lp = new android.widget.AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemHeight);
                } else {
                    lp.height = itemHeight;
                }
                view.setLayoutParams(lp);
            }

            AppItem item = getItem(position);
            if (item != null) {
                view.setText(item.label);
                int iconSize = (int) (view.getTextSize() * 1.5);
                item.icon.setBounds(0, 0, iconSize, iconSize);
                view.setCompoundDrawables(item.icon, null, null, null);
            }

            return view;
        }
    }
}
