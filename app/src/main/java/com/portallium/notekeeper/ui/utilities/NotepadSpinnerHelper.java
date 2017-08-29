package com.portallium.notekeeper.ui.utilities;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;

import com.portallium.notekeeper.R;
import com.portallium.notekeeper.database.DatabaseConstants;
import com.portallium.notekeeper.database.StorageKeeper;
import com.portallium.notekeeper.database.StorageKeeperCursorWrapper;

import java.util.concurrent.ExecutionException;

/**
 * В двух классах - NoteParametersPickerDialogFragment и NotesListFragment - требуется выполнять похожие операции.
 */

public class NotepadSpinnerHelper {

    private Cursor mNotepadsCursor;

    public SimpleCursorAdapter createCursorAdapter(Context context, int userId, String firebaseUserId) {
        final StorageKeeper.GetUserNotepadsAsCursorTask task = StorageKeeper.getInstance(context, firebaseUserId).new GetUserNotepadsAsCursorTask();
        task.execute(userId);

        try {
            mNotepadsCursor = task.get();
            return new SimpleCursorAdapter(
                    context,
                    R.layout.spinner_element_notepad_name,
                    mNotepadsCursor,
                    new String[]{DatabaseConstants.Notepads.Columns.TITLE},
                    new int[]{R.id.spinner_element_title},
                    0);
        }
        catch (InterruptedException | ExecutionException ex) {
            Log.e("GetAllUsersNotepadsTask", ex.getMessage(), ex);
            return null;
        }
    }

    public int getNotepadIdBySpinnerPosition(int position) {
        mNotepadsCursor.moveToPosition(position);
        return mNotepadsCursor.getInt(mNotepadsCursor.getColumnIndex(DatabaseConstants.Notepads.Columns.NOTEPAD_ID));
    }

    /**
     * Метод, нужный для инициализации спиннера.
     * @param notepadId id блокнота
     * @return номер блокнота с данным id в списке блокнотов пользователя либо -1, если блокнота с данным id нет в списке
     */
    public int getSpinnerPositionByNotepadId(int notepadId) {
        StorageKeeperCursorWrapper wrapper = new StorageKeeperCursorWrapper(mNotepadsCursor);
        wrapper.moveToFirst();
        int i = 0;
        while (!wrapper.isAfterLast()) {
            if (wrapper.parseNotepad().getId() == notepadId)
                return i;
            i++;
            wrapper.moveToNext();
        }
        return -1;
    }
}
