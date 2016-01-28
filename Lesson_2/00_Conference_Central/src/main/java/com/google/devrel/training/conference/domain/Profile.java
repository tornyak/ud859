package com.google.devrel.training.conference.domain;

import java.util.ArrayList;
import java.util.List;

import com.google.devrel.training.conference.form.ProfileForm.TeeShirtSize;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;


@Entity
@Cache
public class Profile {
	String displayName;
	String mainEmail;
	TeeShirtSize teeShirtSize;
	
	private List <String> conferenceKeysToAttend = new ArrayList<> (0);

	@Id
	String userId;
    
    /**
     * Public constructor for Profile.
     * @param userId The user id, obtained from the email
     * @param displayName Any string user wants us to display him/her on this system.
     * @param mainEmail User's main e-mail address.
     * @param teeShirtSize The User's tee shirt size
     * 
     */
    public Profile (String userId, String displayName, String mainEmail, TeeShirtSize teeShirtSize) {
    	this.userId = userId;
    	this.displayName = displayName;
    	this.mainEmail = mainEmail;
    	this.teeShirtSize = teeShirtSize;
    }
    
	public String getDisplayName() {
		return displayName;
	}

	public String getMainEmail() {
		return mainEmail;
	}

	public TeeShirtSize getTeeShirtSize() {
		return teeShirtSize;
	}

	public String getUserId() {
		return userId;
	}
	
	public List <String> getConferenceKeysToAttend() {
		return conferenceKeysToAttend;
	}

	public void addToConferenceKeysToAttend(String conferenceKey) {
		conferenceKeysToAttend.add(conferenceKey);
	}
	
	public void unregisterFromConference(String conferenceKey) {
		if(conferenceKeysToAttend.contains(conferenceKey)) {
			conferenceKeysToAttend.remove(conferenceKey);
		} else {
			throw new IllegalArgumentException("Invalid conferenceKey: " + conferenceKey);
		}
	}

	public boolean update(String displayName, TeeShirtSize teeShirtSize) {
		boolean updated = false;
		if(displayName != null && !this.displayName.equals(displayName)) {
			this.displayName = displayName;
			updated = true;
		}
		
		if(teeShirtSize != null && this.teeShirtSize != teeShirtSize) {
			this.teeShirtSize = teeShirtSize;
			updated = true;
		}
		return updated;
	}

	/**
     * Just making the default constructor private.
     */
    private Profile() {}

}
