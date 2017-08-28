package com.portallium.notekeeper.beans;


import java.io.Serializable;
import java.util.Date;

public class Notepad implements Serializable {

    private int mCreatorId;
    private String mTitle;
    private Date mCreationDate;
    private int mId;

    public Notepad(int creatorId, String title, Date creationDate, int id) {
        this(creatorId, title, id);
        mCreationDate = creationDate;
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

}
