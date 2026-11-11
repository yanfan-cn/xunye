package io.legado.app.browser.reader

/**
 * Heuristic article extract for Safari-like reader (SPEC-010/014/041/044).
 * Returns JSON: {"title","html","text","cover"}.
 * [html] keeps paragraph + in-body images in DOM order for mixed layout.
 *
 * SPEC-044: nested containers recurse; never flatten via innerText (that dropped imgs).
 * SPEC-123: ad tokens are bounded so `image-reload` is not treated as an ad.
 * SPEC-211: prefer portal article roots; drop QR/share widgets and near-square control imgs.
 * SPEC-212: skip media nodes; emit jingshi poster thumbs for &lt;video&gt;; player chrome hints.
 * SPEC-217: optional strong extract (extra roots + chrome node hints); default off via prefs.
 */
object WebArticleExtractJs {
    /**
     * Image helpers shared by the WebView script and JVM tests.
     * `ads?` must not match the trailing "ad" inside `reload` / `load` / `upload`.
     */
    val imageFilterJs: String =
        """
        function abs(u){try{return new URL(u,document.baseURI).href;}catch(e){return u||'';}}
        function isBadImg(el,src,fromLazy){
        if(!src||src.indexOf('data:')===0)return true;
        var low=src.toLowerCase();
        if(/avatar|icon|logo|banner|pixel|1x1|spacer|blank\.gif|pagead|doubleclick|googlesyndication|adsbygoogle|qrcode|qr_code|qr-code|wxcode|miniprogram/.test(low))return true;
        if(/(?:^|[^a-z0-9])ads?(?![a-z0-9])/.test(low))return true;
        if(/(?:^|[^a-z0-9])advert/.test(low))return true;
        var cls=((el.className||'')+' '+(el.id||'')).toLowerCase();
        if(/avatar|icon|logo|emoji|sprite|banner|pixel|track|adsbygoogle|thumb|thumbnail|qrcode|qr_code|wxcode|share|v\-icon|verified/.test(cls))return true;
        if(/(?:^|[^a-z0-9])ads?(?![a-z0-9])/.test(cls))return true;
        if(/(?:^|[^a-z0-9])advert/.test(cls))return true;
        var aw=parseInt(el.getAttribute('width'),10)||0,ah=parseInt(el.getAttribute('height'),10)||0;
        if(fromLazy){
        if((aw>1&&aw<120)||(ah>1&&ah<120))return true;
        if(aw>=120&&ah>=120&&aw<=400&&ah<=400){var r=aw/ah;if(r>=0.85&&r<=1.15)return true;}
        }else{
        var w=el.naturalWidth||aw||el.width||0,h=el.naturalHeight||ah||el.height||0;
        if((w>0&&w<120)||(h>0&&h<120)||(w>0&&h>0&&w*h<14400))return true;
        if(w>=120&&h>=120&&w<=400&&h<=400){var rr=w/h;if(rr>=0.85&&rr<=1.15)return true;}
        }
        return false;
        }
        function firstSrcset(v){
        if(!v)return '';var p=(v.split(',')[0]||'').trim().split(/\s+/)[0]||'';return p;
        }
        function imgTag(el){
        // SPEC-259: extra lazy attrs (data-lazyload-src / data-echo / data-img).
        var lazy=el.getAttribute('data-src')||el.getAttribute('data-original')||el.getAttribute('data-lazy-src')||el.getAttribute('data-url')||el.getAttribute('data-actualsrc')||el.getAttribute('data-original-src')||el.getAttribute('data-lazyload-src')||el.getAttribute('data-echo')||el.getAttribute('data-img')||'';
        if(!lazy)lazy=firstSrcset(el.getAttribute('data-srcset'));
        var src=lazy||el.currentSrc||el.src||'';
        if((!src||src.indexOf('data:')===0)&&el.srcset){src=firstSrcset(el.srcset);}
        src=abs(src);
        if(isBadImg(el,src,!!lazy))return '';
        return '<img src="'+src.replace(/"/g,'')+'">';
        }
        """.trimIndent()

    /** Baseline extract (SPEC-211/212). Prefer [script] with prefs. */
    val SCRIPT: String get() = script(strongExtract = false)

    private val baseChromeHints =
        "nav|navbar|menu|breadcrumb|sidebar|aside|footer|header|toolbar|tabbar|comment|reply|" +
            "advert|ads?|banner|popup|modal|recommend|related|paginator|page-nav|chapter-nav|" +
            "read-nav|topnav|bottomnav|share|social|qrcode|qr-code|float|action-bar|tool-bar|" +
            "follow|ask-ai|yuanbao|txp-|tenvideo|tvp-|xgplayer|dplayer|video-js|vjs-|" +
            "player-info|video-info|videoinfo|media-info|video-player|tp-video|player-btn|" +
            "playback|quality|controls|svg-icon|volume|progress-bar|control-bar"

