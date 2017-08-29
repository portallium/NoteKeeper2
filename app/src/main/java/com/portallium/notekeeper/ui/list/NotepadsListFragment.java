package com.portallium.notekeeper.ui.list;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.portallium.notekeeper.R;
import com.portallium.notekeeper.beans.Note;
import com.portallium.notekeeper.beans.Notepad;
import com.portallium.notekeeper.database.StorageKeeper;
import com.portallium.notekeeper.ui.notepad.rename.NotepadRenameDialogFragment;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class NotepadsListFragment extends AbstractListFragment<Notepad> {

    private static final String ARG_USER_ID = "userId";
    private static final String ARG_FIREBASE_ID = "firebaseId";

    public static NotepadsListFragment newInstance(int userId, String firebaseId) {
        Bundle args = new Bundle();
        args.putInt(ARG_USER_ID, userId);
        args.putString(ARG_FIREBASE_ID, firebaseId);
        NotepadsListFragment fragment = new NotepadsListFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    List<Notepad> getElementsListFromDatabase() {
        StorageKeeper.GetUserNotepadsAsListTask getUserNotepadsAsListTask = StorageKeeper.getInstance(getActivity(), getArguments().getString(ARG_FIREBASE_ID)).new GetUserNotepadsAsListTask();
        getUserNotepadsAsListTask.execute(getArguments().getInt(ARG_USER_ID));
        try {
            List<Notepad> notepads = getUserNotepadsAsListTask.get();
            //добавить типа-блокнот "all" (id = 0) со всеми заметками.
            notepads.add(0, new Notepad(getArguments().getInt(ARG_USER_ID), getString(R.string.notes_all), 0));
            return notepads;
        }
        catch (InterruptedException | ExecutionException ex) {
            Log.e("Getting notepads list: ", ex.getMessage(), ex);
            return null;
        }
    }

    @Override
    AbstractListAdapter<Notepad> createAdapter(List<Notepad> elementsList) {
        return new NotepadAdapter(elementsList);
    }

    private class NotepadAdapter extends AbstractListAdapter<Notepad> {
        @Override
        public NotepadViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new NotepadViewHolder(LayoutInflater.from(getActivity()), parent, R.layout.list_notepad_holder);
        }

        NotepadAdapter(List<Notepad> notepads){
            super(notepads);
        }
    }

    private class NotepadViewHolder extends AbstractListFragment<Notepad>.AbstractViewHolder<Notepad>{

        private TextView mNotepadTitlePreview;
        private TextView mNotepadNotesCounter;

        private Notepad mNotepad;

        @Override
        void bind(Notepad element) {
            //логика вывода. Вероятно, запрос к БД на количество заметок нужен здесь.
            mNotepad = element;

            StorageKeeper.GetUserNotesAsListTask getUserNotesAsListTask = StorageKeeper.getInstance(getActivity(), getArguments().getString(ARG_FIREBASE_ID)).new GetUserNotesAsListTask();
            getUserNotesAsListTask.execute(mNotepad.getCreatorId(), mNotepad.getId());
            //TODO: рассмотреть возможность написания Map<Notepad, NotesCounter>, чтобы вместо N раз дергать БД один.

            mNotepadTitlePreview.setText(element.getTitle());

            int notesCounter = -1;
            try {
                List<Note> notes = getUserNotesAsListTask.get();
                notesCounter = notes.size();
            }
            catch (InterruptedException | ExecutionException ex) {
                Log.e("Getting notepad's notes", ex.getMessage(), ex);
            }

            mNotepadNotesCounter.setText(String.format(getString(R.string.notes_counter), notesCounter));
        }

        public NotepadViewHolder(LayoutInflater inflater, ViewGroup parent, int layoutId) {
            super(inflater, parent, layoutId);
            //инициализация компонентов макета
            mNotepadTitlePreview = itemView.findViewById(R.id.holder_notepad_title);
            mNotepadNotesCounter = itemView.findViewById(R.id.holder_notepad_notes_counter);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    NotesListFragment fragment = NotesListFragment.newInstance(mNotepad.getCreatorId(), mNotepad.getId(), getArguments().getString(ARG_FIREBASE_ID));
                    getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
                }
            });
            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    DialogFragment fragment = NotepadRenameDialogFragment.newInstance(mNotepad, getArguments().getString(ARG_FIREBASE_ID));
                    fragment.setTargetFragment(NotepadsListFragment.this, REQUEST_PARAMETERS);
                    fragment.show(getFragmentManager(), PARAMETERS_DIALOG);
                    return true;
                }
            });
            //TODO: научиться удалять блокноты по свайпу.

        }
    }
}
