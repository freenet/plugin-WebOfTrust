/* This code is part of WoT, a plugin for Freenet. It is distributed 
 * under the GNU General Public License, version 2 (or at your option
 * any later version). See http://www.gnu.org/ for details of the GPL. */
package plugins.WebOfTrust.network.input;

import static plugins.WebOfTrust.util.AssertUtil.assertDidNotThrow;

import java.io.Serializable;

import plugins.WebOfTrust.Identity;
import plugins.WebOfTrust.Identity.IdentityID;
import plugins.WebOfTrust.Persistent;
import plugins.WebOfTrust.Trust;
import plugins.WebOfTrust.Trust.TrustID;
import plugins.WebOfTrust.WebOfTrust;
import freenet.keys.FreenetURI;
import freenet.keys.USK;

/**
 * An EditionHint advertises the latest {@link FreenetURI#getEdition() USK edition} an
 * {@link Identity} has discovered of another Identity.
 * 
 * An {@link USK} {@link FreenetURI} is an updateable key where each update is published at an
 * incremented long integer called the "edition" of the USK.
 * Determining the latest edition N can result in O(N) network queries on the Freenet network.
 * As all M WoT {@link Identity}s are published at USKs, we want to avoid creating the high
 * O(N * M) load required to determine the editions of all their USKs.
 * To do so, Identitys publish EditionHints for other identities. They're transported as "bonus"
 * payload when an Identity uploads its {@link Trust} ratings of other identities.
 * So when we download an edition of an Identity, we automatically get an EditionHint for all the
 * other Identitys it trusts.
 * 
 * (Also, keeping an USK up to date requires a constant polling load on the network, so with M
 * identities, there would be O(M) polls on the network repeating at some time interval forever.)
 * 
 * @see IdentityDownloader#storeUpdateEditionHintCommandWithoutCommit(String, String, long) */
public final class EditionHint extends Persistent implements Comparable<EditionHint> {

	/** @see Serializable */
	private static final long serialVersionUID = 1L;


	private final String mSourceIdentityID;

	private final String mTargetIdentityID;
	
	/** @see #getID() */
	@IndexedField
	private final String mID;

	private final long mEdition;


	/** Factory with parameter validation */
	static EditionHint constructSecure(
			String sourceIdentityID, String targetIdentityID, long edition) {
		
		IdentityID.constructAndValidateFromString(sourceIdentityID);
		IdentityID.constructAndValidateFromString(targetIdentityID);
		if(sourceIdentityID.equals(targetIdentityID)) {
			throw new IllegalArgumentException(
				"Identity is trying to assign edition hint to itself, ID: " + sourceIdentityID);
		}
		
		if(edition < 0)
			throw new IllegalArgumentException("Invalid edition: " + edition);
		
		return new EditionHint(sourceIdentityID, targetIdentityID, edition);
	}

	/** Factory WITHOUT parameter validation */
	static EditionHint construcInsecure(
			final String sourceIdentityID, final String targetIdentityID, final long edition) {
		
		assertDidNotThrow(new Runnable() { @Override public void run() {
			constructSecure(sourceIdentityID, targetIdentityID, edition);
		}});
		
		return new EditionHint(sourceIdentityID, targetIdentityID, edition);
	}

	private EditionHint(String sourceIdentityID, String targetIdentityID, long edition) {
		mSourceIdentityID = sourceIdentityID;
		mTargetIdentityID = targetIdentityID;
		mEdition = edition;
		mID = new TrustID(mSourceIdentityID, mTargetIdentityID).toString();
	}

	String getSourceIdentityID() {
		// String is a db4o primitive type so 1 is enough even though it is a reference type
		checkedActivate(1);
		return mSourceIdentityID;
	}

	String getTargetIdentityID() {
		// String is a db4o primitive type so 1 is enough even though it is a reference type
		checkedActivate(1);
		return mTargetIdentityID;
	}

	long getEdition() {
		checkedActivate(1);
		return mEdition;
	}

	@Override public int compareTo(EditionHint o) {
		// FIXME: Implement.
		return 0;
	}

	/** Equals: <code>new TrustID(getSourceIdentityID(), getTargetIdentityID()).toString()</code> */
	@Override public String getID() {
		// String is a db4o primitive type so 1 is enough even though it is a reference type
		checkedActivate(1);
		return mID;
	}

	@Override public void startupDatabaseIntegrityTest() throws Exception {
		activateFully();
		
		// Will throw if any of the three passed member variables is invalid.
		// mID is generated from them, so we keep the returned object to validate it.
		EditionHint this2 = constructSecure(mSourceIdentityID, mTargetIdentityID, mEdition);
		
		if(!mID.equals(this2.mID))
			throw new IllegalStateException("mID invalid: " + this);
		
		// mWebOfTrust is of type WebOfTrustInterface which doesn't contain special functions of
		// the specific implementation which we need for testing our stuff - so we cast to the
		// implementation.
		// This is ok to do here: This function being a debug one for the specific implementation
		// is fine to depend on technical details of it.
		WebOfTrust wot = ((WebOfTrust)mWebOfTrust);
		
		Identity source = wot.getIdentityByID(mSourceIdentityID);
		Identity target = wot.getIdentityByID(mTargetIdentityID);
		
		if(wot.getBestCapacity(source) == 0) {
			throw new IllegalStateException(
				"Identity which isn't allowed to store hints has stored one: " + this);
		}
		
		if(mEdition <= target.getEdition())
			throw new IllegalStateException("Hint is obsolete: " + this);
		
		// The legacy hinting implementation Identity.getLatestEditionHint() stores only the highest
		// edition hint we received from any identity with a Score of > 0.
		if(mEdition > target.getLatestEditionHint() && wot.getBestScore(source) > 0)
			throw new IllegalStateException("Legacy getLatestEditionHint() too low for: " + target);
		
		// FIXME: Once IdentityDownloader has a query function for obtaining an EditionHint by mID,
		// check whether there is only one edition hint for the given ID.
	}

	/**
	 * Activates to depth 1 which is the maximal depth of all getter functions.
	 * You must adjust this when introducing new member variables! */
	@Override protected void activateFully() {
		checkedActivate(1);
	}

	@Override public String toString() {
		activateFully();
		return "[EditionHint: " + super.toString()
		     + "; mID: " + mID
		     + "; mSourceIdentityID: " + mSourceIdentityID
		     + "; mTargetIdentityID: " + mTargetIdentityID
		     + "; mEdition: " + mEdition
		     + "]";
	}

}