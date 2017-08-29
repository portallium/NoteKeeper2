package com.portallium.notekeeper.database;

/**
 * Вырожденный класс, содержащий исключительно константы для обращения к базе данных.
 * По таким референсам к БД обращаться логичнее, чем без них - меньше вероятность ошибиться.
 */
public final class DatabaseConstants {

    /**
     * Константы, используемые для доступа к таблице Users.
     */
    public static final class Users {
        public static final String TABLE_NAME = "users";

        public static final class Columns {
            public static final String ID = "_id";
            public static final String LOGIN = "login";
            public static final String ENCRYPTED_PASSWORD = "encrypted_password";
            public static final String SALT = "salt";
        }
    }

    /**
     * Константы, используемые для доступа к таблице Notepads.
     */
    public static final class Notepads {
        public static final String TABLE_NAME = "notepads";

        public static final class Columns {
            public static final String NOTEPAD_ID = "_id";
            public static final String CREATOR_ID = "user_id";
            public static final String TITLE = "title";
            public static final String CREATION_DATE = "creation_date";
            public static final String FIREBASE_ID = "firebase_id";
        }
    }

    /**
     * Константы, используемые для доступа к таблице Notes.
     */
    public static final class Notes {
        public static final String TABLE_NAME = "notes";

        public static final class Columns {
            public static final String NOTE_ID = "_id";
            public static final String NOTEPAD_ID = "notepad_id";
            public static final String CREATOR_ID = "user_id";
            public static final String TITLE = "title";
            public static final String CREATION_DATE = "creation_date";
            public static final String TEXT = "text";
            public static final String FIREBASE_ID = "firebase_id";
        }
    }
}