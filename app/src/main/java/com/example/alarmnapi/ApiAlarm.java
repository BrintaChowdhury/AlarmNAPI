package com.example.alarmnapi;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class ApiAlarm extends BroadcastReceiver {

    GoogleAccountCredential mCredential;
    private TextView mOutputText;
    ProgressDialog mProgress;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = { CalendarScopes.CALENDAR_READONLY };
    @Override
    public void onReceive(Context context, Intent intent) {

        private void getResultsFromApi() {
            if (! isGooglePlayServicesAvailable()) {
                acquireGooglePlayServices();
            } else if (mCredential.getSelectedAccountName() == null) {
                chooseAccount();
            } else if (! isDeviceOnline()) {
                mOutputText.setText("No network connection available.");
            } else {
                new MakeRequestTask(mCredential).execute();
            }
        }

        @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
        private void chooseAccount() {
            // Request the GET_ACCOUNTS permission via a user dialog
            if (EasyPermissions.hasPermissions(this, Manifest.permission.GET_ACCOUNTS)) {
                String accountName = getPreferences(Context.MODE_PRIVATE)
                        .getString(PREF_ACCOUNT_NAME, null);
                if (accountName != null) {
                    mCredential.setSelectedAccountName(accountName);
                    getResultsFromApi();
                } else {
                    // Start a dialog from which the user can choose an account
                    startActivityForResult(
                            mCredential.newChooseAccountIntent(),
                            REQUEST_ACCOUNT_PICKER);
                }
            } else {
                EasyPermissions.requestPermissions(this, "This app needs to access your Google account (via Contacts).", REQUEST_PERMISSION_GET_ACCOUNTS, Manifest.permission.GET_ACCOUNTS);
            }
        }

        @Override
        protected void onActivityResult(
        int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            switch(requestCode) {
                case REQUEST_GOOGLE_PLAY_SERVICES:
                    if (resultCode != RESULT_OK) {
                        mOutputText.setText(
                                "This app requires Google Play Services. Please install " +
                                        "Google Play Services on your device and relaunch this app.");
                    } else {
                        getResultsFromApi();
                    }
                    break;
                case REQUEST_ACCOUNT_PICKER:
                    if (resultCode == RESULT_OK && data != null &&
                            data.getExtras() != null) {
                        String accountName =
                                data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                        if (accountName != null) {
                            SharedPreferences settings =
                                    getPreferences(Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = settings.edit();
                            editor.putString(PREF_ACCOUNT_NAME, accountName);
                            editor.apply();
                            mCredential.setSelectedAccountName(accountName);
                            getResultsFromApi();
                        }
                    }
                    break;
                case REQUEST_AUTHORIZATION:
                    if (resultCode == RESULT_OK) {
                        getResultsFromApi();
                    }
                    break;
            }
        }

        @Override
        public void onRequestPermissionsResult(int requestCode,
        @NonNull String[] permissions,
        @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            EasyPermissions.onRequestPermissionsResult(
                    requestCode, permissions, grantResults, this);
        }

        @Override
        public void onPermissionsGranted(int requestCode, List<String> list) {
            // Do nothing.
        }


        private boolean isDeviceOnline() {
            ConnectivityManager connMgr =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
            return (networkInfo != null && networkInfo.isConnected());
        }

        /**
         * Check that Google Play services APK is installed and up to date.
         * @return true if Google Play Services is available and up to
         *     date on this device; false otherwise.
         */
        private boolean isGooglePlayServicesAvailable() {
            GoogleApiAvailability apiAvailability =
                    GoogleApiAvailability.getInstance();
            final int connectionStatusCode =
                    apiAvailability.isGooglePlayServicesAvailable(this);
            return connectionStatusCode == ConnectionResult.SUCCESS;
        }

        /**
         * Attempt to resolve a missing, out-of-date, invalid or disabled Google
         * Play Services installation via a user dialog, if possible.
         */
        private void acquireGooglePlayServices() {
            GoogleApiAvailability apiAvailability =
                    GoogleApiAvailability.getInstance();
            final int connectionStatusCode =
                    apiAvailability.isGooglePlayServicesAvailable(this);
            if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
                showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
            }
        }


        /**
         * Display an error dialog showing that Google Play Services is missing
         * or out of date.
         * @param connectionStatusCode code describing the presence (or lack of)
         *     Google Play Services on this device.
         */
        void showGooglePlayServicesAvailabilityErrorDialog(
        final int connectionStatusCode) {
            GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
            Dialog dialog = apiAvailability.getErrorDialog(
                    MainActivity.this,
                    connectionStatusCode,
                    REQUEST_GOOGLE_PLAY_SERVICES);
            dialog.show();
        }

        /**
         * An asynchronous task that handles the Google Calendar API call.
         * Placing the API calls in their own task ensures the UI stays responsive.
         */
        private class MakeRequestTask extends AsyncTask<Void, Void, java.util.List<String>> {
            private com.google.api.services.calendar.Calendar mService = null;
            private Exception mLastError = null;

            MakeRequestTask(GoogleAccountCredential credential) {
                HttpTransport transport = AndroidHttp.newCompatibleTransport();
                JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
                mService = new com.google.api.services.calendar.Calendar.Builder(
                        transport, jsonFactory, credential)
                        .setApplicationName("Google Calendar API Android Quickstart")
                        .build();
            }

            /**
             * Background task to call Google Calendar API.
             * @param params no parameters needed for this task.
             */
            @Override
            protected List<String> doInBackground(Void... params) {
                try {
                    return getDataFromApi();
                } catch (Exception e) {
                    mLastError = e;
                    cancel(true);
                    return null;
                }
            }

            /**
             * Fetch a list of the next 10 events from the primary calendar.
             * @return List of Strings describing returned events.
             * @throws IOException
             */
            private List<String> getDataFromApi() throws IOException {
                // List the next 10 events from the primary calendar.
                DateTime now = new DateTime(System.currentTimeMillis());
                List<String> eventStrings = new ArrayList<String>();
                Events events = mService.events().list("primary")
                        .setMaxResults(10)
                        .setTimeMin(now)
                        .setOrderBy("startTime")
                        .setSingleEvents(true)
                        .execute();
                List<Event> items = events.getItems();

                for (Event event : items) {
                    DateTime start = event.getStart().getDateTime();
                    DateTime end = event.getEnd().getDateTime();
                    if (start == null) {
                        // All-day events don't have start times, so just use
                        // the start date.
                        start = event.getStart().getDate();
                    }
                    eventStrings.add(
                            String.format("%s (%s)", event.getSummary(), start));
                    if (event.getEtag() == "class") {
                        //??if (now.getValue()>=start.getValue() && now.getValue()<end.getValue()){

                        //}
                    }
                }
                return eventStrings;
            }


            @Override
            protected void onPreExecute() {
                mOutputText.setText("");
                mProgress.show();
            }

            @Override
            protected void onPostExecute(List<String> output) {
                mProgress.hide();
                if (output == null || output.size() == 0) {
                    mOutputText.setText("No results returned.");
                } else {
                    output.add(0, "Data retrieved using the Google Calendar API:");
                    mOutputText.setText(TextUtils.join("\n", output));
                }
            }

            @Override
            protected void onCancelled() {
                mProgress.hide();
                if (mLastError != null) {
                    if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                        showGooglePlayServicesAvailabilityErrorDialog(
                                ((GooglePlayServicesAvailabilityIOException) mLastError)
                                        .getConnectionStatusCode());
                    } else if (mLastError instanceof UserRecoverableAuthIOException) {
                        startActivityForResult(
                                ((UserRecoverableAuthIOException) mLastError).getIntent(),
                                MainActivity.REQUEST_AUTHORIZATION);
                    } else {
                        mOutputText.setText("The following error occurred:\n"
                                + mLastError.getMessage());
                    }
                } else {
                    mOutputText.setText("Request cancelled.");
                }
            }
        }


    }
}
