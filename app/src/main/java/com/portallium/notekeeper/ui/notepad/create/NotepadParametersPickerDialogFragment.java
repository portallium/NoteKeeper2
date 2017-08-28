package com.portallium.notekeeper.ui.notepad.create;

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
import android.widget.Toast;

import com.portallium.notekeeper.R;
import com.portallium.notekeeper.beans.Notepad;
import com.portallium.notekeeper.database.FirebaseDatabaseHelper;
import com.portallium.notekeeper.database.StorageKeeper;
import com.portallium.notekeeper.ui.list.NotesListFragment;

import java.util.concurrent.ExecutionException;

public class NotepadParametersPickerDialogFragment extends DialogFragment {

    private static final String ARG_USER_ID = "userId";
    private static final String ARG_FIREBASE_ID = "firebaseId";

    private EditText mNotepadTitle;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_new_notepad, null);

        mNotepadTitle = v.findViewById(R.id.dialog_notepad_set_title);

        return new AlertDialog.Builder(getActivity())
                .setView(v)
                .setTitle(R.string.create_notepad)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String noteTitle = mNotepadTitle.getText().toString();
                        if (noteTitle.isEmpty()) {
                            Toast.makeText(getActivity(), R.string.toast_empty_notepad_title, Toast.LENGTH_LONG).show();
                            sendResult(Activity.RESULT_CANCELED);
                            return; //чтобы не создавать блокнот с пустым названием
                        }
                        Notepad newNotepad = new Notepad(getArguments().getInt(ARG_USER_ID), noteTitle);
                        StorageKeeper.AddNotepadToDatabaseTask addNotepadTask = StorageKeeper.getInstance(getContext()).new AddNotepadToDatabaseTask();
                        try {
                            addNotepadTask.execute(newNotepad);
                            if (addNotepadTask.get() < 0) {
                                Toast.makeText(getActivity(), String.format(getString(R.string.dialog_duplicate_notepad), noteTitle), Toast.LENGTH_LONG).show();
                                sendResult(Activity.RESULT_CANCELED);
                            } else {
                                sendResult(Activity.RESULT_OK);
                            }
                            //можно сделать хитрее. Добавить сначала в firebase, потом onChildEventListener -
                            //добавить в локальную БД и обновить UI.
                            FirebaseDatabaseHelper.getInstance().addNotepadToDatabase(getArguments().getString(ARG_FIREBASE_ID), newNotepad);
                        }
                        catch (InterruptedException | ExecutionException ex) {
                            Log.e("Adding Notepad to DB", ex.getMessage(), ex);
                            sendResult(Activity.RESULT_CANCELED);
                        }

                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        sendResult(Activity.RESULT_CANCELED);
                    }
                })
                .create();
    }

    public static NotepadParametersPickerDialogFragment newInstance(int userId, String firebaseId) {
        Bundle args = new Bundle();
        args.putInt(ARG_USER_ID, userId);
        args.putString(ARG_FIREBASE_ID, firebaseId);
        NotepadParametersPickerDialogFragment fragment = new NotepadParametersPickerDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private void sendResult(int resultCode) {
        getTargetFragment().onActivityResult(NotesListFragment.NEW_NOTEPAD_REQUEST, resultCode, null);
    }
}
