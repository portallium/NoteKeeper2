package com.portallium.notekeeper.ui.note;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;

import com.portallium.notekeeper.R;
import com.portallium.notekeeper.beans.Note;
import com.portallium.notekeeper.database.StorageKeeper;
import com.portallium.notekeeper.ui.utilities.NotepadSpinnerHelper;

public class NoteFragment extends Fragment {

    private static final String ARG_NOTE = "Note";
    private static final String ARG_FIREBASE_ID = "firebaseId";

    private EditText mTitleText;
    private Spinner mNotepadSelector;
    private EditText mNoteText;

    private Note mNote;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mNote = (Note) getArguments().getSerializable(ARG_NOTE);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_note, container, false);
        mTitleText = v.findViewById(R.id.note_title);
        mTitleText.setText(mNote.getTitle());
        mTitleText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                EditText editText = (EditText) view;
                if (!b && !(editText.getText().toString().equals(mNote.getTitle()))) { //виджет потерял фокус, текст в поле изменен
                    mNote.setTitle(editText.getText().toString());
                    updateNoteInDatabase();
                }
            }
        });

        mNoteText = v.findViewById(R.id.note_text);
        mNoteText.setText(mNote.getText());
        mNoteText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                EditText editText = (EditText) view;
                if (!b && !(editText.getText().toString().equals(mNote.getText()))) {
                    mNote.setText(editText.getText().toString());
                    updateNoteInDatabase();
                }
            }
        });

        mNotepadSelector = v.findViewById(R.id.note_notepad_picker);
        final NotepadSpinnerHelper notepadSpinnerHelper = new NotepadSpinnerHelper();
        mNotepadSelector.setAdapter(notepadSpinnerHelper.createCursorAdapter(getActivity(), mNote.getCreatorId(), getArguments().getString(ARG_FIREBASE_ID)));
        mNotepadSelector.setSelection(notepadSpinnerHelper.getSpinnerPositionByNotepadId(mNote.getNotepadId()));
        mNotepadSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                //мне НЕ нравится, что этот коллбек срабатывает при запуске активности.
                int newNotepadId = notepadSpinnerHelper.getNotepadIdBySpinnerPosition(i);
                //если выбран тот же блокнот, в которм заметка изначально и находилась, то нечего в БД лишние запросы кидать.
                if (newNotepadId != mNote.getNotepadId()) {
                    mNote.setNotepadId(newNotepadId);
                    updateNoteInDatabase();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                //сомневаюсь, что здесь возможен такой расклад.
            }
        });
        return v;
    }

    @Override
    public void onPause() {
        //потому что какой-то из виджетов может быть в фокусе при нажатии кнопки back, например.
        super.onPause();
        mNote.setTitle(mTitleText.getText().toString());
        mNote.setText(mNoteText.getText().toString());
        updateNoteInDatabase();
    }

    public static NoteFragment newInstance(Note note, String firebaseId) {
        Bundle args = new Bundle();
        args.putSerializable(ARG_NOTE, note);
        args.putString(ARG_FIREBASE_ID, firebaseId);
        NoteFragment fragment = new NoteFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private void updateNoteInDatabase() {
        //вносим изменения в БД
        StorageKeeper.UpdateNoteTask updateNoteTask = StorageKeeper.getInstance(getActivity(), getArguments().getString(ARG_FIREBASE_ID)).new UpdateNoteTask();
        updateNoteTask.execute(mNote);
    }
}
