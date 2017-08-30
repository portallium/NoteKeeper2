package com.portallium.notekeeper.beans;


import java.io.Serializable;
import java.util.Date;

public class Notepad implements Serializable {

    public static final int ID_NOT_YET_ASSIGNED = -1;

    private int mCreatorId;
    private String mTitle;
    private Date mCreationDate;
    private int mId;

    private String mFirebaseId;
    private int mFirebaseStatus;

    public Notepad(int creatorId, String title, Date creationDate, int id, String firebaseId, int firebaseStatus) {
        this(creatorId, title, creationDate, id);
        mFirebaseId = firebaseId;
        mFirebaseStatus = firebaseStatus;
    }

    /**
     * Конструктор блокнота. Используется для парсинга существующего блокнота из SQLite.
     * @param creatorId локальный id создателя блокнота
     * @param title название блокнота
     * @param creationDate unix-время создания блокнота (в миллисекундах)
     * @param id локальный id блокнота
     */
    public Notepad(int creatorId, String title, Date creationDate, int id) {
        this(creatorId, title, id);
        mCreationDate = creationDate;
        //firebaseId инициализируется как null. Это правильно. Это так и надо.
    }

    /**
     * Конструктор блокнота. Используется для создания фальшивого блокнота "all notes", откуда в UI
     * открывается доступ ко всем заметкам пользователя.
     * @param creatorId локальный id создателя блокнота
     * @param title название блокнота
     * @param id локальный id блокнота
     */
    public Notepad(int creatorId, String title, int id) {
        this(creatorId, title);
        mId = id;
    }

    /**
     * Первоначальный конструктор блокнота. поле mId инициализируется как -1, что означает, что
     * блокнот пока не внесен в базу данных SQLite.
     * @param creatorId локальный id создателя блокнота
     * @param title название блокнота
     */
    public Notepad(int creatorId, String title) {
        mCreatorId = creatorId;
        mTitle = title;
        mCreationDate = new Date();
        mId = ID_NOT_YET_ASSIGNED;
    }

    private Notepad() {} //этот конструктор нужен Firebase.

    public int getCreatorId() {
        return mCreatorId;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        this.mTitle = title;
    }

    public Date getCreationDate() {
        return mCreationDate;
    }

    public int getId() {
        return mId;
    }

    public void setId(int id) {
        this.mId = id;
    }

    public String getFirebaseId() {
        return mFirebaseId;
    }

    public void setFirebaseId(String firebaseId) {
        this.mFirebaseId = firebaseId;
    }

    public int getFirebaseStatus() {
        return mFirebaseStatus;
    }

    public void setFirebaseStatus(int firebaseStatus) {
        this.mFirebaseStatus = firebaseStatus;
    }

    /**
     * Метод, проверяющий идентичность двух блокнотов.
     * Два блокнота идентичны, если все их поля, кроме mFirebaseId, равны между собой.
     * Поле mFirebaseId в расчет не принимается.
     * @param o блокнот, с которым производится сравнение.
     * @return true, если блокноты идентичны, false иначе.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Notepad notepad = (Notepad) o;

        return (mTitle.equals(notepad.mTitle)
                && mId == notepad.mId
                && mCreatorId == notepad.mCreatorId
                && mCreationDate.equals(notepad.mCreationDate));
    }
    //TODO: а этот метод ТОЧНО то, что мне нужно? Потому что я в сомнениях.

    /**
     * Метод подсчитывает хэш-код блокнота.
     * @return хэш-код блокнота.
     */
    @Override
    public int hashCode() {
        int result = mCreatorId;
        result = 31 * result + (mTitle != null ? mTitle.hashCode() : 0);
        result = 31 * result + (mCreationDate != null ? mCreationDate.hashCode() : 0);
        result = 31 * result + mId;
        return result;
    }

    @Override
    public String toString() {
        return "Notepad \"" + mTitle + "\"";
    }
}
