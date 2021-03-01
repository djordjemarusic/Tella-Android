package rs.readahead.washington.mobile.mvp.presenter;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.io.File;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.SingleSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import rs.readahead.washington.mobile.MyApplication;
import rs.readahead.washington.mobile.data.database.DataSource;
import rs.readahead.washington.mobile.data.database.KeyDataSource;
import rs.readahead.washington.mobile.domain.entity.MediaFile;
import rs.readahead.washington.mobile.media.MediaFileBundle;
import rs.readahead.washington.mobile.media.MediaFileHandler;
import rs.readahead.washington.mobile.mvp.contract.ICameraPresenterContract;


public class CameraPresenter implements ICameraPresenterContract.IPresenter {
    private ICameraPresenterContract.IView view;
    private CompositeDisposable disposables = new CompositeDisposable();
    private MediaFileHandler mediaFileHandler;
    private KeyDataSource keyDataSource;
    private int currentRotation = 0;


    public CameraPresenter(ICameraPresenterContract.IView view) {
        this.view = view;
        this.keyDataSource = MyApplication.getKeyDataSource();
        this.mediaFileHandler = new MediaFileHandler(keyDataSource);
    }

    @Override
    public void addJpegPhoto(final byte[] jpeg) {
        disposables.add(Observable.fromCallable(() -> MediaFileHandler.saveJpegPhoto(view.getContext(), jpeg))
                .flatMap((Function<MediaFileBundle, ObservableSource<MediaFileBundle>>) bundle ->
                        mediaFileHandler.registerMediaFileBundle(bundle))
                .subscribeOn(Schedulers.io())
                .doOnSubscribe(disposable -> view.onAddingStart())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(() -> view.onAddingEnd())
                .subscribe(bundle -> view.onAddSuccess(bundle), throwable -> {
                    FirebaseCrashlytics.getInstance().recordException(throwable);
                    view.onAddError(throwable);
                })
        );
    }

    @Override
    public void addMp4Video(final File file) {
        disposables.add(Observable.fromCallable(() -> MediaFileHandler.saveMp4Video(view.getContext(), file))
                .flatMap((Function<MediaFileBundle, ObservableSource<MediaFileBundle>>) bundle ->
                        mediaFileHandler.registerMediaFileBundle(bundle))
                .subscribeOn(Schedulers.io())
                .doOnSubscribe(disposable -> view.onAddingStart())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(() -> view.onAddingEnd())
                .subscribe(bundle -> view.onAddSuccess(bundle), throwable -> {
                    FirebaseCrashlytics.getInstance().recordException(throwable);
                    view.onAddError(throwable);
                })
        );
    }

    @Override
    public void handleRotation(int orientation) {
        int degrees = 270;

        if (orientation < 45 || orientation > 315) {
            degrees = 0;
        } else if (orientation < 135) {
            degrees = 90;
        } else if (orientation < 225) {
            degrees = 180;
        }

        int rotation = (360 - degrees) % 360;

        if (rotation == 270) {
            rotation = -90;
        }

        if (currentRotation == rotation || rotation == 180/*IGNORING THIS ANGLE*/) {
            return;
        }

        currentRotation = rotation;

        view.rotateViews(rotation);
    }

    @Override
    public void getLastMediaFile() {
        disposables.add(keyDataSource.getDataSource()
                .flatMapSingle((Function<DataSource, SingleSource<MediaFile>>) DataSource::getLastMediaFile)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        mediaFile -> view.onLastMediaFileSuccess(mediaFile),
                        throwable -> view.onLastMediaFileError(throwable)
                )
        );
    }

    @Override
    public void destroy() {
        disposables.dispose();
        view = null;
    }
}
