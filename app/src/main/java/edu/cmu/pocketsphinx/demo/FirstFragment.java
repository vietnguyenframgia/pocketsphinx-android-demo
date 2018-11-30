package edu.cmu.pocketsphinx.demo;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;


public class FirstFragment extends Fragment implements
        RecognitionListener, View.OnClickListener{


    private static final String KWS_SEARCH = "wakeup";
    private static final String MENU_SEARCH = "menu";
    private static final String DIGITS_SEARCH = "digits";
    private static final String KEYPHRASE = "wake up for dial";
    private static final String DIAL = "dial";

    private SpeechRecognizer recognizer;
    private TextView resultText;
    private Button btn_Call;

    public FirstFragment() {
        // Required empty public constructor
    }

    public static FirstFragment newInstance() {
        FirstFragment firstfragment = new FirstFragment();
        return firstfragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_first, container, false);
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        resultText = view.findViewById(R.id.result_text);
        btn_Call = view.findViewById(R.id.btn_dial);
        btn_Call.setOnClickListener(this);
    }

    @Override
    public void onBeginningOfSpeech() {

    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_dial:
                //PocketSphinxActivity.CallingBySpeechRegcontion();
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                ft.add(android.R.id.content, PhoneCallFragment.newInstance()).addToBackStack(null);
                ft.commit();
                break;
            default:
                break;
        }
    }

    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;
        String text = hypothesis.getHypstr();
        if (text.equals(DIAL)) {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.add(android.R.id.content, PhoneCallFragment.newInstance()).addToBackStack(null);
            ft.commit();
        } else {
            if (resultText != null) {
                resultText.setText(text);
            }
        }
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        if (resultText != null) {
            resultText.setText("");
        }
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            Toast.makeText(getActivity(), text, Toast.LENGTH_LONG).show();
        }
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
            recognizer.startListening(searchName, 10000);
        else
            recognizer.startListening(searchName, 10000);

//        String caption = getResources().getString(captions.get(searchName));
//        ((TextView) findViewById(R.id.caption_text)).setText(caption);
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

    }

    @Override
    public void onError(Exception error) {
        if (resultText != null) {
            resultText.setText("");
        }
    }

    @Override
    public void onTimeout() {
        switchSearch(KWS_SEARCH);
    }
}
