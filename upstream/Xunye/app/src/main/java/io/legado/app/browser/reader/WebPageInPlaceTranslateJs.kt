package io.legado.app.browser.reader

/**
 * SPEC-251/255: in-page DOM text-node collect / apply / restore (Chrome-like; no navigation).
 * SPEC-255: each text node carries a blockId (nearest block ancestor) for whole-clause translate.
 */
object WebPageInPlaceTranslateJs {

    val COLLECT = """
        (function(){
          try{
            if(window.__xyTr && window.__xyTr.active){
              return JSON.stringify({state:'translated',count:window.__xyTr.orig.length});
            }
            var SKIP={SCRIPT:1,STYLE:1,NOSCRIPT:1,TEXTAREA:1,CODE:1,PRE:1,KBD:1,SAMP:1};
            var BLOCK={P:1,DIV:1,H1:1,H2:1,H3:1,H4:1,H5:1,H6:1,LI:1,TD:1,TH:1,
              BLOCKQUOTE:1,FIGCAPTION:1,DT:1,DD:1,SUMMARY:1,ARTICLE:1,SECTION:1,
              HEADER:1,FOOTER:1,MAIN:1,ASIDE:1,NAV:1,SPAN:0};
            var root=document.body||document.documentElement;
            if(!root) return JSON.stringify({state:'empty',count:0,lang:'',sample:''});
            var blockMap=new Map();
            var nextBlock=0;
            function blockIdFor(el){
              var n=el;
              while(n && n!==root){
                var tag=(n.tagName||'').toUpperCase();
                if(BLOCK[tag]===1){
                  if(!blockMap.has(n)) blockMap.set(n, nextBlock++);
                  return blockMap.get(n);
                }
                n=n.parentElement;
              }
              if(!blockMap.has(el)) blockMap.set(el, nextBlock++);
              return blockMap.get(el);
            }
            var nodes=[], texts=[], blocks=[], sample='';
            var walker=document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
              acceptNode:function(n){
                if(!n||!n.nodeValue||!String(n.nodeValue).trim()) return NodeFilter.FILTER_REJECT;
                var p=n.parentElement; if(!p) return NodeFilter.FILTER_REJECT;
                var tag=(p.tagName||'').toUpperCase();
                if(SKIP[tag]) return NodeFilter.FILTER_REJECT;
                if(p.isContentEditable) return NodeFilter.FILTER_REJECT;
                return NodeFilter.FILTER_ACCEPT;
              }
            });
            while(walker.nextNode()){
              var t=walker.currentNode.nodeValue;
              var p=walker.currentNode.parentElement;
              nodes.push(walker.currentNode);
              texts.push(t);
              blocks.push(blockIdFor(p));
              if(sample.length<800) sample+=t+' ';
            }
            var lang=(document.documentElement.lang||document.documentElement.getAttribute('xml:lang')||'').trim();
            window.__xyTr={nodes:nodes, orig:texts.slice(), blocks:blocks, active:false};
            return JSON.stringify({state:'ready',count:texts.length,lang:lang,sample:sample});
          }catch(e){
            return JSON.stringify({state:'error',message:String(e&&e.message||e)});
          }
        })();
    """.trimIndent()

    fun batchSlice(start: Int, endExclusive: Int): String = """
        (function(){
          var tr=window.__xyTr; if(!tr||!tr.orig) return '{"texts":[],"blocks":[]}';
          return JSON.stringify({
            texts: tr.orig.slice($start,$endExclusive),
            blocks: (tr.blocks||[]).slice($start,$endExclusive)
          });
        })();
    """.trimIndent()

    fun applyBatch(start: Int, translatedJsonArray: String): String = """
        (function(){
          var tr=window.__xyTr; if(!tr||!tr.nodes) return '0';
          var arr=$translatedJsonArray;
          for(var i=0;i<arr.length;i++){
            var n=tr.nodes[$start+i];
            if(n) n.nodeValue=arr[i];
          }
          return String(arr.length);
        })();
    """.trimIndent()

    val MARK_ACTIVE = """
        (function(){ if(window.__xyTr){ window.__xyTr.active=true; } return '1'; })();
    """.trimIndent()

    val RESTORE = """
        (function(){
          var tr=window.__xyTr;
          if(!tr||!tr.nodes||!tr.orig) return '0';
          for(var i=0;i<tr.nodes.length;i++){
            if(tr.nodes[i]) tr.nodes[i].nodeValue=tr.orig[i];
          }
          tr.active=false;
          return String(tr.orig.length);
        })();
    """.trimIndent()

    val IS_ACTIVE = """
        (function(){ return (window.__xyTr && window.__xyTr.active) ? '1' : '0'; })();
    """.trimIndent()

    /** SPEC-255: read current live node values for leftover-English rescan. */
    fun liveSlice(start: Int, endExclusive: Int): String = """
        (function(){
          var tr=window.__xyTr; if(!tr||!tr.nodes) return '[]';
          var out=[];
          for(var i=$start;i<$endExclusive && i<tr.nodes.length;i++){
            out.push(tr.nodes[i] ? String(tr.nodes[i].nodeValue||'') : '');
          }
          return JSON.stringify(out);
        })();
    """.trimIndent()
}
