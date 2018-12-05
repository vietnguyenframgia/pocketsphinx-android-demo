package edu.cmu.pocketsphinx.demo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;


public class DialActivity extends AppCompatActivity implements
        RecognitionListener, View.OnClickListener {

    /* Named searches allow to quickly reconfigure the decoder */
    private static final String KWS_SEARCH = "wake up";
    private static final String DIGITS_SEARCH = "digits";
    private static final String DIAL = "dial";
    private static final String READ_DIGIT = "number";

    private SpeechRecognizer recognizer;
    TelephonyManager mTelephonyManager;
    private Button btn_call;
    private TextView txtResults;
    private TextView txtCaption;


    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_dial);
        new SetupTask(DialActivity.this).execute();

        mTelephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        btn_call = findViewById(R.id.btn_dial);
        btn_call.setOnClickListener(this);
        txtResults = findViewById(R.id.result_text);
        txtCaption = findViewById(R.id.dial_text);

        txtCaption.setText("Say : number");
    }

    private static class SetupTask extends AsyncTask<Void, Void, Exception> {
        WeakReference<DialActivity> activityReference;

        SetupTask(DialActivity activity) {
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
                ((TextView) activityReference.get().findViewById(R.id.result_text))
                        .setText("Failed to init recognizer " + result);
            } else {
                activityReference.get().switchSearch(KWS_SEARCH);
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

    private void CallPhone(final String phoneNumber) {
        if (!TextUtils.isEmpty(phoneNumber)) {
            if (checkPermission(Manifest.permission.CALL_PHONE)) {
                String dial = phoneNumber;
                Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse(dial));
                callIntent.setData(Uri.parse(phoneNumber));
                startActivity(callIntent);
            }
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_dial:
                if(!isTelephoneEnabled()){
                    String phone_number = txtResults.getText().toString();
                    //CallPhone(phone_number);
                    Intent callingIntent = new Intent(this, CallingActivity.class);
                    callingIntent.putExtra("" , phone_number);
                    startActivity(callingIntent);
                    finish();
                }else {
                    Toast.makeText(DialActivity.this, "FALSE" , Toast.LENGTH_LONG).show();
                }

                break;
            default:
                break;
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
        if (text.equals(READ_DIGIT)) {
            recognizer.stop();
            Toast.makeText(DialActivity.this, "READ DIGITS" , Toast.LENGTH_LONG).show();
            txtCaption.setText("To Say Your Phone Number");
            switchSearch(DIGITS_SEARCH);
        } else if (text.equals(DIAL)) {
            Toast.makeText(DialActivity.this, "DIAL" , Toast.LENGTH_LONG).show();
            recognizer.stop();
            Intent intentCalling = new Intent(DialActivity.this, CallingActivity.class);
            startActivity(intentCalling);
            finish();
        }
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            txtResults.setText(text);
            //Toast.makeText(DialActivity.this, text , Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBeginningOfSpeech() {
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
            recognizer.startListening(searchName);

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

        recognizer.addKeyphraseSearch(KWS_SEARCH, READ_DIGIT);
        // Create grammar-based search for digit recognition
        File digitsGrammar = new File(assetsDir, "digits.gram");
        recognizer.addGrammarSearch(DIGITS_SEARCH, digitsGrammar);

    }

    @Override
    public void onError(Exception error) {
        Toast.makeText(DialActivity.this, "ERROR" , Toast.LENGTH_LONG).show();

    }

    @Override
    public void onTimeout() {
        //switchSearch(KWS_SEARCH);
    }
}

