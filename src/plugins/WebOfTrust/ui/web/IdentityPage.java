/*
 * freenet - IdentityPage.java
 * Copyright © 2008 David Roden
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
package plugins.WebOfTrust.ui.web;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.TimeZone;

import plugins.WebOfTrust.Identity;
import plugins.WebOfTrust.OwnIdentity;
import plugins.WebOfTrust.Trust;
import plugins.WebOfTrust.exceptions.InvalidParameterException;
import plugins.WebOfTrust.exceptions.NotTrustedException;
import plugins.WebOfTrust.exceptions.UnknownIdentityException;

import com.db4o.ObjectSet;

import freenet.clients.http.ToadletContext;
import freenet.l10n.BaseL10n;
import freenet.support.CurrentTimeUTC;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * @author xor (xor@freenetproject.org)
 * @author bombe (bombe@freenetproject.org)
 * @version $Id$
 */
public class IdentityPage extends WebPageImpl {
	
	private final static SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	/** The identity to show trust relationships of. */
	private final Identity identity;


	/**
	 * Creates a new trust-relationship web page.
	 * 
	 * @param toadlet A reference to the {@link WebInterfaceToadlet} which created the page, used to get resources the page needs.
	 * @param myRequest The request sent by the user.
	 * @throws UnknownIdentityException 
	 */
	public IdentityPage(WebInterfaceToadlet toadlet, HTTPRequest myRequest, ToadletContext context, BaseL10n _baseL10n) throws UnknownIdentityException {
		super(toadlet, myRequest, context, _baseL10n);
		
		identity = wot.getIdentityByID(request.getParam("id")); 
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see WebPage#make()
	 */
	public void make() {
		synchronized(wot) {			
			makeURIBox();
			makeServicesBox();
			makeStatisticsBox();
			makeAddTrustBox();
			
			HTMLNode trusteeTrustsNode = addContentBox(l10n().getString("IdentityPage.TrusteeTrustsBox.Header", "nickname", identity.getNickname()));
			HTMLNode trusteesTable = trusteeTrustsNode.addChild("table");
			HTMLNode trusteesTableHeader = trusteesTable.addChild("tr");
			trusteesTableHeader.addChild("th", l10n().getString("IdentityPage.TableHeader.Nickname"));
			trusteesTableHeader.addChild("th", l10n().getString("IdentityPage.TableHeader.Identity"));
			trusteesTableHeader.addChild("th", l10n().getString("IdentityPage.TableHeader.Value"));
			trusteesTableHeader.addChild("th", l10n().getString("IdentityPage.TableHeader.Comment"));

			HTMLNode trusterTrustsNode = addContentBox(l10n().getString("IdentityPage.TrusterTrustsBox.Header", "nickname", identity.getNickname()));
			HTMLNode trustersTable = trusterTrustsNode.addChild("table");
			HTMLNode trustersTableHeader = trustersTable.addChild("tr");
			trustersTableHeader.addChild("th", l10n().getString("IdentityPage.TableHeader.Nickname"));
			trustersTableHeader.addChild("th", l10n().getString("IdentityPage.TableHeader.Identity"));
			trustersTableHeader.addChild("th", l10n().getString("IdentityPage.TableHeader.Value"));
			trustersTableHeader.addChild("th", l10n().getString("IdentityPage.TableHeader.Comment"));
			
			for (Trust trust : wot.getGivenTrusts(identity)) {
				HTMLNode trustRow = trusteesTable.addChild("tr");
				Identity trustee = trust.getTrustee();
				trustRow.addChild("td").addChild("a", "href", "?ShowIdentity&id=" + trustee.getID(), trustee.getNickname());
				trustRow.addChild("td", trustee.getID());
				trustRow.addChild("td", new String[]{"align", "style"}, new String[]{"right", "background-color:" + KnownIdentitiesPage.getTrustColor(trust.getValue()) + ";"}, Byte.toString(trust.getValue()));
				trustRow.addChild("td", trust.getComment());
			}

			for (Trust trust : wot.getReceivedTrusts(identity)) {
				HTMLNode trustRow = trustersTable.addChild("tr");
				Identity truster = trust.getTruster();
				trustRow.addChild("td").addChild("a", "href", "?ShowIdentity&id=" + truster.getID(), truster.getNickname());
				trustRow.addChild("td", truster.getID());
				trustRow.addChild("td", new String[]{"align", "style"}, new String[]{"right", "background-color:" + KnownIdentitiesPage.getTrustColor(trust.getValue()) + ";"}, Byte.toString(trust.getValue()));
				trustRow.addChild("td", trust.getComment());
			}
		}
	}
	
	/**
	 * @author ShadowW4lk3r (ShadowW4lk3r@ye~rQ4m~pu2Iu3O2TH-GOLBbSeKoQ~QR~vC6tJbKmDg.freetalkrc2) - Most of the code
	 * @author xor (xor@freenetproject.org)	- Minor improvements only
	 */
	private void makeAddTrustBox() {
		//Change trust level if needed
		if(request.isPartSet("SetTrust")) {
			String trusterID = request.getPartAsStringFailsafe("OwnerID", 128);
			String trusteeID = request.isPartSet("Trustee") ? request.getPartAsStringFailsafe("Trustee", 128) : null;
			String value = request.getPartAsStringFailsafe("Value", 4).trim();
			// TODO: getPartAsString() will return an empty String if the length is exceeded, it should rather return a too long string so that setTrust throws
			// an exception. It's not a severe problem though since we limit the length of the text input field anyway.
			String comment = request.getPartAsStringFailsafe("Comment", Trust.MAX_TRUST_COMMENT_LENGTH + 1);

			try {
				if(value.equals(""))
					wot.removeTrust(trusterID, trusteeID);
				else
					wot.setTrust(trusterID, trusteeID, Byte.parseByte(value), comment);
			} catch(NumberFormatException e) {
				addErrorBox(l10n().getString("KnownIdentitiesPage.SetTrust.Failed"), l10n().getString("Trust.InvalidValue"));
			} catch(InvalidParameterException e) {
				addErrorBox(l10n().getString("KnownIdentitiesPage.SetTrust.Failed"), e.getMessage());
			} catch(Exception e) {
				addErrorBox(l10n().getString("KnownIdentitiesPage.SetTrust.Failed"), e);
			}
		}

		HTMLNode boxContent = addContentBox(l10n().getString("IdentityPage.ChangeTrustBox.Header", "nickname", identity.getNickname()));

		ObjectSet<OwnIdentity> ownId = wot.getAllOwnEnabledIdentities();

		while(ownId.hasNext()) {
			OwnIdentity treeOwner = ownId.next();
			//Create a block
			// HTMLNode boxContent = addContentBox(l10n().getString("IdentityPage.ChangeTrustBox.Header", "nickname", identity.getNickname()));

			String trustValue = "";
			String trustComment = "";

			try
			{
				Trust trust = wot.getTrust(treeOwner, identity);
				trustValue = String.valueOf(trust.getValue());
				trustComment = trust.getComment();
			}
			catch(NotTrustedException e){
			}
			//Adds a caption
			boxContent.addChild("div").addChild("strong", l10n().getString("IdentityPage.ChangeTrustBox.FromOwnIdentity","nickname",treeOwner.getNickname()));
			HTMLNode trustForm = pr.addFormChild(boxContent, uri+"?id="+identity.getID(), "SetTrust");
			trustForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "page", "SetTrust" });
			trustForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "OwnerID", treeOwner.getID() });
			trustForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "Trustee", identity.getID() });

			// Trust value input field
			trustForm.addChild("span", l10n().getString("KnownIdentitiesPage.AddIdentity.Trust") + ": ");
			trustForm.addChild("input", new String[] { "type", "name", "size", "maxlength", "value" },
					new String[] { "text", "Value", "4", "4", trustValue });

			// Trust comment input field
			trustForm.addChild("span", l10n().getString("KnownIdentitiesPage.AddIdentity.Comment") + ": ");
			trustForm.addChild("input", new String[] { "type", "name", "size", "maxlength", "value" },
					new String[] { "text", "Comment", "50", Integer.toString(Trust.MAX_TRUST_COMMENT_LENGTH), trustComment });

			trustForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "SetTrust", l10n().getString("KnownIdentitiesPage.KnownIdentities.Table.UpdateTrustButton") });
		}
	}
        
	private void makeURIBox() {
        HTMLNode boxContent = addContentBox(l10n().getString("IdentityPage.IdentityUriBox.Header", "nickname", identity.getNickname()));
		boxContent.addChild("p", l10n().getString("IdentityPage.IdentityUriBox.Text"));
		boxContent.addChild("p", identity.getRequestURI().toString());
	}
	
	private void makeServicesBox() {
		HTMLNode boxContent = addContentBox(l10n().getString("IdentityPage.ServicesBox.Header", "nickname", identity.getNickname()));
		Iterator<String> iter = identity.getContexts().iterator();
		StringBuilder contexts = new StringBuilder(128);
		while(iter.hasNext()) {
			contexts.append(iter.next());
			if(iter.hasNext())
				contexts.append(", ");
		}
		boxContent.addChild("p", contexts.toString());
	}
	
	private void makeStatisticsBox() {
		HTMLNode box = addContentBox(l10n().getString("IdentityPage.StatisticsBox.Header", "nickname", identity.getNickname()));
		
		long currentTime = CurrentTimeUTC.getInMillis();
		
		Date addedDate = identity.getAddedDate();
		String addedString;
		synchronized(mDateFormat) {
			mDateFormat.setTimeZone(TimeZone.getDefault());
			addedString = mDateFormat.format(addedDate) + " (" + CommonWebUtils.formatTimeDelta(currentTime - addedDate.getTime(), l10n()) + ")";
		}

		Date lastFetched = identity.getLastFetchedDate();
		String lastFetchedString;
		if(!lastFetched.equals(new Date(0))) {
			synchronized(mDateFormat) {
				mDateFormat.setTimeZone(TimeZone.getDefault());
				/* SimpleDateFormat.format(Date in UTC) does convert to the configured TimeZone. Interesting, eh? */
				lastFetchedString = mDateFormat.format(lastFetched) + " (" + CommonWebUtils.formatTimeDelta(currentTime - lastFetched.getTime(), l10n()) + ")";
			}
		}
		else {
			lastFetchedString = l10n().getString("Common.Never");
		}
		
		box.addChild("p", l10n().getString("IdentityPage.StatisticsBox.Added") + ": " + addedString); 
		box.addChild("p", l10n().getString("IdentityPage.StatisticsBox.LastFetched") + ": " + lastFetchedString);
	}
}
