package de.sprachfox.app;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.content.SharedPreferences;
import java.io.File;
import java.util.HashMap;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Vorlesen mit der Sprachausgabe des Handys (Android TextToSpeech).
@CapacitorPlugin(name = "Tts")
public class TtsPlugin extends Plugin {

    private static final String GOOGLE = "com.google.android.tts";

    private TextToSpeech tts;
    private boolean ready = false;
    private boolean initDone = false;
    private int initStatus = -99;
    private String engineUsed = "";
    private boolean triedGoogle = false;
    private final List<PluginCall> waiting = new ArrayList<PluginCall>();
    // Datei-Modus: Satz erst in eine Datei sprechen und selbst abspielen (hilft bei Fehler -4)
    private boolean fileMode = false;
    private final HashMap<String, String[]> pending = new HashMap<String, String[]>();
    private MediaPlayer player;

    @Override
    public void load() {
        try {
            fileMode = prefs().getBoolean("fileMode", false);
        } catch (Exception e) {
            fileMode = false;
        }
        init(null);
    }

    private void init(final String engine) {
        initDone = false;
        ready = false;
        TextToSpeech.OnInitListener l = new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                initStatus = status;
                if (status == TextToSpeech.SUCCESS) {
                    setup();
                    ready = true;
                    initDone = true;
                    flush();
                    return;
                }
                // Standard-Engine geht nicht: Google-Sprachausgabe direkt versuchen
                if (!triedGoogle) {
                    triedGoogle = true;
                    try {
                        if (tts != null) {
                            tts.shutdown();
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                    init(GOOGLE);
                    return;
                }
                initDone = true;
                flush();
            }
        };
        try {
            tts = engine == null ? new TextToSpeech(getContext(), l) : new TextToSpeech(getContext(), l, engine);
        } catch (Exception e) {
            initDone = true;
            initStatus = -98;
        }
    }

    private void setup() {
        try {
            engineUsed = String.valueOf(tts.getDefaultEngine());
        } catch (Exception e) {
            engineUsed = "";
        }
        try {
            tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        } catch (Exception e) {
            // ignore
        }
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String id) {
                if (id.startsWith("file:")) {
                    return;
                }
                send("start", id, 0);
            }

            @Override
            public void onDone(String id) {
                if (id.startsWith("file:")) {
                    playFile(id.substring(5));
                    return;
                }
                pending.remove(id);
                send("done", id, 0);
            }

            @Override
            @Deprecated
            public void onError(String id) {
                onError(id, -1);
            }

