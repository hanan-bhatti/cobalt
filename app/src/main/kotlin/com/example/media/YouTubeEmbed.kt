package com.example.media

/** The installed app ID is the embed identity required by YouTube's WebView integration. */
internal object YouTubeEmbed {
    fun allowNavigation(url: android.net.Uri, mainFrame: Boolean): Boolean {
        if (mainFrame || url.scheme != "https") return false
        val host = url.host?.lowercase(java.util.Locale.ROOT) ?: return false
        return host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
    }

    fun origin(applicationId: String): String {
        require(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+").matches(applicationId))
        return "https://${applicationId.lowercase(java.util.Locale.ROOT)}"
    }

    fun html(videoId: String, applicationId: String): String {
        require(Regex("[A-Za-z0-9_-]{11}").matches(videoId))
        val origin = origin(applicationId)
        return """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="referrer" content="strict-origin-when-cross-origin">
            <style>html,body{margin:0;overflow:hidden;background:#000}#player{position:fixed;left:0;top:0;border:0}</style>
            </head><body><div id="player"></div><script>
            var cobaltPlayer;
            // A WebView in a scrolling Compose layout can resolve percentage/vh heights to zero.
            function cobaltSizePlayer(){
              var w=window.innerWidth,h=window.innerHeight;
              if(w>0&&h>0){
                document.documentElement.style.height=h+'px';document.body.style.height=h+'px';
                var p=document.getElementById('player');
                if(p){p.style.width=w+'px';p.style.height=h+'px';}
                if(cobaltPlayer&&cobaltPlayer.setSize)cobaltPlayer.setSize(w,h);
              }
            }
            window.addEventListener('resize',cobaltSizePlayer);
            function onYouTubeIframeAPIReady(){
              cobaltSizePlayer();
              cobaltPlayer=new YT.Player('player',{
                videoId:'$videoId',width:window.innerWidth,height:window.innerHeight,
                playerVars:{origin:'$origin',playsinline:1,controls:1,fs:0,rel:0},
                events:{
                  onReady:function(){cobaltSizePlayer();CobaltTrailer.ready();},
                  onStateChange:function(e){CobaltTrailer.state(e.data);},
                  onError:function(e){CobaltTrailer.error(e.data);}
                }
              });
            }
            </script><script src="https://www.youtube.com/iframe_api"></script></body></html>
        """.trimIndent()
    }
}
