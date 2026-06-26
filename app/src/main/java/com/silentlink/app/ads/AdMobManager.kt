package com.silentlink.app.ads

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

object AdMobManager {
    const val BANNER_AD_UNIT_ID = "ca-app-pub-9392221797396472/2615383609"

    fun initialize(context: Context) {
        MobileAds.initialize(context)
    }
}

@Composable
fun BannerAdView() {
    AndroidView(
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdMobManager.BANNER_AD_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
