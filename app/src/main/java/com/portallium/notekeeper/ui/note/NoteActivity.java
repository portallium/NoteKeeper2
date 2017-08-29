package com.portallium.notekeeper.ui.note;

import android.content.Context;
import android.content.Intent;
import android.support.v4.app.Fragment;

import com.portallium.notekeeper.beans.Note;
import com.portallium.notekeeper.ui.SingleFragmentActivity;

public class NoteActivity extends SingleFragmentActivity {

    private static final String EXTRA_NOTE = "com.portallium.notekeeper.ui.note";
    private static final String EXTRA_FIREBASE_ID = "com.portallium.notekeeper.user_key";

    @Override
    protected Fragment createFragment() {
        return NoteFragment.newInstance((Note)getIntent().getSerializableExtra(EXTRA_NOTE), getIntent().getStringExtra(EXTRA_FIREBASE_ID));
    }

    public static Intent getIntent(Context context, Note note, String firebaseUserId) {
        Intent intent = new Intent(context, NoteActivity.class);
        intent.putExtra(EXTRA_NOTE, note);
        intent.putExtra(EXTRA_FIREBASE_ID, firebaseUserId);
        return intent;
    }
}
