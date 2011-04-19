/**
 * 
 */
package plugins.WebOfTrust.ui.web;

import plugins.WebOfTrust.OwnIdentity;
import freenet.clients.http.ToadletContext;
import freenet.l10n.BaseL10n;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * @author sima (msizenko@gamil.com)
 */
public class EnableOwnIdentityPage extends WebPageImpl {

	public EnableOwnIdentityPage(WebInterfaceToadlet toadlet, HTTPRequest myRequest, ToadletContext context, BaseL10n _baseL10n) {
		super(toadlet, myRequest, context, _baseL10n);
	}

	public void make() {
		try {
			OwnIdentity mIdentity = wot.getOwnIdentityByID(request.getPartAsStringThrowing("id", 128));
			if(request.isPartSet("enable")) {
				wot.setDisabledState(mIdentity.getID(), false);
				
				/* TODO: Show the OwnIdentities page instead! Use the trick which Freetalk does for inlining pages */
				HTMLNode box = addContentBox(l10n().getString("EnableOwnIdentityPage.IdentityEnabled.Header"));
				box.addChild("#", l10n().getString("EnableOwnIdentityPage.IdentityEnabled.Text"));
			}
		} catch (Exception e) {
			addErrorBox(l10n().getString("EnableOwnIdentityPage.EnableFailed"), e);
		}
	}


}
