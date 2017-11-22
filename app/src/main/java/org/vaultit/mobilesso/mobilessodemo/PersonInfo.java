package org.vaultit.mobilesso.mobilessodemo;

import android.graphics.Bitmap;

/**
 * Contains ID06 card info
 */
class PersonInfo {
    private String personName = null;
    private String address = null;
    private String fetchedTime = null;
    private Bitmap photo = null;

    PersonInfo() {}

    PersonInfo(String personName, String address, Bitmap photo) {
        this.personName = personName;
        this.address = address;
        this.photo = photo;

    }

    public String getPersonName() {
        return personName;
    }

    public void setPersonName(String personName) {
        this.personName = personName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Bitmap getPhoto() {
        return photo;
    }

    public void setPhoto(Bitmap photo) {
        this.photo = photo;
    }


    public String getFetchedTime() {
        return fetchedTime;
    }

    public void setFetchedTime(String fetchedTime) {
        this.fetchedTime = fetchedTime;
    }


}
