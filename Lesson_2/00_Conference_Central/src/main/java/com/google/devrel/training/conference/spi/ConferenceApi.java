package com.google.devrel.training.conference.spi;

import static com.google.devrel.training.conference.service.OfyService.ofy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.config.Named;
import com.google.api.server.spi.response.ConflictException;
import com.google.api.server.spi.response.ForbiddenException;
import com.google.api.server.spi.response.NotFoundException;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.users.User;
import com.google.devrel.training.conference.Constants;
import com.google.devrel.training.conference.domain.Announcement;
import com.google.devrel.training.conference.domain.Conference;
import com.google.devrel.training.conference.domain.Profile;
import com.google.devrel.training.conference.form.ConferenceForm;
import com.google.devrel.training.conference.form.ConferenceQueryForm;
import com.google.devrel.training.conference.form.ProfileForm;
import com.google.devrel.training.conference.form.ProfileForm.TeeShirtSize;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Work;
import com.googlecode.objectify.cmd.Query;

import static com.google.devrel.training.conference.service.OfyService.factory;

/**
 * Defines conference APIs.
 */
@Api(name = "conference", version = "v1", 
		scopes = { Constants.SCOPES }, clientIds = {
        Constants.WEB_CLIENT_ID, Constants.ANDROID_CLIENT_ID,
        Constants.API_EXPLORER_CLIENT_ID }, 
        audiences = {Constants.ANDROID_AUDIENCE},
        description = "API for the Conference Central Backend application.")
public class ConferenceApi {

	private static final Logger log = Logger.getLogger(ConferenceApi.class.getName());
	
    /*
     * Get the display name from the user's email. For example, if the email is
     * lemoncake@example.com, then the display name becomes "lemoncake."
     */
    private static String extractDefaultDisplayNameFromEmail(String email) {
        return email == null ? null : email.substring(0, email.indexOf("@"));
    }

    /**
     * Creates or updates a Profile object associated with the given user
     * object.
     *
     * @param user
     *            A User object injected by the cloud endpoints.
     * @param profileForm
     *            A ProfileForm object sent from the client form.
     * @return Profile object just created.
     * @throws UnauthorizedException
     *             when the User object is null.
     */

    // Declare this method as a method available externally through Endpoints
    @ApiMethod(name = "saveProfile", path = "profile", httpMethod = HttpMethod.POST)
    // The request that invokes this method should provide data that
    // conforms to the fields defined in ProfileForm

    // TODO 1 Pass the ProfileForm parameter
    // TODO 2 Pass the User parameter
    public Profile saveProfile(final User user, ProfileForm profileForm) throws UnauthorizedException {
	
    	log.info("ENTER saveProfile");
        String userId = null;
        String mainEmail = null;
        String displayName = "Your name will go here";
        TeeShirtSize teeShirtSize = TeeShirtSize.NOT_SPECIFIED;

        // Set the displayName to the value sent by the ProfileForm, if sent
        // otherwise set it to null
        displayName = profileForm.getDisplayName();
        log.fine("displayName=" + displayName);
        
        // Get the userId and mainEmail
        if(user == null) {
        	log.severe("User is null");
        	throw new UnauthorizedException("Authorization required");
        }
        
        mainEmail = user.getEmail();
        userId = user.getUserId();
                
        boolean updated = false;
        // fetch the profile from the data store
        Profile profile = getProfile(user);
        
        if(profile == null) {
        	
            if(profileForm.getTeeShirtSize() != null)
            	teeShirtSize = profileForm.getTeeShirtSize();
            
        	// If the displayName is null, set it to default value based on the user's email
            // by calling extractDefaultDisplayNameFromEmail(...)
            if(displayName == null) {
            	displayName = extractDefaultDisplayNameFromEmail(user.getEmail());
            }

        	// Create a new Profile entity from the
        	// userId, displayName, mainEmail and teeShirtSize
        	profile = new Profile(userId, displayName, mainEmail, teeShirtSize);
        	updated = true;
        } else {
        	updated = profile.update(profileForm.getDisplayName(), profileForm.getTeeShirtSize());
        }

        if(updated) {
        	// TODO 3 (In Lesson 3)
        	// Save the Profile entity in the data store
        	ofy().save().entity(profile).now();        	
        }

        // Return the profile
        return profile;
    }

