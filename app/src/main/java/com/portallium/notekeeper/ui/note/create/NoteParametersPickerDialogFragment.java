package com.portallium.notekeeper.ui.note.create;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.portallium.notekeeper.R;
import com.portallium.notekeeper.beans.Note;
import com.portallium.notekeeper.database.FirebaseDatabaseHelper;
import com.portallium.notekeeper.database.StorageKeeper;
import com.portallium.notekeeper.ui.list.NotesListFragment;
import com.portallium.notekeeper.ui.utilities.NotepadSpinnerHelper;

import java.util.concurrent.ExecutionException;

public class NoteParametersPickerDialogFragment extends DialogFragment {

    private static final String ARG_USER_ID = "userId";
    private static final String ARG_NOTEPAD_ID = "notepadId";
    private static final String ARG_FIREBASE_ID = "firebaseId";

    private EditText mNoteTitle;
    private Spinner mNotepadPicker;
    private EditText mNoteText;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_new_note, null);

        mNoteTitle = v.findViewById(R.id.dialog_note_title);

        mNotepadPicker = v.findViewById(R.id.dialog_note_notepad_picker);

        //Все это чудо по получению списка блокнотов из БД выносится в отдельный поток
        final NotepadSpinnerHelper notepadSpinnerHelper = new NotepadSpinnerHelper();
        mNotepadPicker.setAdapter(notepadSpinnerHelper.createCursorAdapter(getActivity(), getArguments().getInt(ARG_USER_ID)));
        int defaultNotepadId = getArguments().getInt(ARG_NOTEPAD_ID, -1);
        if (defaultNotepadId > 0) {
            mNotepadPicker.setSelection(notepadSpinnerHelper.getSpinnerPositionByNotepadId(defaultNotepadId));
        }

        mNoteText = v.findViewById(R.id.dialog_note_text);

        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.dialog_create_note)
                .setView(v)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String noteText = mNoteText.getText().toString();
                        String noteTitle = mNoteTitle.getText().toString();
                        if (noteText.isEmpty() && noteTitle.isEmpty()) {
                            Toast.makeText(getActivity(), R.string.toast_empty_note, Toast.LENGTH_LONG).show();
                            return;
                        }
                        int notepadId = notepadSpinnerHelper.getNotepadIdBySpinnerPosition(mNotepadPicker.getSelectedItemPosition());
                        Note newNote = new Note (notepadId, getArguments().getInt(ARG_USER_ID), noteTitle, noteText);
                        //добавляем заметку в БД
                        StorageKeeper.AddNoteToDatabaseTask addNoteTask = StorageKeeper.getInstance(getActivity()).new AddNoteToDatabaseTask();
                        addNoteTask.execute(newNote);
                        try {
                            newNote.setId(addNoteTask.get());
                        }
                        catch (ExecutionException | InterruptedException ex) {
                            Log.e("Adding note to DB", ex.getMessage(), ex);
                        }

                        //добавляем заметку в firebase
                        FirebaseDatabaseHelper.getInstance().addNoteToDatabase(getArguments().getString(ARG_FIREBASE_ID), newNote);

                        sendResult(Activity.RESULT_OK);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        sendResult(Activity.RESULT_CANCELED);
                    }
                })
                .create();
        //TODO: переписать так, чтобы при неверном инпуте (либо ошибке добавления в БД) пользователь оставался в диалоге (то же - в notepad).
    }

    public static NoteParametersPickerDialogFragment newInstance(int userId, String firebaseId) {

        Bundle args = new Bundle();
        args.putInt(ARG_USER_ID, userId);
        args.putString(ARG_FIREBASE_ID, firebaseId);
        NoteParametersPickerDialogFragment fragment = new NoteParametersPickerDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    //от метода выше этот отличается тем, что туда можно передать id блокнота, который при
    //старте диалога будет выбором по дефолту.
    //этот метод будет вызываться из экрана со списком заметок.
    public static NoteParametersPickerDialogFragment newInstance(int userId, int notepadId, String firebaseId) {
        Bundle args = new Bundle();
        args.putInt(ARG_USER_ID, userId);
        args.putInt(ARG_NOTEPAD_ID, notepadId);
        args.putString(ARG_FIREBASE_ID, firebaseId);
        NoteParametersPickerDialogFragment fragment = new NoteParametersPickerDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private void sendResult(int resultCode) {
        getTargetFragment().onActivityResult(NotesListFragment.NEW_NOTE_REQUEST, resultCode, null);
    }
}
