package rs.readahead.washington.mobile.views.collect;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.WindowDecorActionBar;

import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryPrompt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import rs.readahead.washington.mobile.R;
import rs.readahead.washington.mobile.javarosa.ImmutableDisplayableQuestion;
import rs.readahead.washington.mobile.odk.FormController;
import rs.readahead.washington.mobile.odk.exception.FormDesignException;
import rs.readahead.washington.mobile.odk.exception.JavaRosaException;
import rs.readahead.washington.mobile.views.collect.widgets.QuestionWidget;
import rs.readahead.washington.mobile.views.collect.widgets.WidgetFactory;
import timber.log.Timber;


@SuppressLint("ViewConstructor")
public class CollectFormView extends LinearLayout implements WidgetValueChangedListener{
    public static final String FIELD_LIST = "field-list";

    // starter random number for view IDs
    private static final int VIEW_ID = 12061974;

    private ArrayList<QuestionWidget> widgets;
    private LinearLayout.LayoutParams widgetLayout;


    public CollectFormView(Context context, final FormEntryPrompt[] questionPrompts, FormEntryCaption[] groups) {
        super(context);

        widgets = new ArrayList<>();

        inflate(context, R.layout.collect_form_view, this);

        // set my layout
        LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        setLayoutParams(layoutParams);
        setOrientation(VERTICAL);

        // set padding
        int padding = getResources().getDimensionPixelSize(R.dimen.collect_form_padding_vertical);
        setPadding(0, padding, 0, padding);

        TextView formViewTitle = findViewById(R.id.formViewTitle);
        if (getGroupTitle(groups).length() > 0) {
            formViewTitle.setVisibility(VISIBLE);
            formViewTitle.setText(getGroupTitle(groups));
        } else {
            formViewTitle.setVisibility(GONE);
        }

        // prepare widgets layout
        widgetLayout = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        // when the grouped fields are populated by an external app, this will get true.
        boolean readOnlyOverride = false;

        // external app handling removed..
        LayoutInflater inflater = LayoutInflater.from(getContext());

        int id = 0;
        for (FormEntryPrompt p : questionPrompts) {
          //  Timber.d("++++ FormEntryPrompt %s", p.getQuestionText());
            if (id > 0) {
                LinearLayout separator = new LinearLayout(getContext());
               // Timber.d("++++ separator");
                inflater.inflate(R.layout.collect_form_delimiter, separator, true);
                addView(separator, widgetLayout);
            }
            QuestionWidget qw = WidgetFactory.createWidgetFromPrompt(p, getContext(), readOnlyOverride);
            //Timber.d("++++ QuestionWidget");
            qw.setId(VIEW_ID + id++);
            widgets.add(qw);
            qw.setValueChangedListener(this);
            addView(qw, widgetLayout);
        }
    }

    public void setFocus(Context context) {
        if (widgets.size() > 0) {
            widgets.get(0).setFocus(context);
        }
    }

    public void setValidationConstraintText(FormIndex formIndex, String text) {
        for (QuestionWidget q : widgets) {
            if (q.getPrompt().getIndex() == formIndex) {
                q.setConstraintValidationText(text);
                break;
            }
        }
    }

    public void clearValidationConstraints() {
        for (QuestionWidget q : widgets) {
            q.setConstraintValidationText(null);
        }
    }

    public LinkedHashMap<FormIndex, IAnswerData> getAnswers() {
        LinkedHashMap<FormIndex, IAnswerData> answers = new LinkedHashMap<>();

        for (QuestionWidget q : widgets) {
            FormEntryPrompt p = q.getPrompt();
            answers.put(p.getIndex(), q.getAnswer());
        }

        return answers;
    }

    public String setBinaryData(@NonNull Object data) {
        for (QuestionWidget q : widgets) {
            if (isWaitingForBinaryData(q)) {
                try {
                    return q.setBinaryData(data);
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Error attaching data", Toast.LENGTH_LONG).show();
                }
            }
        }

        return null;
    }