    /**
     * Returns a Profile object associated with the given user object. The cloud
     * endpoints system automatically inject the User object.
     *
     * @param user
     *            A User object injected by the cloud endpoints.
     * @return Profile object.
     * @throws UnauthorizedException
     *             when the User object is null.
     */
    @ApiMethod(name = "getProfile", path = "profile", httpMethod = HttpMethod.GET)
    public Profile getProfile(final User user) throws UnauthorizedException {
    	log.info("ENTER getProfile");
        if (user == null) {
        	log.severe("User is null");
            throw new UnauthorizedException("Authorization required");
        }

        // load the Profile Entity
        String userId = user.getUserId();
        Key<Profile> key = Key.create(Profile.class, userId);
        Profile profile = ofy().load().key(key).now();
        
        return profile;
    }
    
    /**
     * Gets the Profile entity for the current user
     * or creates it if it doesn't exist
     * @param user
     * @return user's Profile
     */
    private static Profile getProfileFromUser(User user) {
        // First fetch the user's Profile from the datastore.
        Profile profile = ofy().load().key(
                Key.create(Profile.class, user.getUserId())).now();
        if (profile == null) {
            // Create a new Profile if it doesn't exist.
            // Use default displayName and teeShirtSize
            String email = user.getEmail();
            profile = new Profile(user.getUserId(),
                    extractDefaultDisplayNameFromEmail(email), email, TeeShirtSize.NOT_SPECIFIED);
        }
        return profile;
    }

/**
     * Creates a new Conference object and stores it to the datastore.
     *
     * @param user A user who invokes this method, null when the user is not signed in.
     * @param conferenceForm A ConferenceForm object representing user's inputs.
     * @return A newly created Conference Object.
     * @throws UnauthorizedException when the user is not signed in.
     */
    @ApiMethod(name = "createConference", path = "conference", httpMethod = HttpMethod.POST)
    public Conference createConference(final User user, final ConferenceForm conferenceForm)
        throws UnauthorizedException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }

        final String userId = user.getUserId();
        Key<Profile> profileKey = Key.create(Profile.class, userId);
        final Key<Conference> conferenceKey = factory().allocateId(profileKey, Conference.class);
        final long conferenceId = conferenceKey.getId();
        final Queue queue = QueueFactory.getDefaultQueue();
        
        
        
        Conference conference = ofy().transact(new Work<Conference>() {
        	@Override
        	public Conference run() {
        		Profile profile = getProfileFromUser(user);
        		Conference conference = new Conference(conferenceId, userId, conferenceForm);
        		ofy().save().entities(conference, profile).now();
        		queue.add(ofy().getTransaction(), TaskOptions.Builder.withUrl("/tasks/send_confirmation_email").
                	    param("email", user.getEmail()).param("conferenceInfo", conference.toString()));
        		return conference;
        	}
        });

        return conference;
    }

    //@ApiMethod(name = "saveConference", path = "conference", httpMethod = HttpMethod.POST)
    public Conference saveConference(final User user, final ConferenceForm conferenceForm) throws UnauthorizedException {
    	return null;
    }
    
    @ApiMethod(name = "queryConferences", path = "queryConferences",  httpMethod = HttpMethod.POST)
    public List<Conference> queryConferences(ConferenceQueryForm conferenceQueryForm) {
    	
    	Query<Conference> conferenceIterable = conferenceQueryForm.getQuery();
        List<Conference> result = new ArrayList<>(0);
        List<Key<Profile>> organizersKeyList = new ArrayList<>(0);
        for (Conference conference : conferenceIterable) {
            organizersKeyList.add(Key.create(Profile.class, conference.getOrganizerUserId()));
            result.add(conference);
        }
        // To avoid separate datastore gets for each Conference, pre-fetch the Profiles.
        ofy().load().keys(organizersKeyList);
        return result;

    }
    
    @ApiMethod(name = "getConferencesCreated", path = "getConferencesCreated",  httpMethod = HttpMethod.POST)
    public List<Conference> getConferencesCreated(final User user)
    throws UnauthorizedException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        Key<Profile> key = Key.create(Profile.class, user.getUserId());
        Query<Conference> query = ofy().load().type(Conference.class).ancestor(key).order("name");
        return query.list();
    	
    }
    
    public List<Conference> filterByProperty() {
    	Query<Conference> query = ofy().load().type(Conference.class);
    	query = query.filter("month = ", 6);
    	query =  query.filter("city = ", "London");
    	query = query.filter("maxAttendees > ",10).order("maxAttendees").order("name");
    	return query.list();
    }
    
    @ApiMethod(name = "registerForConference", 
    		   path = "conference/{websafeConferenceKey}/registration",
    		   httpMethod = HttpMethod.POST)
    public WrappedBoolean registerForConference(final User user,
    		@Named("websafeConferenceKey") final String websafeConferenceKey)
    		throws UnauthorizedException, NotFoundException,
    		ForbiddenException, ConflictException {
    	
    	if(user == null) {
    		throw new UnauthorizedException("Authorization required");
    	}
    	
    	final String userId = user.getUserId();
    	
    	// Start transaction
    	WrappedBoolean result = ofy().transact(new Work<WrappedBoolean>() {
    		public WrappedBoolean run () {
    			try {
    				Key<Conference> conferenceKey = Key.create(websafeConferenceKey);
    		        Conference conference = ofy().load().key(conferenceKey).now();
    		        
    		        if(conference == null) {
    		        	return new WrappedBoolean(false, "No Conference found with the key: " + 
    		        								websafeConferenceKey);
    		        }
    		        
    		        Profile profile = getProfile(user);
    		        if(profile.getConferenceKeysToAttend().contains(websafeConferenceKey)) {
    		        	return new WrappedBoolean(false, "Already registered");
    		        } else if(conference.getSeatsAvailable() <= 0) {
    		        	return new WrappedBoolean(false, "No seats available");
    		        } else {
    		        	profile.addToConferenceKeysToAttend(websafeConferenceKey);
    		        	conference.bookSeats(1);
    		        	ofy().save().entities(conference, profile).now();
    		        	return new WrappedBoolean(true, "Registration successful");
    		        }

    			} catch (Exception e) {
    				return new WrappedBoolean(false, "Unknown exception");
    			}	
    		}
    	});
    	
    	if(!result.getResult()) {
    		if(result.getReason().contains("No Conference found with the key")) {
    			throw new NotFoundException(result.getReason());
    		} else if(result.getReason().contains("Already registered")) {
    			throw new ConflictException("You are already registered");
    		} else if (result.getReason().contains("No seats available")) {
    			throw new ConflictException("There are no seats available");
    		} 		
    	}
    	return result;
    }
    
    @ApiMethod(name = "getConference", 
    		   path = "conference/{websafeConferenceKey}", 
    		   httpMethod = HttpMethod.GET)
    public Conference getConference(@Named("websafeConferenceKey") final String websafeConferenceKey)
    		throws NotFoundException {
    	log.info("ENTER getConference");
        
        Key<Conference> key = Key.create(websafeConferenceKey);
        Conference conference = ofy().load().key(key).now();
        
        if(conference == null) {
        	throw new NotFoundException("No Conference found with the key: " + websafeConferenceKey);
        }
        
        
        
        return conference;
    }
    
    /**
     * Returns a collection of Conference Object that the user is going to attend.
     *
     * @param user An user who invokes this method, null when the user is not signed in.
     * @return a Collection of Conferences that the user is going to attend.
     * @throws UnauthorizedException when the User object is null.
     */
    @ApiMethod(
            name = "getConferencesToAttend",
            path = "getConferencesToAttend",
            httpMethod = HttpMethod.GET
    )
    public Collection<Conference> getConferencesToAttend(final User user)
            throws UnauthorizedException, NotFoundException {
    	
        // If not signed in, throw a 401 error.
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        
        Profile profile = ofy().load().key(Key.create(Profile.class, user.getUserId())).now();
        
        if (profile == null) {
            throw new NotFoundException("Profile doesn't exist.");
        }
        
        List<String> keyStringsToAttend = profile.getConferenceKeysToAttend();  
        
        List<Key<Conference>> keysToAttend = new ArrayList<>();
        for (String keyString : keyStringsToAttend) {
            keysToAttend.add(Key.<Conference>create(keyString));
        }
        
        return ofy().load().keys(keysToAttend).values();
        
    }
    
    /**
     * Unregister from the specified Conference.
     *
     * @param user An user who invokes this method, null when the user is not signed in.
     * @param websafeConferenceKey The String representation of the Conference Key 
     * to unregister from.
     * @return Boolean true when success, otherwise false.
     * @throws UnauthorizedException when the user is not signed in.
     * @throws NotFoundException when there is no Conference with the given conferenceId.
     */
    @ApiMethod(
            name = "unregisterFromConference",
            path = "conference/{websafeConferenceKey}/registration",
            httpMethod = HttpMethod.DELETE
    )
    public WrappedBoolean unregisterFromConference(final User user,
            @Named("websafeConferenceKey") final String websafeConferenceKey)
            throws UnauthorizedException, NotFoundException,
            ForbiddenException, ConflictException {
    	
    	if(user == null) {
    		throw new UnauthorizedException("Authorization required");
    	}
    	
    	// Start transaction
    	WrappedBoolean result = ofy().transact(new Work<WrappedBoolean>() {
    		public WrappedBoolean run () {
    			try {
    				Key<Conference> conferenceKey = Key.create(websafeConferenceKey);
    		        Conference conference = ofy().load().key(conferenceKey).now();
    		        
    		        if(conference == null) {
    		        	return new WrappedBoolean(false, "No Conference found with the key: " + 
    		        								websafeConferenceKey);
    		        }
    		        
    		        Profile profile = getProfile(user);
    		        if(!profile.getConferenceKeysToAttend().contains(websafeConferenceKey)) {
    		        	return new WrappedBoolean(false, "User not registered for the conference");
    		        } else {
    		        	profile.unregisterFromConference(websafeConferenceKey);
    		        	conference.giveBackSeats(1);
    		        	ofy().save().entities(conference, profile).now();
    		        	return new WrappedBoolean(true, "Unregistration successful");
    		        }

    			} catch (Exception e) {
    				return new WrappedBoolean(false, "Unknown exception");
    			}	
    		}
    	});
    	
    	if(!result.getResult()) {
    		if(result.getReason().contains("No Conference found with the key")) {
    			throw new NotFoundException(result.getReason());
    		} else if(result.getReason().contains("User not registered for the conference")) {
    			throw new ConflictException("You are not registered");
    		}	
    	}
    	return result;
    	
    }
    
    @ApiMethod(
            name = "getAnnouncement",
            path = "announcement",
            httpMethod = HttpMethod.GET
    )
    public Announcement getAnnouncement() {
    	MemcacheService memcacheService = MemcacheServiceFactory.getMemcacheService();
    	String announcementKey = Constants.MEMCACHE_ANNOUNCEMENTS_KEY;
    	Object message = memcacheService.get(announcementKey);
    	if(message != null) {
    		return new Announcement(message.toString());
    	}
    	return null;
    }
    
    
    
    public static class WrappedBoolean {

        private final Boolean result;
        private final String reason;

        public WrappedBoolean(Boolean result) {
            this.result = result;
            this.reason = "";
        }

        public WrappedBoolean(Boolean result, String reason) {
            this.result = result;
            this.reason = reason;
        }

        public Boolean getResult() {
            return result;
        }

        public String getReason() {
            return reason;
        }
    }
    
}
