/* This code is part of WoT, a plugin for Freenet. It is distributed
 * under the GNU General Public License, version 2 (or at your option
 * any later version). See http://www.gnu.org/ for details of the GPL. */
package plugins.WebOfTrust;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Random;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import plugins.WebOfTrust.Trust.TrustID;
import plugins.WebOfTrust.exceptions.DuplicateTrustException;
import plugins.WebOfTrust.exceptions.InvalidParameterException;
import plugins.WebOfTrust.exceptions.NotTrustedException;
import plugins.WebOfTrust.exceptions.UnknownIdentityException;
import plugins.WebOfTrust.util.RandomGrabHashSet;
import freenet.crypt.DummyRandomSource;
import freenet.crypt.RandomSource;
import freenet.keys.FreenetURI;
import freenet.keys.InsertableClientSSK;

/**
 * The base class for all JUnit4 tests in WOT.<br>
 * Contains utilities useful for all tests such as deterministic random number generation. 
 * 
 * @author xor (xor@freenetproject.org)
 */
@Ignore("Is ignored so it can be abstract. If you need to add self-tests, use member classes, "
    +   "they likely won't be ignored. But then also check that to make sure.")
public abstract class AbstractJUnit4BaseTest {

    protected RandomSource mRandom;
    
    @Rule
    public final TemporaryFolder mTempFolder = new TemporaryFolder();
    
    
    @Before public void setupRandomNumberGenerator() {
        Random seedGenerator = new Random();
        long seed = seedGenerator.nextLong();
        mRandom = new DummyRandomSource(seed);
        System.out.println(this + " Random seed: " + seed);
    }

    /**
     * Will be used as backend by member functions which generate random
     * {@link Identity} / {@link Trust} / {@link Score} objects. 
     */
    protected abstract WebOfTrust getWebOfTrust();

