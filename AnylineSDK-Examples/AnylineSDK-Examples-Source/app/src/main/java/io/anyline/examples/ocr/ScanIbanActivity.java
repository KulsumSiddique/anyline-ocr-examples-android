package io.anyline.examples.ocr;

import android.graphics.PointF;
import android.hardware.Camera;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import java.util.List;

import at.nineyards.anyline.camera.AnylineViewConfig;
import at.nineyards.anyline.camera.FocusConfig;
import at.nineyards.anyline.modules.ocr.AnylineOcrConfig;
import at.nineyards.anyline.modules.ocr.AnylineOcrError;
import at.nineyards.anyline.modules.ocr.AnylineOcrListener;
import at.nineyards.anyline.modules.ocr.AnylineOcrResult;
import at.nineyards.anyline.modules.ocr.AnylineOcrScanView;
import io.anyline.examples.R;
import io.anyline.examples.SettingsFragment;
import io.anyline.examples.ocr.result.IbanResultView;

public class ScanIbanActivity extends AppCompatActivity {

    private static final String TAG = ScanIbanActivity.class.getSimpleName();
    private AnylineOcrScanView scanView;
    private IbanResultView ibanResultView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Set the flag to keep the screen on (otherwise the screen may go dark during scanning)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_anyline_ocr);

        addIbanResultView();

        String license = getString(R.string.anyline_license_key);
        // Get the view from the layout
        scanView = (AnylineOcrScanView) findViewById(R.id.scan_view);
        // Configure the view (cutout, the camera resolution, etc.) via json (can also be done in xml in the layout)
        scanView.setConfig(new AnylineViewConfig(this, "iban_view_config.json"));

        // Copies given traineddata-file to a place where the core can access it.
        // This MUST be called for every traineddata file that is used (before startScanning() is called).
        // The file must be located directly in the assets directory (or in tessdata/ but no other folders are allowed)
        scanView.copyTrainedData("tessdata/eng_no_dict.traineddata", "d142032d86da1be4dbe22dce2eec18d7");
        scanView.copyTrainedData("tessdata/deu.traineddata", "2d5190b9b62e28fa6d17b728ca195776");

        //Configure the OCR for IBANs
        AnylineOcrConfig anylineOcrConfig = new AnylineOcrConfig();
        // use the line mode (line length and font may vary)
        anylineOcrConfig.setScanMode(AnylineOcrConfig.ScanMode.LINE);
        // set the languages used for OCR
        anylineOcrConfig.setTesseractLanguages("eng_no_dict", "deu");
        // allow only capital letters and numbers
        anylineOcrConfig.setCharWhitelist("ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890");
        // set the height range the text can have
        anylineOcrConfig.setMinCharHeight(20);
        anylineOcrConfig.setMaxCharHeight(60);
        // The minimum confidence required to return a result, a value between 0 and 100.
        // (higher confidence means less likely to get a wrong result, but may be slower to get a result)
        anylineOcrConfig.setMinConfidence(65);
        // a simple regex for a basic validation of the IBAN, results that don't match this, will not be returned
        // (full validation is more complex, as different countries have different formats)
        anylineOcrConfig.setValidationRegex("^[A-Z]{2}([0-9A-Z]\\s*){13,32}$");
        // removes small contours (helpful in this case as no letters with small artifacts are allowed, like iöäü)
        anylineOcrConfig.setRemoveSmallContours(true);
        // removes whitespaces from the result
        // (also causes faster processing, because optimizations can be made if whitespaces are not relevant)
        anylineOcrConfig.setRemoveWhitespaces(true);
        // Experimental parameter to set the minimum sharpness (value between 0-100; 0 to turn sharpness detection off)
        // The goal of the minimum sharpness is to avoid a time consuming ocr step,
        // if the image is blurry and good results are therefor not likely.
        anylineOcrConfig.setMinSharpness(66);
        // set the ocr config
        scanView.setAnylineOcrConfig(anylineOcrConfig);

        // set an individual focus configuration for this example
        FocusConfig focusConfig = new FocusConfig.Builder()
                .setDefaultMode(Camera.Parameters.FOCUS_MODE_AUTO) // set default focus mode to be auto focus
                .setAutoFocusInterval(8000) // set an interval of 8 seconds for auto focus
                .setEnableFocusOnTouch(true) // enable focus on touch functionality
                .setEnablePhaseAutoFocus(true)  // enable phase focus for faster focusing on new devices
                .setEnableFocusAreas(true)  // enable focus areas to coincide with the cutout
                .build();
        // set the focus config
        scanView.setFocusConfig(focusConfig);
        // set the highest possible preview fps range
        scanView.setUseMaxFpsRange(true);
        // set sports scene mode to try and bump up the fps count even more
        scanView.setSceneMode(Camera.Parameters.SCENE_MODE_SPORTS);

        // initialize with the license and a listener
        scanView.initAnyline(license, new AnylineOcrListener() {
            @Override
            public void onReport(String identifier, Object value) {
                // Called with interesting values, that arise during processing.
                // Some possibly reported values:
                //
                // $brightness - the brightness of the center region of the cutout as a float value
                // $confidence - the confidence, an Integer value between 0 and 100
                // $thresholdedImage - the current image transformed into black and white
                // $sharpness - the detected sharpness value (only reported if minSharpness > 0)
            }

            @Override
            public boolean onTextOutlineDetected(List<PointF> list) {
                // Called when the outline of a possible text is detected.
                // If false is returned, the outline is drawn automatically.
                return false;
            }

            @Override
            public void onResult(AnylineOcrResult result) {
                // Called when a valid result is found (minimum confidence is exceeded and validation with regex was ok)
                ibanResultView.setResult(result.getText());
                ibanResultView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAbortRun(AnylineOcrError code, String message) {
                // Is called when no result was found for the current image.
                // E.g. if no text was found or the result is not valid.
            }
        });

        // disable the reporting if set to off in preferences
        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                SettingsFragment.KEY_PREF_REPORTING_ON, true)) {
            // The reporting of results - including the photo of a scanned meter -
            // helps us in improving our product, and the customer experience.
            // However, if you wish to turn off this reporting feature, you can do it like this:
            scanView.setReportingEnabled(false);
        }
        ibanResultView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ibanResultView.setVisibility(View.INVISIBLE);
                scanView.startScanning();
            }
        });
    }

    private void addIbanResultView() {
        RelativeLayout mainLayout = (RelativeLayout) findViewById(R.id.main_layout);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
        params.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);

        ibanResultView = new IbanResultView(this);
        ibanResultView.setVisibility(View.INVISIBLE);

        mainLayout.addView(ibanResultView, params);
    }

    @Override
    protected void onResume() {
        super.onResume();

        scanView.startScanning();
    }

    @Override
    protected void onPause() {
        super.onPause();

        scanView.cancelScanning();
        scanView.releaseCameraInBackground();
    }

    @Override
    public void onBackPressed() {
        if (ibanResultView.getVisibility() == View.VISIBLE) {
            ibanResultView.setVisibility(View.INVISIBLE);
            scanView.startScanning();
        } else {
            super.onBackPressed();
        }

    }
}
