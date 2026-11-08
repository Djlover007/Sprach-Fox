package de.sprachfox.app;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Locale;

// Vorlesen mit der Sprachausgabe des Handys (Android TextToSpeech).
@CapacitorPlugin(name = "Tts")
public class TtsPlugin extends Plugin {

    private TextToSpeech tts;
    private boolean ready = false;
    private PluginCall waiting;

    @Override
    public void load() {
        tts = new TextToSpeech(getContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                ready = status == TextToSpeech.SUCCESS;
                if (ready) {
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String id) {
                        }

                        @Override
                        public void onDone(String id) {
                            JSObject o = new JSObject();
                            o.put("id", id);
                            notifyListeners("done", o);
                        }

                        @Override
                        public void onError(String id) {
                            JSObject o = new JSObject();
                            o.put("id", id);
                            notifyListeners("done", o);
                        }
                    });
                }
                if (waiting != null) {
                    PluginCall c = waiting;
                    waiting = null;
                    doSpeak(c);
                }
            }
        });
    }

    private Locale toLocale(String tag) {
        if (tag == null || tag.length() == 0) {
            return Locale.GERMANY;
        }
        return Locale.forLanguageTag(tag);
    }

    private void doSpeak(PluginCall call) {
        if (!ready) {
            call.reject("Die Sprachausgabe des Handys ist nicht bereit.");
            return;
        }
        String text = call.getString("text", "");
        Locale loc = toLocale(call.getString("lang", "de-DE"));
        int res = tts.setLanguage(loc);
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            Locale base = new Locale(loc.getLanguage());
            res = tts.setLanguage(base);
        }
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            call.reject("missing");
            return;
        }
        Double rate = call.getDouble("rate", 1.0);
        tts.setSpeechRate(rate == null ? 1.0f : rate.floatValue());
        tts.setPitch(1.0f);
        Bundle b = new Bundle();
        String id = "fox" + System.currentTimeMillis();
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, b, id);
        JSObject o = new JSObject();
        o.put("id", id);
        call.resolve(o);
    }

    @PluginMethod
    public void speak(PluginCall call) {
        if (tts == null) {
            call.reject("Keine Sprachausgabe vorhanden.");
            return;
        }
        if (!ready) {
            if (waiting != null) {
                waiting.reject("abgebrochen");
            }
            waiting = call;
            return;
        }
        doSpeak(call);
    }

    @PluginMethod
    public void stop(PluginCall call) {
        if (tts != null) {
            tts.stop();
        }
        call.resolve();
    }

    @PluginMethod
    public void check(PluginCall call) {
        JSObject o = new JSObject();
        if (tts == null || !ready) {
            o.put("ok", false);
            call.resolve(o);
            return;
        }
        int r = tts.isLanguageAvailable(toLocale(call.getString("lang", "de-DE")));
        o.put("ok", r >= TextToSpeech.LANG_AVAILABLE);
        call.resolve(o);
    }

    // Oeffnet die Einstellungen, um fehlende Stimmen herunterzuladen.
    @PluginMethod
    public void install(PluginCall call) {
        try {
            Intent i = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            try {
                Intent s = new Intent("com.android.settings.TTS_SETTINGS");
                s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(s);
                call.resolve();
            } catch (Exception e2) {
                call.reject("Einstellungen lassen sich nicht oeffnen.");
            }
        }
    }

    @Override
    protected void handleOnDestroy() {
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        super.handleOnDestroy();
    }
}