    /**
     * Adds identities with random request URIs to the database.
     * Their state will be as if they have never been fetched: They won't have a nickname, edition
     * will be 0, etc.
     * 
     * TODO: Make sure that this function also adds random contexts & publish trust list flags.
     * 
     * @param count Amount of identities to add
     * @return An {@link ArrayList} which contains all added identities.
     */
    protected ArrayList<Identity> addRandomIdentities(int count) {
        ArrayList<Identity> result = new ArrayList<Identity>(count+1);
        
        while(count-- > 0) {
            try {
                result.add(getWebOfTrust().addIdentity(getRandomRequestURI().toString()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        
        return result;
    }

    /**
     * Generates {@link OwnIdentity}s with:<br>
     * - a valid random insert URI and request URI.<br>
     * - a valid a random Latin nickname with length of {@link Identity#MAX_NICKNAME_LENGTH}.<br>
     * - a random setting for {@link Identity#doesPublishTrustList()}.<br>
     * - a random context with length {@link Identity#MAX_CONTEXT_NAME_LENGTH}.<br><br>
     * 
     * The OwnIdentitys are stored in the WOT database, and the original (= non-cloned) objects are
     * returned in an {@link ArrayList}.
     * 
     * @throws MalformedURLException
     *             Should never happen.
     * @throws InvalidParameterException
     *             Should never happen.
     */
    protected ArrayList<OwnIdentity> addRandomOwnIdentities(int count)
            throws MalformedURLException, InvalidParameterException {
        
        ArrayList<OwnIdentity> result = new ArrayList<OwnIdentity>(count+1);
        
        while(count-- > 0) {
            final OwnIdentity ownIdentity = getWebOfTrust().createOwnIdentity(getRandomInsertURI(),
                getRandomLatinString(Identity.MAX_NICKNAME_LENGTH), mRandom.nextBoolean(),
                getRandomLatinString(Identity.MAX_CONTEXT_NAME_LENGTH));
            result.add(ownIdentity); 
        }
        
        return result;
        
    }
    
    /**
     * ATTENTION: Its impossible to store more trust values than the amount of identities squared:
     * There can only be a single trust value between each pair of identities. The amount of such
     * pairs is identities². If you specify a trustCount which is higher than this value then this
     * function will run into an infinite loop.
     * 
     * TODO: Adapt this to respect {@link Identity#doesPublishTrustList()}. First you need to adapt
     * the callers of this function to actually use identities which have set this to true - most
     * callers generate identities with the default value which is false.
     */
    protected void addRandomTrustValues(final ArrayList<Identity> identities, final int trustCount)
            throws InvalidParameterException {
        
        final int identityCount = identities.size();
        
        getWebOfTrust().beginTrustListImport();
        for(int i=0; i < trustCount; ++i) {
            Identity truster = identities.get(mRandom.nextInt(identityCount));
            Identity trustee = identities.get(mRandom.nextInt(identityCount));
            
            if(truster == trustee) { // You cannot assign trust to yourself
                --i;
                continue;
            }
            
            try {
                // Only one trust value can exist between a given pair of identities:
                // We are bound to generate an amount of trustCount values,
                // so we have to check whether this pair of identities already has a trust.
                getWebOfTrust().getTrust(truster, trustee);
                --i;
                continue;
            } catch(NotTrustedException e) {}
            
            
            getWebOfTrust().setTrustWithoutCommit(truster, trustee, getRandomTrustValue(),
                getRandomLatinString(mRandom.nextInt(Trust.MAX_TRUST_COMMENT_LENGTH+1)));
        }
        getWebOfTrust().finishTrustListImport();
        Persistent.checkedCommit(getWebOfTrust().getDatabase(), this);
    }

    protected void doRandomChangesToWOT(int eventCount) throws DuplicateTrustException, NotTrustedException, InvalidParameterException, UnknownIdentityException, MalformedURLException {
        @Ignore
        class Randomizer {
            final RandomGrabHashSet<String> allOwnIdentities = new RandomGrabHashSet<String>(mRandom);
            final RandomGrabHashSet<String> allIdentities = new RandomGrabHashSet<String>(mRandom);
            final RandomGrabHashSet<String> allTrusts = new RandomGrabHashSet<String>(mRandom);
            
            Randomizer() { 
                for(Identity identity : mWoT.getAllIdentities())
                    allIdentities.addOrThrow(identity.getID());
                
                for(OwnIdentity ownIdentity : mWoT.getAllOwnIdentities())
                    allOwnIdentities.addOrThrow(ownIdentity.getID());
                
                for(Trust trust : mWoT.getAllTrusts())
                    allTrusts.addOrThrow(trust.getID());
            }
        }
        final Randomizer randomizer = new Randomizer();
        
        final int eventTypeCount = 15;
        final long[] eventDurations = new long[eventTypeCount];
        final int[] eventIterations = new int[eventTypeCount];
        
        for(int i=0; i < eventCount; ++i) {
            final int type = mRandom.nextInt(eventTypeCount);
            final long startTime = System.nanoTime();
            switch(type) {
                case 0: // WebOfTrust.createOwnIdentity()
                    {
                        final OwnIdentity identity = mWoT.createOwnIdentity(
                                    getRandomSSKPair()[0], 
                                    getRandomLatinString(Identity.MAX_NICKNAME_LENGTH), 
                                    mRandom.nextBoolean(),
                                    getRandomLatinString(Identity.MAX_CONTEXT_NAME_LENGTH)
                                );
                        randomizer.allIdentities.addOrThrow(identity.getID());
                        randomizer.allOwnIdentities.addOrThrow(identity.getID());
                    }
                    break;
                case 1: // WebOfTrust.deleteOwnIdentity()
                    {
                        final String original = randomizer.allOwnIdentities.getRandom();
                        mWoT.deleteOwnIdentity(original);
                        randomizer.allIdentities.remove(original);
                        randomizer.allOwnIdentities.remove(original);
                        // Dummy non-own identity which deleteOwnIdenity() has replaced it with.
                        final Identity surrogate = mWoT.getIdentityByID(original);
                        assertFalse(surrogate.getClass().equals(OwnIdentity.class));
                        randomizer.allIdentities.addOrThrow(surrogate.getID());
                    }
                    break;
                case 2: // WebOfTrust.restoreOwnIdentity()
                    {
                        final FreenetURI[] keypair = getRandomSSKPair();
                        mWoT.restoreOwnIdentity(keypair[0]);
                        final String id = mWoT.getOwnIdentityByURI(keypair[1]).getID();
                        randomizer.allIdentities.addOrThrow(id);
                        randomizer.allOwnIdentities.addOrThrow(id);
                    }
                    break;
                case 3: // WebOfTrust.restoreOwnIdentity() with previously existing non-own version of it
                    {
                        final FreenetURI[] keypair = getRandomSSKPair();
                        mWoT.addIdentity(keypair[1].toString());
                        mWoT.restoreOwnIdentity(keypair[0]);
                        final String id = mWoT.getOwnIdentityByURI(keypair[1]).getID();
                        randomizer.allIdentities.addOrThrow(id);
                        randomizer.allOwnIdentities.addOrThrow(id);
                    }
                    break;
                case 4: // WebOfTrust.addIdentity()
                    randomizer.allIdentities.addOrThrow(mWoT.addIdentity(getRandomRequestURI().toString()).getID());
                    break;
                case 5: // WebOfTrust.addContext() (adds context to identity)
                    {
                        final String ownIdentityID = randomizer.allOwnIdentities.getRandom();
                        final String context = getRandomLatinString(Identity.MAX_CONTEXT_NAME_LENGTH);
                        mWoT.addContext(ownIdentityID, context);
                        if(mRandom.nextBoolean())
                            mWoT.removeContext(ownIdentityID, context);
                    }
                    break;
                case 6: // WebOfTrust.setProperty (adds property to identity)
                    {
                        final String ownIdentityID = randomizer.allOwnIdentities.getRandom();
                        final String propertyName = getRandomLatinString(Identity.MAX_PROPERTY_NAME_LENGTH);
                        final String propertyValue = getRandomLatinString(Identity.MAX_PROPERTY_VALUE_LENGTH);
                        mWoT.setProperty(ownIdentityID, propertyName, propertyValue);
                        if(mRandom.nextBoolean())
                            mWoT.removeProperty(ownIdentityID, propertyName);
                    }
                    break;
                case 7: // Add/change trust value. Higher probability because trust values are the most changes which will happen on the real network
                case 8:
                case 9:
                case 10:
                case 11:
                case 12:
                case 13:
                    {
                        Identity truster;
                        Identity trustee;
                        do {
                            truster = mWoT.getIdentityByID(randomizer.allIdentities.getRandom());
                            trustee = mWoT.getIdentityByID(randomizer.allIdentities.getRandom());
                        } while(truster == trustee);
                        
                        mWoT.beginTrustListImport();
                        mWoT.setTrustWithoutCommit(truster, trustee, getRandomTrustValue(), getRandomLatinString(Trust.MAX_TRUST_COMMENT_LENGTH));
                        mWoT.finishTrustListImport();
                        Persistent.checkedCommit(mWoT.getDatabase(), this);
                        
                        final String trustID = new TrustID(truster, trustee).toString();
                        if(!randomizer.allTrusts.contains(trustID)) // We selected the truster/trustee randomly so a value may have existed
                            randomizer.allTrusts.addOrThrow(trustID); 
                    }
                    break;
                case 14: // Remove trust value
                    {
                        mWoT.beginTrustListImport();
                        final Trust trust = mWoT.getTrust(randomizer.allTrusts.getRandom());
                        mWoT.removeTrustWithoutCommit(trust);
                        mWoT.finishTrustListImport();
                        Persistent.checkedCommit(mWoT.getDatabase(), this);
                        
                        randomizer.allTrusts.remove(trust.getID());
                    }
                    break;
                default:
                    throw new RuntimeException("Please adapt eventTypeCount above!");
            }
            final long endTime = System.nanoTime();
            eventDurations[type] += (endTime-startTime);
            ++eventIterations[type];
        }
        
        for(int i=0; i < eventTypeCount; ++i) {
            System.out.println("Event type " + i + ": Happend " + eventIterations[i] + " times; "
                    + "avg. seconds: " + (((double)eventDurations[i])/eventIterations[i]) / (1000*1000*1000));
        }
    }

    /**
     * Returns a normally distributed value with a bias towards positive trust values.
     * TODO: Remove this bias once trust computation is equally fast for negative values;
     */
    private byte getRandomTrustValue() {
        final double trustRange = Trust.MAX_TRUST_VALUE - Trust.MIN_TRUST_VALUE + 1;
        long result;
        do {
            result = Math.round(mRandom.nextGaussian()*(trustRange/2) + (trustRange/3));
        } while(result < Trust.MIN_TRUST_VALUE || result > Trust.MAX_TRUST_VALUE);
        
        return (byte)result;
    }

    /**
     * Generates a random SSK insert URI, for being used when creating {@link OwnIdentity}s.
     */
    protected FreenetURI getRandomInsertURI() {
        return InsertableClientSSK.createRandom(mRandom, "").getInsertURI();
    }

    /**
     * Generates a random SSK request URI, suitable for being used when creating identities.
     */
    protected FreenetURI getRandomRequestURI() {
        return InsertableClientSSK.createRandom(mRandom, "").getURI();
    }
    
    /**
     * Generates a String containing random characters of the lowercase Latin alphabet.
     * @param The length of the returned string.
     */
    protected String getRandomLatinString(int length) {
        char[] s = new char[length];
        for(int i=0; i<length; ++i)
            s[i] = (char)('a' + mRandom.nextInt(26));
        return new String(s);
    }
}