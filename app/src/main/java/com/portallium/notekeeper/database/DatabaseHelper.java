package com.portallium.notekeeper.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final int VERSION = 4; //
    private static final String DATABASE_NAME = "noteKeeperDatabase.db";

    public DatabaseHelper(Context context) {
        super (context, DATABASE_NAME, null, VERSION);
    }

    /**
     * Выполняет запросы для создания SQLite-таблиц, в которых будут храниться заметки и блокноты.
     * @param sqLiteDatabase база данных приложения, в которой будут созданы таблицы.
     */
    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {

        sqLiteDatabase.execSQL("PRAGMA FOREIGN_KEYS=ON;");

        //create table users...
        sqLiteDatabase.execSQL("CREATE TABLE " + DatabaseConstants.Users.TABLE_NAME + " (" +
                        DatabaseConstants.Users.Columns.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        DatabaseConstants.Users.Columns.LOGIN + ", " +
                        DatabaseConstants.Users.Columns.ENCRYPTED_PASSWORD + ", " +
                        DatabaseConstants.Users.Columns.SALT + ");");

        sqLiteDatabase.execSQL("CREATE TABLE " + DatabaseConstants.Notepads.TABLE_NAME +
                        " (" + DatabaseConstants.Notepads.Columns.NOTEPAD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        DatabaseConstants.Notepads.Columns.CREATOR_ID + " INTEGER, " +
                        DatabaseConstants.Notepads.Columns.TITLE + ", " +
                        DatabaseConstants.Notepads.Columns.CREATION_DATE + ", " +
                        DatabaseConstants.Notepads.Columns.FIREBASE_ID + " TEXT DEFAULT NULL, " +
                        DatabaseConstants.Notepads.Columns.FIREBASE_STATUS + " INTEGER DEFAULT " +
                        DatabaseConstants.FirebaseCodes.NEEDS_ADDITION + ", FOREIGN KEY (" +
                        DatabaseConstants.Notepads.Columns.CREATOR_ID + ") REFERENCES " +
                        DatabaseConstants.Users.TABLE_NAME + " (" + DatabaseConstants.Users.Columns.ID + "));");

        sqLiteDatabase.execSQL("CREATE TABLE " + DatabaseConstants.Notes.TABLE_NAME +
                        "( " + DatabaseConstants.Notes.Columns.NOTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        DatabaseConstants.Notes.Columns.NOTEPAD_ID + " INTEGER, " +
                        DatabaseConstants.Notes.Columns.CREATOR_ID + " INTEGER, " +
                        DatabaseConstants.Notes.Columns.TITLE + ", " +
                        DatabaseConstants.Notes.Columns.CREATION_DATE + ", " +
                        DatabaseConstants.Notes.Columns.TEXT + ", " +
                        DatabaseConstants.Notes.Columns.FIREBASE_ID + " TEXT DEFAULT NULL, " +
                        DatabaseConstants.Notes.Columns.FIREBASE_STATUS + " INTEGER DEFAULT " +
                        DatabaseConstants.FirebaseCodes.NEEDS_ADDITION + ", " +
                        DatabaseConstants.Notes.Columns.FIREBASE_NOTEPAD_ID + " TEXT DEFAULT NULL, FOREIGN KEY (" +
                        DatabaseConstants.Notes.Columns.NOTEPAD_ID + ") REFERENCES " +
                        DatabaseConstants.Notepads.TABLE_NAME + " (" + DatabaseConstants.Notepads.Columns.NOTEPAD_ID + "), " +
                        "FOREIGN KEY (" + DatabaseConstants.Notes.Columns.CREATOR_ID + ") REFERENCES " +
                        DatabaseConstants.Users.TABLE_NAME + " (" + DatabaseConstants.Users.Columns.ID + "));");
    }

    /**
     * Обновляет базу данных, если на пользовательском девайсе обнаружена устаревшая схема.
     * В частности, при обновлении с версии 1 до версии 2
     * добавляет колонки firebase_id, потому что их для синхронизации приходится хранить в SQLite.
     */
    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        switch (oldVersion) {
            case 1: {
                sqLiteDatabase.execSQL("ALTER TABLE " + DatabaseConstants.Notepads.TABLE_NAME + " ADD COLUMN " +
                        DatabaseConstants.Notepads.Columns.FIREBASE_ID + " TEXT DEFAULT NULL");
                sqLiteDatabase.execSQL("ALTER TABLE " + DatabaseConstants.Notes.TABLE_NAME + " ADD COLUMN " +
                        DatabaseConstants.Notes.Columns.FIREBASE_ID + " TEXT DEFAULT NULL");
                Log.d("Updating DB schema", "from v.1 to v.2");
                //break'a тут теперь нет, чтобы обновление продолжилось до актуальной версии.
            }
            case 2: {
                sqLiteDatabase.execSQL("ALTER TABLE " + DatabaseConstants.Notepads.TABLE_NAME + " ADD COLUMN " +
                        DatabaseConstants.Notepads.Columns.FIREBASE_STATUS + " INTEGER DEFAULT " +
                        DatabaseConstants.FirebaseCodes.NEEDS_ADDITION);
                sqLiteDatabase.execSQL("ALTER TABLE " + DatabaseConstants.Notes.TABLE_NAME + " ADD COLUMN " +
                        DatabaseConstants.Notes.Columns.FIREBASE_STATUS + " INTEGER DEFAULT " +
                        DatabaseConstants.FirebaseCodes.NEEDS_ADDITION);
                //todo: и вот тут! вызывается метод synchronize.
                Log.d("Updating DB schema", "from v.2 to v.3");
            }
            case 3: {
                sqLiteDatabase.execSQL("ALTER TABLE " + DatabaseConstants.Notes.TABLE_NAME + " ADD COLUMN " +
                        DatabaseConstants.Notes.Columns.FIREBASE_NOTEPAD_ID + " TEXT DEFAULT NULL;");
                break;
            }
            default: {
                throw new IllegalStateException("onUpgrade() called with unknown oldVersion " + oldVersion);
            }
        }
    }
}

//todo: удалить колонку FIREBASE_NOTEPAD_ID, это была очень плохая идея.