    public void setBinaryData(FormIndex formIndex, @NonNull Object data) {
        for (QuestionWidget q : widgets) {
            if (q.getPrompt().getIndex().equals(formIndex)) {
                try {
                    q.setBinaryData(data);
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Error attaching data", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Nullable
    public String clearBinaryData() {
        for (QuestionWidget q : widgets) {
            if (isWaitingForBinaryData(q)) {
                String name = q.getBinaryName();
                q.clearAnswer();
                return name;
            }
        }

        return null;
    }

    public void clearBinaryData(FormIndex formIndex) {
        for (QuestionWidget q : widgets) {
            if (q.getPrompt().getIndex().equals(formIndex)) {
                q.clearAnswer();
                break;
            }
        }
    }

    private boolean isWaitingForBinaryData(QuestionWidget q) {
        return q.getPrompt().getIndex().equals(
                FormController.getActive().getIndexWaitingForData()
        );
    }

    private String getGroupTitle(FormEntryCaption[] groups) {
        StringBuilder s = new StringBuilder();
        String t;
        int i;

        // list all groups in one string
        for (FormEntryCaption g : groups) {
            i = g.getMultiplicity() + 1;
            t = g.getLongText();
            if (t != null) {
                s.append(t);
                if (g.repeats() && i > 0) {
                    s.append(" (").append(i).append(")");
                }
                s.append(" > ");
            }
        }

        return s.length() > 0 ? s.substring(0, s.length() - 3) : s.toString();
    }

    @Override
    public void widgetValueChanged(QuestionWidget changedWidget) {
        Timber.d("++++ widgetValueChanged, %s", changedWidget.getPrompt().getQuestionText());
        FormController formController = FormController.getActive();
        if (formController == null) {
            // TODO: As usual, no idea if/how this is possible.
            return;
        }
        if (formController.indexIsInFieldList()) {
            //runOnUiThread(new Runnable() {FormEntryActivity.this.

            try {
                Timber.d("++++ update questions, %s, index :%d", changedWidget.getPrompt().getQuestionText(), changedWidget.getPrompt().getIndex().getInstanceIndex());
                updateFieldListQuestions(changedWidget.getPrompt().getIndex());
                   /*
                this.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                        if (!this.isDisplayed(changedWidget)) {
                            this.scrollTo(changedWidget);
                        }
                        this.removeOnLayoutChangeListener(this);
                    }
                });
                */
            } catch (Throwable e) {
                Timber.d("++++ error %s", e.getMessage());
                Timber.e(e);
               //createErrorDialog(e.getMessage(), DO_NOT_EXIT);
            }

        }
    }

    /**
     * Returns true if any part of the question widget is currently on the screen or false otherwise.
     */
    /*
    public boolean isDisplayed(QuestionWidget qw) {
        Rect scrollBounds = new Rect();
        findViewById(R.id.odk_view_container).getHitRect(scrollBounds);
        return qw.getLocalVisibleRect(scrollBounds);
    }

    public void scrollTo(@Nullable QuestionWidget qw) {
        if (qw != null && widgets.contains(qw)) {
            findViewById(R.id.odk_view_container).scrollTo(0, qw.getTop());
        }
    }
*/
    /**
     * Saves the form and updates displayed widgets accordingly:
     * - removes widgets corresponding to questions that are no longer relevant
     * - adds widgets corresponding to questions that are newly-relevant
     * - removes and rebuilds widgets corresponding to questions that have changed in some way. For
     * example, the question text or hint may have updated due to a value they refer to changing.
     * <p>
     * The widget corresponding to the {@param lastChangedIndex} is never changed.
     */
    private void updateFieldListQuestions(FormIndex lastChangedIndex) throws FormDesignException {
        Timber.d("+++ updateFieldListQuestions at index %d", lastChangedIndex.getInstanceIndex());
        // Save the user-visible state for all questions in this field-list
        FormEntryPrompt[] questionsBeforeSave = FormController.getActive().getQuestionPrompts();
        List<ImmutableDisplayableQuestion> immutableQuestionsBeforeSave = new ArrayList<>();
        Timber.d("+++ immutable Questions Before Save :");
        for (FormEntryPrompt questionBeforeSave : questionsBeforeSave) {
            Timber.d("+++ %s : %d", questionBeforeSave.getQuestionText(), questionBeforeSave.getIndex().getLocalIndex() );
            immutableQuestionsBeforeSave.add(new ImmutableDisplayableQuestion(questionBeforeSave));
        }

        saveAnswersForCurrentScreen(questionsBeforeSave, immutableQuestionsBeforeSave);

        FormEntryPrompt[] questionsAfterSave = FormController.getActive().getQuestionPrompts();

        Map<FormIndex, FormEntryPrompt> questionsAfterSaveByIndex = new HashMap<>();
        Timber.d("+++ Questions after Save :");
        for (FormEntryPrompt question : questionsAfterSave) {
            Timber.d("+++ %s : %d", question.getQuestionText(), question.getIndex().getLocalIndex() );
            questionsAfterSaveByIndex.put(question.getIndex(), question);
        }

        // Identify widgets to remove or rebuild (by removing and re-adding). We'd like to do the
        // identification and removal in the same pass but removal has to be done in a loop that
        // starts from the end and itemset-based select choices will only be correctly recomputed
        // if accessed from beginning to end because the call on sameAs is what calls
        // populateDynamicChoices. See https://github.com/getodk/javarosa/issues/436
        List<FormEntryPrompt> questionsThatHaveNotChanged = new ArrayList<>();
        List<FormIndex> formIndexesToRemove = new ArrayList<>();
        for (ImmutableDisplayableQuestion questionBeforeSave : immutableQuestionsBeforeSave) {
            FormEntryPrompt questionAtSameFormIndex = questionsAfterSaveByIndex.get(questionBeforeSave.getFormIndex());

            // Always rebuild questions that use database-driven external data features since they
            // bypass SelectChoices stored in ImmutableDisplayableQuestion
            if (questionBeforeSave.sameAs(questionAtSameFormIndex)
                    ) {//&& !FormController.getActive().usesDatabaseExternalDataFeature(questionBeforeSave.getFormIndex())
                questionsThatHaveNotChanged.add(questionAtSameFormIndex);
                Timber.d("+++  add to NEpromenjena pitanja %d", questionBeforeSave.getFormIndex().getLocalIndex() );
            } else if (!lastChangedIndex.equals(questionBeforeSave.getFormIndex())) {
                formIndexesToRemove.add(questionBeforeSave.getFormIndex());
                Timber.d("+++  add to promenjena pitanja %d", questionBeforeSave.getFormIndex().getLocalIndex() );
            }
        }

        for (int i = immutableQuestionsBeforeSave.size() - 1; i >= 0; i--) {
            ImmutableDisplayableQuestion questionBeforeSave = immutableQuestionsBeforeSave.get(i);

            if (formIndexesToRemove.contains(questionBeforeSave.getFormIndex())) {
                Timber.d("+++ odkView.removeWidgetAt %d", questionBeforeSave.getFormIndex().getInstanceIndex());
                removeWidgetAt(i);
            }
        }

        for (int i = 0; i < questionsAfterSave.length; i++) {
            if (!questionsThatHaveNotChanged.contains(questionsAfterSave[i])
                    && !questionsAfterSave[i].getIndex().equals(lastChangedIndex)) {
                // The values of widgets in intent groups are set by the view so widgetValueChanged
                // is never called. This means readOnlyOverride can always be set to false.
                addWidgetForQuestion(questionsAfterSave[i], i);
                Timber.d("+++ odkView.addWidgetForQuestion %d, %s", i, questionsAfterSave[i].getQuestionText() );
            }
        }

    }

    // The method saves questions one by one in order to support calculations in field-list groups
    private void saveAnswersForCurrentScreen(FormEntryPrompt[] mutableQuestionsBeforeSave, List<ImmutableDisplayableQuestion> immutableQuestionsBeforeSave) {
        Timber.d("+++ saveAnswersForCurrentScreen");
        Timber.d("+++ mutableQuestionsBeforeSave:");
        int t=1;
        for (FormEntryPrompt p: mutableQuestionsBeforeSave){
            Timber.d("+++ %d. %s", t, p.getQuestionText());
            t++;
        }

        Timber.d("+++ immutableQuestionsBeforeSave:");
        int tt=1;

        for (ImmutableDisplayableQuestion q: immutableQuestionsBeforeSave){
            Timber.d("+++ %d. %s", tt,  q.toString());
            tt++;
        }

        FormController formController = FormController.getActive();
        if (formController == null) {
            Timber.d("++++ FormController is null");
            return;
        }

        int index = 0;
        for (Map.Entry<FormIndex, IAnswerData> answer : this.getAnswers().entrySet()) {
            // Questions with calculates will have their answers updated as the questions they depend on are saved
            Timber.d("+++++ index %d", index);
            if (!isQuestionRecalculated(mutableQuestionsBeforeSave[index], immutableQuestionsBeforeSave.get(index))) {
                try {
                    Timber.d("+++ ssaveOneScreenAnswer %s, %s", answer.getKey().getInstanceIndex(), answer.getValue().getDisplayText());
                    formController.saveOneScreenAnswer(answer.getKey(), answer.getValue(), false);
                } catch (JavaRosaException e) {
                    Timber.e(e);
                }
            }
            index++;
        }
    }

    // If an answer has changed after saving one of previous answers that means it has been recalculated automatically
    private boolean isQuestionRecalculated(FormEntryPrompt mutableQuestionBeforeSave, ImmutableDisplayableQuestion immutableQuestionBeforeSave) {
        Boolean bb = !(mutableQuestionBeforeSave.getAnswerText().equals(immutableQuestionBeforeSave.getAnswerText()));
        Timber.d("++++ isQuestionRecalculated %s, %b", mutableQuestionBeforeSave.getQuestionText(), bb);
        //return !Objects.equals(mutableQuestionBeforeSave.getAnswerText(), immutableQuestionBeforeSave.getAnswerText());
        return bb;
    }

    /**
     * Removes the widget and corresponding divider at a particular index.
     */
    public void removeWidgetAt(int index) {
        Timber.d("++++ removeWidgetAt %d", index);
        int indexAccountingForDividers = index * 2;

        // There may be a first TextView to display the group path. See addGroupText(FormEntryCaption[])
        if (this.getChildCount() > 0 && this.getChildAt(0) instanceof TextView) {
            indexAccountingForDividers += 1;
        }
        this.removeViewAt(indexAccountingForDividers);

        if (index > 0) {
            this.removeViewAt(indexAccountingForDividers - 1);
        }

        widgets.remove(index);
    }

    /**
     * Creates a {@link QuestionWidget} for the given {@link FormEntryPrompt}, sets its listeners,
     * and adds it to the end of the view. If this widget is not the first one, add a divider above
     * it.
     */
    private void addWidgetForQuestion(FormEntryPrompt question) {
        Timber.d("++++ addWidgetForQuestion %s", question.getQuestionText());
        QuestionWidget qw = configureWidgetForQuestion(question);

        widgets.add(qw);

        if (widgets.size() > 1) {
            this.addView(getDividerView());
        }
        this.addView(qw, widgetLayout);
    }

    /**
     * Creates a {@link QuestionWidget} for the given {@link FormEntryPrompt}, sets its listeners,
     * and adds it to the view at the specified {@code index}. If this widget is not the first one,
     * add a divider above it. If the specified {@code index} is beyond the end of the widget list,
     * add it to the end.
     */
    public void addWidgetForQuestion(FormEntryPrompt question, int index) {
        Timber.d("++++ addWidgetForQuestion %s, %d", question.getQuestionText(), index);
        if (index > widgets.size() - 1) {
            addWidgetForQuestion(question);
            return;
        }

        QuestionWidget qw = configureWidgetForQuestion(question);

        widgets.add(index, qw);

        int indexAccountingForDividers = index * 2;
        if (index > 0) {
            this.addView(getDividerView(), indexAccountingForDividers - 1);
        }

        this.addView(qw, indexAccountingForDividers, widgetLayout);
    }

    /**
     * Creates and configures a {@link QuestionWidget} for the given {@link FormEntryPrompt}.
     * <p>
     * Note: if the given question is of an unsupported type, a text widget will be created.
     */
    private QuestionWidget configureWidgetForQuestion(FormEntryPrompt question) {
        // when the grouped fields are populated by an external app, this will get true.
        boolean readOnlyOverride = false;
        QuestionWidget qw = WidgetFactory.createWidgetFromPrompt(question, getContext(), readOnlyOverride);
        qw.setValueChangedListener(this);

        return qw;
    }

    private View getDividerView() {
        LayoutInflater inflater = LayoutInflater.from(getContext());

        return inflater.inflate(R.layout.collect_form_delimiter,null);
    }

}
