package rs.readahead.washington.mobile.javarosa;

import org.hzontal.tella.keys.key.LifecycleMainKey;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryController;

import java.util.LinkedHashMap;

import io.reactivex.SingleSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.AsyncSubject;
import rs.readahead.washington.mobile.MyApplication;
import rs.readahead.washington.mobile.R;
import rs.readahead.washington.mobile.data.database.DataSource;
import rs.readahead.washington.mobile.domain.entity.collect.CollectFormInstance;
import rs.readahead.washington.mobile.odk.FormController;
import rs.readahead.washington.mobile.odk.FormController.FailedConstraint;
import rs.readahead.washington.mobile.odk.exception.JavaRosaException;


public class FormSaver implements IFormSaverContract.IFormSaver{
    private IFormSaverContract.IView view;
    private AsyncSubject<DataSource> asyncDataSource = AsyncSubject.create();
    private CompositeDisposable disposables = new CompositeDisposable();
    private boolean autoSaveDraft;


    public FormSaver(IFormSaverContract.IView view) {
        this.view = view;

        /*Single.fromCallable(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return SharedPrefs.getInstance().isAutoSaveDrafts();
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean aBoolean) throws Exception {
                        autoSaveDraft = aBoolean;
                    }
                });*/
        autoSaveDraft = false; // this is requested default..
        initDataSource();
    }

    @Override
    public boolean saveScreenAnswers(LinkedHashMap<FormIndex, IAnswerData> answers, boolean checkConstraints) {
        FormController formController = FormController.getActive();

        try {
            if (!formController.currentPromptIsQuestion()) { // bad name for method..
                return true;
            }

            FailedConstraint constraint = formController.saveAllScreenAnswers(answers, checkConstraints);

            if (constraint != null) {
                showFailedConstraint(constraint);
                return false;
            }
        } catch (JavaRosaException e) {
            view.formSaveError(e);
        }

        return true;
    }

    @Override
    public void saveActiveFormInstance() {
        disposables.add(asyncDataSource
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(disposable -> view.showSaveFormInstanceLoading())
                .flatMapSingle((Function<DataSource, SingleSource<CollectFormInstance>>) dataSource ->
                        dataSource.saveInstance(FormController.getActive().getCollectFormInstance()))
                .doFinally(() -> view.hideSaveFormInstanceLoading())
                .subscribe(instance -> view.formInstanceSaveSuccess(instance),
                        throwable -> view.formInstanceSaveError(throwable)
                )
        );
    }

    public void saveActiveFormInstanceOnExit() {
        final CollectFormInstance instance = FormController.getActive().getCollectFormInstance();

        disposables.add(asyncDataSource
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMapSingle(dataSource -> dataSource.saveInstance(instance))
                .subscribe(saved -> view.formSavedOnExit(), throwable -> {
                })
        );
    }

    @Override
    public void autoSaveFormInstance() {
        if (!autoSaveDraft) return;

        final CollectFormInstance instance = FormController.getActive().getCollectFormInstance();

        /*if (!CollectFormInstanceStatus.UNKNOWN.equals(instance.getStatus()) &&
                !CollectFormInstanceStatus.UNKNOWN.equals(instance.getStatus())) { // only auto save draft and unknown
            return;
        }*/ // todo: check this!

        disposables.add(asyncDataSource
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMapSingle((Function<DataSource, SingleSource<CollectFormInstance>>) dataSource
                        -> dataSource.saveInstance(instance))
                .subscribe(instance1 -> view.formInstanceAutoSaveSuccess(instance1))
        );
    }

    @Override
    public boolean isAutoSaveDraft() {
        return autoSaveDraft;
    }

    @Override
    public void deleteActiveFormInstance() {
        final CollectFormInstance instance = FormController.getActive().getCollectFormInstance();
        final boolean cloned = instance.getClonedId() > 0;

        disposables.add(asyncDataSource
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(disposable -> view.showDeleteFormInstanceStart())
                .flatMapCompletable(dataSource -> dataSource.deleteInstance(cloned ? instance.getClonedId() : instance.getId()))
                .doFinally(() -> view.hideDeleteFormInstanceEnd())
                .subscribe(() -> view.formInstanceDeleteSuccess(cloned),
                        throwable -> view.formInstanceDeleteError(throwable)
                )
        );
    }

    @Override
    public boolean isActiveInstanceCloned() {
        return FormController.getActive().getCollectFormInstance().getClonedId() > 0;
    }

    @Override
    public void destroy() {
        disposables.dispose();
        view = null;
    }

    private void initDataSource() {
        if (view != null) {
            DataSource dataSource;
            try {
                dataSource = DataSource.getInstance(view.getContext(), MyApplication.getMainKeyHolder().get().getKey().getEncoded());
                asyncDataSource.onNext(dataSource);
                asyncDataSource.onComplete();
            } catch (LifecycleMainKey.MainKeyUnavailableException e) {
                e.printStackTrace();
            }
        }
    }

    private void showFailedConstraint(FailedConstraint constraint) {
        FormController formController = FormController.getActive();
        String constraintText;

        switch (constraint.status) {
            case FormEntryController.ANSWER_CONSTRAINT_VIOLATED:
                constraintText = formController.getQuestionPromptConstraintText(constraint.index);
                if (constraintText == null) {
                    constraintText = formController.getQuestionPrompt(constraint.index).getSpecialFormQuestionText("constraintMsg");
                    if (constraintText == null) {
                        constraintText = view.getContext().getString(R.string.collect_form_toast_validation_error);
                    }
                }
                break;

            case FormEntryController.ANSWER_REQUIRED_BUT_EMPTY:
                constraintText = formController.getQuestionPromptRequiredText(constraint.index);
                if (constraintText == null) {
                    constraintText = formController.getQuestionPrompt(constraint.index).getSpecialFormQuestionText("requiredMsg");
                    if (constraintText == null) {
                        constraintText = view.getContext().getString(R.string.collect_form_error_response_mandatory);
                    }
                }
                break;

            default:
                return; // ignore this..
        }

        view.formConstraintViolation(constraint.index, constraintText);
    }
}
