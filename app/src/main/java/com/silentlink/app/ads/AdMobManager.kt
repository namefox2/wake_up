package com.silentlink.app.ads

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

object AdMobManager {
    const val BANNER_AD_UNIT_ID = "ca-app-pub-9392221797396472/2615383609"

    fun initialize(context: Context) {
        MobileAds.initialize(context)
    }

    fun buildAdRequest(adConsentAccepted: Boolean): AdRequest {
        return if (adConsentAccepted) {
            AdRequest.Builder().build()
        } else {
            val extras = Bundle().apply { putString("npa", "1") }
            AdRequest.Builder()
                .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
                .build()
        }
    }
}

@Composable
fun BannerAdView(adConsentAccepted: Boolean = false) {
    AndroidView(
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdMobManager.BANNER_AD_UNIT_ID
                loadAd(AdMobManager.buildAdRequest(adConsentAccepted))
            }
        }
    )
}
