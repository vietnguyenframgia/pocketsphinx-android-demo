package edu.cmu.pocketsphinx.demo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
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

public class CallingActivity extends AppCompatActivity implements
        RecognitionListener, View.OnClickListener {

    /* Named searches allow to quickly reconfigure the decoder */
    private static final String KWS_SEARCH = "wakeup";
    private static final String CANCEL = "cancel";


    private SpeechRecognizer recognizer;
    private Button btn_cancel;
    private TextView textPhoneNumber;
    private TextView textNetwork;


    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        // Prepare the data for UI
        setContentView(R.layout.activity_calling);
        new CallingActivity.SetupTask(this).execute();

        btn_cancel = findViewById(R.id.btn_cancel);
        btn_cancel.setOnClickListener(this);
        textPhoneNumber = findViewById(R.id.txtPhoneNumber);
        textNetwork = findViewById(R.id.txtNetwork);
        Intent intent = getIntent();
        String PhoneNumber = intent.getStringExtra("");
        textPhoneNumber.setText(PhoneNumber);
        String is_network = CheckNetworkHome(GetHeadNumber(PhoneNumber));
        textNetwork.setText( "Network : "+ is_network);
    }

    private String GetHeadNumber(String PhoneNumber){
        String NumberHead = "";
        String[] data = PhoneNumber.split("");
        for(int i = 0 ; i < 2 ; i++){
           NumberHead += data[i];
        }
        return NumberHead;
    }

    private static class SetupTask extends AsyncTask<Void, Void, Exception> {
        WeakReference<CallingActivity> activityReference;

        SetupTask(CallingActivity activity) {
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
                ((TextView) activityReference.get().findViewById(R.id.txtPhoneNumber))
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

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_cancel:
                recognizer.stop();
                recognizer.cancel();
                recognizer.shutdown();
                Intent startIntent = new Intent(this, DialActivity.class);
                startActivity(startIntent);
                finish();
                break;
            default:
                break;
        }
    }

    private String CheckNetworkHome(String head){
        String headNumber = "";
        if(head.equals("03") || head.equals("097") || head.equals("098") || head.equals("096") || head.equals("086")){
            headNumber = "Vietel Network" + "cước phí 1300p";
        }else if(head.equals("07")|| head.equals("089") || head.equals("090") || head.equals("093") ){
            headNumber = "Mobifone Network" + "cước phí 1500p";
        }else if(head.equals("08")|| head.equals("088") || head.equals("094") || head.equals("091") ){
            headNumber = "Vinaphone Network" + "cước phí 1400p";
        }else {
            headNumber = "Unknown Network" + "cước phí ?";
        }
        return headNumber;
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
        if (text.equals(CANCEL)) {
            Toast.makeText(CallingActivity.this , "Call End" , Toast.LENGTH_LONG).show();
            recognizer.stop();
            final Intent pocketIntent = new Intent(CallingActivity.this, PocketSphinxActivity.class);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    startActivity(pocketIntent);
                    finish();
                }
            }, 3000);
        }
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
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
            recognizer.startListening(searchName, 10000);
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))

                .setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)

                .getRecognizer();
        recognizer.addListener(this);

        // Create keyword-activation search.
        recognizer.addKeyphraseSearch(KWS_SEARCH, CANCEL);
    }

    @Override
    public void onError(Exception error) {
        ((TextView) findViewById(R.id.txtPhoneNumber)).setText(error.getMessage());
    }

    @Override
    public void onTimeout() {
        switchSearch(KWS_SEARCH);
    }
}

