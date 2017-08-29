package com.portallium.notekeeper.beans;


import java.io.Serializable;
import java.util.Date;

public class Notepad implements Serializable {

    private int mCreatorId;
    private String mTitle;
    private Date mCreationDate;
    private int mId;

    private String mFirebaseId;

    public Notepad(int creatorId, String title, Date creationDate, int id, String firebaseId) {
        this(creatorId, title, creationDate, id);
        mFirebaseId = firebaseId;
    }

    public Notepad(int creatorId, String title, Date creationDate, int id) {
        this(creatorId, title, id);
        mCreationDate = creationDate;
        //firebaseId инициализируется как null. Это правильно. Это так и надо.
    }

    public Notepad(int creatorId, String title, int id) {
        this(creatorId, title);
        mId = id;
    }

    //то же, что и для Note: id = -1 означает, что заметка еще не внесена в БД.
    public Notepad(int creatorId, String title) {
        mCreatorId = creatorId;
        mTitle = title;
        mCreationDate = new Date();
        mId = -1;
    }

    public Notepad() {} //этот конструктор нужен Firebase.
    //Было бы здорово сделать его приватным (там все равно рефлексия),
    //но, видимо, нельзя.

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

    @Override
    public int hashCode() {
        int result = mCreatorId;
        result = 31 * result + (mTitle != null ? mTitle.hashCode() : 0);
        result = 31 * result + (mCreationDate != null ? mCreationDate.hashCode() : 0);
        result = 31 * result + mId;
        return result;
    }
}
