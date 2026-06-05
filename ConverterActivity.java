package com.example.currnecyconeverterbyserega.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.currnecyconeverterbyserega.R;
import com.example.currnecyconeverterbyserega.repository.CurrencyRepository;
import com.example.currnecyconeverterbyserega.ui.adapter.CurrencySpinnerAdapter;
import com.example.currnecyconeverterbyserega.ui.model.Currency;
import com.example.currnecyconeverterbyserega.utils.NumberFormatUtils;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ConverterActivity extends AppCompatActivity {

    private Spinner spinnerSource;
    private Spinner spinnerTarget;
    private TextInputEditText etSourceAmount;
    private TextInputEditText etTargetAmount;
    private TextInputLayout tilTargetAmount;
    private View bannerOffline;
    private TextView tvOfflineMessage;
    private TextView tvSyncTime;
    private View btnRefresh;
    private ValueAnimator successFlashAnimator;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean isLoadingRates;
    private boolean offlineBannerVisible;

    private CurrencyRepository repository;
    private List<Currency> currencyList;
    private boolean suppressConversion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        repository = CurrencyRepository.getInstance(this);

        initViews();
        setupCurrencyList();
        setupSpinners();
        setupAmountInputs();
        setupButtons();

        loadRates(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerNetworkCallback();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (offlineBannerVisible && isNetworkAvailable()) {
            loadRates(true);
        }
    }

    @Override
    protected void onStop() {
        unregisterNetworkCallback();
        super.onStop();
    }

    private void initViews() {
        spinnerSource = findViewById(R.id.spinner_source);
        spinnerTarget = findViewById(R.id.spinner_target);
        etSourceAmount = findViewById(R.id.et_source_amount);
        etTargetAmount = findViewById(R.id.et_target_amount);
        tilTargetAmount = findViewById(R.id.til_target_amount);
        bannerOffline = findViewById(R.id.banner_offline);
        tvOfflineMessage = findViewById(R.id.tv_offline_message);
        tvSyncTime = findViewById(R.id.tv_sync_time);
        btnRefresh = findViewById(R.id.btn_refresh);

        int resultBlue = ContextCompat.getColor(this, R.color.result_blue);
        etTargetAmount.setTextColor(resultBlue);
    }

    private void setupCurrencyList() {
        currencyList = new ArrayList<>();
        currencyList.add(new Currency("USD", "Доллар США", "🇺🇸"));
        currencyList.add(new Currency("EUR", "Евро", "🇪🇺"));
        currencyList.add(new Currency("RUB", "Российский рубль", "🇷🇺"));
        currencyList.add(new Currency("GBP", "Британский фунт", "🇬🇧"));
        currencyList.add(new Currency("JPY", "Японская иена", "🇯🇵"));
        currencyList.add(new Currency("CNY", "Китайский юань", "🇨🇳"));
        currencyList.add(new Currency("AUD", "Австралийский доллар", "🇦🇺"));
        currencyList.add(new Currency("CAD", "Канадский доллар", "🇨🇦"));
        currencyList.add(new Currency("CHF", "Швейцарский франк", "🇨🇭"));
        currencyList.add(new Currency("KZT", "Казахстанский тенге", "🇰🇿"));
        currencyList.add(new Currency("BYN", "Белорусский рубль", "🇧🇾"));
        currencyList.add(new Currency("UAH", "Украинская гривна", "🇺🇦"));
        currencyList.add(new Currency("INR", "Индийская рупия", "🇮🇳"));
        currencyList.add(new Currency("TRY", "Турецкая лира", "🇹🇷"));
        currencyList.add(new Currency("AED", "Дирхам ОАЭ", "🇦🇪"));
        currencyList.add(new Currency("SGD", "Сингапурский доллар", "🇸🇬"));
    }

    private void setupSpinners() {
        CurrencySpinnerAdapter adapterSource = new CurrencySpinnerAdapter(this, currencyList);
        CurrencySpinnerAdapter adapterTarget = new CurrencySpinnerAdapter(this, currencyList);

        spinnerSource.setAdapter(adapterSource);
        spinnerTarget.setAdapter(adapterTarget);
        spinnerSource.setSelection(0);
        spinnerTarget.setSelection(1);

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                performConversion(true, false);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };

        spinnerSource.setOnItemSelectedListener(listener);
        spinnerTarget.setOnItemSelectedListener(listener);
    }

    private void setupAmountInputs() {
        etSourceAmount.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!suppressConversion && etSourceAmount.hasFocus()) {
                    performConversion(true, true);
                }
            }
        });

        etTargetAmount.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!suppressConversion && etTargetAmount.hasFocus()) {
                    performConversion(false, false);
                }
            }
        });
    }

    private void setupButtons() {
        btnRefresh.setOnClickListener(v -> loadRates(true));

        findViewById(R.id.btn_swap).setOnClickListener(v -> {
            int sourcePos = spinnerSource.getSelectedItemPosition();
            int targetPos = spinnerTarget.getSelectedItemPosition();
            spinnerSource.setSelection(targetPos);
            spinnerTarget.setSelection(sourcePos);
        });
    }

    private void loadRates(boolean forceRefresh) {
        if (isLoadingRates) {
            return;
        }
        isLoadingRates = true;
        btnRefresh.setEnabled(false);
        repository.fetchRates(forceRefresh, new CurrencyRepository.RatesCallback() {
            @Override
            public void onSuccess(Map<String, Double> rates, boolean offlineMode, long lastUpdatedTimeMs) {
                isLoadingRates = false;
                btnRefresh.setEnabled(true);
                offlineBannerVisible = offlineMode;
                updateSyncIndicator(lastUpdatedTimeMs);
                updateOfflineBanner(offlineMode, lastUpdatedTimeMs);

                if (forceRefresh && !offlineMode) {
                    showSnackbar(getString(R.string.rates_updated));
                } else if (forceRefresh && offlineMode) {
                    showSnackbar(getString(R.string.rates_cache_used));
                }

                performConversion(true, false);
            }

            @Override
            public void onFailure(Throwable t) {
                isLoadingRates = false;
                btnRefresh.setEnabled(true);
                offlineBannerVisible = false;
                bannerOffline.setVisibility(View.GONE);
                tvSyncTime.setText(R.string.sync_never);
                showSnackbarWithRetry(
                        getString(R.string.rates_load_failed),
                        () -> loadRates(true)
                );
            }
        });
    }

    private void updateSyncIndicator(long lastUpdatedTimeMs) {
        if (lastUpdatedTimeMs <= 0) {
            tvSyncTime.setText(R.string.sync_never);
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        tvSyncTime.setText(getString(R.string.sync_last, sdf.format(new Date(lastUpdatedTimeMs))));
    }

    private void updateOfflineBanner(boolean offlineMode, long lastUpdatedTimeMs) {
        if (!offlineMode) {
            offlineBannerVisible = false;
            bannerOffline.setVisibility(View.GONE);
            return;
        }
        offlineBannerVisible = true;
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        String time = lastUpdatedTimeMs > 0
                ? sdf.format(new Date(lastUpdatedTimeMs))
                : getString(R.string.sync_unknown);
        tvOfflineMessage.setText(getString(R.string.offline_banner, time));
        bannerOffline.setVisibility(View.VISIBLE);
    }

    private void registerNetworkCallback() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null || networkCallback != null) {
            return;
        }

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    if (offlineBannerVisible && isNetworkAvailable()) {
                        loadRates(true);
                    }
                });
            }
        };

        connectivityManager.registerNetworkCallback(request, networkCallback);
    }

    private void unregisterNetworkCallback() {
        if (networkCallback == null) {
            return;
        }
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
        networkCallback = null;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void performConversion(boolean fromSourceToTarget, boolean showSuccessFlash) {
        if (repository.getCachedRatesInMemory() == null) {
            return;
        }

        Currency sourceCurrency = (Currency) spinnerSource.getSelectedItem();
        Currency targetCurrency = (Currency) spinnerTarget.getSelectedItem();
        if (sourceCurrency == null || targetCurrency == null) {
            return;
        }

        suppressConversion = true;
        try {
            if (fromSourceToTarget) {
                String inputStr = getInputText(etSourceAmount);
                if (inputStr.isEmpty()) {
                    etTargetAmount.setText("");
                    return;
                }
                try {
                    double amount = Double.parseDouble(inputStr.replace(',', '.'));
                    double result = repository.convert(
                            amount,
                            sourceCurrency.getCode(),
                            targetCurrency.getCode()
                    );
                    etTargetAmount.setText(NumberFormatUtils.formatAmount(result));
                    etTargetAmount.setTextColor(ContextCompat.getColor(this, R.color.result_blue));
                    if (showSuccessFlash && result != 0) {
                        flashSuccessHighlight();
                    }
                } catch (NumberFormatException e) {
                    etTargetAmount.setText("");
                }
            } else {
                String inputStr = getInputText(etTargetAmount);
                if (inputStr.isEmpty()) {
                    etSourceAmount.setText("");
                    return;
                }
                try {
                    double amount = Double.parseDouble(inputStr.replace(',', '.'));
                    double result = repository.convert(
                            amount,
                            targetCurrency.getCode(),
                            sourceCurrency.getCode()
                    );
                    etSourceAmount.setText(NumberFormatUtils.formatAmount(result));
                } catch (NumberFormatException e) {
                    etSourceAmount.setText("");
                }
            }
        } finally {
            suppressConversion = false;
        }
    }

    private String getInputText(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private void flashSuccessHighlight() {
        if (tilTargetAmount == null) {
            return;
        }

        if (successFlashAnimator != null && successFlashAnimator.isRunning()) {
            successFlashAnimator.cancel();
        }

        int normalColor = ContextCompat.getColor(this, R.color.white);
        int flashColor = ContextCompat.getColor(this, R.color.success_flash);

        successFlashAnimator = ValueAnimator.ofObject(
                new ArgbEvaluator(),
                normalColor,
                flashColor,
                normalColor
        );
        successFlashAnimator.setDuration(550);
        successFlashAnimator.addUpdateListener(animation ->
                tilTargetAmount.setBoxBackgroundColor((int) animation.getAnimatedValue())
        );
        successFlashAnimator.start();
    }

    @Override
    protected void onDestroy() {
        if (successFlashAnimator != null) {
            successFlashAnimator.cancel();
        }
        unregisterNetworkCallback();
        super.onDestroy();
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(R.id.main), message, Snackbar.LENGTH_LONG).show();
    }

    private void showSnackbarWithRetry(String message, Runnable retryAction) {
        Snackbar.make(findViewById(R.id.main), message, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.retry, v -> retryAction.run())
                .show();
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
