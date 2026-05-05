package com.example.purelauncher;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

final class NetworkUtils {

    private NetworkUtils() {
    }

    static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null)
            return false;

        Network network = cm.getActiveNetwork();
        if (network == null)
            return false;

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        if (capabilities == null)
            return false;

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
}
