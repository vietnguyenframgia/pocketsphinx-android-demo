/* ====================================================================
 * Copyright (c) 2014 Alpha Cephei Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY ALPHA CEPHEI INC. ``AS IS'' AND
 * ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL CARNEGIE MELLON UNIVERSITY
 * NOR ITS EMPLOYEES BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 */

package edu.cmu.pocketsphinx.demo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

public class PocketSphinxActivity extends Activity implements View.OnClickListener,
        RecognitionListener {

    /* Named searches allow to quickly reconfigure the decoder */
    private static final String KWS_SEARCH = "wakeup";
    private static final String FORECAST_SEARCH = "forecast";
    private static final String DIGITS_SEARCH = "digits";
    private static final String PHONE_SEARCH = "phones";
    private static final String MENU_SEARCH = "menu";

    /* Keyword we are looking for to activate menu */
    private static final String KEYPHRASE = "oh mighty computer";

    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;
    private static final int MY_PERMISSIONS_REQUEST_CALL_PHONE = 1;
    private static final int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 1;

    private SpeechRecognizer recognizer;
    private HashMap<String, Integer> captions;
    TelephonyManager mTelephonyManager;
    private TextView tvResults;
    private TextView textViewCall;
    private Button btnCall;
    private Button btnReject;
    private boolean isWakeUp = false;
    private boolean isFeedBack = false;
    private boolean Decision = false ;


    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);
        mTelephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        //UI - Main
        tvResults = findViewById(R.id.result_text);
        textViewCall = findViewById(R.id.tvCall);
        btnCall = findViewById(R.id.btn_call);
        btnReject = findViewById(R.id.btn_cancel);

        // set onClick Event
        btnCall.setOnClickListener(this);
        btnReject.setOnClickListener(this);

        // Prepare the data for UI
        captions = new HashMap<>();
        captions.put(KWS_SEARCH, R.string.kws_caption);
        captions.put(MENU_SEARCH, R.string.menu_caption);
        captions.put(DIGITS_SEARCH, R.string.digits_caption);
        captions.put(PHONE_SEARCH, R.string.phone_caption);
        captions.put(FORECAST_SEARCH, R.string.forecast_caption);
        ((TextView) findViewById(R.id.caption_text))
                .setText("Preparing for Call Phone");

        // Check for dial
        isTelephoneEnabled();
        CheckCallPhoneButton();

        // Check if user has given permission to record audio
        int permissionCheckAudio = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        int permissionCheckCallPhone = ContextCompat.checkSelfPermission(getApplicationContext(),Manifest.permission.CALL_PHONE);
        int permissionCheckContact = ContextCompat.checkSelfPermission(getApplicationContext(),Manifest.permission.READ_CONTACTS);

        if (permissionCheckAudio != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            return;
        }

        if(permissionCheckCallPhone != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, MY_PERMISSIONS_REQUEST_CALL_PHONE);
            return;
        }

        if(permissionCheckContact != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.READ_CONTACTS},MY_PERMISSIONS_REQUEST_READ_CONTACTS);
        }
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new SetupTask(this).execute();
    }

    private boolean isTelephoneEnabled() {
        if (mTelephonyManager != null) {
            if (mTelephonyManager.getSimState() == TelephonyManager.SIM_STATE_READY) {
                return true;
            }
        }
        return false;
    }

    private boolean checkPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void CallPhone(final String phoneNumber){
        if (!TextUtils.isEmpty(phoneNumber)) {
            if (checkPermission(Manifest.permission.CALL_PHONE)) {
                String dial = "tel :" + phoneNumber;
                textViewCall.setText("Calling...."+ phoneNumber);
                Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse(dial));
                callIntent.setData(Uri.parse(phoneNumber));
                Intent chooser= Intent.createChooser(callIntent,"title");
                startActivity(chooser);
            } else {
                Toast.makeText(PocketSphinxActivity.this, "Permission Call Phone denied", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(PocketSphinxActivity.this, "Enter a phone number", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean WakeUp(String wakeup){
        // make decisions for calling by sppech : "Calling to phone number : " Words
        // Reject the dial
        if(wakeup.equals("Wake Up")){
            isFeedBack = true;
            return isWakeUp;
        }
        return false;
    }

    private boolean FeedBack(Boolean isFeedBack){
        // the app will feedback for user "Ok" and "No" when calling
        if(isFeedBack){
            tvResults.setText("OK");
            isFeedBack = true;
        }else {
            tvResults.setText("NO");
            isFeedBack = false;
        }
        return isFeedBack;
    }

    private void ReadPhoneNumber(String phoneNumber){
        // Phone Number
    }

    private boolean MakeDecision(String decision){
        // Calling and Cancel
        if(decision.equals("Dial")){
            Decision = true;
        }
        if(decision.equals("Cancel")){
            Decision = false;
        }
        return Decision;
    }

    private void CallingBySpeechRegcontion(){
        String results = GetData();
        boolean wake = WakeUp("Wake Up");
        Boolean isFeedback = FeedBack(wake);
        if(isFeedback){
            ReadPhoneNumber(results);
            boolean decision = MakeDecision("Dial");
            if(decision){
                CallPhone(results);
            }else {
                return;
            }

        }else {
            Toast.makeText(PocketSphinxActivity.this , "Plase check the device ", Toast.LENGTH_LONG).show();
        }
    }

    private String GetData(){
        String Res = null;
        String PhoneNumber ;
        String Voice ;
        String Data = tvResults.getText().toString();
        for(int i = 0 ; i < Data.length() ; i++){
            // get wake up , decision , feedback , phone number

        }
        return Res;
    }

    private void CheckCallPhoneButton(){
        if (checkPermission(Manifest.permission.CALL_PHONE)) {
            btnCall.setEnabled(true);
        } else {
            btnReject.setEnabled(false);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, MY_PERMISSIONS_REQUEST_CALL_PHONE);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.btn_call:
                String phoneNumber = "+84963638496";
                CallPhone(phoneNumber);
                Toast.makeText(PocketSphinxActivity.this, "Hello CallPhone Function", Toast.LENGTH_LONG).show();
                break;
            case R.id.btn_cancel:
                recognizer.stop();
                break;
            default:
                break;
        }
    }

    private static class SetupTask extends AsyncTask<Void, Void, Exception> {
        WeakReference<PocketSphinxActivity> activityReference;
        SetupTask(PocketSphinxActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }
        @Override
        protected Exception doInBackground(Void... params) {
            try {
                Assets assets = new Assets(activityReference.get());
                File assetDir = assets.syncAssets();
                activityReference.get().setupRecognizer(assetDir);
            } catch (IOException e) {
                return e;
            }
            return null;
        }
        @Override
        protected void onPostExecute(Exception result) {
            if (result != null) {
                ((TextView) activityReference.get().findViewById(R.id.caption_text))
                        .setText("Failed to init recognizer " + result);
            } else {
                activityReference.get().switchSearch(KWS_SEARCH);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull  int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                new SetupTask(this).execute();
            } else {
                finish();
            }
        }
        if(requestCode == MY_PERMISSIONS_REQUEST_CALL_PHONE){
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                new SetupTask(this).execute();
            }else{
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }
    }

    /**
     * In partial result we get quick updates about current hypothesis. In
     * keyword spotting mode we can react here, in other modes we need to wait
     * for final result in onResult.
     */
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;

        String text = hypothesis.getHypstr();
        if (text.equals(KEYPHRASE))
            switchSearch(MENU_SEARCH);
        else if (text.equals(DIGITS_SEARCH))
            switchSearch(DIGITS_SEARCH);
        else if (text.equals(PHONE_SEARCH))
            switchSearch(PHONE_SEARCH);
        else if (text.equals(FORECAST_SEARCH))
            switchSearch(FORECAST_SEARCH);
        else
            ((TextView) findViewById(R.id.result_text)).setText(text);
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        ((TextView) findViewById(R.id.result_text)).setText("");
        if (hypothesis != null) {
            String text = hypothesis.getHypstr(); // data recognition
            tvResults.setText(text);
        }
    }

    @Override
    public void onBeginningOfSpeech() {
        //Toast.makeText(PocketSphinxActivity.this, "Welcome to Speech Recognition ", Toast.LENGTH_LONG).show();
    }

    /**
     * We stop recognizer here to get a final result
     */
    @Override
    public void onEndOfSpeech() {
        if (!recognizer.getSearchName().equals(KWS_SEARCH))
            switchSearch(KWS_SEARCH);
    }

    private void switchSearch(String searchName) {
        recognizer.stop();

        // If we are not spotting, start listening with timeout (10000 ms or 10 seconds).
        if (searchName.equals(KWS_SEARCH))
            recognizer.startListening(searchName);
        else
            recognizer.startListening(searchName, 10000);

        String caption = getResources().getString(captions.get(searchName));
        ((TextView) findViewById(R.id.caption_text)).setText(caption);
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them

        recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))

                .setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)

                .getRecognizer();
        recognizer.addListener(this);

        /* In your application you might not need to add all those searches.
          They are added here for demonstration. You can leave just one.
         */

        // Create keyword-activation search.
        recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);

        // Create grammar-based search for selection between demos
        File menuGrammar = new File(assetsDir, "menu.gram");
        recognizer.addGrammarSearch(MENU_SEARCH, menuGrammar);

        // Create grammar-based search for digit recognition
        File digitsGrammar = new File(assetsDir, "digits.gram");
        recognizer.addGrammarSearch(DIGITS_SEARCH, digitsGrammar);

        // Create language model search
        File languageModel = new File(assetsDir, "weather.dmp");
        recognizer.addNgramSearch(FORECAST_SEARCH, languageModel);

        // Phonetic search
        File phoneticModel = new File(assetsDir, "en-phone.dmp");
        recognizer.addAllphoneSearch(PHONE_SEARCH, phoneticModel);
    }

    @Override
    public void onError(Exception error) {
        ((TextView) findViewById(R.id.caption_text)).setText(error.getMessage());
    }

    @Override
    public void onTimeout() {
        //switchSearch(KWS_SEARCH);
    }
}
