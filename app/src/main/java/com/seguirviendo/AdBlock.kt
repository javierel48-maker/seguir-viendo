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

    // Intenta pasar a pantalla completa los videos de la página apenas empiezan (solo si el navegador lo permite).
    const val FS_JS = "(function(){if(window.__svfs)return;window.__svfs=1;document.addEventListener('play',function(e){" +
        "try{var v=e.target;if(v&&v.requestFullscreen&&!document.fullscreenElement){v.requestFullscreen().catch(function(){});}}catch(x){}},true);})();"

    // Oculta las capas que quedan fijas sobre la página (carteles de publicidad), menos las que contienen un video.
    val CLEAN_OVERLAYS_JS = """
(function(){var n=0,W=innerWidth,H=innerHeight;
document.querySelectorAll('body *').forEach(function(e){try{
var cs=getComputedStyle(e);
if(cs.position!=='fixed'||cs.display==='none'||cs.visibility==='hidden')return;
if(e.hasAttribute('data-svmax')||e.id==='__svmaxbg'||e.tagName==='VIDEO'||e.querySelector('video'))return;
var r=e.getBoundingClientRect();
if(r.width*r.height<W*H*0.02)return;
e.style.setProperty('display','none','important');n++;}catch(x){}});
return n;})()
"""

    // Estira el video o reproductor más grande para que ocupe toda la pantalla; si ya está estirado, lo devuelve a su lugar.
    val MAX_JS = """
(function(){
var P=document.querySelector('[data-svmax]');
if(P){P.style.cssText=P.getAttribute('data-svmax');P.removeAttribute('data-svmax');
var b=document.getElementById('__svmaxbg');if(b)b.remove();
document.documentElement.style.overflow=window.__svov||'';return 'off';}
var c=[].slice.call(document.querySelectorAll('video,iframe')).map(function(e){var r=e.getBoundingClientRect();return {e:e,a:r.width*r.height};})
.filter(function(o){return o.a>20000;}).sort(function(x,y){return y.a-x.a;});
if(!c.length)return 'none';
var e=c[0].e;e.setAttribute('data-svmax',e.style.cssText||' ');
var bg=document.createElement('div');bg.id='__svmaxbg';
bg.style.cssText='position:fixed;left:0;top:0;width:100vw;height:100vh;background:#000;z-index:2147483646';
document.body.appendChild(bg);
e.style.cssText='position:fixed!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;z-index:2147483647!important;background:#000!important;border:0!important;margin:0!important;transform:none!important';
window.__svov=document.documentElement.style.overflow;document.documentElement.style.overflow='hidden';
return 'on';})()
"""
}
