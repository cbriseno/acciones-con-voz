package org.brisoft.voiceactions

import android.annotation.TargetApi
import android.app.Dialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import kotlin.collections.ArrayList

private const val TAG = "dialog_tag"

class MainActivity : AppCompatActivity() {

    private var tts: TextToSpeech? = null
    private var voices: List<Voice>? = null
    private var voiceTitles: List<String>? = null

    private val supportVoices = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        tts = TextToSpeech(applicationContext, TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                onTtsReady()
            } else {
                onTtsFailure(status)
            }
        })

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        if (savedInstanceState != null && supportFragmentManager.findFragmentByTag(TAG) != null) {
            supportFragmentManager.findFragmentByTag(TAG)
                ?.let { it as? VoiceDialog }
                ?.also { fragment ->
                    savedInstanceState.getParcelableArray("voices")
                        ?.let { it.map { item -> item as? Voice } }
                        ?.filterNotNull()
                        ?.let { cachedVoices ->
                            savedInstanceState.getStringArrayList("titles")
                                ?.let { cachedTitles ->
                                    fragment.setup(cachedVoices, cachedTitles)
                                }
                        }
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        return if (supportVoices) {
            menuInflater.inflate(R.menu.main, menu)
            true
        } else {
            false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.action_settings -> {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                if (voices?.isEmpty() == false && supportVoices && supportFragmentManager.findFragmentByTag(TAG) == null) {
                    VoiceDialog()
                        .also { fragment ->
                            voices?.let { voices ->
                                voiceTitles?.let { titles ->
                                    fragment.setup(voices, titles)
                                }
                            }
                        }
                        .show(supportFragmentManager, TAG)
                }
                true
            }
            else -> false
        }
    }

    private fun VoiceDialog.setup(voices: List<Voice>, titles: List<String>) {
        this.items = titles.toTypedArray()
        this.voices = voices
        this@MainActivity.tts?.let { tts = it }
        onVoiceSelected = { saveVoice(it) }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        voices?.map { it as Parcelable }?.toTypedArray()?.let { outState?.putParcelableArray("voices", it) }
        voiceTitles?.let { outState?.putStringArrayList("titles", ArrayList(it)) }
    }

    private fun onTtsReady() {
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        if (supportVoices) {
            voices = tts?.voices
                ?.asSequence()
                ?.filter { it.locale.language.toLowerCase().contains("es") }
                ?.filter { it.name.toLowerCase().contains("male") }
                ?.filter { !it.name.toLowerCase().contains("female") }
                ?.toList()?.also {
                    voiceTitles = it.mapIndexed { index, _ -> "Voz #${index+1}" }
                }
        }

        tts?.setLanguage(Locale("es", "ES"))
        loadVoice()
        setupListeners()
    }

    private fun onTtsFailure(error: Int) {
        Toast.makeText(this, "Error: ${error}", Toast.LENGTH_LONG).show()
    }

    private fun setupListeners() {
        yes.setSound("Sí")
        no.setSound("No")
        food.setSound("Comida")
        water.setSound("Agua")
        restroom.setSound("Baño")
        bath.setSound("Quiero bañarme")
        sleepy.setSound("Tengo sueño")
        tv.setSound("Quiero ver televisión")
        heat.setSound("Tengo calor")
        cold.setSound("Tengo frío")
        music.setSound("Quiero música")
        sad.setSound("Me siento triste")
        stress.setSound("Me siento estresado")
        pain.setSound("Siento dolor")
        assistance.setSound("Necesito algo")
        help.setSound("Tengo una emergencia")
    }

    private fun View.setSound(text: String) {
        setOnClickListener { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null) }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun saveVoice(voice: Voice) {
        getSharedPreferences("default", Context.MODE_PRIVATE)
            .edit()
            .also {
                it.putString("voice_name", voice.name)
            }
            .apply()
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun loadVoice() {
        getSharedPreferences("default", Context.MODE_PRIVATE)
            .getString("voice_name", null)
            ?.let { name -> voices?.find { it.name == name } }
            ?.let { tts?.setVoice(it) }
    }
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class VoiceDialog: DialogFragment() {

    var items: Array<String> = emptyArray()
    var voices: List<Voice> = emptyList()
    var tts: TextToSpeech? = null
    var originalVoice: Voice? = null
    var selectedVoice: Voice? = null
    var onVoiceSelected: ((Voice) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        originalVoice = tts?.voice
        savedInstanceState?.let { it.getParcelable("tmp_voice") as? Voice }
            ?.let { selectedVoice = it }
        return AlertDialog.Builder(requireActivity())
            .setTitle("Voces disponibles en este dispositivo")
            .setPositiveButton("Usar") { _, _ ->
                selectedVoice?.let {
                    onVoiceSelected?.invoke(it)
                    tts?.voice = it
                }
            }
            .setSingleChoiceItems(items, -1) { _, index ->
                voices.getOrNull(index)?.let { voice ->
                    selectedVoice = voice
                    tts?.voice = voice
                    tts?.speak("Voz número ${index+1}", TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
            .setNegativeButton("Cancelar") { _, _ ->
                originalVoice?.let { tts?.setVoice(it) }
            }
            .setCancelable(true)
            .create()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedVoice?.let { outState.putParcelable("tmp_voice", it) }
    }
}