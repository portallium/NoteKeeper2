package com.portallium.notekeeper.ui.list;


import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.portallium.notekeeper.R;
import com.portallium.notekeeper.beans.Note;
import com.portallium.notekeeper.database.StorageKeeper;
import com.portallium.notekeeper.ui.note.NoteActivity;
import com.portallium.notekeeper.ui.note.create.NoteParametersPickerDialogFragment;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class NotesListFragment extends AbstractListFragment<Note> {

    private static final String ARG_USER_ID = "userId";
    private static final String ARG_NOTEPAD_ID = "notepadId";
    private static final String ARG_FIREBASE_ID = "firebaseId";

    /**
     * Максимально допустимая длина названия заметки в ее превью во ViewHolder. Значение = {@value}
     */
    private static final int TITLE_PREVIEW_MAX_LENGTH = 30;
    /**
     * Максимально допустимая длина названия блокнота в его превью во ViewHolder. Значение = {@value}
     */
    private static final int NOTEPAD_TITLE_PREVIEW_MAX_LENGTH = 20;
    /**
     * Максимально допустимая длина текста заметки в ее превью во ViewHolder. Значение = {@value}
     */
    private static final int TEXT_PREVIEW_MAX_LENGTH = 60;
    /**
     * Максимально допустимое количество строк в превью текста заметки во ViewHolder. Значение = {@value}
     */
    private static final int TEXT_PREVIEW_MAX_LINES = 3;


    public static NotesListFragment newInstance(int userId, int notepadId, String firebaseId) {
        Bundle args = new Bundle();
        args.putInt(ARG_USER_ID, userId);
        args.putInt(ARG_NOTEPAD_ID, notepadId);
        args.putString(ARG_FIREBASE_ID, firebaseId);
        NotesListFragment fragment = new NotesListFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    NoteAdapter createAdapter(List<Note> elementsList) {
        return new NoteAdapter(elementsList);
    }

    @Override
    List<Note> getElementsListFromDatabase() {
        StorageKeeper.GetUserNotesAsListTask getNotesTask = StorageKeeper.getInstance(getActivity(), getArguments().getString(ARG_FIREBASE_ID)).new GetUserNotesAsListTask();
        getNotesTask.execute(NotesListFragment.this.getArguments().getInt(ARG_USER_ID), NotesListFragment.this.getArguments().getInt(ARG_NOTEPAD_ID));
        try {
            return getNotesTask.get();
        }
        catch (InterruptedException | ExecutionException ex) {
            Log.e("Getting notes: ", ex.getMessage(), ex);
            return null;
        }
        //TODO:если в блокноте нет заметок, нарисовать об этом картинку
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_notes_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.icon_go_to_notepads) { //иконка появляется только в этом фрагменте
            NotepadsListFragment fragment = NotepadsListFragment.newInstance(getArguments().getInt(ARG_USER_ID), getArguments().getString(ARG_FIREBASE_ID));
            getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
            return true;
        } else if (item.getItemId() == R.id.icon_new_note) {
            return startDialogFragment(NoteParametersPickerDialogFragment.newInstance(getArguments().getInt(ARG_USER_ID), getArguments().getInt(ARG_NOTEPAD_ID), getActivity().getIntent().getStringExtra(ListActivity.EXTRA_FIREBASE_ID)));
            //если notepadId = 0, то на вызываемом фрагменте это никак не скажется.
        }
        else {
            return super.onOptionsItemSelected(item);
        }
    }

    private class NoteViewHolder extends AbstractListFragment<Note>.AbstractViewHolder<Note> {

        private TextView mNoteTitleView;
        private TextView mNoteTextPreview;

        private Note mNote;

        @Override
        public void bind(Note element) {
            mNote = element;

            StorageKeeper.GetNotepadTitleByIdTask task = StorageKeeper.getInstance(getActivity(), getArguments().getString(ARG_FIREBASE_ID)).new GetNotepadTitleByIdTask();
            task.execute(mNote.getNotepadId());

            String titlePreview = mNote.getTitle().trim();
            if (titlePreview.length() > TITLE_PREVIEW_MAX_LENGTH) {
                titlePreview = titlePreview.substring(0, TITLE_PREVIEW_MAX_LENGTH - 3).trim() + "...";
            }

            try {
                String notepadTitle = task.get();
                if (notepadTitle.length() > NOTEPAD_TITLE_PREVIEW_MAX_LENGTH)
                    notepadTitle = notepadTitle.substring(0, NOTEPAD_TITLE_PREVIEW_MAX_LENGTH - 3) + "...";
                titlePreview = titlePreview.concat(" [" + notepadTitle.toUpperCase() + "]");
            }
            catch (InterruptedException | ExecutionException ex) {
                Log.e("Getting notepad title", ex.getMessage(), ex);
            }
            mNoteTitleView.setText(titlePreview);
            //если название заметки длиннее 30 символов, то превью будет выводить только первые 27.
            //(для названия блокнота - то же самое для 20 символов: будет выведено 17).

            //с превью текста похожая история. но там еще важно учитывать количество абзацев. я не хочу, чтобы
            //превью одной заметки занимало весь экран.
            String textPreview = mNote.getText().trim();
            String[] lines = textPreview.split("\n", TEXT_PREVIEW_MAX_LINES + 1);
            textPreview = "";
            for (int i = 0; i < TEXT_PREVIEW_MAX_LINES && i < lines.length; ++i) {
                textPreview = textPreview.concat(lines[i]);
                if (i < (TEXT_PREVIEW_MAX_LINES - 1) && i < (lines.length - 1)) {
                    textPreview = textPreview.concat("\n");
                }
            }
            //теперь textPreview - это первых три абзаца заметки
            boolean noteIsLong = false;
            if (textPreview.length() > TEXT_PREVIEW_MAX_LENGTH) {
                textPreview = textPreview.substring(0, TEXT_PREVIEW_MAX_LENGTH - 3).trim();
                noteIsLong = true;
            }
            if (lines.length > TEXT_PREVIEW_MAX_LINES || noteIsLong) {
                textPreview = textPreview.concat("...");
            }
            mNoteTextPreview.setText(textPreview.trim());
        }

        NoteViewHolder (LayoutInflater inflater, ViewGroup parent, int layoutId){
            super(inflater, parent, layoutId);
            mNoteTitleView = itemView.findViewById(R.id.view_holder_title);
            //забавно. Android Studio 3.0 не видит ресурс с этим id и выделяет его красным.
            //код, слава богу, компилируется. ну, хоть так.
            mNoteTextPreview = itemView.findViewById(R.id.view_holder_text_preview);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Log.d("Starting NoteActivity", "Note clicked");
                    startActivity(NoteActivity.getIntent(getActivity(), mNote, getArguments().getString(ARG_FIREBASE_ID)));
                }
            });
            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.dialog_confirm_note_deletion_title)
                            .setMessage(String.format(getString(R.string.dialog_confirm_note_deletion_text), mNote.getTitle()))
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    StorageKeeper.DeleteNoteTask deleteNoteTask = StorageKeeper.getInstance(getActivity(), getArguments().getString(ARG_FIREBASE_ID)).new DeleteNoteTask();
                                    deleteNoteTask.execute(mNote.getId());
                                    updateUI();
                                }
                            })
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    //do nothing, just close the dialog
                                }
                            })
                            .show();
                    //как android.R.string.yes, так и ...no выводят все те же "ok" и "cancel", интересно.
                    return true;
                }
            });
        }
    }

    private class NoteAdapter extends AbstractListAdapter<Note> {

        NoteAdapter(List<Note> noteList) {
            super(noteList);
        }

        @Override
        public NoteViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new NoteViewHolder(LayoutInflater.from(getActivity()), parent, R.layout.list_note_holder);
        }
        //по идее, все. адаптер должен работать.
    }
}
