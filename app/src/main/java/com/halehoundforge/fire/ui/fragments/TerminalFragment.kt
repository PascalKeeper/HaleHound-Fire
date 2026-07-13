package com.halehoundforge.fire.ui.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.halehoundforge.fire.R
import com.halehoundforge.fire.databinding.FragmentTerminalBinding
import com.halehoundforge.fire.terminal.OpsTerminalEngine
import com.halehoundforge.fire.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * Human-easy + Grok-friendly ops terminal.
 * Chips for thumbs; full commands for agents; transcript for both.
 */
class TerminalFragment : Fragment() {

    private var _binding: FragmentTerminalBinding? = null
    private val binding get() = _binding!!
    private lateinit var engine: OpsTerminalEngine
    private val transcript = StringBuilder()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTerminalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        engine = OpsTerminalEngine(requireContext().applicationContext)
        buildChips()

        if (transcript.isEmpty()) {
            appendBanner()
        } else {
            binding.output.text = transcript.toString()
        }

        binding.btnRun.setOnClickListener { runCurrent() }
        binding.btnClear.setOnClickListener {
            transcript.clear()
            binding.output.text = ""
            appendBanner()
        }
        binding.btnCopy.setOnClickListener {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("hhf-terminal", transcript.toString()))
            Toast.makeText(requireContext(), "Transcript copied", Toast.LENGTH_SHORT).show()
        }

        binding.input.setOnEditorActionListener { _, actionId, event ->
            val go = actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (go) {
                runCurrent()
                true
            } else false
        }
    }

    private fun appendBanner() {
        appendRaw(
            "hhf$ ready · HaleHound Fire ops console\n" +
                "Humans: tap chips. Agents: type `agent` then commands.\n" +
                "Family stays behind the glass — you keep on keeping on.\n"
        )
    }

    private fun buildChips() {
        val row = binding.chipRow
        row.removeAllViews()
        val green = ContextCompat.getColor(requireContext(), R.color.cyd_green)
        val pad = (6 * resources.displayMetrics.density).toInt()
        OpsTerminalEngine.QUICK_CHIPS.forEach { label ->
            val b = MaterialButton(
                requireContext(),
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = label.uppercase()
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setTextColor(green)
                minHeight = 0
                minimumHeight = (32 * resources.displayMetrics.density).toInt()
                insetTop = 0
                insetBottom = 0
                setPadding(pad * 2, pad, pad * 2, pad)
                setOnClickListener {
                    if (label == "clear") {
                        binding.btnClear.performClick()
                    } else {
                        binding.input.setText(label)
                        runCurrent()
                    }
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = pad }
            row.addView(b, lp)
        }
    }

    private fun runCurrent() {
        val cmd = binding.input.text?.toString()?.trim().orEmpty()
        if (cmd.isEmpty()) return
        binding.input.setText("")
        appendRaw("hhf$ $cmd\n")
        binding.btnRun.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val result = engine.execute(cmd)
            binding.btnRun.isEnabled = true
            if (result.output == "__CLEAR__") {
                transcript.clear()
                binding.output.text = ""
                appendBanner()
                return@launch
            }
            if (result.output.isNotBlank()) {
                appendRaw(result.output.trimEnd() + "\n")
            }
            result.navigate?.let { handleNav(it) }
            scrollBottom()
        }
    }

    private fun handleNav(target: String) {
        val act = activity as? MainActivity ?: return
        when (target) {
            "harden" -> act.navigateTo(R.id.nav_harden)
            "guard" -> act.navigateTo(R.id.nav_guard)
            "wifi" -> act.openWifi()
            "cyd" -> act.navigateTo(R.id.nav_cyd)
            "home" -> act.navigateTo(R.id.nav_home)
            "term" -> { /* already here */ }
            "ble" -> act.openBle()
            "about" -> act.openAbout()
        }
    }

    private fun appendRaw(s: String) {
        transcript.append(s)
        if (transcript.length > 60_000) {
            transcript.delete(0, transcript.length - 40_000)
        }
        binding.output.text = transcript.toString()
        scrollBottom()
    }

    private fun scrollBottom() {
        binding.scrollOut.post {
            binding.scrollOut.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
