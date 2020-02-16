package org.subnode.response;

import org.subnode.response.base.ResponseBase;

public class AddPrivilegeResponse extends ResponseBase {
    private String principalPublicKey;

    /* we send this back to the client, for use as the more efficient way to identify the user after the 
    browser encrypts a key to send back to the server */
    private String principalNodeId;

    public String getPrincipalPublicKey() {
        return this.principalPublicKey;
    }

    public void setPrincipalPublicKey(String key) {
        this.principalPublicKey = key;
    }

    public String getPrincipalNodeId() {
        return this.principalNodeId;
    }

    public void setPrincipalNodeId(String id) {
        this.principalNodeId = id;
    }
}
