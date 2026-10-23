package site.openlist.player;

import android.content.Context;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class VideoPlayerView extends PlayerView {
    private int mode;
    private float sourceAspect;
    private AspectRatioFrameLayout content;

    VideoPlayerView(Context context) { super(context); }

    void setVideoMode(int requested) {
        mode = VideoPresentation.validMode(requested);
        setResizeMode(mode == VideoPresentation.STRETCH
                ? AspectRatioFrameLayout.RESIZE_MODE_FILL : AspectRatioFrameLayout.RESIZE_MODE_FIT);
        if (content != null) super.onContentAspectRatioChanged(content, VideoPresentation.aspect(mode, sourceAspect));
    }

    @Override protected void onContentAspectRatioChanged(AspectRatioFrameLayout frame, float ratio) {
        // Override Media3's computed ratio here so track changes cannot undo the selected mode.
        content = frame; sourceAspect = ratio;
        super.onContentAspectRatioChanged(frame, VideoPresentation.aspect(mode, ratio));
    }
}