            @Override
            public void onError(String id, int code) {
                if (id.startsWith("file:")) {
                    send("error", id.substring(5), code);
                    return;
                }
                if (code == TextToSpeech.ERROR_OUTPUT && !fileMode) {
                    // Direkte Ausgabe klappt nicht: ab jetzt ueber Datei abspielen
                    fileMode = true;
                    try {
                        prefs().edit().putBoolean("fileMode", true).apply();
                    } catch (Exception e) {
                        // ignore
                    }
                    String[] p = pending.get(id);
                    if (p != null) {
                        speakToFile(id, p[0]);
                        return;
                    }
                }
                send("error", id, code);
            }
        });
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences("sprachfox_tts", Context.MODE_PRIVATE);
    }

    private void send(String ev, String id, int code) {
        JSObject o = new JSObject();
        o.put("id", id);
        o.put("code", code);
        notifyListeners(ev, o);
    }

    private File fileFor(String id) {
        return new File(getContext().getCacheDir(), "say.wav");
    }

    private void speakToFile(String id, String text) {
        try {
            stopPlayer();
            Bundle b = new Bundle();
            int r = tts.synthesizeToFile(text, b, fileFor(id), "file:" + id);
            if (r != TextToSpeech.SUCCESS) {
                send("error", id, -40);
            }
        } catch (Exception e) {
            send("error", id, -41);
        }
    }

    private synchronized void stopPlayer() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception e) {
                // ignore
            }
            try {
                player.release();
            } catch (Exception e) {
                // ignore
            }
            player = null;
        }
    }

    private synchronized void playFile(final String id) {
        stopPlayer();
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
            mp.setDataSource(fileFor(id).getAbsolutePath());
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer m) {
                    send("done", id, 0);
                }
            });
            mp.prepare();
            mp.start();
            player = mp;
            pending.remove(id);
            send("start", id, 0);
        } catch (Exception e) {
            send("error", id, -42);
        }
    }

    private synchronized void flush() {
        ArrayList<PluginCall> list = new ArrayList<PluginCall>(waiting);
        waiting.clear();
        for (PluginCall c : list) {
            doSpeak(c);
        }
    }

    private Locale toLocale(String tag) {
        if (tag == null || tag.length() == 0) {
            return Locale.GERMANY;
        }
        return Locale.forLanguageTag(tag);
    }

    private int mediaVolume() {
        try {
            AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            return am.getStreamVolume(AudioManager.STREAM_MUSIC);
        } catch (Exception e) {
            return -1;
        }
    }

    private void doSpeak(PluginCall call) {
        if (!ready || tts == null) {
            call.reject("init:" + initStatus);
            return;
        }
        String text = call.getString("text", "");
        Locale loc = toLocale(call.getString("lang", "de-DE"));
        int res = tts.setLanguage(loc);
        if (res < TextToSpeech.LANG_AVAILABLE) {
            res = tts.setLanguage(new Locale(loc.getLanguage()));
        }
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            call.reject("missing");
            return;
        }
        Double rate = call.getDouble("rate", 1.0);
        tts.setSpeechRate(rate == null ? 1.0f : rate.floatValue());
        tts.setPitch(1.0f);
        String id = "fox" + System.currentTimeMillis();
        pending.clear();
        pending.put(id, new String[]{text});
        if (fileMode) {
            tts.stop();
            speakToFile(id, text);
        } else {
            stopPlayer();
            int r = tts.speak(text, TextToSpeech.QUEUE_FLUSH, new Bundle(), id);
            if (r != TextToSpeech.SUCCESS) {
                call.reject("speak:" + r);
                return;
            }
        }
        JSObject o = new JSObject();
        o.put("id", id);
        o.put("volume", mediaVolume());
        o.put("lang", res);
        call.resolve(o);
    }

    @PluginMethod
    public void speak(PluginCall call) {
        synchronized (this) {
            if (!initDone) {
                waiting.add(call);
                return;
            }
        }
        doSpeak(call);
    }

    @PluginMethod
    public void stop(PluginCall call) {
        if (tts != null) {
            tts.stop();
        }
        stopPlayer();
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

    // Zustand der Sprachausgabe fuer die Fehlersuche
    @PluginMethod
    public void diag(PluginCall call) {
        JSObject o = new JSObject();
        o.put("ready", ready);
        o.put("initDone", initDone);
        o.put("initStatus", initStatus);
        o.put("engine", engineUsed);
        o.put("volume", mediaVolume());
        try {
            AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            o.put("maxVolume", am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        } catch (Exception e) {
            o.put("maxVolume", -1);
        }
        JSArray eng = new JSArray();
        try {
            if (tts != null) {
                for (TextToSpeech.EngineInfo e : tts.getEngines()) {
                    eng.put(e.name);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        o.put("engines", eng);
        o.put("fileMode", fileMode);
        call.resolve(o);
    }

    // Neu verbinden (z. B. nachdem eine Stimme installiert wurde)
    @PluginMethod
    public void restart(PluginCall call) {
        try {
            if (tts != null) {
                tts.shutdown();
            }
        } catch (Exception e) {
            // ignore
        }
        triedGoogle = false;
        fileMode = false;
        try {
            prefs().edit().putBoolean("fileMode", false).apply();
        } catch (Exception e) {
            // ignore
        }
        init(null);
        call.resolve();
    }

    // Lauter stellen: Medienlautstaerke auf etwa 60 Prozent
    @PluginMethod
    public void louder(PluginCall call) {
        try {
            AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(1, max * 6 / 10), AudioManager.FLAG_SHOW_UI);
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    // Oeffnet die Android-Einstellungen der Sprachausgabe
    @PluginMethod
    public void settings(PluginCall call) {
        try {
            Intent s = new Intent("com.android.settings.TTS_SETTINGS");
            s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(s);
            call.resolve();
        } catch (Exception e) {
            call.reject("Einstellungen lassen sich nicht oeffnen.");
        }
    }

    // Oeffnet das Herunterladen fehlender Stimmen
    @PluginMethod
    public void install(PluginCall call) {
        try {
            Intent i = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            settings(call);
        }
    }

    // Google-Sprachausgabe im Play Store oeffnen
    @PluginMethod
    public void getGoogle(PluginCall call) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=" + GOOGLE));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + GOOGLE));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
                call.resolve();
            } catch (Exception e2) {
                call.reject("Play Store laesst sich nicht oeffnen.");
            }
        }
    }

    @Override
    protected void handleOnDestroy() {
        stopPlayer();
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        super.handleOnDestroy();
    }
}
