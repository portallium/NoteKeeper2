package com.portallium.notekeeper.ui.note;

import android.content.Context;
import android.content.Intent;
import android.support.v4.app.Fragment;

import com.portallium.notekeeper.beans.Note;
import com.portallium.notekeeper.ui.SingleFragmentActivity;

public class NoteActivity extends SingleFragmentActivity {

    private static final String EXTRA_NOTE = "com.portallium.notekeeper.controller.note";

    @Override
    protected Fragment createFragment() {
        return NoteFragment.newInstance((Note)getIntent().getSerializableExtra(EXTRA_NOTE));
    }

    public static Intent getIntent(Context context, Note note) {
        Intent intent = new Intent(context, NoteActivity.class);
        intent.putExtra(EXTRA_NOTE, note);
        return intent;
    }
}
