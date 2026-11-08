package de.sprachfox.app;

import android.app.Activity;
import android.content.Intent;
import android.speech.RecognizerIntent;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.ArrayList;

// Spracherkennung ueber den Google-Sprachdialog des Handys.
// Braucht keine eigene Mikrofon-Berechtigung, weil der Dialog das Mikrofon selbst nutzt.
@CapacitorPlugin(name = "Speech")
public class SpeechPlugin extends Plugin {

    private Intent buildIntent(String lang, String prompt) {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        if (lang != null && lang.length() > 0) {
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
            i.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{lang});
        }
        if (prompt != null && prompt.length() > 0) {
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, prompt);
        }
        return i;
    }

    @PluginMethod
    public void available(PluginCall call) {
        JSObject o = new JSObject();
        boolean ok = false;
        try {
            ok = buildIntent("de-DE", null).resolveActivity(getContext().getPackageManager()) != null;
        } catch (Exception e) {
            ok = false;
        }
        o.put("available", ok);
        call.resolve(o);
    }

    @PluginMethod
    public void listen(PluginCall call) {
        Intent i = buildIntent(call.getString("lang", "de-DE"), call.getString("prompt", ""));
        try {
            if (i.resolveActivity(getContext().getPackageManager()) == null) {
                call.reject("Auf diesem Handy ist keine Spracherkennung installiert (Google-App fehlt).");
                return;
            }
            startActivityForResult(call, i, "onSpeech");
        } catch (Exception e) {
            call.reject("Spracherkennung konnte nicht gestartet werden: " + e.getMessage());
        }
    }

    @ActivityCallback
    private void onSpeech(PluginCall call, ActivityResult result) {
        if (call == null) {
            return;
        }
        JSObject o = new JSObject();
        JSArray arr = new JSArray();
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            ArrayList<String> list = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (list != null) {
                for (String s : list) {
                    arr.put(s);
                }
            }
        }
        o.put("matches", arr);
        call.resolve(o);
    }
}
