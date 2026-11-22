package com.seguirviendo
import android.net.Uri

// Utilidades del navegador propio: direcciones permitidas y bloqueo de anuncios, rastreadores y ventanas de publicidad.
object WebUtil {
    fun strip(h: String): String = h.lowercase().removePrefix("www.")
    fun host(url: String): String = strip(try { Uri.parse(url).host ?: "" } catch (e: Exception) { "" })

    // Arregla lo que escribas: agrega https:// y exige que tenga un punto (pagina.com).
    fun norm(raw: String): String? {
        var t = raw.trim().replace(" ", "")
        if (t.isEmpty()) return null
        if (t.startsWith("http://")) t = "https://" + t.substring(7)
        if (!t.startsWith("https://")) t = "https://$t"
        val h = try { Uri.parse(t).host } catch (e: Exception) { null } ?: return null
        if (!h.contains(".")) return null
        return t
    }
}

object AdBlock {
    // Dominios conocidos de publicidad, ventanas emergentes y rastreo. Se bloquean ellos y todos sus subdominios.
    private val hosts: Set<String> = hashSetOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com", "google-analytics.com",
        "googletagservices.com", "googletagmanager.com", "pagead2.googlesyndication.com", "adsense.com", "admob.com",
        "taboola.com", "outbrain.com", "mgid.com", "revcontent.com", "zemanta.com", "adblade.com", "content.ad",
        "popads.net", "popcash.net", "popcdn.net", "propellerads.com", "propellerclick.com", "exoclick.com", "exosrv.com",
        "adsterra.com", "adsterratools.com", "juicyads.com", "trafficjunky.com", "trafficfactory.biz", "adcash.com",
        "clickadu.com", "hilltopads.net", "hilltopads.com", "monetag.com", "a-ads.com", "ad-maven.com", "admaven.com",
        "richpush.co", "onclickads.net", "onclkds.com", "clickaine.com", "adspyglass.com", "adxpansion.com", "bidvertiser.com",
        "revenuehits.com", "plugrush.com", "evadav.com", "pushame.com", "push-sdk.com", "notifpush.com", "pushprofit.net",
        "adnxs.com", "rubiconproject.com", "pubmatic.com", "openx.net", "criteo.com", "criteo.net", "adform.net",
        "smartadserver.com", "amazon-adsystem.com", "moatads.com", "adsrvr.org", "casalemedia.com", "indexww.com",
        "scorecardresearch.com", "quantserve.com", "hotjar.com", "mouseflow.com", "statcounter.com", "histats.com",
        "yandex.ru", "mc.yandex.ru", "adriver.ru", "mail.ru", "top-fwz1.mail.ru", "mxptint.net", "ad.gt", "adtng.com",
        "adtelligent.com", "advertising.com", "advertserve.com", "adsco.re", "adskeeper.com", "adscale.de", "adtech.de",
        "lijit.com", "sharethrough.com", "teads.tv", "teads.com", "spotxchange.com", "springserve.com", "yieldmo.com",
        "tsyndicate.com", "tubecorporate.com", "realsrv.com", "voluumtrk.com", "voluum.com", "adk2x.com", "bongacams.com",
        "dtscout.com", "dtscdn.com", "ero-advertising.com", "creative-serving.com", "syndication.exdynsrv.com", "kerg.net",
        "wpadmngr.com", "wpushorg.com", "ofcdn.com", "bestcontentweb.top", "betterstat.com", "cdn4ads.com", "clksite.com",
        "go.oclasrv.com", "oclasrv.com", "ad-delivery.net", "ad.doubleclick.net", "popunder.net", "pop-under.com",
        "trackingclick.net", "clickfuse.com", "linkbucks.com", "adf.ly", "shorte.st", "bc.vc", "ouo.io"
    )

    // Partes de la dirección que casi siempre son publicidad.
    private val words = listOf("/popunder", "/pop-under", "/popads", "/adserver", "/pagead/", "/banner_ads", "/ads/banner", "/advert/", "ad_frame", "/prebid")

    fun blocked(u: Uri): Boolean {
        val host = (u.host ?: return false).lowercase()
        var h = host
        while (true) {
            if (hosts.contains(h)) return true
            val i = h.indexOf('.')
            if (i < 0) break
            h = h.substring(i + 1)
            if (!h.contains('.')) break
        }
        if (host.startsWith("ads.") || host.startsWith("adserver.") || host.startsWith("tracking.") || host.startsWith("popunder.")) return true
        val path = (u.path ?: "").lowercase()
        return words.any { path.contains(it) }
    }

    // Esconde los huecos de publicidad más comunes dentro de la página y anula las ventanas nuevas.
    const val CLEAN_JS = "(function(){try{var s=document.createElement('style');" +
        "s.textContent='ins.adsbygoogle,.adsbygoogle,[id^=\"google_ads\"],iframe[src*=\"doubleclick\"],iframe[src*=\"googlesyndication\"]," +
        "iframe[src*=\"adsterra\"],iframe[src*=\"exoclick\"],[id^=\"div-gpt-ad\"],.ad-banner,.ads-banner,.popup-ad{display:none!important}';" +
        "document.documentElement.appendChild(s);window.open=function(){return null};}catch(e){}})();"
}