    /** SPEC-217/220: extra chrome when「极致净化」on. */
    private val strongChromeHints =
        "open-app|openapp|app-open|download-app|app-download|guide-download|promote|" +
            "listen-all|listen-full|askai|ai-ask|hot-news|hotnews|bottom-bar|top-bar|" +
            "fixed-bar|sticky-bar|float-btn|open-client|client-open|app-guide|download-bar|" +
            "share-bar|tool-box|toolbox|side-tool|widget|promo|incentive"

    private val baseRootSelectors =
        "#ArticleContent,.content-article,.qq-article,.article-content"

    /** SPEC-217: more portal / WeChat / news roots when strong. */
    private val strongRootSelectors =
        "#ArticleContent,.content-article,.qq-article,.article-content," +
            ".rich_media_content,#js_content,.post-content,.article-body,.news_txt," +
            ".content_area,.article__content,.article-detail,.main-content,#main-content," +
            ".post_body,.entry-content,#contentTxt,.contentTxt"

    /**
     * SPEC-217: [strongExtract]=true → stronger root prefer + more chrome node skip.
     */
    fun script(strongExtract: Boolean): String {
        val chrome = if (strongExtract) {
            "$baseChromeHints|$strongChromeHints"
        } else {
            baseChromeHints
        }
        val rootFirst = if (strongExtract) strongRootSelectors else baseRootSelectors
        return buildScript(chromeHints = chrome, rootFirstSelectors = rootFirst)
    }

