package com.portallium.notekeeper.ui.notepad.rename;

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
import com.portallium.notekeeper.database.StorageKeeper;
import com.portallium.notekeeper.ui.list.NotesListFragment;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class NotepadRenameDialogFragment extends DialogFragment {

    public static final String ARG_NOTEPAD = "notepadName";
    private static final String ARG_FIREBASE_ID = "firebaseId";

    private EditText mNotepadTitle;

    private Notepad mNotepad;

    public static NotepadRenameDialogFragment newInstance(Notepad notepad, String firebaseId) {
        Bundle args = new Bundle();
        args.putSerializable(ARG_NOTEPAD, notepad);
        args.putString(ARG_FIREBASE_ID, firebaseId);
        NotepadRenameDialogFragment fragment = new NotepadRenameDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_rename_notepad, null);

        mNotepadTitle = v.findViewById(R.id.dialog_rename_notepad_edittext);

        mNotepad = (Notepad) getArguments().getSerializable(ARG_NOTEPAD);
        if (mNotepad != null) {
            mNotepadTitle.setText(mNotepad.getTitle());
        }
        return new AlertDialog.Builder(getActivity())
                .setView(v)
                .setTitle(R.string.rename_notepad)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String noteTitle = mNotepadTitle.getText().toString();
                        if (noteTitle.isEmpty()) {
                            Toast.makeText(getActivity(), R.string.toast_empty_notepad_title, Toast.LENGTH_LONG).show();
                            sendResult(Activity.RESULT_CANCELED);
                            return;
                        }

                        //проверить, нет ли уже блокнота с таким названием.
                        StorageKeeper.GetUserNotepadsAsListTask getUserNotepadsAsListTask = StorageKeeper.getInstance(getActivity(), getArguments().getString(ARG_FIREBASE_ID)).new GetUserNotepadsAsListTask();
                        getUserNotepadsAsListTask.execute(mNotepad.getCreatorId());
                        try {
                            List<Notepad> notepads = getUserNotepadsAsListTask.get();
                            for (Notepad notepad : notepads) {
                                if (noteTitle.equals(notepad.getTitle())) {
                                    sendResult(Activity.RESULT_CANCELED);
                                    Toast.makeText(getActivity(), String.format(getString(R.string.dialog_duplicate_notepad), noteTitle), Toast.LENGTH_LONG).show();
                                    return;
                                }
                            }
                        }
                        catch (InterruptedException | ExecutionException ex) {
                            Log.e("Getting notepads' list", ex.getMessage(), ex);
                            sendResult(Activity.RESULT_CANCELED);
                        }

                        mNotepad.setTitle(noteTitle);
                        StorageKeeper.UpdateNotepadTask updateNotepadTask = StorageKeeper.getInstance(getActivity(), getArguments().getString(ARG_FIREBASE_ID)).new UpdateNotepadTask();
                        updateNotepadTask.execute(mNotepad);
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

    }


    private void sendResult(int resultCode) {
        getTargetFragment().onActivityResult(NotesListFragment.RENAME_NOTEPAD_REQUEST, resultCode, null);
    }
}
