/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.view;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader.ImageContainer;
import com.davemorrissey.labs.subscaleview.ImageSource;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.FileCache;
import org.floens.chan.utils.Logger;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Future;

import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

public class MultiImageView extends FrameLayout implements View.OnClickListener {
    public enum Mode {
        UNLOADED, LOWRES, BIGIMAGE, GIF, MOVIE
    }

    private static final String TAG = "MultiImageView";

    private PostImage postImage;
    private Callback callback;

    private Mode mode = Mode.UNLOADED;

    private boolean hasContent = false;
    private ImageContainer thumbnailRequest;
    private Future bigImageRequest;
    private Future gifRequest;
    private Future videoRequest;

    private VideoView videoView;

    public MultiImageView(Context context) {
        super(context);
        init();
    }

    public MultiImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MultiImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOnClickListener(this);
    }

    public void bindPostImage(PostImage postImage, Callback callback) {
        this.postImage = postImage;
        this.callback = callback;
    }

    public PostImage getPostImage() {
        return postImage;
    }

    public void setMode(final Mode newMode) {
        if (this.mode != newMode) {
//            Logger.d(TAG, "Changing mode from " + this.mode + " to " + newMode + " for " + postImage.thumbnailUrl);
            this.mode = newMode;

            AndroidUtils.waitForMeasure(this, new AndroidUtils.OnMeasuredCallback() {
                @Override
                public boolean onMeasured(View view) {
                    switch (newMode) {
                        case LOWRES:
                            setThumbnail(postImage.thumbnailUrl);
                            break;
                        case BIGIMAGE:
                            setBigImage(postImage.imageUrl);
                            break;
                        case GIF:
                            setGif(postImage.imageUrl);
                            break;
                        case MOVIE:
                            setVideo(postImage.imageUrl);
                            break;
                    }
                    return false;
                }
            });
        }
    }

    public Mode getMode() {
        return mode;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public CustomScaleImageView findScaleImageView() {
        CustomScaleImageView bigImage = null;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof CustomScaleImageView) {
                bigImage = (CustomScaleImageView) getChildAt(i);
            }
        }
        return bigImage;
    }

    public void setThumbnail(String thumbnailUrl) {
        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading");
            return;
        }

        // Also use volley for the thumbnails
        thumbnailRequest = ChanApplication.getVolleyImageLoader().get(thumbnailUrl, new com.android.volley.toolbox.ImageLoader.ImageListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                thumbnailRequest = null;
                onError();
            }

            @Override
            public void onResponse(ImageContainer response, boolean isImmediate) {
                thumbnailRequest = null;
                if (response.getBitmap() != null && (!hasContent || mode == Mode.LOWRES)) {
                    ImageView thumbnail = new ImageView(getContext());
                    thumbnail.setImageBitmap(response.getBitmap());

                    onModeLoaded(Mode.LOWRES, thumbnail);
                }
            }
        }, getWidth(), getHeight());
    }

    public void setBigImage(String imageUrl) {
        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading big image");
            return;
        }

        callback.showProgress(this, true);
        bigImageRequest = ChanApplication.getFileCache().downloadFile(imageUrl, new FileCache.DownloadedCallback() {
            @Override
            public void onProgress(long downloaded, long total, boolean done) {
                callback.onProgress(MultiImageView.this, downloaded, total);
                if (done) {
                    callback.showProgress(MultiImageView.this, false);
                }
            }

            @Override
            public void onSuccess(File file) {
                bigImageRequest = null;
                setBigImageFile(file);
            }

            @Override
            public void onFail(boolean notFound) {
                bigImageRequest = null;
                if (notFound) {
                    onNotFoundError();
                } else {
                    onError();
                }
            }
        });
    }

    public void setBigImageFile(File file) {
        final CustomScaleImageView image = new CustomScaleImageView(getContext());
        image.setImage(ImageSource.uri(file.getAbsolutePath()));
        image.setOnClickListener(MultiImageView.this);

        addView(image, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        image.setCallback(new CustomScaleImageView.Callback() {
            @Override
            public void onReady() {
                if (!hasContent || mode == Mode.BIGIMAGE) {
                    callback.showProgress(MultiImageView.this, false);
                    onModeLoaded(Mode.BIGIMAGE, image);
                }
            }

            @Override
            public void onError(boolean wasInitial) {
                onBigImageError(wasInitial);
            }
        });
    }

    public void setGif(String gifUrl) {
        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading");
            return;
        }

        callback.showProgress(this, true);
        gifRequest = ChanApplication.getFileCache().downloadFile(gifUrl, new FileCache.DownloadedCallback() {
            @Override
            public void onProgress(long downloaded, long total, boolean done) {
                callback.onProgress(MultiImageView.this, downloaded, total);
                if (done) {
                    callback.showProgress(MultiImageView.this, false);
                }
            }

            @Override
            public void onSuccess(File file) {
                gifRequest = null;
                if (!hasContent || mode == Mode.GIF) {
                    setGifFile(file);
                }
            }

            @Override
            public void onFail(boolean notFound) {
                gifRequest = null;
                if (notFound) {
                    onNotFoundError();
                } else {
                    onError();
                }
            }
        });
    }

    public void setGifFile(File file) {
        GifDrawable drawable;
        try {
            drawable = new GifDrawable(file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            onError();
            return;
        } catch (OutOfMemoryError e) {
            System.gc();
            e.printStackTrace();
            onOutOfMemoryError();
            return;
        }

        GifImageView view = new GifImageView(getContext());
        view.setImageDrawable(drawable);
        onModeLoaded(Mode.GIF, view);
    }

    public void setVideo(String videoUrl) {
        callback.showProgress(this, true);
        videoRequest = ChanApplication.getFileCache().downloadFile(videoUrl, new FileCache.DownloadedCallback() {
            @Override
            public void onProgress(long downloaded, long total, boolean done) {
                callback.onProgress(MultiImageView.this, downloaded, total);
                if (done) {
                    callback.showProgress(MultiImageView.this, false);
                }
            }

            @Override
            public void onSuccess(File file) {
                videoRequest = null;
                if (!hasContent || mode == Mode.MOVIE) {
                    setVideoFile(file);
                }
            }

            @Override
            public void onFail(boolean notFound) {
                videoRequest = null;
                if (notFound) {
                    onNotFoundError();
                } else {
                    onError();
                }
            }
        });
    }

    public void setVideoFile(final File file) {
        if (ChanSettings.getVideoExternal()) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(file), "video/*");

            try {
                getContext().startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getContext(), R.string.open_link_failed, Toast.LENGTH_SHORT).show();
            }
            // TODO: check this
            onModeLoaded(Mode.GIF, videoView);
        } else {
            Context proxyContext = new NoMusicServiceCommandContext(getContext());

            videoView = new VideoView(proxyContext);
            videoView.setZOrderOnTop(true);
            videoView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT));
            videoView.setLayoutParams(AndroidUtils.MATCH_PARAMS);
            LayoutParams par = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            par.gravity = Gravity.CENTER;
            videoView.setLayoutParams(par);

            videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.setLooping(true);
                    onModeLoaded(Mode.MOVIE, videoView);
                }
            });
            videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    callback.onVideoError(MultiImageView.this, file);

                    return true;
                }
            });

            videoView.setVideoPath(file.getAbsolutePath());

            addView(videoView, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));

            videoView.start();
        }
    }

    public VideoView getVideoView() {
        return videoView;
    }

    private void onError() {
        Toast.makeText(getContext(), R.string.image_preview_failed, Toast.LENGTH_SHORT).show();
        callback.showProgress(this, false);
    }

    private void onNotFoundError() {
        callback.showProgress(this, false);
        Toast.makeText(getContext(), R.string.image_not_found, Toast.LENGTH_SHORT).show();
    }

    private void onOutOfMemoryError() {
        Toast.makeText(getContext(), R.string.image_preview_failed_oom, Toast.LENGTH_SHORT).show();
        callback.showProgress(this, false);
    }

    private void onBigImageError(boolean wasInitial) {
        if (wasInitial) {
            Toast.makeText(getContext(), R.string.image_failed_big_image, Toast.LENGTH_SHORT).show();
            callback.showProgress(this, false);
        }
    }

    public void cancelLoad() {
        if (thumbnailRequest != null) {
            thumbnailRequest.cancelRequest();
        }
        if (bigImageRequest != null) {
            bigImageRequest.cancel(true);
        }
        if (gifRequest != null) {
            gifRequest.cancel(true);
        }
        if (videoRequest != null) {
            videoRequest.cancel(true);
        }
    }

    @Override
    public void onClick(View v) {
        callback.onTap(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelLoad();
    }

    private void onModeLoaded(Mode mode, View view) {
        if (view != null) {
            // Remove all other views
            boolean alreadyAttached = false;
            for (int i = getChildCount() - 1; i >= 0; i--) {
                if (getChildAt(i) != view) {
                    removeViewAt(i);
                } else {
                    alreadyAttached = true;
                }
            }

            if (!alreadyAttached) {
                addView(view, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            }
        }

        hasContent = true;
        callback.onModeLoaded(this, mode);
    }

    public interface Callback {
        void onTap(MultiImageView multiImageView);

        void showProgress(MultiImageView multiImageView, boolean progress);

        void onProgress(MultiImageView multiImageView, long current, long total);

        void onVideoLoaded(MultiImageView multiImageView);

        void onVideoError(MultiImageView multiImageView, File video);

        void onModeLoaded(MultiImageView multiImageView, Mode mode);
    }

    public static class NoMusicServiceCommandContext extends ContextWrapper {
        public NoMusicServiceCommandContext(Context base) {
            super(base);
        }

        @Override
        public void sendBroadcast(Intent intent) {
            // Only allow broadcasts when it's not a music service command
            // Prevents pause intents from broadcasting
            if (!"com.android.music.musicservicecommand".equals(intent.getAction())) {
                super.sendBroadcast(intent);
            }
        }
    }
}