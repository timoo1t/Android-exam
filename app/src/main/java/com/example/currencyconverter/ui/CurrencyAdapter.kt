package com.example.currencyconverter.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.example.currencyconverter.R
import com.example.currencyconverter.model.Currency

class CurrencyAdapter(
    context: Context,
    items: List<Currency>
) : ArrayAdapter<Currency>(context, 0, items) {

    private val inflater = LayoutInflater.from(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        bind(position, convertView, parent, dropdown = false)

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
        bind(position, convertView, parent, dropdown = true)

    private fun bind(position: Int, convertView: View?, parent: ViewGroup, dropdown: Boolean): View {
        val view = convertView ?: inflater.inflate(R.layout.item_currency, parent, false)
        val item = getItem(position) ?: return view
        view.findViewById<TextView>(R.id.tvFlag).text = item.flag
        view.findViewById<TextView>(R.id.tvCode).text = item.code
        view.findViewById<TextView>(R.id.tvName).apply {
            text = item.name
            visibility = if (dropdown) View.VISIBLE else View.GONE
        }
        return view
    }
}
