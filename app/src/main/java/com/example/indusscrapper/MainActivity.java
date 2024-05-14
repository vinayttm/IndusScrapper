package com.example.indusscrapper;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.EditText;
import android.widget.Toast;

import com.example.indusscrapper.Repository.QueryUPIStatus;
import com.example.indusscrapper.Services.IndusRecorderService;

import java.util.List;

import com.example.indusscrapper.Utils.Config;
import com.example.indusscrapper.Utils.SharedData;
import com.example.testappjava.R;

public class MainActivity extends AppCompatActivity {

    private EditText editText1, editText2, editText3;
    private SharedPreferences sharedPreferences;
    static boolean isAccessibilityServiceEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        determineIfAppInstalled();

        if (!isAccessibilityServiceEnabled(this, IndusRecorderService.class)) {
            showAccessibilityDialog();
        }

        Intent serviceIntent = new Intent(this, IndusRecorderService.class);
        startService(serviceIntent);

        editText1 = findViewById(R.id.editText1);
        editText2 = findViewById(R.id.editText2);
        editText3 = findViewById(R.id.editText0);

        sharedPreferences = getSharedPreferences(Config.packageName, MODE_PRIVATE);
        editText1.setText(sharedPreferences.getString("loginId", ""));
        editText2.setText(sharedPreferences.getString("loginPin", ""));
        editText3.setText(sharedPreferences.getString("bankLoginId", ""));

        if (!editText1.getText().toString().isEmpty()) {
            Config.loginId = editText1.getText().toString();
        }
        if (!editText2.getText().toString().isEmpty()) {
            Config.loginPin = editText2.getText().toString();
        }
        if (!editText3.getText().toString().isEmpty()) {
            Config.bankLoginId = editText2.getText().toString();
        }

    }

    private void determineIfAppInstalled() {
        if (!appInstalledOrNot(Config.packageName)) {
            Toast.makeText(this, "Induslnd Bank App is not installed", Toast.LENGTH_LONG).show();
        }
    }

    public void onAppFlowStarted(View view) {
        String text1 = editText1.getText().toString().trim();
        String text2 = editText2.getText().toString().trim();
        String text3 = editText3.getText().toString().trim();

        if (text1.isEmpty() || text2.isEmpty() || text3.isEmpty()) {
            Toast.makeText(this, "Both text fields must be filled.", Toast.LENGTH_SHORT).show();
            return;
        }

        Config.loginId = text1;
        Config.loginPin = text2;
        Config.bankLoginId = text3;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("loginId", text1);
        editor.putString("loginPin", text2);
        editor.putString("bankLoginId", text3);
        editor.apply();

        new QueryUPIStatus(() -> {

//            SharedData.startedChecking = true
            Intent intent = getPackageManager().getLaunchIntentForPackage(Config.packageName);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            Handler handler = new Handler();
            handler.postDelayed(() -> {
//                SharedData.startedChecking = true;
                Intent serviceIntent;
                if (!isAccessibilityServiceEnabled(this, IndusRecorderService.class)) {
                    showAccessibilityDialog();
                } else {
                    serviceIntent = new Intent(this, IndusRecorderService.class);
                    startService(serviceIntent);
                }
            }, 1000);
        }, () -> {
            runOnUiThread(() -> {
                Toast.makeText(this, "Scrapper inactive", Toast.LENGTH_LONG).show();
            });
        }).evaluate();


    }

    public static boolean isAccessibilityServiceEnabled(Context context, Class<?> serviceClass) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am != null) {
            List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
            for (AccessibilityServiceInfo service : enabledServices) {
                ComponentName enabledServiceComponentName = new ComponentName(service.getResolveInfo().serviceInfo.packageName, service.getResolveInfo().serviceInfo.name);
                ComponentName expectedServiceComponentName = new ComponentName(context, serviceClass);
                if (enabledServiceComponentName.equals(expectedServiceComponentName)) {
                    Log.d("App", "Application has accessibility permissions");
                    return true;
                }
            }
        }
        Log.d("App", "Application does not have accessibility permissions");
        return false;
    }

    private void showAccessibilityDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Accessibility Permission Required");
        builder.setMessage("To use this app, you need to enable Accessibility Service. Go to Settings to enable it?");
        builder.setPositiveButton("Settings", (dialog, which) -> openAccessibilitySettings());
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.setCancelable(false);
        builder.show();
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    private boolean appInstalledOrNot(String uri) {
        PackageManager pm = getPackageManager();
        try {
            pm.getPackageInfo(uri, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {

        }
        return false;
    }
}