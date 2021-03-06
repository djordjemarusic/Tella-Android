package rs.readahead.washington.mobile.views.activity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.hardware.SensorManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.OrientationEventListener;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.crashlytics.android.Crashlytics;
import com.otaliastudios.cameraview.CameraException;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraOptions;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.Facing;
import com.otaliastudios.cameraview.Flash;
import com.otaliastudios.cameraview.Gesture;
import com.otaliastudios.cameraview.GestureAction;
import com.otaliastudios.cameraview.SessionType;

import java.io.File;
import java.util.Set;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rs.readahead.washington.mobile.R;
import rs.readahead.washington.mobile.data.database.CacheWordDataSource;
import rs.readahead.washington.mobile.domain.entity.MediaFile;
import rs.readahead.washington.mobile.domain.entity.Metadata;
import rs.readahead.washington.mobile.domain.entity.TempMediaFile;
import rs.readahead.washington.mobile.media.MediaFileBundle;
import rs.readahead.washington.mobile.media.MediaFileHandler;
import rs.readahead.washington.mobile.media.MediaFileUrlLoader;
import rs.readahead.washington.mobile.mvp.contract.ICameraPresenterContract;
import rs.readahead.washington.mobile.mvp.contract.IMetadataAttachPresenterContract;
import rs.readahead.washington.mobile.mvp.presenter.CameraPresenter;
import rs.readahead.washington.mobile.mvp.presenter.MetadataAttacher;
import rs.readahead.washington.mobile.presentation.entity.MediaFileLoaderModel;
import rs.readahead.washington.mobile.util.C;
import rs.readahead.washington.mobile.util.DialogsUtil;
import rs.readahead.washington.mobile.views.custom.CameraCaptureButton;
import rs.readahead.washington.mobile.views.custom.CameraDurationTextView;
import rs.readahead.washington.mobile.views.custom.CameraFlashButton;
import rs.readahead.washington.mobile.views.custom.CameraSwitchButton;


