package com.example.currencyconverter.util

import android.text.InputFilter
import android.text.Spanned

/**
 * InputFilter для полей ввода суммы.
 *
 * Разрешает:
 *  - цифры 0..9
 *  - один десятичный разделитель — точку ИЛИ запятую
 *  - до [maxDecimals] знаков после разделителя
 *
 * Блокирует буквы и любые повторные разделители — пользователь физически
 * не сможет ввести букву или две запятые.
 */
class DecimalInputFilter(
    private val maxDecimals: Int = 4
) : InputFilter {

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        // Что получится в итоге, если мы примем source как есть:
        val before = dest.subSequence(0, dstart).toString()
        val after = dest.subSequence(dend, dest.length).toString()
        val inserted = source.subSequence(start, end).toString()
        val result = before + inserted + after

        // Пустая строка — разрешаем (например, удаление).
        if (result.isEmpty()) return null

        // Проверка: только цифры, точка и запятая.
        if (!result.all { it.isDigit() || it == '.' || it == ',' }) {
            return ""  // отклоняем ввод
        }

        // Не больше одного разделителя.
        val separatorsCount = result.count { it == '.' || it == ',' }
        if (separatorsCount > 1) return ""

        // Ограничение по количеству знаков после разделителя.
        val sepIndex = result.indexOfAny(charArrayOf('.', ','))
        if (sepIndex >= 0 && result.length - sepIndex - 1 > maxDecimals) return ""

        // Запрет ведущих нулей вида "0123" (но "0", "0.5", "0,5" — ок).
        if (result.length >= 2 && result[0] == '0' && result[1].isDigit()) return ""

        return null  // принять ввод как есть
    }
}