    private fun buildScript(chromeHints: String, rootFirstSelectors: String): String =
        "(function(){" +
            imageFilterJs +
            "function meta(sel){var el=document.querySelector(sel);if(!el)return '';return el.getAttribute('content')||'';}" +
            "function textOf(el){if(!el)return '';return (el.innerText||el.textContent||'').replace(/\\s+\\n/g,'\\n').trim();}" +
            "function isSkip(tag){" +
            "return tag==='script'||tag==='style'||tag==='noscript'||tag==='svg'||tag==='button'" +
            "||tag==='nav'||tag==='footer'||tag==='header'||tag==='aside'||tag==='form'||tag==='iframe'" +
            "||tag==='audio'||tag==='source'||tag==='object'||tag==='embed'||tag==='track';" +
            "}" +
            // SPEC-057/211/212/217: skip chrome containers (share/QR + player + optional strong).
            "function isChromeNode(el){" +
            "if(!el||!el.getAttribute)return false;" +
            "var s=((el.className||'')+' '+(el.id||'')+' '+(el.getAttribute('role')||'')).toLowerCase();" +
            "return /\\b($chromeHints)\\b/.test(s);" +
            "}" +
            // SPEC-227: txp/腾讯播放器壳层整树会被 chrome 跳过；先抽封面再跳过。
            "function isVideoPlayerChrome(el){" +
            "if(!el||!el.getAttribute)return false;" +
            "var s=((el.className||'')+' '+(el.id||'')).toLowerCase();" +
            "return /\\b(txp-|tenvideo|tvp-|xgplayer|dplayer|video-js|vjs-|tp-video|video-player)\\b/.test(s);" +
            "}" +
            "function videoPosterFrom(node){" +
            "var poster='';" +
            "var v=node.tagName&&(node.tagName.toLowerCase()==='video')?node:(node.querySelector?node.querySelector('video'):null);" +
            "if(v)poster=abs(v.getAttribute('poster')||v.getAttribute('data-poster')||'');" +
            "if(!poster||poster.indexOf('http')!==0){" +
            "var img=node.querySelector?node.querySelector('img'):null;" +
            "if(img){var t=imgTag(img);var m=t&&t.match(/src=\\\"([^\\\"]+)\\\"/);if(m)poster=m[1];}" +
            "}" +
            "if((!poster||poster.indexOf('http')!==0)&&node.getAttribute){" +
            "var st=node.getAttribute('style')||'';" +
            "var bm=st.match(/url\\(['\\\"]?(https?:[^'\\\")\\\\s]+)/i);if(bm)poster=abs(bm[1]);" +
            "}" +
            "if((!poster||poster.indexOf('http')!==0)&&node.previousElementSibling){" +
            "var ps=node.previousElementSibling;" +
            "var psi=(ps.tagName||'').toLowerCase()==='img'?ps:(ps.querySelector?ps.querySelector('img'):null);" +
            "if(psi){var pst=imgTag(psi);var psm=pst&&pst.match(/src=\\\"([^\\\"]+)\\\"/);if(psm)poster=psm[1];}" +
            "}" +
            "return (poster&&poster.indexOf('http')===0)?poster:'';" +
            "}" +
            "function isBlock(tag){" +
            "return tag==='p'||tag==='div'||tag==='section'||tag==='article'||tag==='li'" +
            "||tag==='blockquote'||tag==='h1'||tag==='h2'||tag==='h3'||tag==='h4'||tag==='h5'||tag==='h6'" +
            "||tag==='figure'||tag==='figcaption'||tag==='td'||tag==='pre';" +
            "}" +
            "function walk(root){" +
            "var out=[];" +
            "function emitJingshi(poster){" +
            "if(poster){out.push('<img src=\"'+poster.replace(/\\\"/g,'')+'#browsesth-jingshi\">');}" +
            "else{out.push('<img src=\"browsesth-jingshi://pending\">');}" +
            "}" +
            "function pushText(t){" +
            "t=(t||'').replace(/\\s+/g,' ').trim();" +
            "if(t.length>=2)out.push('<p>'+t.replace(/</g,'&lt;').replace(/>/g,'&gt;')+'</p>');" +
            "}" +
            "function visit(node,buf){" +
            "if(!node)return;" +
            "var nt=node.nodeType;" +
            "if(nt===3){if(buf)buf.push(node.nodeValue||'');else pushText(node.nodeValue);return;}" +
            "if(nt!==1)return;" +
            "var tag=(node.tagName||'').toLowerCase();" +
            // SPEC-212/213/227: video → 原海报 jingshi；无 poster 时 pending。
            "if(tag==='video'){" +
            "if(buf&&buf.length){pushText(buf.join(''));buf.length=0;}" +
            "emitJingshi(videoPosterFrom(node));" +
            "return;" +
            "}" +
            "if(isSkip(tag))return;" +
            "if(isChromeNode(node)){" +
            "if(isVideoPlayerChrome(node)){" +
            "if(buf&&buf.length){pushText(buf.join(''));buf.length=0;}" +
            "emitJingshi(videoPosterFrom(node));" +
            "}" +
            "return;" +
            "}" +
            "if(tag==='img'){" +
            "if(buf&&buf.length){pushText(buf.join(''));buf.length=0;}" +
            "var im=imgTag(node);if(im)out.push(im);" +
            "return;" +
            "}" +
            "if(tag==='br'){if(buf)buf.push('\\n');else out.push('');return;}" +
            "if(tag==='picture'){" +
            "var picImg=node.querySelector('img');" +
            "if(picImg)visit(picImg,buf);" +
            "return;" +
            "}" +
            "if(isBlock(tag)){" +
            "if(buf&&buf.length){pushText(buf.join(''));buf.length=0;}" +
            "var local=[];" +
            "for(var c=node.firstChild;c;c=c.nextSibling)visit(c,local);" +
            "if(local.length)pushText(local.join(''));" +
            "return;" +
            "}" +
            "for(var k=node.firstChild;k;k=k.nextSibling)visit(k,buf);" +
            "}" +
            "visit(root,null);" +
            "return out.filter(function(x,i,a){return x!==''||(i>0&&a[i-1]!=='');}).join('\\n');" +
            "}" +
            "var title=(document.title||'').trim();" +
            "var h1=document.querySelector('h1');if(h1){var h1t=textOf(h1);if(h1t)title=h1t;}" +
            // SPEC-211/217: portal article roots before generic article/main/body.
            "var root=document.querySelector('$rootFirstSelectors')" +
            "||document.querySelector('article')||document.querySelector('[role=\"main\"]')" +
            "||document.querySelector('main')||document.querySelector('#content, .content, .article, .post, .entry-content, .chapter, .chapter-content, #chaptercontent, .read-content')" +
            "||document.body;" +
            "var html=walk(root);" +
            "var text=textOf(root);" +
            "if((!html||html.length<40)&&text.length>=40){" +
            "html=text.split(/\\n+/).map(function(l){l=l.trim();return l?('<p>'+l.replace(/</g,'&lt;').replace(/>/g,'&gt;')+'</p>'):'';}).filter(Boolean).join('\\n');" +
            "}" +
            "var cover=meta('meta[property=\"og:image\"]')||meta('meta[name=\"twitter:image\"]')||meta('meta[name=\"twitter:image:src\"]')||'';" +
            "cover=abs(cover);" +
            "if(!cover||cover.indexOf('http')!==0){" +
            "var best='',bw=0,bh=0;" +
            // SPEC-160: prefer images inside article root; lower size floor for mobile pages.
            "var scope=root||document;" +
            "var nodes=scope.querySelectorAll('img');" +
            "if(!nodes.length)nodes=document.querySelectorAll('img');" +
            "for(var i=0;i<nodes.length;i++){" +
            "var el=nodes[i];" +
            "var src=el.currentSrc||el.src||el.getAttribute('data-src')||el.getAttribute('data-original')||'';" +
            "if(!src||src.indexOf('data:')===0)continue;" +
            "var w=el.naturalWidth||el.width||0;" +
            "var h=el.naturalHeight||el.height||0;" +
            "if(w>0&&h>0&&(w<120||h<120))continue;" +
            "var area=(w||320)*(h||320);" +
            "if(area>bw*bh){bw=w||320;bh=h||320;best=abs(src);}" +
            "}" +
            "cover=best;" +
            "}" +
            "return JSON.stringify({title:title,html:html||'',text:text||'',cover:cover||''});" +
            "})();"
}