public class CameraActivity extends MetadataActivity implements
        ICameraPresenterContract.IView,
        IMetadataAttachPresenterContract.IView {
    public static String CAMERA_MODE = "cm";
    public static String INTENT_MODE = "im";
    public static final String MEDIA_FILE_KEY = "mfk";

    @BindView(R.id.camera)
    CameraView cameraView;
    @BindView(R.id.switchButton)
    CameraSwitchButton switchButton;
    @BindView(R.id.flashButton)
    CameraFlashButton flashButton;
    @BindView(R.id.captureButton)
    CameraCaptureButton captureButton;
    @BindView(R.id.durationView)
    CameraDurationTextView durationView;
    @BindView(R.id.camera_zoom)
    SeekBar mSeekBar;
    /*@BindView(R.id.resolution)
    CameraResolutionButton resolutionButton;*/
    @BindView(R.id.video_line)
    View videoLine;
    @BindView(R.id.photo_line)
    View photoLine;
    @BindView(R.id.preview_image)
    ImageView previewView;
    @BindView(R.id.photo_text)
    TextView photoModeText;
    @BindView(R.id.video_text)
    TextView videoModeText;

    private CameraPresenter presenter;
    private MetadataAttacher metadataAttacher;
    private Mode mode;
    private boolean modeLocked;
    private IntentMode intentMode;
    private boolean videoRecording;
    private ProgressDialog progressDialog;
    private OrientationEventListener mOrientationEventListener;
    private int zoomLevel = 0;
    private MediaFile capturedMediaFile;

    public enum Mode {
        PHOTO,
        VIDEO
    }

    public enum IntentMode {
        COLLECT,
        RETURN,
        STAND
    }

    private final static int CLICK_DELAY = 1200;
    private final static int CLICK_MODE_DELAY = 2000;
    private long lastClickTime = System.currentTimeMillis();

    private RequestManager.ImageModelRequest<MediaFileLoaderModel> glide;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_camera);
        ButterKnife.bind(this);

        presenter = new CameraPresenter(this);
        metadataAttacher = new MetadataAttacher(this);

        mode = Mode.PHOTO;
        if (getIntent().hasExtra(CAMERA_MODE)) {
            mode = Mode.valueOf(getIntent().getStringExtra(CAMERA_MODE));
            modeLocked = true;
        }

        intentMode = IntentMode.RETURN;
        if (getIntent().hasExtra(INTENT_MODE)) {
            intentMode = IntentMode.valueOf(getIntent().getStringExtra(INTENT_MODE));
        }

        CacheWordDataSource cacheWordDataSource = new CacheWordDataSource(getContext());
        MediaFileHandler mediaFileHandler = new MediaFileHandler(cacheWordDataSource);
        MediaFileUrlLoader glideLoader = new MediaFileUrlLoader(getContext().getApplicationContext(), mediaFileHandler);
        glide = Glide.with(getContext()).using(glideLoader);

        setupCameraView();
        setupCameraModeButton();
        setupImagePreview();
    }

    @Override
    protected void onResume() {
        super.onResume();

        mOrientationEventListener.enable();

        startLocationMetadataListening();

        cameraView.start();
        mSeekBar.setProgress(zoomLevel);
        setCameraZoom();

        presenter.getLastMediaFile();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopLocationMetadataListening();

        mOrientationEventListener.disable();

        if (videoRecording) {
            captureButton.performClick();
        }

        cameraView.stop();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPresenter();
        hideProgressDialog();
        cameraView.destroy();
    }

    @Override
    public void onBackPressed() {
        if (maybeStopVideoRecording()) return;
        super.onBackPressed();
    }

    @Override
    public void onAddingStart() {
        progressDialog = DialogsUtil.showLightProgressDialog(this, getString(R.string.ra_import_media_progress));
    }

    @Override
    public void onAddingEnd() {
        hideProgressDialog();
        showToast(R.string.ra_file_encrypted);
    }

    @Override
    public void onAddSuccess(MediaFileBundle bundle) {
        capturedMediaFile = bundle.getMediaFile();
        if (intentMode != IntentMode.COLLECT) {
            Glide.with(this).load(bundle.getMediaFileThumbnailData().getData()).into(previewView);
        }
        attachMediaFileMetadata(capturedMediaFile.getId(), metadataAttacher);
    }

    @Override
    public void onAddError(Throwable error) {
        showToast(R.string.ra_capture_error);
    }

    @Override
    public void onMetadataAttached(long mediaFileId, @Nullable Metadata metadata) {
        Intent data = new Intent();
        if (intentMode == IntentMode.COLLECT) {
            data.putExtra(MEDIA_FILE_KEY, capturedMediaFile);
        } else {
            data.putExtra(C.CAPTURED_MEDIA_FILE_ID, mediaFileId);
        }
        setResult(RESULT_OK, data);

        if (intentMode != IntentMode.STAND) {
            finish();
        }
    }

    @Override
    public void onMetadataAttachError(Throwable throwable) {
        onAddError(throwable);
    }

    @OnClick(R.id.close)
    void closeCamera() {
        onBackPressed();
    }

    @Override
    public void rotateViews(int rotation) {
        switchButton.rotateView(rotation);
        flashButton.rotateView(rotation);
        durationView.rotateView(rotation);
        captureButton.rotateView(rotation);
        //resolutionButton.rotateView(rotation);
        if (intentMode != IntentMode.COLLECT) {
            previewView.animate().rotation(rotation).start();
        }
    }

    @Override
    public void onLastMediaFileSuccess(MediaFile mediaFile) {
        if (intentMode != IntentMode.COLLECT) {
            glide.load(new MediaFileLoaderModel(mediaFile, MediaFileLoaderModel.LoadType.THUMBNAIL))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(previewView);
        }
    }

    @Override
    public void onLastMediaFileError(Throwable throwable) {
        if (intentMode != IntentMode.COLLECT) {
            previewView.setImageResource(R.drawable.white);
        }
    }

    @Override
    public Context getContext() {
        return this;
    }

    @OnClick(R.id.captureButton)
    void onCaptureClicked() {
        if (cameraView.getSessionType() == SessionType.PICTURE) {
            cameraView.capturePicture();
        } else {

            switchButton.setVisibility(videoRecording ? View.VISIBLE : View.GONE);
            if (videoRecording) {
                if (System.currentTimeMillis() - lastClickTime >= CLICK_DELAY) {
                    cameraView.stopCapturingVideo();
                    videoRecording = false;
                    switchButton.setVisibility(View.VISIBLE);
                }
            } else {
                lastClickTime = System.currentTimeMillis();
                TempMediaFile tmp = TempMediaFile.newMp4();
                File file = MediaFileHandler.getTempFile(this, tmp);
                cameraView.startCapturingVideo(file);
                captureButton.displayStopVideo();
                durationView.start();
                videoRecording = true;
                switchButton.setVisibility(View.GONE);
            }
        }
    }

    @OnClick(R.id.photo_mode)
    void onPhotoClicked() {
        if (modeLocked) {
            return;
        }

        if (System.currentTimeMillis() - lastClickTime < CLICK_MODE_DELAY) {
            return;
        }

        if (cameraView.getSessionType() == SessionType.PICTURE) {
            return;
        }

        if (cameraView.getFlash() == Flash.TORCH) {
            cameraView.setFlash(Flash.AUTO);
        }

        setPhotoActive();
        captureButton.displayPhotoButton();
        cameraView.setSessionType(SessionType.PICTURE);
        mode = CameraActivity.Mode.PHOTO;

        resetZoom();
        lastClickTime = System.currentTimeMillis();
    }

    @OnClick(R.id.video_mode)
    void onVideoClicked() {
        if (modeLocked) {
            return;
        }

        if (System.currentTimeMillis() - lastClickTime < CLICK_MODE_DELAY) {
            return;
        }

        if (cameraView.getSessionType() == SessionType.VIDEO) {
            return;
        }

        cameraView.setSessionType(SessionType.VIDEO);
        turnFlashDown();
        captureButton.displayVideoButton();
        setVideoActive();
        mode = CameraActivity.Mode.VIDEO;

        resetZoom();
        lastClickTime = System.currentTimeMillis();
    }

    @OnClick(R.id.switchButton)
    void onSwitchClicked() {
        if (cameraView.getFacing() == Facing.BACK) {
            cameraView.setFacing(Facing.FRONT);
            switchButton.displayFrontCamera();
        } else {
            cameraView.setFacing(Facing.BACK);
            switchButton.displayBackCamera();
        }
    }

    @OnClick(R.id.preview_image)
    void onPreviewClicked() {
        startActivity(new Intent(this, GalleryActivity.class));
    }

    private void resetZoom() {
        zoomLevel = 0;
        mSeekBar.setProgress(0);
        setCameraZoom();
    }

    private void setCameraZoom() {
        cameraView.setZoom((float) zoomLevel / 100);
    }

    private boolean maybeStopVideoRecording() {
        if (videoRecording) {
            captureButton.performClick();
            return true;
        }

        return false;
    }

    private void stopPresenter() {
        if (presenter != null) {
            presenter.destroy();
            presenter = null;
        }
    }

    private void showConfirmVideoView(final File video) {
        captureButton.displayVideoButton();
        durationView.stop();
        presenter.addMp4Video(video);
    }

    private void setupCameraView() {
        if (mode == Mode.PHOTO) {
            cameraView.setSessionType(SessionType.PICTURE);
            captureButton.displayPhotoButton();
        } else {
            cameraView.setSessionType(SessionType.VIDEO);
            captureButton.displayVideoButton();
        }

        //cameraView.setEnabled(PermissionUtil.checkPermission(this, Manifest.permission.CAMERA));
        cameraView.mapGesture(Gesture.TAP, GestureAction.FOCUS_WITH_MARKER);

        setOrientationListener();

        cameraView.addCameraListener(new CameraListener() {
            @Override
            public void onPictureTaken(byte[] jpeg) {
                presenter.addJpegPhoto(jpeg);
            }

            @Override
            public void onVideoTaken(File video) {
                showConfirmVideoView(video);
            }

            @Override
            public void onCameraError(@NonNull CameraException exception) {
                Crashlytics.logException(exception);
            }

            @Override
            public void onCameraOpened(CameraOptions options) {
                if (options.getSupportedFacing().size() < 2) {
                    switchButton.setVisibility(View.GONE);
                } else {
                    switchButton.setVisibility(View.VISIBLE);
                    setupCameraSwitchButton();
                }

                if (options.getSupportedFlash().size() < 2) {
                    flashButton.setVisibility(View.INVISIBLE);
                } else {
                    flashButton.setVisibility(View.VISIBLE);
                    setupCameraFlashButton(options.getSupportedFlash());
                }
                // options object has info
                super.onCameraOpened(options);
            }
        });

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                zoomLevel = i;
                setCameraZoom();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void setupCameraModeButton() {
        if (cameraView.getSessionType() == SessionType.PICTURE) {
            setPhotoActive();
        } else {
            setVideoActive();
        }
    }

    private void setupCameraSwitchButton() {
        if (cameraView.getFacing() == Facing.FRONT) {
            switchButton.displayFrontCamera();
        } else {
            switchButton.displayBackCamera();
        }
    }

    private void setupImagePreview() {
        if (intentMode == IntentMode.COLLECT) {
            previewView.setVisibility(View.GONE);
        }
    }

    private void setupCameraFlashButton(final Set<Flash> supported) {
        if (cameraView.getFlash() == Flash.AUTO) {
            flashButton.displayFlashAuto();
        } else if (cameraView.getFlash() == Flash.OFF) {
            flashButton.displayFlashOff();
        } else {
            flashButton.displayFlashOn();
        }

        flashButton.setOnClickListener(view -> {
            if (cameraView.getSessionType() == SessionType.VIDEO) {
                if (cameraView.getFlash() == Flash.OFF && supported.contains(Flash.TORCH)) {
                    flashButton.displayFlashOn();
                    cameraView.setFlash(Flash.TORCH);
                } else {
                    turnFlashDown();
                }
            } else {
                if (cameraView.getFlash() == Flash.ON || cameraView.getFlash() == Flash.TORCH) {
                    turnFlashDown();
                } else if (cameraView.getFlash() == Flash.OFF && supported.contains(Flash.AUTO)) {
                    flashButton.displayFlashAuto();
                    cameraView.setFlash(Flash.AUTO);
                } else {
                    flashButton.displayFlashOn();
                    cameraView.setFlash(Flash.ON);
                }
            }
        });
    }

    private void turnFlashDown() {
        flashButton.displayFlashOff();
        cameraView.setFlash(Flash.OFF);
    }

    private void hideProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    private void setOrientationListener() {
        mOrientationEventListener = new OrientationEventListener(
                this, SensorManager.SENSOR_DELAY_NORMAL) {

            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
                    presenter.handleRotation(orientation);
                }
            }
        };
    }

    private void setPhotoActive() {
        videoLine.setVisibility(View.GONE);
        photoLine.setVisibility(View.VISIBLE);
        photoModeText.setAlpha(1f);
        videoModeText.setAlpha(modeLocked ? 0.1f : 0.5f);
    }

    private void setVideoActive() {
        videoLine.setVisibility(View.VISIBLE);
        photoLine.setVisibility(View.GONE);
        videoModeText.setAlpha(1);
        photoModeText.setAlpha(modeLocked ? 0.1f : 0.5f);
    }
}
