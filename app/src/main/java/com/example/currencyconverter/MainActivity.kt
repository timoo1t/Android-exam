package com.example.currencyconverter

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.currencyconverter.data.CurrencyRepository
import com.example.currencyconverter.data.RatesResult
import com.example.currencyconverter.data.RatesSnapshot
import com.example.currencyconverter.databinding.ActivityMainBinding
import com.example.currencyconverter.model.Currencies
import com.example.currencyconverter.model.Currency
import com.example.currencyconverter.ui.CurrencyAdapter
import com.example.currencyconverter.util.DecimalInputFilter
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Текущий снимок курсов, который реально используется для расчёта. */
    private var snapshot: RatesSnapshot? = null

    /** Флаг для подавления рекурсии: когда мы программно ставим текст другому полю. */
    private var programmaticEdit = false

    /** Какое поле сейчас «ведущее» — на нём фокус, его правим, второе пересчитываем. */
    private enum class Side { FROM, TO }
    private var leader: Side = Side.FROM

    private val timeFormat by lazy { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        CurrencyRepository.init(this)

        setupSpinners()
        setupAmountFields()
        setupRefreshButton()

        // Показать кэш сразу (если есть), чтобы UI не висел пустым.
        CurrencyRepository.peekCache()?.let {
            snapshot = it
            updateStatusLabel(fromCache = true, hadNetworkError = false)
            recalc()
        }

        // Подгрузить актуальные курсы.
        loadRates(forceRefresh = false)
    }

    // -------------------- Setup --------------------

    private fun setupSpinners() {
        val adapter = CurrencyAdapter(this, Currencies.ALL)
        binding.spinnerFrom.adapter = adapter
        binding.spinnerTo.adapter = adapter
        binding.spinnerFrom.setSelection(Currencies.indexOf("USD"))
        binding.spinnerTo.setSelection(Currencies.indexOf("RUB"))

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                recalc()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.spinnerFrom.onItemSelectedListener = listener
        binding.spinnerTo.onItemSelectedListener = listener
    }

    private fun setupAmountFields() {
        val filters = arrayOf<InputFilter>(DecimalInputFilter(maxDecimals = 4))
        binding.etAmountFrom.filters = filters
        binding.etAmountTo.filters = filters

        binding.etAmountFrom.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) leader = Side.FROM
        }
        binding.etAmountTo.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) leader = Side.TO
        }

        binding.etAmountFrom.addTextChangedListener(amountWatcher(Side.FROM))
        binding.etAmountTo.addTextChangedListener(amountWatcher(Side.TO))

        binding.etAmountFrom.setText("1")
    }

    private fun setupRefreshButton() {
        binding.btnRefresh.setOnClickListener {
            loadRates(forceRefresh = true)
        }
    }

    // -------------------- Conversion --------------------

    private fun amountWatcher(side: Side) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (programmaticEdit) return
            // Изменение от пользователя в этом поле — оно становится ведущим.
            leader = side
            recalc()
        }
    }

    /** Пересчитывает значение в ведомом поле, основываясь на ведущем. */
    private fun recalc() {
        val snap = snapshot ?: return
        val from = (binding.spinnerFrom.selectedItem as? Currency)?.code ?: return
        val to = (binding.spinnerTo.selectedItem as? Currency)?.code ?: return

        val (sourceField, targetField, srcCode, dstCode) = when (leader) {
            Side.FROM -> Quad(binding.etAmountFrom, binding.etAmountTo, from, to)
            Side.TO   -> Quad(binding.etAmountTo, binding.etAmountFrom, to, from)
        }

        val amount = parseAmount(sourceField.text?.toString())
        if (amount == null) {
            setProgrammatic(targetField, "")
            return
        }
        val result = CurrencyRepository.convert(amount, srcCode, dstCode, snap)
        if (result.isNaN()) {
            setProgrammatic(targetField, "")
            return
        }
        setProgrammatic(targetField, formatAmount(result))
    }

    private fun parseAmount(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val normalized = raw.trim().replace(',', '.')
        return normalized.toDoubleOrNull()
    }

    private fun formatAmount(value: Double): String {
        // Округляем до 4 знаков, обрезаем «хвостовые» нули.
        val rounded = String.format(Locale.US, "%.4f", value)
        return rounded.trimEnd('0').trimEnd('.')
    }

    private fun setProgrammatic(field: EditText, text: String) {
        if (field.text?.toString() == text) return
        programmaticEdit = true
        field.setText(text)
        programmaticEdit = false
    }

    // -------------------- Data load --------------------

    private fun loadRates(forceRefresh: Boolean) {
        binding.btnRefresh.isEnabled = false
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = if (forceRefresh) {
                CurrencyRepository.refreshRates()
            } else {
                CurrencyRepository.getRates()
            }
            handleResult(result)
            binding.progress.visibility = View.GONE
            binding.btnRefresh.isEnabled = true
        }
    }

    private fun handleResult(result: RatesResult) {
        when (result) {
            is RatesResult.Success -> {
                snapshot = result.snapshot
                updateStatusLabel(fromCache = result.fromCache, hadNetworkError = false)
                recalc()
                if (!result.fromCache) {
                    showSnack(getString(R.string.rates_updated))
                }
            }
            is RatesResult.Error -> {
                val cached = result.cached
                if (cached != null) {
                    snapshot = cached
                    updateStatusLabel(fromCache = true, hadNetworkError = true)
                    recalc()
                    showSnack(getString(R.string.offline_using_cache_with_reason, result.message))
                } else {
                    updateStatusLabel(fromCache = false, hadNetworkError = true)
                    showSnack(getString(R.string.error_with_reason, result.message))
                }
            }
        }
    }

    private fun updateStatusLabel(fromCache: Boolean, hadNetworkError: Boolean) {
        val snap = snapshot
        if (snap == null) {
            binding.tvStatus.text = getString(R.string.status_no_data)
            return
        }
        val timeStr = timeFormat.format(Date(snap.savedAtMs))
        binding.tvStatus.text = when {
            hadNetworkError -> getString(R.string.status_offline, timeStr)
            fromCache && snap.isExpired() -> getString(R.string.status_offline, timeStr)
            else -> getString(R.string.status_updated, timeStr)
        }
    }

    private fun showSnack(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    /** Локальный неизменяемый «контейнер на четверых», чтобы не плодить лямбды. */
    private data class Quad(
        val source: EditText,
        val target: EditText,
        val sourceCode: String,
        val targetCode: String
    )
}
