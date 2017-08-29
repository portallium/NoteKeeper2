package com.portallium.notekeeper.ui.list;

import android.content.Context;
import android.content.Intent;
import android.support.v4.app.Fragment;

import com.portallium.notekeeper.ui.SingleFragmentActivity;

/**
 * Однофрагментная активность, которая хостит RecyclerView с заметками. Либо с блокнотами.
 */
public class ListActivity extends SingleFragmentActivity {

    public static final String EXTRA_USER_ID = "com.portallium.notekeeper.user_id";
    public static final String EXTRA_FIREBASE_ID = "com.portallium.notekeeper.user_key";

    @Override
    protected Fragment createFragment() {
        return NotepadsListFragment.newInstance(getIntent().getIntExtra(EXTRA_USER_ID, 0), getIntent().getStringExtra(EXTRA_FIREBASE_ID));
    }

    public static Intent getIntent(Context context, int userId, String firebaseId) {
        Intent startMainActivityIntent = new Intent(context, ListActivity.class);
        startMainActivityIntent.putExtra(EXTRA_USER_ID, userId);
        startMainActivityIntent.putExtra(EXTRA_FIREBASE_ID, firebaseId);
        return startMainActivityIntent;
    }
}
